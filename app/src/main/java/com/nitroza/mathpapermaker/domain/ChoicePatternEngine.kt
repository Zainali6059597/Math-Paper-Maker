package com.nitroza.mathpapermaker.domain


/** A slot describes one offered question position, not one attempted question. */
data class OfferedQuestionSlot(
    val slotId: String,
    val marks: Int,
    val chapterId: String? = null,
    val sloId: String? = null,
    val questionType: String? = null,
    val bundleId: String? = null,
    val groupId: String? = null
)

/** All questions in a choice group carry the same marks; students attempt only attemptCount. */
data class ChoiceGroupRule(
    val groupId: String,
    val offeredCount: Int,
    val attemptCount: Int,
    val marksPerQuestion: Int,
    val slots: List<OfferedQuestionSlot>
)

data class ChoicePaperPattern(
    val identity: PaperIdentity,
    val expectedAttemptedMarks: Int,
    val groups: List<ChoiceGroupRule>
)

data class OfferedGroupSelection(
    val groupId: String,
    val offered: List<Pair<OfferedQuestionSlot, QuestionCandidate>>,
    val attemptCount: Int,
    val marksPerQuestion: Int
) {
    val offeredMarks: Int get() = offered.sumOf { it.second.marks }
    val attemptedMarks: Int get() = attemptCount * marksPerQuestion
}

data class ChoicePaperSelection(
    val groups: List<OfferedGroupSelection>,
    val offeredMarks: Int,
    val attemptedMarks: Int,
    val seed: Long
)

sealed interface ChoiceSelectionResult {
    data class Success(val paper: ChoicePaperSelection) : ChoiceSelectionResult
    data class Blocked(val code: String, val message: String, val affectedSlots: List<String> = emptyList()) : ChoiceSelectionResult
}

/**
 * Local deterministic solver for papers with offered/attempted choices.
 * It never substitutes another board, grade, stream, subject, or session.
 * Groups currently require equal marks per question; mixed-mark bundles are rejected explicitly.
 */
object ChoicePatternEngine {
    fun generate(pattern: ChoicePaperPattern, bank: List<QuestionCandidate>, mode: GenerationMode, seed: Long): ChoiceSelectionResult {
        val groups = pattern.groups
        if (groups.isEmpty()) return blocked("EMPTY_PATTERN", "The paper has no choice groups.")
        if (groups.map { it.groupId }.distinct().size != groups.size) return blocked("DUPLICATE_GROUP", "Choice group IDs must be unique.")
        if (groups.any { g ->
                g.groupId.isBlank() || g.offeredCount <= 0 || g.attemptCount <= 0 ||
                    g.attemptCount > g.offeredCount || g.marksPerQuestion <= 0 ||
                    g.slots.size != g.offeredCount || g.slots.any { it.slotId.isBlank() || it.marks != g.marksPerQuestion }
            }) return blocked("INVALID_GROUP_RULE", "Each group must define exactly offeredCount unique slots, with positive equal marks and a valid attempt count.")
        val allSlots = groups.flatMap { it.slots }
        if (allSlots.map { it.slotId }.distinct().size != allSlots.size) return blocked("DUPLICATE_SLOT", "Slot IDs must be unique across the entire paper.")
        if (pattern.expectedAttemptedMarks <= 0) return blocked("INVALID_TOTAL", "Expected attempted marks must be positive.")
        val calculatedAttempted = groups.sumOf { it.attemptCount * it.marksPerQuestion }
        if (calculatedAttempted != pattern.expectedAttemptedMarks) {
            return blocked("ATTEMPTED_TOTAL_MISMATCH", "Choice rules calculate $calculatedAttempted attempted marks, but the pattern declares ${pattern.expectedAttemptedMarks}.")
        }

        val exact = bank.filter { it.identity == pattern.identity }
        val eligible = exact.filter { q -> q.status != ContentStatus.CONFLICTED &&
            (mode == GenerationMode.PRACTICE || (q.status == ContentStatus.VERIFIED_OFFICIAL && q.summative)) }
        if (eligible.map { it.id }.distinct().size != eligible.size) {
            return blocked("DUPLICATE_QUESTION_ID", "The local question bank contains duplicate question IDs.")
        }
        val assignment = UniqueQuestionAssignment.solve(
            slots = allSlots,
            slotId = { it.slotId },
            candidates = eligible,
            matches = { candidate, slot -> candidate.matches(slot) },
            seed = seed
        )
        val picked = assignment.assignment
        if (picked == null) {
            val missing = allSlots.filter { slot -> exact.none { it.matches(slot) } }.map { it.slotId }
            val noEligible = allSlots.filter { slot -> eligible.none { it.matches(slot) } }.map { it.slotId }
            val code = when {
                assignment.limitReached -> "SEARCH_LIMIT_REACHED"
                missing.isNotEmpty() -> "NO_CANDIDATE_FOR_SLOT"
                mode == GenerationMode.OFFICIAL -> "SOURCE_VERIFICATION_REQUIRED"
                else -> "INSUFFICIENT_QUESTION_BANK"
            }
            val affected = (missing + noEligible).distinct().ifEmpty { allSlots.map { it.slotId } }
            return blocked(code, "No unique eligible assignment can fill every offered choice slot. Add or review suitable local content; no stream/session substitution is allowed.", affected)
        }
        val results = mutableListOf<OfferedGroupSelection>()

        groups.forEach { group ->
            val offered = group.slots.map { picked.getValue(it.slotId) }
            results += OfferedGroupSelection(group.groupId, offered, group.attemptCount, group.marksPerQuestion)
        }
        val paper = ChoicePaperSelection(
            groups = results,
            offeredMarks = results.sumOf { it.offeredMarks },
            attemptedMarks = results.sumOf { it.attemptedMarks },
            seed = seed
        )
        return ChoiceSelectionResult.Success(paper)
    }

    private fun QuestionCandidate.matches(slot: OfferedQuestionSlot): Boolean =
        marks == slot.marks &&
            (slot.chapterId == null || chapterId == slot.chapterId) &&
            (slot.sloId == null || sloId == slot.sloId) &&
            (slot.questionType == null || questionType == slot.questionType)

    private fun blocked(code: String, message: String, slots: List<String> = emptyList()) =
        ChoiceSelectionResult.Blocked(code, message, slots)
}
