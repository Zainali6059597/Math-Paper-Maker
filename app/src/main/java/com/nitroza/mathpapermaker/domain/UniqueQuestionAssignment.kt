package com.nitroza.mathpapermaker.domain

import kotlin.random.Random
import kotlin.math.ln

/** Bounded backtracking matcher. Prevents a greedy early pick from starving a constrained slot. */
internal object UniqueQuestionAssignment {
    data class Result<T>(val assignment: Map<String, Pair<T, QuestionCandidate>>?, val exploredNodes: Int, val limitReached: Boolean)

    fun <T> solve(
        slots: List<T>,
        slotId: (T) -> String,
        candidates: List<QuestionCandidate>,
        matches: (QuestionCandidate, T) -> Boolean,
        seed: Long,
        maxNodes: Int = 50_000
    ): Result<T> {
        if (slots.isEmpty() || maxNodes <= 0) return Result(null, 0, false)
        if (candidates.any { !it.selectionWeight.isFinite() || it.selectionWeight <= 0.0 }) return Result(null, 0, false)
        val pools = slots.associate { slot -> slotId(slot) to candidates.filter { matches(it, slot) } }
        if (pools.values.any { it.isEmpty() }) return Result(null, 0, false)
        val ordered = slots.sortedWith(compareBy<T> { pools.getValue(slotId(it)).size }.thenBy { slotId(it) })
        val randomized = ordered.associate { slot ->
            val id = slotId(slot)
            val random = Random(seed xor id.hashCode().toLong())
            // Weighted random ordering without replacement (exponential-race sampling).
            id to pools.getValue(id).sortedBy { candidate ->
                -ln(random.nextDouble().coerceAtLeast(1e-12)) / candidate.selectionWeight
            }
        }
        val used = mutableSetOf<String>()
        val selected = mutableMapOf<String, Pair<T, QuestionCandidate>>()
        var nodes = 0
        var limitReached = false

        fun search(index: Int): Boolean {
            if (nodes >= maxNodes) { limitReached = true; return false }
            nodes++
            if (index == ordered.size) return true
            val slot = ordered[index]
            val id = slotId(slot)
            for (candidate in randomized.getValue(id)) {
                if (candidate.id.isBlank() || candidate.id in used) continue
                used += candidate.id
                selected[id] = slot to candidate
                if (search(index + 1)) return true
                selected.remove(id)
                used.remove(candidate.id)
                if (limitReached) return false
            }
            return false
        }
        val success = search(0)
        return Result(if (success) selected.toMap() else null, nodes, limitReached)
    }
}
