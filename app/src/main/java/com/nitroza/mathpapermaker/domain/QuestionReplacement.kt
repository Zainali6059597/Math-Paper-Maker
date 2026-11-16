package com.nitroza.mathpapermaker.domain

data class ReplacementRequest(
    val identity: PaperIdentity,
    val slot: OfferedQuestionSlot,
    val mode: GenerationMode,
    val currentQuestionId: String,
    /** IDs already used by every other position in this paper. */
    val questionIdsUsedElsewhere: Set<String>
)

data class ReplacementOptions(
    val candidates: List<QuestionCandidate>,
    val matchingIdentityCount: Int,
    val matchingSlotCount: Int,
    val excludedAsAlreadyUsed: Int,
    val excludedByVerification: Int
) {
    val hasOptions: Boolean get() = candidates.isNotEmpty()
}

/** Finds safe replacements only; the caller commits a replacement after explicit teacher selection. */
object QuestionReplacementEngine {
    fun findOptions(request: ReplacementRequest, bank: List<QuestionCandidate>): ReplacementOptions {
        val sameIdentity = bank.filter { it.identity == request.identity }
        val matchingSlot = sameIdentity.filter { it.matches(request.slot) }
        val notCurrent = matchingSlot.filter { it.id != request.currentQuestionId }
        val unused = notCurrent.filter { it.id !in request.questionIdsUsedElsewhere }
        val eligible = unused.filter { candidate ->
            candidate.status != ContentStatus.CONFLICTED &&
                (request.mode == GenerationMode.PRACTICE ||
                    (candidate.status == ContentStatus.VERIFIED_OFFICIAL && candidate.summative))
        }
        return ReplacementOptions(
            candidates = eligible.sortedBy { it.id },
            matchingIdentityCount = sameIdentity.size,
            matchingSlotCount = matchingSlot.size,
            excludedAsAlreadyUsed = notCurrent.size - unused.size,
            excludedByVerification = unused.size - eligible.size
        )
    }

    private fun QuestionCandidate.matches(slot: OfferedQuestionSlot): Boolean =
        marks == slot.marks &&
            (slot.chapterId == null || chapterId == slot.chapterId) &&
            (slot.sloId == null || sloId == slot.sloId) &&
            (slot.questionType == null || questionType == slot.questionType)
}
