package com.gemma.agentphone.agent

import com.gemma.agentphone.accessibility.AccessibilityNodeSnapshot
import com.gemma.agentphone.accessibility.AccessibilitySnapshot

/**
 * Resolves a Gemma 4 plain-text target label to a concrete nodeId by fuzzy-matching
 * against the live accessibility snapshot.
 *
 * Gemma 4 occasionally outputs a human-readable target description (e.g. "Download button"
 * or "Wi-Fi toggle") instead of a numeric nodeId, especially when the node tree is large
 * and the model is near the token budget. This grounder bridges that gap so the action
 * dispatcher can still act without requiring a nodeId in the JSON output.
 *
 * Match priority (highest to lowest):
 *   1. Exact label match (case-insensitive)
 *   2. Exact hint match (case-insensitive)
 *   3. Contains match on label
 *   4. Contains match on hint
 *   5. Levenshtein-distance best match (within [MAX_EDIT_DISTANCE] edits)
 *
 * Only nodes that are enabled and clickable (or long-clickable) are considered as
 * grounding candidates, since the accessibility dispatcher can only act on those.
 */
class AccessibilityNodeGrounder {

    companion object {
        private const val MAX_EDIT_DISTANCE = 4
    }

    /**
     * Tries to find a nodeId in [snapshot] whose label or hint best matches [targetText].
     *
     * @param targetText The target description produced by Gemma 4 (may be partial or imprecise).
     * @param snapshot The current accessibility snapshot of the screen.
     * @param requireClickable If true (default), only considers clickable or long-clickable nodes.
     * @return The matching node's [AccessibilityNodeSnapshot.id], or null if no acceptable match is found.
     */
    fun groundByText(
        targetText: String,
        snapshot: AccessibilitySnapshot,
        requireClickable: Boolean = true
    ): Int? {
        if (targetText.isBlank() || snapshot.nodes.isEmpty()) {
            return null
        }

        val query = targetText.trim().lowercase()
        val candidates = if (requireClickable) {
            snapshot.nodes.filter { it.enabled && (it.clickable || it.longClickable) }
        } else {
            snapshot.nodes.filter { it.enabled }
        }

        if (candidates.isEmpty()) {
            return null
        }

        // Pass 1 — exact match on label
        candidates.firstOrNull { it.label.trim().lowercase() == query }
            ?.let { return it.id }

        // Pass 2 — exact match on hint
        candidates.firstOrNull { it.hint.trim().lowercase() == query }
            ?.let { return it.id }

        // Pass 3 — contains match on label (query ⊆ label or label ⊆ query)
        candidates.firstOrNull { node ->
            val label = node.label.trim().lowercase()
            label.contains(query) || (query.length > 3 && query.contains(label))
        }?.let { return it.id }

        // Pass 4 — contains match on hint
        candidates.firstOrNull { node ->
            val hint = node.hint.trim().lowercase()
            hint.isNotBlank() && (hint.contains(query) || (query.length > 3 && query.contains(hint)))
        }?.let { return it.id }

        // Pass 5 — fuzzy Levenshtein match as a last resort
        candidates
            .map { node -> node to minOf(editDistance(query, node.label.lowercase()), editDistance(query, node.hint.lowercase())) }
            .filter { (_, dist) -> dist <= MAX_EDIT_DISTANCE }
            .minByOrNull { (_, dist) -> dist }
            ?.let { (node, _) -> return node.id }

        return null
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     * Uses a two-row rolling array to keep memory allocation minimal.
     */
    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) {
                    prev[j - 1]
                } else {
                    1 + minOf(prev[j], curr[j - 1], prev[j - 1])
                }
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }
}
