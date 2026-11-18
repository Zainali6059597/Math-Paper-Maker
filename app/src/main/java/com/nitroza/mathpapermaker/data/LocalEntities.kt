package com.nitroza.mathpapermaker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "question_sources")
data class QuestionSourceEntity(
    @PrimaryKey val sourceId: String,
    val issuer: String,
    val title: String,
    val sourceUrl: String?,
    val sourceType: String,
    val session: String?,
    val pageReference: String?,
    val verificationStatus: String
)

@Entity(
    tableName = "questions",
    foreignKeys = [ForeignKey(entity = QuestionSourceEntity::class, parentColumns = ["sourceId"], childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT)],
    indices = [
        Index(value = ["board", "grade", "stream", "subject", "session"]),
        Index(value = ["semanticFingerprint"]),
        Index(value = ["sourceId"])
    ]
)
data class QuestionEntity(
    @PrimaryKey val questionId: String,
    val board: String,
    val grade: Int,
    val stream: String,
    val subject: String,
    val session: String,
    val chapterId: String,
    val topic: String,
    val sloId: String,
    val cognitiveLevel: String,
    val difficulty: String,
    val questionType: String,
    val marks: Int,
    val questionText: String,
    val answerText: String?,
    val summative: Boolean,
    val verificationStatus: String,
    val copyrightStatus: String,
    val sourceId: String,
    val sourcePage: String?,
    val semanticFingerprint: String,
    @ColumnInfo(defaultValue = "1.0") val selectionWeight: Double = 1.0
)

@Entity(
    tableName = "paper_patterns",
    foreignKeys = [ForeignKey(entity = QuestionSourceEntity::class, parentColumns = ["sourceId"], childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT)],
    indices = [
        Index(value = ["board", "grade", "stream", "subject", "session", "version"], unique = true),
        Index(value = ["sourceId"])
    ]
)
data class PaperPatternEntity(
    @PrimaryKey val patternId: String,
    val board: String,
    val grade: Int,
    val stream: String,
    val subject: String,
    val session: String,
    val version: Int,
    val totalMarks: Int,
    val durationMinutes: Int?,
    val verificationStatus: String,
    val sourceId: String,
    val sourcePage: String?
)

@Entity(
    tableName = "pattern_slots", primaryKeys = ["patternId", "slotId"],
    foreignKeys = [ForeignKey(entity = PaperPatternEntity::class, parentColumns = ["patternId"], childColumns = ["patternId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["patternId"])]
)
data class PatternSlotEntity(
    val patternId: String,
    val slotId: String,
    val chapterId: String?,
    val sloId: String?,
    val questionType: String?,
    val marks: Int,
    val displayOrder: Int,
    val groupId: String? = null,
    val bundleId: String? = null,
    val bundleChoiceGroupId: String? = null,
    @ColumnInfo(defaultValue = "'[]'") val allowedChapterIdsJson: String = "[]"
)

@Entity(
    tableName = "choice_groups",
    primaryKeys = ["patternId", "groupId"],
    foreignKeys = [ForeignKey(entity = PaperPatternEntity::class, parentColumns = ["patternId"], childColumns = ["patternId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["patternId"])]
)
data class ChoiceGroupEntity(
    val patternId: String,
    val groupId: String,
    val offeredCount: Int,
    val attemptCount: Int,
    val marksPerQuestion: Int,
    val displayOrder: Int
)



@Entity(
    tableName = "bundle_choice_groups",
    primaryKeys = ["patternId", "groupId"],
    foreignKeys = [ForeignKey(entity = PaperPatternEntity::class, parentColumns = ["patternId"], childColumns = ["patternId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["patternId"])]
)
data class BundleChoiceGroupEntity(
    val patternId: String,
    val groupId: String,
    val offeredBundleCount: Int,
    val attemptBundleCount: Int,
    val marksPerBundle: Int,
    val bundleIdsJson: String,
    val displayOrder: Int
)

@Entity(
    tableName = "pattern_quotas",
    primaryKeys = ["patternId", "dimension", "value"],
    foreignKeys = [ForeignKey(entity = PaperPatternEntity::class, parentColumns = ["patternId"], childColumns = ["patternId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["patternId"])]
)
data class PatternQuotaEntity(
    val patternId: String,
    val dimension: String,
    val value: String,
    val minimum: Int,
    val maximum: Int
)

@Entity(tableName = "saved_drafts", indices = [Index(value = ["updatedAt"])])
data class SavedPaperDraftEntity(
    @PrimaryKey val draftId: String,
    val displayName: String,
    val board: String,
    val grade: Int,
    val stream: String,
    val subject: String,
    val session: String,
    val schoolName: String,
    val examTitle: String,
    val instructions: String,
    val totalAttemptedMarks: Int,
    val offeredMarks: Int,
    val customDraft: Boolean,
    val itemsJson: String,
    val slotsJson: String,
    val choiceInstructionsJson: String,
    val generationMode: String,
    val updatedAt: Long,
    val patternId: String? = null,
    val patternVersion: Int? = null,
    val patternSourceId: String? = null
)



@Entity(tableName = "textbook_candidates", indices = [Index(value = ["bookFile", "chapterId", "exerciseId"])])
data class TextbookCandidateEntity(
    @PrimaryKey val candidateId: String,
    val bookFile: String,
    val chapterId: String,
    val exerciseId: String,
    val questionNumber: String,
    val questionText: String,
    val sourcePage: Int,
    @androidx.room.ColumnInfo(defaultValue = "'SHORT'") val questionType: String = "SHORT",
    @androidx.room.ColumnInfo(defaultValue = "2") val marks: Int = 2,
    val imageFile: String? = null,
    val reviewStatus: String = "NEEDS_REVIEW",
    val reviewNote: String = "",
    val detectedAt: Long
)

@Entity(tableName = "review_audit", indices = [Index(value = ["itemType", "itemId"]), Index(value = ["reviewedAt"])])
data class ReviewAuditEntity(
    @PrimaryKey val auditId: String,
    val itemType: String,
    val itemId: String,
    val previousStatus: String,
    val newStatus: String,
    val checklistJson: String,
    val reviewNote: String,
    val reviewedAt: Long
)


@Entity(
    tableName = "syllabus_exclusions",
    primaryKeys = ["patternId", "exclusionType", "value"],
    foreignKeys = [ForeignKey(entity = PaperPatternEntity::class, parentColumns = ["patternId"], childColumns = ["patternId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["patternId"])]
)
data class SyllabusExclusionEntity(
    val patternId: String,
    val exclusionType: String,
    val value: String,
    val reason: String? = null,
    val sourcePage: String? = null
)
