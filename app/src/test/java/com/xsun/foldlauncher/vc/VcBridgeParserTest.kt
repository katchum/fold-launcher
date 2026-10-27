package com.xsun.foldlauncher.vc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VcBridgeParserTest {

    @Test
    fun `parseAndMergeAttention parses full attention payload and counts unique agents`() {
        val now = 1_761_520_000_000L
        val json = """
            {
              "cursor": "42",
              "running": 2,
              "nextWakeAt": null,
              "rosterTag": "abc12345",
              "working": [
                {
                  "id": "sess-worker-1",
                  "name": "UI Polish Agent",
                  "project": "Android launcher"
                }
              ],
              "roster": [
                {
                  "id": "sess-general",
                  "name": "General",
                  "project": "General",
                  "role": "general",
                  "status": "idle",
                  "approvals": 0,
                  "queuedCount": 0
                },
                {
                  "id": "sess-worker-1",
                  "name": "UI Polish Agent",
                  "project": "Android launcher",
                  "role": "worker",
                  "status": "working",
                  "approvals": 0,
                  "queuedCount": 1
                },
                {
                  "id": "sess-worker-2",
                  "name": "VC Bridge Specialist",
                  "project": "Android launcher",
                  "role": "worker",
                  "status": "idle",
                  "approvals": 1,
                  "queuedCount": 0
                }
              ],
              "events": [
                {
                  "id": "evt-1",
                  "sessionId": "sess-worker-2",
                  "name": "VC Bridge Specialist",
                  "project": "Android launcher",
                  "kind": "approval",
                  "text": "Run ./gradlew testDebugUnitTest?",
                  "createdAt": 1761519990000,
                  "read": false,
                  "seq": 41
                },
                {
                  "id": "evt-2",
                  "sessionId": "sess-general",
                  "name": "General",
                  "project": "General",
                  "kind": "completed",
                  "text": "Spawned worker agents",
                  "createdAt": 1761519995000,
                  "read": false,
                  "seq": 42
                }
              ]
            }
        """.trimIndent()

        val summary = VcBridgeClient.parseAndMergeAttention(
            rawJson = json,
            previousSummary = null,
            nowMs = now,
            isVcInstalled = true,
            installedVcPackage = VcPackages.PREVIEW,
            isVcInForeground = false
        )

        assertEquals(VcConnectionState.LIVE, summary.connectionState)
        assertEquals("42", summary.cursor)
        assertEquals("abc12345", summary.rosterTag)
        assertEquals(1, summary.workingCount)
        assertEquals(1, summary.approvalCount)
        assertEquals(2, summary.unreadCount)
        assertEquals(0, summary.failedCount)
        assertEquals(2, summary.runningCount)
        assertEquals(2, summary.attentionItems.size)
        // Urgent approval item should be ordered first
        assertEquals("evt-1", summary.attentionItems[0].id)
        assertEquals(VcAttentionKind.APPROVAL, summary.attentionItems[0].kind)
        // Roster agent with approval should be ordered first in activeAgents
        assertEquals("sess-worker-2", summary.activeAgents[0].sessionId)
        assertTrue(summary.activeAgents[0].needsApproval)
    }

    @Test
    fun `parseAndMergeAttention merges delta events and preserves roster when rosterTag matches`() {
        val now = 1_761_520_000_000L
        val initialJson = """
            {
              "cursor": "10",
              "running": 1,
              "rosterTag": "tag-v1",
              "working": [{"id": "s1", "name": "Builder", "project": "Launcher"}],
              "roster": [
                {"id": "s1", "name": "Builder", "project": "Launcher", "role": "worker", "status": "working", "approvals": 0, "queuedCount": 0}
              ],
              "events": [
                {"id": "e1", "sessionId": "s1", "name": "Builder", "project": "Launcher", "kind": "approval", "text": "Approve write", "createdAt": 1761519900000, "read": false, "seq": 10}
              ]
            }
        """.trimIndent()

        val firstSummary = VcBridgeClient.parseAndMergeAttention(
            rawJson = initialJson,
            previousSummary = null,
            nowMs = now
        )
        assertEquals(1, firstSummary.approvalCount)
        assertEquals(1, firstSummary.workingCount)

        // Delta reply: e1 is marked read, e2 (completed) is added, working is now empty, roster is omitted
        val deltaJson = """
            {
              "cursor": "12",
              "running": 0,
              "rosterTag": "tag-v1",
              "working": [],
              "events": [
                {"id": "e1", "sessionId": "s1", "name": "Builder", "project": "Launcher", "kind": "approval", "text": "Approve write", "createdAt": 1761519900000, "read": true, "seq": 11},
                {"id": "e2", "sessionId": "s1", "name": "Builder", "project": "Launcher", "kind": "completed", "text": "Agent completed", "createdAt": 1761520005000, "read": false, "seq": 12}
              ]
            }
        """.trimIndent()

        val mergedSummary = VcBridgeClient.parseAndMergeAttention(
            rawJson = deltaJson,
            previousSummary = firstSummary,
            nowMs = now + 5_000L
        )

        assertEquals("12", mergedSummary.cursor)
        assertEquals(0, mergedSummary.workingCount)
        assertEquals(0, mergedSummary.approvalCount)
        assertEquals(1, mergedSummary.unreadCount)
        assertEquals(1, mergedSummary.attentionItems.size)
        assertEquals("e2", mergedSummary.attentionItems[0].id)
        assertEquals(1, mergedSummary.activeAgents.size)
        assertFalse(mergedSummary.activeAgents[0].isWorking)
    }

    @Test
    fun `computeNewBackgroundAlerts alerts only on new unread transitions when VC is backgrounded`() {
        val item1 = VcAttentionItem(
            id = "evt-existing",
            sessionId = "s1",
            agentName = "Coordinator",
            projectName = "Launcher",
            kind = VcAttentionKind.COMPLETED,
            text = "Initial turn complete",
            createdAt = 1_000L
        )
        val initialSummary = VcLiveSummary(
            unreadCount = 1,
            attentionItems = listOf(item1),
            connectionState = VcConnectionState.LIVE
        )

        // 1. Initial seed when launcher starts: should record seen IDs but emit 0 notifications
        val seedDiff = VcBridgeClient.computeNewBackgroundAlerts(
            previousSeenUnreadIds = emptySet(),
            currentSummary = initialSummary,
            isVcInForeground = false,
            isInitialSeed = true
        )
        assertTrue(seedDiff.alertsToNotify.isEmpty())
        assertEquals(setOf("evt-existing"), seedDiff.updatedSeenUnreadIds)

        // 2. New approval arrives while VC IS in the foreground: should NOT emit background notification
        val item2 = VcAttentionItem(
            id = "evt-fg-approval",
            sessionId = "s2",
            agentName = "Worker",
            projectName = "Launcher",
            kind = VcAttentionKind.APPROVAL,
            text = "Needs permission",
            createdAt = 2_000L
        )
        val fgSummary = initialSummary.copy(
            approvalCount = 1,
            unreadCount = 2,
            attentionItems = listOf(item2, item1)
        )
        val fgDiff = VcBridgeClient.computeNewBackgroundAlerts(
            previousSeenUnreadIds = seedDiff.updatedSeenUnreadIds,
            currentSummary = fgSummary,
            isVcInForeground = true,
            isInitialSeed = false
        )
        assertTrue(fgDiff.alertsToNotify.isEmpty())
        assertEquals(setOf("evt-existing", "evt-fg-approval"), fgDiff.updatedSeenUnreadIds)

        // 3. New approval & failure arrive while VC is in the BACKGROUND: should alert only on new items!
        val item3 = VcAttentionItem(
            id = "evt-bg-approval",
            sessionId = "s3",
            agentName = "VC Bridge",
            projectName = "Launcher",
            kind = VcAttentionKind.APPROVAL,
            text = "Approve gradle build",
            createdAt = 3_000L
        )
        val bgSummary = fgSummary.copy(
            approvalCount = 2,
            unreadCount = 3,
            attentionItems = listOf(item3, item2, item1)
        )
        val bgDiff = VcBridgeClient.computeNewBackgroundAlerts(
            previousSeenUnreadIds = fgDiff.updatedSeenUnreadIds,
            currentSummary = bgSummary,
            isVcInForeground = false,
            isInitialSeed = false
        )
        assertEquals(1, bgDiff.alertsToNotify.size)
        assertEquals("evt-bg-approval", bgDiff.alertsToNotify[0].id)
        assertEquals(
            setOf("evt-existing", "evt-fg-approval", "evt-bg-approval"),
            bgDiff.updatedSeenUnreadIds
        )
    }

    @Test
    fun `normalizeServerUrlInput extracts origin from VC pairing link or plain address`() {
        assertEquals(
            "https://vc.example.com:8443",
            VcBridgeClient.normalizeServerUrlInput("https://vc.example.com:8443/pair#v=1&c=ABCD123456&p=pin123")
        )
        assertEquals(
            "http://100.96.188.90:8080",
            VcBridgeClient.normalizeServerUrlInput("100.96.188.90:8080")
        )
        assertEquals(
            "http://127.0.0.1:4327",
            VcBridgeClient.normalizeServerUrlInput("127.0.0.1:4327")
        )
    }

    @Test
    fun `parseAndMergeAttention supports title and summary event fields and vci package`() {
        val now = 1_761_520_000_000L
        val json = """
            {
              "cursor": "99",
              "running": 1,
              "nextWakeAt": 1761520030000,
              "rosterTag": "vci-tag",
              "working": [],
              "roster": [
                {
                  "id": "s-research",
                  "name": "Research",
                  "projectId": "ab16dc52",
                  "project": "Android launcher",
                  "role": "worker",
                  "status": "idle"
                }
              ],
              "events": [
                {
                  "id": "evt-99",
                  "sessionId": "s-research",
                  "projectId": "ab16dc52",
                  "name": "Research",
                  "project": "Android launcher",
                  "kind": "completed",
                  "title": "Tailscale VC host verified at 100.96.188.90:8080",
                  "createdAt": 1761519999000,
                  "read": false,
                  "seq": 99
                }
              ]
            }
        """.trimIndent()

        val summary = VcBridgeClient.parseAndMergeAttention(
            rawJson = json,
            previousSummary = null,
            nowMs = now,
            isVcInstalled = true,
            installedVcPackage = VcPackages.VCI,
            isVcInForeground = false
        )

        assertEquals(1, summary.attentionItems.size)
        assertEquals("Tailscale VC host verified at 100.96.188.90:8080", summary.attentionItems[0].text)
        assertEquals("ab16dc52", summary.attentionItems[0].projectId)
        assertEquals("ab16dc52", summary.activeAgents[0].projectId)
        assertEquals(1_761_520_030_000L, summary.nextWakeAt)
        assertTrue(VcPackages.isVcPackage("dev.agentconversation.vci"))
        assertTrue(VcPackages.isVcPackage("dev.agentconversation.preview"))
    }

    @Test
    fun `v0_3_0 VcVariant VcPromptMacro and resolvePinnedAgent work as expected`() {
        assertEquals(VcVariant.PROD, VcVariant.fromPackageName(VcPackages.RELEASE))
        assertEquals(VcVariant.PREVIEW, VcVariant.fromPackageName(VcPackages.PREVIEW))
        assertEquals(VcVariant.COMPARE, VcVariant.fromPackageName(VcPackages.COMPARE))

        assertEquals(5, VcPromptMacro.entries.size)
        assertTrue(VcPromptMacro.STATUS.promptText.contains("status", ignoreCase = true))
        assertTrue(VcPromptMacro.APPROVE_CONTINUE.promptText.contains("Approved", ignoreCase = true))
        assertTrue(VcPromptMacro.RUN_TESTS.promptText.contains("test", ignoreCase = true))
        assertTrue(VcPromptMacro.SHIP_RELEASE.promptText.contains("release", ignoreCase = true))

        val a1 = VcAgentStatus(sessionId = "s-coord", name = "Coordinator", project = "Launcher", status = "idle")
        val a2 = VcAgentStatus(sessionId = "s-qa", name = "QA Specialist", project = "Launcher", status = "working")
        val summary = VcLiveSummary(
            workingCount = 1,
            runningCount = 2,
            activeAgents = listOf(a1, a2),
            pinnedSessionId = "s-qa"
        )
        assertEquals("s-qa", summary.resolvePinnedAgent()?.sessionId)
        assertEquals("s-qa", summary.resolvePinnedAgent()?.id)
        assertEquals(2, summary.totalSessions)
    }

    @Test
    fun `parseSnapshotResponse extracts active non-archived sessions and projects`() {
        val snapshotJson = """
            {
              "projects": [
                {"id": "proj-1", "name": "Android launcher"}
              ],
              "sessions": [
                {"id": "s-1", "name": "Coordinator", "projectId": "proj-1", "role": "general", "status": "working"},
                {"id": "s-2", "name": "Old Agent", "projectId": "proj-1", "role": "worker", "status": "idle", "archived": true}
              ]
            }
        """.trimIndent()

        val agents = VcBridgeClient.parseSnapshotResponse(snapshotJson)
        assertEquals(1, agents.size)
        assertEquals("s-1", agents[0].sessionId)
        assertEquals("Android launcher", agents[0].project)
        assertTrue(agents[0].isWorking)
    }
}
