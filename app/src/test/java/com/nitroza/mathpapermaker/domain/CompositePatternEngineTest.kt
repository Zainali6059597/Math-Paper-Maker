package com.nitroza.mathpapermaker.domain

import org.junit.Assert.*
import org.junit.Test

class CompositePatternEngineTest {
    private val identity = PaperIdentity("BOARD", 9, "MATHEMATICS", "Mathematics", "2027")
    private fun q(id: String, marks: Int) = QuestionCandidate(
        id, identity, "CH1", "SLO1", "SHORT", marks, true, ContentStatus.VERIFIED_OFFICIAL
    )

    @Test fun fixedChoiceAndRequiredPairedPartsShareOneUniqueAssignment() {
        val slots = listOf(
            OfferedQuestionSlot("FIXED", 5),
            OfferedQuestionSlot("PAIR_A", 2, bundleId = "PAIR"),
            OfferedQuestionSlot("PAIR_B", 3, bundleId = "PAIR"),
            OfferedQuestionSlot("CHOICE_1", 2, groupId = "SHORT"),
            OfferedQuestionSlot("CHOICE_2", 2, groupId = "SHORT"),
            OfferedQuestionSlot("CHOICE_3", 2, groupId = "SHORT")
        )
        val pattern = CompositePaperPattern(
            identity, 14, slots,
            listOf(ChoiceGroupRule("SHORT", 3, 2, 2, slots.filter { it.groupId == "SHORT" }))
        )
        val bank = listOf(q("Q5", 5), q("Q2", 2), q("Q3", 3), q("Q21", 2), q("Q22", 2), q("Q23", 2))
        val result = CompositePatternEngine.generate(pattern, bank, GenerationMode.OFFICIAL, 77)
        assertTrue(result is CompositeSelectionResult.Success)
        val paper = (result as CompositeSelectionResult.Success).paper
        assertEquals(16, paper.offeredMarks)
        assertEquals(14, paper.attemptedMarks)
        assertEquals(6, paper.selectedBySlot.size)
        assertEquals(6, paper.selectedBySlot.values.map { it.id }.distinct().size)
    }

    @Test fun aSlotCannotBeBothChoiceAndBundle() {
        val slots = listOf(OfferedQuestionSlot("AMBIGUOUS", 2, bundleId = "PAIR", groupId = "G"))
        val pattern = CompositePaperPattern(identity, 2, slots, listOf(ChoiceGroupRule("G", 1, 1, 2, slots)))
        val result = CompositePatternEngine.generate(pattern, listOf(q("Q", 2)), GenerationMode.PRACTICE, 1)
        assertTrue(result is CompositeSelectionResult.Blocked)
        assertEquals("AMBIGUOUS_SLOT", (result as CompositeSelectionResult.Blocked).code)
    }

    @Test fun requiredMarksAndAttemptedChoiceMarksMustMatchDeclaredTotal() {
        val slots = listOf(
            OfferedQuestionSlot("FIXED", 4),
            OfferedQuestionSlot("C1", 2, groupId = "G"),
            OfferedQuestionSlot("C2", 2, groupId = "G")
        )
        val pattern = CompositePaperPattern(identity, 8, slots, listOf(ChoiceGroupRule("G", 2, 1, 2, slots.drop(1))))
        val result = CompositePatternEngine.generate(pattern, listOf(q("Q4", 4), q("Q2a", 2), q("Q2b", 2)), GenerationMode.PRACTICE, 1)
        assertTrue(result is CompositeSelectionResult.Blocked)
        assertEquals("ATTEMPTED_TOTAL_MISMATCH", (result as CompositeSelectionResult.Blocked).code)
    }

    @Test fun optionalPairedLongQuestionsAreSelectedAsWholeEightMarkBundles() {
        val slots = mutableListOf<OfferedQuestionSlot>()
        val bank = mutableListOf<QuestionCandidate>()
        repeat(15) { i ->
            slots += OfferedQuestionSlot("MCQ$i", 1, questionType = "MCQ")
            bank += QuestionCandidate("M$i", identity, "CH1", "SLO", "MCQ", 1, true, ContentStatus.VERIFIED_OFFICIAL)
        }
        val shortGroups = listOf("Q2" to 1..9, "Q3" to 10..18, "Q4" to 19..27)
        shortGroups.forEach { (group, range) ->
            range.forEach { i ->
                slots += OfferedQuestionSlot("S$i", 2, questionType = "SHORT", groupId = group)
                bank += QuestionCandidate("S$i", identity, "CH1", "SLO", "SHORT", 2, true, ContentStatus.VERIFIED_OFFICIAL)
            }
        }
        val choiceGroups = shortGroups.map { (group, range) ->
            ChoiceGroupRule(group, 9, 6, 2, slots.filter { it.groupId == group })
        }
        val bundleIds = listOf("Q5", "Q6", "Q7", "Q8", "Q9")
        bundleIds.forEach { bundle ->
            listOf("a", "b").forEach { part ->
                slots += OfferedQuestionSlot("${bundle}_$part", 4, questionType = "LONG",
                    bundleId = bundle, bundleChoiceGroupId = if (bundle in setOf("Q5", "Q6", "Q7")) "Q5-Q7" else "Q8-Q9")
                bank += QuestionCandidate("${bundle}_$part", identity, "CH1", "SLO", "LONG", 4, true, ContentStatus.VERIFIED_OFFICIAL)
            }
        }
        val pattern = CompositePaperPattern(
            identity = identity,
            expectedAttemptedMarks = 75,
            slots = slots,
            groups = choiceGroups,
            bundleGroups = listOf(
                BundleChoiceGroupRule("Q5-Q7", 3, 2, 8, setOf("Q5", "Q6", "Q7")),
                BundleChoiceGroupRule("Q8-Q9", 2, 1, 8, setOf("Q8", "Q9"))
            )
        )
        val result = CompositePatternEngine.generate(pattern, bank, GenerationMode.OFFICIAL, 2027)
        assertTrue(result.toString(), result is CompositeSelectionResult.Success)
        val paper = (result as CompositeSelectionResult.Success).paper
        assertEquals(75, paper.attemptedMarks)
        assertEquals(109, paper.offeredMarks)
        assertEquals(52, paper.selectedBySlot.size)
    }

