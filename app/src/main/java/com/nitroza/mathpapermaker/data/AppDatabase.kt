package com.nitroza.mathpapermaker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction

@Dao
interface LocalContentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTextbookCandidates(items: List<TextbookCandidateEntity>): List<Long>
    @Query("SELECT * FROM textbook_candidates ORDER BY bookFile, chapterId, sourcePage, questionNumber")
    suspend fun allTextbookCandidates(): List<TextbookCandidateEntity>
    @Query("UPDATE textbook_candidates SET reviewStatus=:status, reviewNote=:note WHERE candidateId=:candidateId")
    suspend fun updateTextbookCandidateReview(candidateId: String, status: String, note: String)
    @Query("SELECT COUNT(*) FROM textbook_candidates WHERE reviewStatus='NEEDS_REVIEW'")
    suspend fun pendingTextbookCandidateCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertQuestions(items: List<QuestionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSources(items: List<QuestionSourceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPatterns(items: List<PaperPatternEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSlots(items: List<PatternSlotEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertChoiceGroups(items: List<ChoiceGroupEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBundleChoiceGroups(items: List<BundleChoiceGroupEntity>)
    @Query("SELECT * FROM bundle_choice_groups WHERE patternId=:patternId ORDER BY displayOrder, groupId") suspend fun findBundleChoiceGroups(patternId: String): List<BundleChoiceGroupEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPatternQuotas(items: List<PatternQuotaEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSyllabusExclusions(items: List<SyllabusExclusionEntity>)
    @Query("SELECT * FROM syllabus_exclusions WHERE patternId=:patternId ORDER BY exclusionType, value") suspend fun findSyllabusExclusions(patternId: String): List<SyllabusExclusionEntity>

    @Query("SELECT COUNT(*) FROM questions") suspend fun questionCount(): Int
    @Query("SELECT COUNT(*) FROM paper_patterns") suspend fun patternCount(): Int
    @Query("SELECT * FROM questions WHERE board=:board AND grade=:grade AND stream=:stream AND subject=:subject AND session=:session")
    suspend fun findQuestions(board: String, grade: Int, stream: String, subject: String, session: String): List<QuestionEntity>
    @Query("SELECT * FROM paper_patterns WHERE board=:board AND grade=:grade AND stream=:stream AND subject=:subject AND session=:session ORDER BY version DESC LIMIT 1")
    suspend fun findPattern(board: String, grade: Int, stream: String, subject: String, session: String): PaperPatternEntity?
    @Query("SELECT * FROM paper_patterns WHERE patternId=:patternId LIMIT 1")
    suspend fun findPatternById(patternId: String): PaperPatternEntity?
    @Query("SELECT * FROM pattern_slots WHERE patternId=:patternId ORDER BY displayOrder, slotId")
    suspend fun findSlots(patternId: String): List<PatternSlotEntity>
    @Query("SELECT * FROM choice_groups WHERE patternId=:patternId ORDER BY displayOrder, groupId")
    suspend fun findChoiceGroups(patternId: String): List<ChoiceGroupEntity>
    @Query("SELECT * FROM pattern_quotas WHERE patternId=:patternId ORDER BY dimension, value")
    suspend fun findPatternQuotas(patternId: String): List<PatternQuotaEntity>
    @Query("SELECT * FROM question_sources WHERE sourceId=:sourceId LIMIT 1")
    suspend fun findSource(sourceId: String): QuestionSourceEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDraft(draft: SavedPaperDraftEntity)
    @Query("SELECT * FROM saved_drafts ORDER BY updatedAt DESC") suspend fun listDrafts(): List<SavedPaperDraftEntity>
    @Query("SELECT * FROM saved_drafts WHERE draftId=:draftId LIMIT 1") suspend fun getDraft(draftId: String): SavedPaperDraftEntity?
    @Query("DELETE FROM saved_drafts WHERE draftId=:draftId") suspend fun deleteDraft(draftId: String)

    @Query("SELECT * FROM question_sources WHERE verificationStatus='NEEDS_REVIEW' ORDER BY title")
    suspend fun pendingSources(): List<QuestionSourceEntity>
    @Query("SELECT * FROM paper_patterns WHERE verificationStatus='NEEDS_REVIEW' ORDER BY board, grade, subject")
    suspend fun pendingPatterns(): List<PaperPatternEntity>
    @Query("SELECT * FROM questions WHERE verificationStatus='NEEDS_REVIEW' ORDER BY board, grade, chapterId")
    suspend fun pendingQuestions(): List<QuestionEntity>
    @Query("SELECT * FROM question_sources WHERE sourceId=:id LIMIT 1")
    suspend fun sourceById(id: String): QuestionSourceEntity?
    @Query("UPDATE question_sources SET verificationStatus=:status WHERE sourceId=:id")
    suspend fun setSourceStatus(id: String, status: String)
    @Query("UPDATE paper_patterns SET verificationStatus=:status WHERE patternId=:id")
    suspend fun setPatternStatus(id: String, status: String)
    @Query("UPDATE questions SET verificationStatus=:status WHERE questionId=:id")
    suspend fun setQuestionStatus(id: String, status: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReviewAudit(record: ReviewAuditEntity)
    @Query("SELECT * FROM review_audit ORDER BY reviewedAt DESC LIMIT 100")
    suspend fun recentReviewAudit(): List<ReviewAuditEntity>
}

@Database(
    entities = [QuestionSourceEntity::class, QuestionEntity::class, PaperPatternEntity::class, PatternSlotEntity::class, ChoiceGroupEntity::class, SavedPaperDraftEntity::class, ReviewAuditEntity::class, PatternQuotaEntity::class, SyllabusExclusionEntity::class, TextbookCandidateEntity::class, BundleChoiceGroupEntity::class],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contentDao(): LocalContentDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pattern_slots ADD COLUMN groupId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS choice_groups (patternId TEXT NOT NULL, groupId TEXT NOT NULL, offeredCount INTEGER NOT NULL, attemptCount INTEGER NOT NULL, marksPerQuestion INTEGER NOT NULL, displayOrder INTEGER NOT NULL, PRIMARY KEY(patternId, groupId), FOREIGN KEY(patternId) REFERENCES paper_patterns(patternId) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_choice_groups_patternId ON choice_groups(patternId)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS saved_drafts (draftId TEXT NOT NULL, displayName TEXT NOT NULL, board TEXT NOT NULL, grade INTEGER NOT NULL, stream TEXT NOT NULL, subject TEXT NOT NULL, session TEXT NOT NULL, schoolName TEXT NOT NULL, examTitle TEXT NOT NULL, instructions TEXT NOT NULL, totalAttemptedMarks INTEGER NOT NULL, offeredMarks INTEGER NOT NULL, customDraft INTEGER NOT NULL, itemsJson TEXT NOT NULL, slotsJson TEXT NOT NULL, choiceInstructionsJson TEXT NOT NULL, generationMode TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(draftId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_drafts_updatedAt ON saved_drafts(updatedAt)")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS review_audit (auditId TEXT NOT NULL, itemType TEXT NOT NULL, itemId TEXT NOT NULL, previousStatus TEXT NOT NULL, newStatus TEXT NOT NULL, checklistJson TEXT NOT NULL, reviewNote TEXT NOT NULL, reviewedAt INTEGER NOT NULL, PRIMARY KEY(auditId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_review_audit_itemType_itemId ON review_audit(itemType, itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_review_audit_reviewedAt ON review_audit(reviewedAt)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_drafts ADD COLUMN patternId TEXT")
                db.execSQL("ALTER TABLE saved_drafts ADD COLUMN patternVersion INTEGER")
                db.execSQL("ALTER TABLE saved_drafts ADD COLUMN patternSourceId TEXT")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pattern_quotas (patternId TEXT NOT NULL, dimension TEXT NOT NULL, value TEXT NOT NULL, minimum INTEGER NOT NULL, maximum INTEGER NOT NULL, PRIMARY KEY(patternId, dimension, value), FOREIGN KEY(patternId) REFERENCES paper_patterns(patternId) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pattern_quotas_patternId ON pattern_quotas(patternId)")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS syllabus_exclusions (patternId TEXT NOT NULL, exclusionType TEXT NOT NULL, value TEXT NOT NULL, reason TEXT, sourcePage TEXT, PRIMARY KEY(patternId, exclusionType, value), FOREIGN KEY(patternId) REFERENCES paper_patterns(patternId) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_syllabus_exclusions_patternId ON syllabus_exclusions(patternId)")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pattern_slots ADD COLUMN bundleId TEXT")
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN selectionWeight REAL NOT NULL DEFAULT 1.0")
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS textbook_candidates (candidateId TEXT NOT NULL PRIMARY KEY, bookFile TEXT NOT NULL, chapterId TEXT NOT NULL, exerciseId TEXT NOT NULL, questionNumber TEXT NOT NULL, questionText TEXT NOT NULL, sourcePage INTEGER NOT NULL, reviewStatus TEXT NOT NULL, reviewNote TEXT NOT NULL, detectedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_textbook_candidates_bookFile_chapterId_exerciseId ON textbook_candidates(bookFile, chapterId, exerciseId)")
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pattern_slots ADD COLUMN bundleChoiceGroupId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS bundle_choice_groups (patternId TEXT NOT NULL, groupId TEXT NOT NULL, offeredBundleCount INTEGER NOT NULL, attemptBundleCount INTEGER NOT NULL, marksPerBundle INTEGER NOT NULL, bundleIdsJson TEXT NOT NULL, displayOrder INTEGER NOT NULL, PRIMARY KEY(patternId, groupId), FOREIGN KEY(patternId) REFERENCES paper_patterns(patternId) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bundle_choice_groups_patternId ON bundle_choice_groups(patternId)")
            }
        }
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "maths-paper-maker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build().also { INSTANCE = it }
        }
    }
}

suspend fun AppDatabase.importPackage(
    source: QuestionSourceEntity,
    pattern: PaperPatternEntity,
    slots: List<PatternSlotEntity>,
    questions: List<QuestionEntity>,
    choiceGroups: List<ChoiceGroupEntity> = emptyList(),
    quotas: List<PatternQuotaEntity> = emptyList(),
    exclusions: List<SyllabusExclusionEntity> = emptyList(),
    bundleChoiceGroups: List<BundleChoiceGroupEntity> = emptyList()
) = withTransaction {
    require(slots.isNotEmpty()) { "Pattern has no slots." }
    require(questions.isNotEmpty()) { "Question bank is empty." }
    require(source.sourceId.isNotBlank() && source.issuer.isNotBlank() && source.title.isNotBlank() && source.sourceType.isNotBlank()) {
        "Source ID, issuer, title and source type are required."
    }
    require(pattern.patternId.isNotBlank() && pattern.sourceId == source.sourceId) { "Pattern must reference the imported source." }
    require(pattern.board.isNotBlank() && pattern.subject.isNotBlank() && pattern.session.isNotBlank() &&
        pattern.grade in 9..10 && pattern.version > 0 && pattern.totalMarks > 0 &&
        pattern.stream in setOf("MATHEMATICS", "GENERAL_MATHEMATICS")) { "Pattern identity, version or total marks are invalid." }
    require(source.session.isNullOrBlank() || source.session == pattern.session) { "Source session conflicts with pattern session." }
    require(slots.map { it.slotId }.distinct().size == slots.size && slots.all { it.patternId == pattern.patternId && it.slotId.isNotBlank() && it.displayOrder >= 0 }) {
        "Pattern slot IDs must be unique, nonblank, correctly linked and ordered."
    }
    require(questions.map { it.questionId }.distinct().size == questions.size) { "Question IDs must be unique." }
    require(questions.all { it.questionId.isNotBlank() && it.questionText.isNotBlank() && it.marks > 0 &&
        it.board == pattern.board && it.grade == pattern.grade && it.stream == pattern.stream &&
        it.subject == pattern.subject && it.session == pattern.session && it.sourceId == source.sourceId &&
        it.chapterId.isNotBlank() && it.questionType.isNotBlank() && it.semanticFingerprint.isNotBlank() }) {
        "Every question must match the pattern identity and imported source, with required metadata."
    }
    require(slots.all { it.marks > 0 }) { "All pattern slot marks must be positive." }
    val requiredBundles = slots.filter { it.bundleId != null }.groupBy { it.bundleId!! }
    require(requiredBundles.keys.all { it.isNotBlank() } && requiredBundles.values.all { it.size >= 2 }) {
        "Required paired bundles need a nonblank bundleId and at least two parts."
    }
    require(slots.mapNotNull { it.bundleId }.all { it.isNotBlank() }) { "Bundle IDs cannot be blank." }
    require(slots.none { it.groupId != null && it.bundleId != null }) {
        "A slot cannot be both an optional choice and a required paired-bundle part."
    }
    require(questions.all { it.marks > 0 && it.questionText.isNotBlank() }) { "Question rows contain invalid marks or blank text." }
    require(questions.all { it.sourceId == source.sourceId }) { "Every question must point to the package source." }
    require(quotas.all { it.patternId == pattern.patternId && it.dimension in setOf("CHAPTER", "SLO", "COGNITIVE_LEVEL", "DIFFICULTY") && it.value.isNotBlank() && it.minimum >= 0 && it.maximum >= it.minimum && it.maximum <= slots.size }) { "Pattern quotas are invalid." }
    require(quotas.map { it.dimension to it.value }.distinct().size == quotas.size) { "Pattern quota keys must be unique." }
    require(choiceGroups.all { it.patternId == pattern.patternId && it.groupId.isNotBlank() && it.displayOrder >= 0 && it.offeredCount > 0 && it.attemptCount in 1..it.offeredCount && it.marksPerQuestion > 0 }) { "Choice group rules are invalid." }
    require(choiceGroups.map { it.groupId }.distinct().size == choiceGroups.size) { "Choice group IDs must be unique." }
    require(slots.all { it.groupId == null || choiceGroups.any { group -> group.groupId == it.groupId } }) { "A slot refers to an unknown choice group." }
    if (choiceGroups.isNotEmpty()) {
        choiceGroups.forEach { group ->
            val groupSlots = slots.filter { it.groupId == group.groupId }
            require(groupSlots.size == group.offeredCount) { "Choice group ${group.groupId} offered count does not match its slots." }
            require(groupSlots.all { it.marks == group.marksPerQuestion }) { "Choice group slot marks must match the group's marks per question." }
        }
        val requiredMarks = slots.filter { it.groupId == null }.sumOf { it.marks }
        val attemptedChoiceMarks = choiceGroups.sumOf { it.attemptCount * it.marksPerQuestion }
        require(requiredMarks + attemptedChoiceMarks == pattern.totalMarks) {
            "Pattern total must equal required fixed/paired marks plus attempted choice-group marks."
        }
    } else {
        require(pattern.totalMarks == slots.sumOf { it.marks }) { "Legacy required-slot patterns must sum to totalMarks." }
    }
    contentDao().insertSources(listOf(source))
    contentDao().insertPatterns(listOf(pattern))
    contentDao().insertSlots(slots)
    if (choiceGroups.isNotEmpty()) contentDao().insertChoiceGroups(choiceGroups)
    if (quotas.isNotEmpty()) contentDao().insertPatternQuotas(quotas)
    require(exclusions.all { it.patternId == pattern.patternId && it.exclusionType in setOf("CHAPTER", "SLO") && it.value.isNotBlank() }) { "Syllabus exclusions are invalid." }
    require(exclusions.map { it.exclusionType to it.value }.distinct().size == exclusions.size) { "Duplicate syllabus exclusions." }
    if (exclusions.isNotEmpty()) contentDao().insertSyllabusExclusions(exclusions)
    contentDao().insertQuestions(questions)
}
