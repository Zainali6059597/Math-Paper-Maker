package com.nitroza.mathpapermaker.domain

/** A section's printed structure. Offered and attempted totals are deliberately separate. */
data class ComplianceSection(
    val id: String,
    val offeredCount: Int,
    val attemptCount: Int,
    val marksPerQuestion: Int,
    val offeredMarksOverride: Int? = null,
    val attemptedMarksOverride: Int? = null,
    val allowVariableQuestionMarks: Boolean = false
)

data class CompliancePattern(
    val identity: PaperIdentity,
    val declaredAttemptedMarks: Int,
    val sections: List<ComplianceSection>,
    val sourceStatus: ContentStatus,
    val patternStatus: ContentStatus,
    val hasUnresolvedSourceConflict: Boolean = false,
    val metadataQuotas: List<MetadataQuota> = emptyList()
)

data class ComplianceQuestion(
    val id: String,
    val identity: PaperIdentity,
    val sectionId: String,
    val chapterId: String,
    val sloId: String,
    val marks: Int,
    val status: ContentStatus,
    val summative: Boolean,
    val cognitiveLevel: String = "",
    val difficulty: String = "",
    val allowedChapterIds: Set<String> = emptySet()
)

data class SyllabusExclusion(val chapterId: String? = null, val sloId: String? = null)

data class ComplianceReport(
    val passed: Boolean,
    val offeredMarks: Int,
    val attemptedMarks: Int,
    val errors: List<String>,
    val warnings: List<String>
)

/** Strict pre-export gate. It reports conflicts; it never guesses which source is correct. */
object BoardPatternCompliance {
    fun audit(
        pattern: CompliancePattern,
        offeredQuestions: List<ComplianceQuestion>,
        exclusions: Set<SyllabusExclusion> = emptySet(),
        officialMode: Boolean = true
    ): ComplianceReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val sections = pattern.sections

        if (sections.isEmpty()) errors += "Pattern has no sections."
        if (sections.any {
                it.id.isBlank() || it.offeredCount <= 0 || it.attemptCount !in 1..it.offeredCount ||
                    it.marksPerQuestion <= 0 ||
                    (it.offeredMarksOverride != null && it.offeredMarksOverride <= 0) ||
                    (it.attemptedMarksOverride != null && it.attemptedMarksOverride <= 0) ||
                    (it.offeredMarksOverride != null && it.attemptedMarksOverride != null &&
                        it.attemptedMarksOverride > it.offeredMarksOverride)
            }) {
            errors += "Every section must have a unique ID, positive marks/counts, valid mark totals, and attempts no greater than offers."
        }
        if (sections.map { it.id }.distinct().size != sections.size) errors += "Pattern contains duplicate section IDs."
        if (pattern.declaredAttemptedMarks <= 0) errors += "Declared attempted total must be positive."
        if (pattern.hasUnresolvedSourceConflict) errors += "Official source conflict is unresolved; official generation is blocked."
        if (officialMode && pattern.sourceStatus != ContentStatus.VERIFIED_OFFICIAL) errors += "Primary source is not verified official."
        if (officialMode && pattern.patternStatus != ContentStatus.VERIFIED_OFFICIAL) errors += "Paper pattern is not verified official."

        val offered = sections.sumOf { section ->
            section.offeredMarksOverride ?: section.offeredCount.coerceAtLeast(0) * section.marksPerQuestion.coerceAtLeast(0)
        }
        val attempted = sections.sumOf { section ->
            section.attemptedMarksOverride ?: section.attemptCount.coerceAtLeast(0) * section.marksPerQuestion.coerceAtLeast(0)
        }
        if (attempted != pattern.declaredAttemptedMarks) errors += "Attempted total mismatch: sections produce $attempted, pattern declares ${pattern.declaredAttemptedMarks}."

        val expectedCount = sections.sumOf { it.offeredCount.coerceAtLeast(0) }
        if (offeredQuestions.size != expectedCount) errors += "Expected $expectedCount offered questions, received ${offeredQuestions.size}."
        if (offeredQuestions.map { it.id }.distinct().size != offeredQuestions.size) errors += "The offered paper contains duplicate question IDs."
        val sectionById = sections.associateBy { it.id }
        val grouped = offeredQuestions.groupBy { it.sectionId }
        val unknownSections = grouped.keys - sectionById.keys
        if (unknownSections.isNotEmpty()) errors += "Questions belong to unknown section(s): ${unknownSections.joinToString()} ."

        offeredQuestions.forEach { q ->
            if (q.identity != pattern.identity) errors += "Question ${q.id} has a board/grade/stream/subject/session mismatch."
            val rule = sectionById[q.sectionId]
            if (rule != null && !rule.allowVariableQuestionMarks && q.marks != rule.marksPerQuestion) errors += "Question ${q.id} has ${q.marks} marks; section ${q.sectionId} requires ${rule.marksPerQuestion}."
            if (q.chapterId.isBlank() || q.sloId.isBlank()) warnings += "Question ${q.id} is missing chapter or SLO metadata."
            if (q.allowedChapterIds.isNotEmpty() && q.chapterId !in q.allowedChapterIds) {
                errors += "Question ${q.id} uses chapter ${q.chapterId}; its slot allows only ${q.allowedChapterIds.sorted().joinToString(" or ")}."
            }
            if (exclusions.any { ex -> (ex.chapterId != null && ex.chapterId == q.chapterId) || (ex.sloId != null && ex.sloId == q.sloId) }) {
                errors += "Question ${q.id} belongs to an excluded syllabus chapter/SLO."
            }
            if (officialMode && (!q.summative || q.status != ContentStatus.VERIFIED_OFFICIAL)) {
                errors += "Question ${q.id} is not a verified summative question."
            }
            if (q.status == ContentStatus.CONFLICTED) errors += "Question ${q.id} has conflicted provenance."
        }
        sections.forEach { rule ->
            val actual = grouped[rule.id].orEmpty().size
            if (actual != rule.offeredCount) errors += "Section ${rule.id} requires ${rule.offeredCount} offered questions; received $actual."
        }
        pattern.metadataQuotas.forEach { quota ->
            if (quota.groupId != null && quota.groupId !in sectionById) {
                errors += "Metadata quota refers to unknown section ${quota.groupId}."
            } else {
                val matching = offeredQuestions.count { q ->
                    (quota.groupId == null || q.sectionId == quota.groupId) && when (quota.dimension) {
                        QuotaDimension.CHAPTER -> q.chapterId == quota.value
                        QuotaDimension.SLO -> q.sloId == quota.value
                        QuotaDimension.COGNITIVE_LEVEL -> q.cognitiveLevel == quota.value
                        QuotaDimension.DIFFICULTY -> q.difficulty == quota.value
                    }
                }
                if (matching !in quota.minimum..quota.maximum) {
                    val scope = quota.groupId?.let { " in section $it" } ?: ""
                    errors += "Quota ${quota.dimension}=${quota.value}$scope requires ${quota.minimum}..${quota.maximum}; paper has $matching."
                }
            }
        }
        if (officialMode && warnings.isNotEmpty()) warnings += "Metadata warnings should be resolved before approving a production content pack."
        return ComplianceReport(errors.isEmpty(), offered, attempted, errors.distinct(), warnings.distinct())
    }
}
