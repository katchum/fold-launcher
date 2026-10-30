package com.xsun.foldlauncher.search

import com.xsun.foldlauncher.model.AppEntry
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

object SearchEngine {

    /**
     * Physical QWERTY + foldable split-keyboard key adjacency map (`a`..`z`).
     * Includes split-keyboard inner seam neighbors (`t`/`y`, `g`/`h`, `v`/`b`).
     */
    private val QWERTY_NEIGHBORS: Array<String> = arrayOf(
        /* a */ "qwsxz",
        /* b */ "vghn",
        /* c */ "xdfv",
        /* d */ "wersfvcx",
        /* e */ "wsdrf",
        /* f */ "ertdgvbc",
        /* g */ "rtyfhvbn",
        /* h */ "tyugjbvn",
        /* i */ "ujklo",
        /* j */ "yuihknbm",
        /* k */ "uiojlm",
        /* l */ "iopk",
        /* m */ "njk",
        /* n */ "bhjm",
        /* o */ "iklp",
        /* p */ "ol",
        /* q */ "wa",
        /* r */ "edfgt",
        /* s */ "qwedcxza",
        /* t */ "rfghy",
        /* u */ "yhjki",
        /* v */ "cfgb",
        /* w */ "qeasd",
        /* x */ "zsdc",
        /* y */ "tghju",
        /* z */ "asx"
    )

    /**
     * Ranks installed apps against a user query using Spotlight-style scoring:
     * 1. Exact match (1000 pts)
     * 2. Prefix match on full label (850 pts)
     * 3. Acronym match, e.g. "yt" -> "YouTube" or "gm" -> "Google Maps" (750 pts)
     * 4. Word-boundary prefix match (650 pts)
     * 5. Substring match in label (450..500 pts)
     * 6. QWERTY-aware typo-tolerant match on full label / word tokens / prefixes (160..425 pts)
     * 7. Substring match in package name (300 pts)
     * 8. Fuzzy subsequence match in label (200 pts)
     * Plus a frequency boost from launchCount.
     */
    fun rankApps(apps: List<AppEntry>, rawQuery: String, limit: Int = 24): List<AppEntry> {
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty()) {
            return apps
                .sortedWith(compareByDescending<AppEntry> { it.launchCount }.thenBy { it.normalizedLabel })
                .take(limit)
        }

        val scored = mutableListOf<Pair<AppEntry, Int>>()
        for (app in apps) {
            val baseScore = scoreApp(app.normalizedLabel, app.acronym, app.packageName.lowercase(), query)
            if (baseScore > 0) {
                val freqBoost = (app.launchCount.coerceAtMost(50)) * 3
                scored.add(app to (baseScore + freqBoost))
            }
        }

