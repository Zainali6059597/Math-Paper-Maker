package com.nitroza.mathpapermaker.domain

data class SectionRule(
    val sectionId: String,
    val offeredCount: Int,
    val attemptCount: Int,
    val marksPerQuestion: Int
)

data class PaperPattern(
    val expectedAttemptedMarks: Int,
    val sections: List<SectionRule>
)

data class SectionSelection(
    val sectionId: String,
    val offeredQuestionMarks: List<Int>,
    val attemptedCount: Int
)

data class PatternAudit(
    val offeredMarks: Int,
    val attemptedMarks: Int,
    val valid: Boolean,
    val issues: List<String>
)

/** Validates fixed-mark sections; choice bundles and variable-mark questions need a richer rule model. */
object PatternAuditEngine {
    fun audit(pattern: PaperPattern, selections: List<SectionSelection>): PatternAudit {
        val issues = mutableListOf<String>()
        if (pattern.sections.map { it.sectionId }.distinct().size != pattern.sections.size) {
            issues += "Pattern contains duplicate section IDs."
        }
        val byId = selections.associateBy { it.sectionId }
        if (byId.size != selections.size) issues += "Selection contains duplicate section IDs."
        pattern.sections.forEach { rule ->
            val selected = byId[rule.sectionId]
            if (rule.offeredCount <= 0 || rule.attemptCount <= 0 || rule.attemptCount > rule.offeredCount || rule.marksPerQuestion <= 0) {
                issues += "Invalid rule for section ${rule.sectionId}."
            }
            if (selected == null) {
                issues += "Missing section ${rule.sectionId}."
            } else {
                if (selected.offeredQuestionMarks.size != rule.offeredCount) issues += "Wrong offered count in ${rule.sectionId}."
                if (selected.offeredQuestionMarks.any { it != rule.marksPerQuestion }) issues += "Unexpected question marks in ${rule.sectionId}."
                if (selected.attemptedCount != rule.attemptCount) issues += "Wrong attempt count in ${rule.sectionId}."
            }
        }
        val extra = selections.map { it.sectionId }.toSet() - pattern.sections.map { it.sectionId }.toSet()
        if (extra.isNotEmpty()) issues += "Unknown section(s): ${extra.joinToString()}."
        val offered = pattern.sections.sumOf { it.offeredCount * it.marksPerQuestion }
        val attempted = pattern.sections.sumOf { it.attemptCount * it.marksPerQuestion }
        if (attempted != pattern.expectedAttemptedMarks) issues += "Attempted marks do not equal the official total."
        return PatternAudit(offered, attempted, issues.isEmpty(), issues)
    }
}