    @Test fun optionalPairedGroupRejectsAnIncompleteQuestionBundle() {
        val slots = listOf(
            OfferedQuestionSlot("Q5_a", 4, questionType = "LONG", bundleId = "Q5", bundleChoiceGroupId = "Q5-Q7"),
            OfferedQuestionSlot("Q6_a", 4, questionType = "LONG", bundleId = "Q6", bundleChoiceGroupId = "Q5-Q7")
        )
        val pattern = CompositePaperPattern(identity, 8, slots, emptyList(),
            listOf(BundleChoiceGroupRule("Q5-Q7", 2, 1, 8, setOf("Q5", "Q6"))))
        val result = CompositePatternEngine.generate(pattern, listOf(
            QuestionCandidate("A", identity, "CH1", "SLO", "LONG", 4, true, ContentStatus.VERIFIED_OFFICIAL),
            QuestionCandidate("B", identity, "CH1", "SLO", "LONG", 4, true, ContentStatus.VERIFIED_OFFICIAL)
        ), GenerationMode.PRACTICE, 1)
        assertTrue(result is CompositeSelectionResult.Blocked)
        assertEquals("INCOMPLETE_BUNDLE_GROUP", (result as CompositeSelectionResult.Blocked).code)
    }

    @Test fun chapterAlternativeSlotAcceptsOnlyDeclaredChapters() {
        val slot = OfferedQuestionSlot("Q8_B", 4, questionType = "LONG", allowedChapterIds = setOf("CH8", "CH11"))
        val pattern = CompositePaperPattern(identity, 4, listOf(slot), emptyList())
        val allowed = QuestionCandidate("ALLOWED", identity, "CH11", "SLO", "LONG", 4, true, ContentStatus.VERIFIED_OFFICIAL)
        val blocked = QuestionCandidate("WRONG", identity, "CH9", "SLO", "LONG", 4, true, ContentStatus.VERIFIED_OFFICIAL)
        val accepted = CompositePatternEngine.generate(pattern, listOf(allowed), GenerationMode.OFFICIAL, 1)
        assertTrue(accepted is CompositeSelectionResult.Success)
        val rejected = CompositePatternEngine.generate(pattern, listOf(blocked), GenerationMode.OFFICIAL, 1)
        assertTrue(rejected is CompositeSelectionResult.Blocked)
        assertEquals("NO_CANDIDATE_FOR_SLOT", (rejected as CompositeSelectionResult.Blocked).code)
    }
    @Test fun chapterQuotaIsScopedToItsOwnQuestionSection() {
        val slots = listOf(
            OfferedQuestionSlot("Q2_1", 1, chapterId = "CH2", questionType = "SHORT", groupId = "Q2"),
            OfferedQuestionSlot("Q3_1", 1, chapterId = "CH1", questionType = "SHORT", groupId = "Q3")
        )
        val groups = listOf(
            ChoiceGroupRule("Q2", 1, 1, 1, listOf(slots[0])),
            ChoiceGroupRule("Q3", 1, 1, 1, listOf(slots[1]))
        )
        val pattern = CompositePaperPattern(identity, 2, slots, groups, quotas = listOf(
            MetadataQuota(QuotaDimension.CHAPTER, "CH1", 1, 1, "Q2")
        ))
        val bank = listOf(
            QuestionCandidate("Q2-WRONG", identity, "CH2", "S", "SHORT", 1, true, ContentStatus.VERIFIED_OFFICIAL),
            QuestionCandidate("Q3-ONLY", identity, "CH1", "S", "SHORT", 1, true, ContentStatus.VERIFIED_OFFICIAL)
        )
        val result = CompositePatternEngine.generate(pattern, bank, GenerationMode.OFFICIAL, 9)
        assertTrue(result is CompositeSelectionResult.Blocked)
        assertEquals("QUOTA_UNSATISFIABLE", (result as CompositeSelectionResult.Blocked).code)

    }
}
