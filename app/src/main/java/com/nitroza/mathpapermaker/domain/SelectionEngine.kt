package com.nitroza.mathpapermaker.domain


enum class ContentStatus { VERIFIED_OFFICIAL, NEEDS_REVIEW, LEGACY, CONFLICTED }
enum class GenerationMode { OFFICIAL, PRACTICE }

data class PaperIdentity(
    val board: String,
    val grade: Int,
    val stream: String,
    val subject: String,
    val session: String
)

data class QuestionCandidate(
    val id: String,
    val identity: PaperIdentity,
    val chapterId: String,
    val sloId: String,
    val questionType: String,
    val marks: Int,
    val summative: Boolean,
    val status: ContentStatus,
    val cognitiveLevel: String = "",
    val difficulty: String = "",
    val selectionWeight: Double = 1.0
)

data class QuestionSlot(
    val slotId: String,
    val chapterId: String? = null,
    val sloId: String? = null,
    val questionType: String? = null,
    val marks: Int,
    val allowedChapterIds: Set<String> = emptySet(),
    val groupId: String? = null
)

data class SelectionRequest(
    val identity: PaperIdentity,
    val slots: List<QuestionSlot>,
    val mode: GenerationMode,
    val seed: Long
)

sealed interface SelectionResult {
    data class Success(val selected: List<Pair<QuestionSlot, QuestionCandidate>>, val seed: Long) : SelectionResult
    data class Blocked(val code: String, val message: String, val affectedSlots: List<String>) : SelectionResult
}

/** Deterministic local selector. It never relaxes board, grade, stream, subject or session matching. */
object LocalSelectionEngine {
    fun select(request: SelectionRequest, bank: List<QuestionCandidate>): SelectionResult {
        if (request.slots.isEmpty()) return SelectionResult.Blocked("EMPTY_PATTERN", "The pattern contains no question slots.", emptyList())
        if (request.slots.any { it.marks <= 0 } || request.slots.map { it.slotId }.distinct().size != request.slots.size) {
            return SelectionResult.Blocked("INVALID_PATTERN", "Question slots must have unique IDs and positive marks.", emptyList())
        }

        val exact = bank.filter { it.identity == request.identity }
        if (exact.map { it.id }.distinct().size != exact.size) {
            return SelectionResult.Blocked("DUPLICATE_QUESTION_ID", "The local question bank contains duplicate question IDs.", emptyList())
        }
        val eligible = exact.filter { candidate ->
            candidate.status != ContentStatus.CONFLICTED &&
                (request.mode == GenerationMode.PRACTICE ||
                    (candidate.status == ContentStatus.VERIFIED_OFFICIAL && candidate.summative))
        }
        if (eligible.map { it.id }.distinct().size != eligible.size) {
            return SelectionResult.Blocked("DUPLICATE_QUESTION_ID", "The local question bank contains duplicate question IDs.", emptyList())
        }
        val assignment = UniqueQuestionAssignment.solve(
            slots = request.slots,
            slotId = { it.slotId },
            candidates = eligible,
            matches = { candidate, slot -> candidate.matches(slot) },
            seed = request.seed
        )
        val selected = assignment.assignment
        if (selected == null) {
            val missing = request.slots.filter { slot -> exact.none { it.matches(slot) } }.map { it.slotId }
            val code = when {
                assignment.limitReached -> "SEARCH_LIMIT_REACHED"
                missing.isNotEmpty() -> "NO_CANDIDATE_FOR_SLOT"
                request.mode == GenerationMode.OFFICIAL -> "SOURCE_VERIFICATION_REQUIRED"
                else -> "INSUFFICIENT_QUESTION_BANK"
            }
            val affected = missing.ifEmpty { request.slots.map { it.slotId } }
            return SelectionResult.Blocked(code, "No unique eligible assignment can fill every required slot. No board, stream or session substitution is allowed.", affected)
        }
        val slotOrder = request.slots.mapIndexed { index, slot -> slot.slotId to index }.toMap()
        return SelectionResult.Success(selected.values.sortedBy { slotOrder.getValue(it.first.slotId) }, request.seed)
    }

    private fun QuestionCandidate.matches(slot: QuestionSlot): Boolean =
        marks == slot.marks &&
            (if (slot.allowedChapterIds.isNotEmpty()) chapterId in slot.allowedChapterIds else slot.chapterId == null || chapterId == slot.chapterId) &&
            (slot.sloId == null || sloId == slot.sloId) &&
            (slot.questionType == null || questionType == slot.questionType)
}
