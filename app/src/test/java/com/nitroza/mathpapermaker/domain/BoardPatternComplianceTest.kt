package com.nitroza.mathpapermaker.domain

import org.junit.Assert.*
import org.junit.Test

class BoardPatternComplianceTest {
    private val identity = PaperIdentity("Punjab", 9, "MATHEMATICS", "Mathematics", "2026-27")
    private val pattern = CompliancePattern(identity, 18,
        listOf(ComplianceSection("B", 12, 9, 2)),
        ContentStatus.VERIFIED_OFFICIAL, ContentStatus.VERIFIED_OFFICIAL)
    private fun questions() = (1..12).map { ComplianceQuestion("q$it", identity, "B", "ch1", "slo1", 2, ContentStatus.VERIFIED_OFFICIAL, true) }

    @Test fun validOfferedAndAttemptedTotalsAreSeparate() {
        val report = BoardPatternCompliance.audit(pattern, questions())
        assertTrue(report.passed)
        assertEquals(24, report.offeredMarks)
        assertEquals(18, report.attemptedMarks)
    }

    @Test fun unresolvedOfficialSourceConflictBlocks() {
        val report = BoardPatternCompliance.audit(pattern.copy(hasUnresolvedSourceConflict = true), questions())
        assertFalse(report.passed)
        assertTrue(report.errors.any { it.contains("conflict") })
    }

    @Test fun wrongStreamAndExcludedSloBlock() {
        val wrong = questions().mapIndexed { i, q -> if (i == 0) q.copy(identity = identity.copy(stream = "GENERAL_MATHS")) else q }
        val report = BoardPatternCompliance.audit(pattern, wrong, setOf(SyllabusExclusion(sloId = "slo1")))
        assertFalse(report.passed)
        assertTrue(report.errors.any { it.contains("stream") })
        assertTrue(report.errors.any { it.contains("excluded") })
    }

    @Test fun incorrectOfferedCountBlocks() {
        val report = BoardPatternCompliance.audit(pattern, questions().dropLast(1))
        assertFalse(report.passed)
        assertTrue(report.errors.any { it.contains("offered") })
    }

    @Test fun excludedChapterBlocksEvenWhenSloDiffers() {
        val bank = questions().map { it.copy(chapterId = "excluded-chapter", sloId = "allowed-slo") }
        val report = BoardPatternCompliance.audit(pattern, bank,
            setOf(SyllabusExclusion(chapterId = "excluded-chapter")))
        assertFalse(report.passed)
        assertTrue(report.errors.any { it.contains("excluded") })
    }

    @Test fun malformedSectionAndDuplicateIdsAreRejected() {
        val malformed = pattern.copy(sections = listOf(
            ComplianceSection("B", 12, 13, 2), ComplianceSection("B", 1, 1, 2)))
        val report = BoardPatternCompliance.audit(malformed, questions())
        assertFalse(report.passed)
        assertTrue(report.errors.any { it.contains("duplicate section") })
        assertTrue(report.errors.any { it.contains("positive marks/counts") })
    }

    @Test fun multipleChoiceGroupsKeepOfferedAndAttemptedMarksDistinct() {
        val multi = pattern.copy(
            declaredAttemptedMarks = 10,
            sections = listOf(
                ComplianceSection("B", 3, 1, 2),
                ComplianceSection("C", 3, 2, 4)
            )
        )
        val bank = (1..3).map { ComplianceQuestion("b$it", identity, "B", "ch1", "slo1", 2, ContentStatus.VERIFIED_OFFICIAL, true) } +
            (1..3).map { ComplianceQuestion("c$it", identity, "C", "ch2", "slo2", 4, ContentStatus.VERIFIED_OFFICIAL, true) }
        val report = BoardPatternCompliance.audit(multi, bank)
        assertTrue(report.passed)
        assertEquals(18, report.offeredMarks)
        assertEquals(10, report.attemptedMarks)
    }

    @Test fun optionalPairedBundleSectionsAuditOfferedAndAttemptedMarksSeparately() {
        val multi = pattern.copy(
            declaredAttemptedMarks = 16,
            sections = listOf(
                ComplianceSection("SHORT", 3, 2, 2),
                ComplianceSection(
                    id = "Q5-Q7",
                    offeredCount = 6,
                    attemptCount = 6,
                    marksPerQuestion = 4,
                    offeredMarksOverride = 24,
                    attemptedMarksOverride = 16,
                    allowVariableQuestionMarks = true
                )
            )
        )
        val bank = (1..3).map {
            ComplianceQuestion("s$it", identity, "SHORT", "ch1", "slo1", 2, ContentStatus.VERIFIED_OFFICIAL, true)
        } + (1..6).map {
            ComplianceQuestion("l$it", identity, "Q5-Q7", "ch2", "slo2", 4, ContentStatus.VERIFIED_OFFICIAL, true)
        }
        val report = BoardPatternCompliance.audit(multi, bank)
        assertTrue(report.errors.joinToString(), report.passed)
        assertEquals(30, report.offeredMarks)
        assertEquals(20, report.attemptedMarks)
    }

    @Test fun sourceConflictBlocksEvenInPracticeMode() {
        val report = BoardPatternCompliance.audit(pattern.copy(hasUnresolvedSourceConflict = true), questions(), officialMode = false)
        assertFalse(report.passed)
        assertTrue(report.errors.any { it.contains("conflict") })
    }

    @Test fun practiceModeCanUseUnverifiedContentButNotConflict() {
        val bank = questions().map { it.copy(status = ContentStatus.NEEDS_REVIEW) }
        assertTrue(BoardPatternCompliance.audit(pattern, bank, officialMode = false).passed)
        val conflicted = bank.toMutableList().also { it[0] = it[0].copy(status = ContentStatus.CONFLICTED) }
        assertFalse(BoardPatternCompliance.audit(pattern, conflicted, officialMode = false).passed)
    }
}