        return scored
            .sortedWith(
                compareByDescending<Pair<AppEntry, Int>> { it.second }
                    .thenBy { it.first.normalizedLabel.length }
                    .thenBy { it.first.normalizedLabel }
            )
            .map { it.first }
            .take(limit)
    }

    internal fun scoreApp(
        normalizedLabel: String,
        acronym: String,
        packageName: String,
        query: String
    ): Int {
        if (query.isEmpty()) return 100
        if (normalizedLabel == query) return 1000
        if (normalizedLabel.startsWith(query)) return 850
        if (acronym.length >= 2 && acronym.startsWith(query)) return 750

        val words = normalizedLabel.split(Regex("[\\s\\-_/.]+")).filter { it.isNotEmpty() }
        if (words.any { it.startsWith(query) }) return 650

        val labelIdx = normalizedLabel.indexOf(query)
        if (labelIdx >= 0) return 500 - labelIdx.coerceAtMost(50)

        var bestFuzzyScore = 0
        if (packageName.contains(query)) {
            bestFuzzyScore = 300
        }

        if (query.length >= 3) {
            val typoScore = computeTypoMatchScore(normalizedLabel, words, query)
            if (typoScore > bestFuzzyScore) {
                bestFuzzyScore = typoScore
            }
        }

        if (query.length >= 2 && isSubsequence(query, normalizedLabel)) {
            if (200 > bestFuzzyScore) {
                bestFuzzyScore = 200
            }
        }

        return bestFuzzyScore
    }

    /**
     * Returns true if [a] and [b] are physically adjacent keys on a QWERTY or split foldable keyboard.
     */
    internal fun isKeyboardAdjacent(a: Char, b: Char): Boolean {
        val idxA = a - 'a'
        if (idxA !in 0..25) return false
        return QWERTY_NEIGHBORS[idxA].indexOf(b) >= 0
    }

    /**
     * Evaluates typo-tolerant similarity between [query] (length >= 3) and:
     * 1. Each word token in [words] (and its prefixes of length `query.length - 1 .. query.length + 1`),
     * 2. The full [normalizedLabel] (and its prefixes of length `query.length - 1 .. query.length + 1`).
     *
     * Uses QWERTY-weighted Optimal String Alignment (Damerau-Levenshtein) + Winkler prefix bonus +
     * strict LCS & initial-character guards to eliminate false positives.
     */
    internal fun computeTypoMatchScore(
        normalizedLabel: String,
        words: List<String>,
        query: String
    ): Int {
        val qLen = query.length
        if (qLen < 3 || normalizedLabel.isEmpty()) return 0

        val maxAllowedDistance = when {
            qLen == 3 -> 1.0f
            qLen in 4..6 -> 1.65f
            else -> 2.0f
        }
        val minRequiredLcs = if (qLen <= 5) qLen - 1 else qLen - 2

        var bestScore = 0

        fun evaluateCandidate(
            candidate: String,
            isFullLabel: Boolean,
            isCompleteWord: Boolean,
            wordIndex: Int
        ) {
            if (candidate.length < 2) return
            if (abs(candidate.length - qLen) > 2) return
            if (!passesInitialCharGuard(query, candidate)) return

            val lcs = longestCommonSubsequenceLength(query, candidate)
            if (lcs < minRequiredLcs) return

            val dist = weightedDamerauLevenshtein(query, candidate)
            if (dist > maxAllowedDistance) return
            // For 3-letter queries, require either a QWERTY-neighbor edit (<= 0.65) or matching 1st char
            if (qLen == 3 && dist > 0.65f && query[0] != candidate[0]) return

            val prefixLen = commonPrefixLength(query, candidate).coerceAtMost(4)
            val prefixBonus = prefixLen * 8
            val matchTypeBonus = when {
                isFullLabel -> 25
                isCompleteWord -> if (wordIndex == 0) 12 else 4
                else -> -15 - (wordIndex * 6)
            }

            val rawScore = 430 - (dist * 90f).roundToInt() + prefixBonus + matchTypeBonus
            val clamped = rawScore.coerceIn(140, 425)
            if (clamped > bestScore) {
                bestScore = clamped
            }
        }

        // 1. Evaluate each word token and its prefix slices around query.length
        for ((index, word) in words.withIndex()) {
            evaluateCandidate(
                candidate = word,
                isFullLabel = words.size == 1,
                isCompleteWord = true,
                wordIndex = index
            )
            if (word.length > qLen) {
                for (sliceLen in (qLen - 1)..(qLen + 1)) {
                    if (sliceLen in 3 until word.length) {
                        evaluateCandidate(
                            candidate = word.substring(0, sliceLen),
                            isFullLabel = false,
                            isCompleteWord = false,
                            wordIndex = index
                        )
                    }
                }
            }
        }

        // 2. Evaluate full normalizedLabel if multi-word
        if (words.size > 1) {
            val compactLabel = normalizedLabel.replace(" ", "")
            evaluateCandidate(
                candidate = compactLabel,
                isFullLabel = true,
                isCompleteWord = true,
                wordIndex = 0
            )
            if (compactLabel.length > qLen) {
                val slice = compactLabel.substring(0, qLen)
                evaluateCandidate(
                    candidate = slice,
                    isFullLabel = false,
                    isCompleteWord = false,
                    wordIndex = 0
                )
            }
        }

        return bestScore
    }

    private fun passesInitialCharGuard(query: String, candidate: String): Boolean {
        val q0 = query[0]
        val c0 = candidate[0]
        if (q0 == c0) return true
        if (isKeyboardAdjacent(q0, c0)) return true
        // Allow adjacent transposition of the first two characters (e.g. "hcome" -> "chrome" or "bur" -> "uber")
        if (query.length >= 2 && candidate.length >= 2 && query[0] == candidate[1] && query[1] == candidate[0]) {
            return true
        }
        return false
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val maxLen = min(a.length, b.length)
        var i = 0
        while (i < maxLen && a[i] == b[i]) {
            i++
        }
        return i
    }

    private fun longestCommonSubsequenceLength(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = IntArray(n + 1)
        for (i in 1..m) {
            var prevDiag = 0
            val ca = a[i - 1]
            for (j in 1..n) {
                val temp = dp[j]
                if (ca == b[j - 1]) {
                    dp[j] = prevDiag + 1
                } else if (dp[j - 1] > dp[j]) {
                    dp[j] = dp[j - 1]
                }
                prevDiag = temp
            }
        }
        return dp[n]
    }

    /**
     * Computes the QWERTY-weighted Optimal String Alignment (Damerau-Levenshtein) distance:
     * - Substitution of physically adjacent QWERTY/split-keyboard keys: `0.5f`
     * - Adjacent character transposition (`ab` <-> `ba`): `0.6f`
     * - Deletion of an accidentally fat-fingered duplicate/adjacent key in [source]: `0.65f`
     * - Standard insertion / deletion / non-adjacent substitution: `1.0f`
     */
    internal fun weightedDamerauLevenshtein(source: String, target: String): Float {
        if (source == target) return 0f
        val m = source.length
        val n = target.length
        if (m == 0) return n.toFloat()
        if (n == 0) return m.toFloat()

        var prev2 = FloatArray(n + 1)
        var prev1 = FloatArray(n + 1) { it.toFloat() }
        var curr = FloatArray(n + 1)

        for (i in 1..m) {
            val sc = source[i - 1]
            curr[0] = i.toFloat()
            for (j in 1..n) {
                val tc = target[j - 1]
                val subCost = when {
                    sc == tc -> 0f
                    isKeyboardAdjacent(sc, tc) -> 0.5f
                    else -> 1.0f
                }

                // Deletion from source (extra char typed by user): slightly discounted if fat-finger neighbor
                val isFatFingerExtraChar = (i >= 2 && (sc == source[i - 2] || isKeyboardAdjacent(sc, source[i - 2]))) ||
                    (i < m && (sc == source[i] || isKeyboardAdjacent(sc, source[i])))
                val delCost = if (isFatFingerExtraChar) 0.65f else 1.0f

                val deletion = prev1[j] + delCost
                val insertion = curr[j - 1] + 1.0f
                val substitution = prev1[j - 1] + subCost

                var best = min(min(deletion, insertion), substitution)

                if (i > 1 && j > 1 && sc == target[j - 2] && source[i - 2] == tc) {
                    val transposition = prev2[j - 2] + 0.6f
                    if (transposition < best) {
                        best = transposition
                    }
                }
                curr[j] = best
            }
            val tmp = prev2
            prev2 = prev1
            prev1 = curr
            curr = tmp
        }

        return prev1[n]
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var nIdx = 0
        var hIdx = 0
        while (nIdx < needle.length && hIdx < haystack.length) {
            if (needle[nIdx] == haystack[hIdx]) {
                nIdx++
            }
            hIdx++
        }
        return nIdx == needle.length
    }

    /**
     * Evaluates simple inline math expressions like "120 * 4.5" or "(1920 - 1080) / 2"
     * right inside the Spotlight search bar. Returns null if the query is not a math expression.
     */
    fun evaluateInlineMath(rawQuery: String): String? {
        val cleaned = rawQuery.trim()
        if (cleaned.length < 3) return null
        // Must contain at least one digit and one operator, and only valid math chars
        if (!cleaned.any { it.isDigit() }) return null
        if (!cleaned.any { it in "+-*/" }) return null
        if (!cleaned.all { it.isDigit() || it in "+-*/.() " }) return null

        return try {
            val result = SimpleMathParser(cleaned).parse()
            if (result.isNaN() || result.isInfinite()) return null
            val asLong = result.toLong()
            if (abs(result - asLong.toDouble()) < 1e-9) {
                asLong.toString()
            } else {
                "%.4f".format(result).trimEnd('0').trimEnd('.')
            }
        } catch (_: Exception) {
            null
        }
    }

    private class SimpleMathParser(private val input: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < input.length) input[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < input.length) throw IllegalArgumentException("Unexpected char")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = input.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected token")
            }
            return x
        }
    }
}
