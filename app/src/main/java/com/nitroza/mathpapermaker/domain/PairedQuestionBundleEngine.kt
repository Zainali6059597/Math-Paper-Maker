package com.nitroza.mathpapermaker.domain


/** A fixed, non-optional bundle such as Q5(a)+Q5(b). Parts may carry different marks. */
data class PairedQuestionBundleRule(
    val bundleId: String,
    val displayOrder: Int,
    val parts: List<OfferedQuestionSlot>
)

data class PairedQuestionBundleSelection(
    val bundleId: String,
    val parts: List<Pair<OfferedQuestionSlot, QuestionCandidate>>
) {
    val totalMarks: Int get() = parts.sumOf { it.first.marks }
}

data class PairedBundlePaperSelection(
    val bundles: List<PairedQuestionBundleSelection>,
    val totalMarks: Int,
    val seed: Long
)

sealed interface PairedBundleSelectionResult {
    data class Success(val paper: PairedBundlePaperSelection) : PairedBundleSelectionResult
    data class Blocked(val code: String, val message: String, val affectedSlots: List<String> = emptyList()) : PairedBundleSelectionResult
}

/**
 * Selects every part of each required bundle as one logical unit. Unlike a choice group,
 * a bundle does not mean "attempt any N": all of its parts are required. This makes
 * mixed-mark paired long questions safe without inventing an equal-mark assumption.
 */
object PairedQuestionBundleEngine {
    fun generate(
        identity: PaperIdentity,
        expectedTotalMarks: Int,
        bundles: List<PairedQuestionBundleRule>,
        bank: List<QuestionCandidate>,
        mode: GenerationMode,
        seed: Long
    ): PairedBundleSelectionResult {
        if (bundles.isEmpty()) return blocked("EMPTY_BUNDLES", "The paper contains no required question bundles.")
        if (expectedTotalMarks <= 0) return blocked("INVALID_TOTAL", "Expected total marks must be positive.")
        if (bundles.any { it.bundleId.isBlank() || it.displayOrder < 0 || it.parts.isEmpty() }) {
            return blocked("INVALID_BUNDLE", "Every bundle needs an ID, a non-negative display order and at least one part.")
        }
        if (bundles.map { it.bundleId }.distinct().size != bundles.size) {
            return blocked("DUPLICATE_BUNDLE", "Bundle IDs must be unique.")
        }
        val allParts = bundles.flatMap { it.parts }
        if (allParts.any { it.slotId.isBlank() || it.marks <= 0 }) {
            return blocked("INVALID_PART", "Every paired-question part needs a slot ID and positive marks.")
        }
        if (allParts.map { it.slotId }.distinct().size != allParts.size) {
            return blocked("DUPLICATE_PART", "Part slot IDs must be unique across all bundles.")
        }
        val calculatedTotal = allParts.sumOf { it.marks }
        if (calculatedTotal != expectedTotalMarks) {
            return blocked("TOTAL_MARKS_MISMATCH", "Required bundle parts total $calculatedTotal marks, but the pattern declares $expectedTotalMarks.")
        }

        val exact = bank.filter { it.identity == identity }
        val eligible = exact.filter { candidate ->
            candidate.status != ContentStatus.CONFLICTED &&
                (mode == GenerationMode.PRACTICE ||
                    (candidate.status == ContentStatus.VERIFIED_OFFICIAL && candidate.summative))
        }
        if (eligible.map { it.id }.distinct().size != eligible.size) {
            return blocked("DUPLICATE_QUESTION_ID", "The local question bank contains duplicate question IDs.")
        }
        val assignment = UniqueQuestionAssignment.solve(
            slots = allParts,
            slotId = { it.slotId },
            candidates = eligible,
            matches = { candidate, part -> candidate.matches(part) },
            seed = seed
        )
        val selectedBySlot = assignment.assignment?.mapValues { it.value.second }
        if (selectedBySlot == null) {
            val affected = allParts.filter { part -> eligible.none { it.matches(part) } }.map { it.slotId }
            val exactMissing = allParts.filter { part -> exact.none { it.matches(part) } }.map { it.slotId }
            val code = when {
                assignment.limitReached -> "SEARCH_LIMIT_REACHED"
                exactMissing.isNotEmpty() -> "NO_CANDIDATE_FOR_PART"
                mode == GenerationMode.OFFICIAL -> "SOURCE_VERIFICATION_REQUIRED"
                else -> "INSUFFICIENT_QUESTION_BANK"
            }
            val slots = (affected + exactMissing).distinct().ifEmpty { allParts.map { it.slotId } }
            return blocked(code, "No unique eligible assignment can fill every paired part. No board, stream or session substitution is allowed.", slots)
        }

        val selectedBundles = bundles.sortedBy { it.displayOrder }.map { bundle ->
            PairedQuestionBundleSelection(bundle.bundleId, bundle.parts.map { part -> part to selectedBySlot.getValue(part.slotId) })
        }
        return PairedBundleSelectionResult.Success(
            PairedBundlePaperSelection(selectedBundles, selectedBundles.sumOf { it.totalMarks }, seed)
        )
    }

    /** Human-readable instruction for the printed paper; includes each part's own marks. */
    fun instruction(bundle: PairedQuestionBundleSelection): String = buildString {
        append(bundle.bundleId).append(": Attempt all parts. ")
        append(bundle.parts.mapIndexed { index, pair -> "(${('a' + index)}) ${pair.first.marks} marks" }.joinToString("; "))
        append(". Total: ").append(bundle.totalMarks).append(" marks.")
    }

    private fun QuestionCandidate.matches(slot: OfferedQuestionSlot): Boolean =
        marks == slot.marks &&
            (if (slot.allowedChapterIds.isNotEmpty()) chapterId in slot.allowedChapterIds else slot.chapterId == null || chapterId == slot.chapterId) &&
            (slot.sloId == null || sloId == slot.sloId) &&
            (slot.questionType == null || questionType == slot.questionType)

    private fun blocked(code: String, message: String, slots: List<String> = emptyList()) =
        PairedBundleSelectionResult.Blocked(code, message, slots)
}
