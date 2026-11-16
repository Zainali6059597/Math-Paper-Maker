package com.nitroza.mathpapermaker.domain

/**
 * An optional group of complete multi-part questions. Each bundle is offered in full,
 * but the candidate attempts only attemptBundleCount whole bundles.
 */
data class BundleChoiceGroupRule(
    val groupId: String,
    val offeredBundleCount: Int,
    val attemptBundleCount: Int,
    val marksPerBundle: Int,
    val bundleIds: Set<String>
)

/** A paper may contain fixed slots, required paired bundles and optional choice groups together. */
data class CompositePaperPattern(
    val identity: PaperIdentity,
    val expectedAttemptedMarks: Int,
    val slots: List<OfferedQuestionSlot>,
    val groups: List<ChoiceGroupRule>,
    val bundleGroups: List<BundleChoiceGroupRule> = emptyList(),
    val quotas: List<MetadataQuota> = emptyList()
)

data class CompositePaperSelection(
    val selectedBySlot: Map<String, QuestionCandidate>,
    val orderedSlots: List<OfferedQuestionSlot>,
    val offeredMarks: Int,
    val attemptedMarks: Int,
    val seed: Long
)

sealed interface CompositeSelectionResult {
    data class Success(val paper: CompositePaperSelection) : CompositeSelectionResult
    data class Blocked(val code: String, val message: String, val affectedSlots: List<String> = emptyList()) : CompositeSelectionResult
}

/**
 * Performs one unique assignment across every offered position. Required slots and paired
 * bundle parts count in full; a choice group contributes only attemptCount * marksPerQuestion
 * to attempted marks. Choice groups intentionally retain the equal-mark rule.
 */
