package com.xsun.foldlauncher.search

import com.xsun.foldlauncher.model.AppEntry
import kotlin.math.abs

object SearchEngine {

    /**
     * Ranks installed apps against a user query using Spotlight-style scoring:
     * 1. Exact match (1000 pts)
     * 2. Prefix match on full label (800 pts)
     * 3. Acronym match, e.g. "yt" -> "YouTube" or "gm" -> "Google Maps" (700 pts)
     * 4. Word-boundary prefix match (600 pts)
     * 5. Substring match in label or package name (400 pts)
     * 6. Fuzzy subsequence match in label (200 pts)
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

        // CamelCase / consonant shorthand check (e.g., "yt" in "youtube", "gbd" in "gboard")
        val words = normalizedLabel.split(Regex("[\\s\\-_/.]+"))
        if (words.any { it.startsWith(query) }) return 650

        val labelIdx = normalizedLabel.indexOf(query)
        if (labelIdx >= 0) return 500 - labelIdx.coerceAtMost(50)

        if (packageName.contains(query)) return 300

        if (query.length >= 2 && isSubsequence(query, normalizedLabel)) {
            return 200
        }

        return 0
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
