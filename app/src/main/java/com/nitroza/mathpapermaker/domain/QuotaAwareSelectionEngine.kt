package com.nitroza.mathpapermaker.domain

import kotlin.random.Random
import kotlin.math.ln

enum class QuotaDimension { CHAPTER, SLO, COGNITIVE_LEVEL, DIFFICULTY }

data class MetadataQuota(val dimension: QuotaDimension, val value: String, val minimum: Int, val maximum: Int)
data class QuotaSelectionRequest(
    val identity: PaperIdentity,
    val slots: List<QuestionSlot>,
    val quotas: List<MetadataQuota>,
    val mode: GenerationMode,
    val seed: Long,
    val maxSearchNodes: Int = 50_000
)
sealed interface QuotaSelectionResult {
    data class Success(val selected: List<Pair<QuestionSlot, QuestionCandidate>>, val seed: Long, val exploredNodes: Int) : QuotaSelectionResult
    data class Blocked(val code: String, val message: String, val affectedSlots: List<String> = emptyList()) : QuotaSelectionResult
}

/** Bounded deterministic solver: exact identity, no duplicate questions, all explicit metadata quotas enforced. */
object QuotaAwareSelectionEngine {
    fun select(request: QuotaSelectionRequest, bank: List<QuestionCandidate>): QuotaSelectionResult {
        val slots = request.slots
        if (slots.isEmpty()) return blocked("EMPTY_PATTERN", "The pattern contains no question slots.")
        if (slots.any { it.slotId.isBlank() || it.marks <= 0 } || slots.map { it.slotId }.distinct().size != slots.size)
            return blocked("INVALID_PATTERN", "Slots must have unique nonblank IDs and positive marks.")
        if (request.maxSearchNodes <= 0) return blocked("INVALID_SEARCH_LIMIT", "Search limit must be positive.")
        if (request.quotas.any { it.value.isBlank() || it.minimum < 0 || it.maximum < it.minimum || it.maximum > slots.size })
            return blocked("INVALID_QUOTA", "Each quota needs 0 ≤ minimum ≤ maximum ≤ offered question count.")
        if (request.quotas.map { it.dimension to it.value }.distinct().size != request.quotas.size)
            return blocked("DUPLICATE_QUOTA", "A metadata value may have only one quota rule per dimension.")

        if (bank.any { !it.selectionWeight.isFinite() || it.selectionWeight <= 0.0 }) return blocked("INVALID_SELECTION_WEIGHT", "Every question weight must be finite and greater than zero.")
        val exact = bank.filter { it.identity == request.identity }
        val eligible = exact.filter { q -> q.status != ContentStatus.CONFLICTED &&
            (request.mode == GenerationMode.PRACTICE || (q.status == ContentStatus.VERIFIED_OFFICIAL && q.summative)) }
        val pools = slots.associate { slot -> slot.slotId to eligible.filter { it.matches(slot) } }
        val empty = slots.filter { pools.getValue(it.slotId).isEmpty() }
        if (empty.isNotEmpty()) {
            val code = if (request.mode == GenerationMode.OFFICIAL && empty.any { slot -> exact.any { it.matches(slot) } })
                "SOURCE_VERIFICATION_REQUIRED" else "NO_CANDIDATE_FOR_SLOT"
            return blocked(code, "No eligible question matches one or more required slots.", empty.map { it.slotId })
        }

        fun value(candidate: QuestionCandidate, dimension: QuotaDimension): String = when (dimension) {
            QuotaDimension.CHAPTER -> candidate.chapterId
            QuotaDimension.SLO -> candidate.sloId
            QuotaDimension.COGNITIVE_LEVEL -> candidate.cognitiveLevel
            QuotaDimension.DIFFICULTY -> candidate.difficulty
        }
        val ordered = slots.sortedBy { pools.getValue(it.slotId).size }
        val shuffled = pools.map { (slotId, pool) ->
            val random = Random(request.seed xor slotId.hashCode().toLong())
            slotId to pool.sortedBy { candidate ->
                -ln(random.nextDouble().coerceAtLeast(1e-12)) / candidate.selectionWeight
            }
        }.toMap()
        val counts = request.quotas.associateWith { 0 }.toMutableMap()
        val chosen = mutableMapOf<String, QuestionCandidate>()
        val used = mutableSetOf<String>()
        var nodes = 0
        var limitReached = false

        fun minimaStillReachable(next: Int): Boolean = request.quotas.all { quota ->
            val current = counts.getValue(quota)
            if (current >= quota.minimum) true else {
                val possibleSlots = ordered.drop(next).count { slot ->
                    shuffled.getValue(slot.slotId).any { it.id !in used && value(it, quota.dimension) == quota.value }
                }
                current + possibleSlots >= quota.minimum
            }
        }

        fun search(index: Int): Boolean {
            if (nodes >= request.maxSearchNodes) { limitReached = true; return false }
            nodes++
            if (index == ordered.size) return request.quotas.all { counts.getValue(it) in it.minimum..it.maximum }
            if (!minimaStillReachable(index)) return false
            val slot = ordered[index]
            for (candidate in shuffled.getValue(slot.slotId)) {
                if (candidate.id in used) continue
                val hit = request.quotas.filter { value(candidate, it.dimension) == it.value }
                if (hit.any { counts.getValue(it) >= it.maximum }) continue
                used += candidate.id
                chosen[slot.slotId] = candidate
                hit.forEach { counts[it] = counts.getValue(it) + 1 }
                if (search(index + 1)) return true
                hit.forEach { counts[it] = counts.getValue(it) - 1 }
                chosen.remove(slot.slotId)
                used.remove(candidate.id)
                if (limitReached) return false
            }
            return false
        }

        if (!search(0)) return if (limitReached)
            blocked("SEARCH_LIMIT_REACHED", "Quota solver reached its ${request.maxSearchNodes}-node safety limit. Narrow the pattern or increase the limit.")
        else blocked("QUOTA_UNSATISFIABLE", "No unique-question assignment satisfies every declared chapter/SLO/cognitive/difficulty quota. The rules will not be silently relaxed.")

        val indexBySlot = slots.mapIndexed { index, slot -> slot.slotId to index }.toMap()
        val result = chosen.map { (slotId, candidate) -> slots[indexBySlot.getValue(slotId)] to candidate }
            .sortedBy { indexBySlot.getValue(it.first.slotId) }
        return QuotaSelectionResult.Success(result, request.seed, nodes)
    }

    private fun QuestionCandidate.matches(slot: QuestionSlot): Boolean = marks == slot.marks &&
        (slot.chapterId == null || chapterId == slot.chapterId) &&
        (slot.sloId == null || sloId == slot.sloId) &&
        (slot.questionType == null || questionType == slot.questionType)

    private fun blocked(code: String, message: String, slots: List<String> = emptyList()) = QuotaSelectionResult.Blocked(code, message, slots)
}