object CompositePatternEngine {
    fun generate(pattern: CompositePaperPattern, bank: List<QuestionCandidate>, mode: GenerationMode, seed: Long): CompositeSelectionResult {
        if (pattern.expectedAttemptedMarks <= 0 || pattern.slots.isEmpty())
            return blocked("INVALID_PATTERN", "A composite pattern needs slots and a positive attempted-mark total.")
        if (pattern.slots.any { it.slotId.isBlank() || it.marks <= 0 } ||
            pattern.slots.map { it.slotId }.distinct().size != pattern.slots.size)
            return blocked("INVALID_SLOT", "All offered slots need unique IDs and positive marks.")
        if (pattern.groups.map { it.groupId }.distinct().size != pattern.groups.size ||
            pattern.groups.any { it.groupId.isBlank() || it.offeredCount <= 0 || it.attemptCount !in 1..it.offeredCount || it.marksPerQuestion <= 0 })
            return blocked("INVALID_GROUP", "Choice group IDs, offered counts, attempt counts or marks are invalid.")

        val groupIds = pattern.groups.map { it.groupId }.toSet()
        if (pattern.slots.any { it.groupId != null && it.groupId !in groupIds })
            return blocked("UNKNOWN_GROUP", "A slot refers to a choice group that does not exist.")
        if (pattern.slots.any { it.groupId != null && it.bundleId != null })
            return blocked("AMBIGUOUS_SLOT", "A slot cannot be both optional-choice and part of a required paired bundle.")
        if (pattern.bundleGroups.map { it.groupId }.distinct().size != pattern.bundleGroups.size ||
            pattern.bundleGroups.any { g ->
                g.groupId.isBlank() || g.offeredBundleCount <= 0 ||
                    g.attemptBundleCount !in 1..g.offeredBundleCount ||
                    g.marksPerBundle <= 0 || g.bundleIds.size != g.offeredBundleCount ||
                    g.bundleIds.any { it.isBlank() }
            }) return blocked("INVALID_BUNDLE_GROUP", "Optional paired-question group metadata is invalid.")
        if (pattern.slots.any { it.bundleChoiceGroupId != null &&
                (it.bundleId == null || pattern.bundleGroups.none { g -> g.groupId == it.bundleChoiceGroupId }) })
            return blocked("UNKNOWN_BUNDLE_GROUP", "A slot refers to an unknown paired-question choice group.")
        if (pattern.slots.any { it.bundleChoiceGroupId != null && it.groupId != null })
            return blocked("AMBIGUOUS_BUNDLE_GROUP", "A paired-question choice slot cannot also belong to a single-question choice group.")
        val bundleChoiceIds = pattern.bundleGroups.flatMap { it.bundleIds }.toSet()
        if (pattern.bundleGroups.sumOf { it.bundleIds.size } != bundleChoiceIds.size)
            return blocked("DUPLICATE_BUNDLE_CHOICE", "A paired question bundle cannot belong to multiple choice groups.")
        for (group in pattern.bundleGroups) {
            val memberSlots = pattern.slots.filter { it.bundleChoiceGroupId == group.groupId }
            val actualBundles = memberSlots.mapNotNull { it.bundleId }.toSet()
            if (actualBundles != group.bundleIds)
                return blocked("INCOMPLETE_BUNDLE_GROUP", "Every offered paired question must have all its parts represented.")
            val partsByBundle = memberSlots.groupBy { it.bundleId!! }
            if (partsByBundle.values.any { parts -> parts.size < 2 })
                return blocked("INCOMPLETE_BUNDLE_GROUP", "Every offered paired question must contain all of its parts.")
            if (partsByBundle.values.any { parts -> parts.sumOf { it.marks } != group.marksPerBundle })
                return blocked("INVALID_BUNDLE_MARKS", "Each offered paired question must match marksPerBundle.")
        }
        if (pattern.groups.any { group ->
                val members = pattern.slots.filter { it.groupId == group.groupId }
                members.size != group.offeredCount || members.any { it.marks != group.marksPerQuestion }
            }) return blocked("INVALID_GROUP_SLOTS", "Each choice group must have exactly offeredCount slots with equal marks.")
        val bundles = pattern.slots.filter { it.bundleId != null }.groupBy { it.bundleId!! }
        if (bundles.keys.any { it.isBlank() } || bundles.values.any { it.size < 2 })
            return blocked("INVALID_BUNDLE", "Every required paired bundle needs a nonblank ID and at least two parts.")

        val optionalBundleSlots = pattern.slots.filter { it.bundleChoiceGroupId != null }.toSet()
        val requiredMarks = pattern.slots.filter { it.groupId == null && it !in optionalBundleSlots }.sumOf { it.marks }
        val choiceAttemptedMarks = pattern.groups.sumOf { it.attemptCount * it.marksPerQuestion } +
            pattern.bundleGroups.sumOf { it.attemptBundleCount * it.marksPerBundle }
        val calculated = requiredMarks + choiceAttemptedMarks
        if (calculated != pattern.expectedAttemptedMarks)
            return blocked("ATTEMPTED_TOTAL_MISMATCH", "Fixed and paired slots plus attempted choice marks total $calculated, but the pattern declares ${pattern.expectedAttemptedMarks}.")

        // Fail fast when a section-scoped quota cannot be met inside its own group.
        for (quota in pattern.quotas.filter { it.groupId != null }) {
            val groupSlots = pattern.slots.filter { it.groupId == quota.groupId }
            val possible = groupSlots.count { slot ->
                bank.any { candidate ->
                    candidate.identity == pattern.identity &&
                        candidate.status != ContentStatus.CONFLICTED &&
                        (mode == GenerationMode.PRACTICE || (candidate.status == ContentStatus.VERIFIED_OFFICIAL && candidate.summative)) &&
                        candidate.matches(QuestionSlot(slot.slotId, slot.chapterId, slot.sloId, slot.questionType, slot.marks, slot.allowedChapterIds, slot.groupId)) &&
                        when (quota.dimension) {
                            QuotaDimension.CHAPTER -> candidate.chapterId == quota.value
                            QuotaDimension.SLO -> candidate.sloId == quota.value
                            QuotaDimension.COGNITIVE_LEVEL -> candidate.cognitiveLevel == quota.value
                            QuotaDimension.DIFFICULTY -> candidate.difficulty == quota.value
                        }
                }
            }
            if (possible < quota.minimum) {
                return blocked("QUOTA_UNSATISFIABLE", "Section ${quota.groupId} cannot meet its ${quota.dimension}=${quota.value} minimum of ${quota.minimum}.")
            }
        }
        val slots = pattern.slots.map { QuestionSlot(it.slotId, it.chapterId, it.sloId, it.questionType, it.marks, it.allowedChapterIds, it.groupId) }
        val picked: Map<String, QuestionCandidate> = if (pattern.quotas.isNotEmpty()) {
            when (val quotaResult = QuotaAwareSelectionEngine.select(
                QuotaSelectionRequest(pattern.identity, slots, pattern.quotas, mode, seed), bank
            )) {
                is QuotaSelectionResult.Blocked ->
                    return CompositeSelectionResult.Blocked(quotaResult.code, quotaResult.message, quotaResult.affectedSlots)
                is QuotaSelectionResult.Success ->
                    quotaResult.selected.associate { it.first.slotId to it.second }
            }
        } else {
            when (val result = LocalSelectionEngine.select(SelectionRequest(pattern.identity, slots, mode, seed), bank)) {
                is SelectionResult.Blocked ->
                    return CompositeSelectionResult.Blocked(result.code, result.message, result.affectedSlots)
                is SelectionResult.Success ->
                    result.selected.associate { it.first.slotId to it.second }
            }
        }
        return CompositeSelectionResult.Success(
            CompositePaperSelection(picked, pattern.slots.sortedBy { slot -> pattern.slots.indexOf(slot) },
                pattern.slots.sumOf { it.marks }, calculated, seed)
        )
    }

    private fun blocked(code: String, message: String) = CompositeSelectionResult.Blocked(code, message)
}
