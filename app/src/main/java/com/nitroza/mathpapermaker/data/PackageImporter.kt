package com.nitroza.mathpapermaker.data

import org.json.JSONObject

object PackageImporter {
    data class Parsed(
        val source: QuestionSourceEntity,
        val pattern: PaperPatternEntity,
        val slots: List<PatternSlotEntity>,
        val questions: List<QuestionEntity>,
        val choiceGroups: List<ChoiceGroupEntity>,
        val quotas: List<PatternQuotaEntity>,
        val exclusions: List<SyllabusExclusionEntity>,
        val bundleChoiceGroups: List<BundleChoiceGroupEntity>,
        val warnings: List<String>
    )

    fun parse(raw: String): Parsed {
        require(raw.length <= 5_000_000) { "Package is too large (5 MB maximum)." }
        val root = JSONObject(raw)
        val schemaVersion = root.optInt("schemaVersion", 0)
        require(schemaVersion in 1..5) { "Unsupported package schemaVersion. Expected 1, 2, 3, 4 or 5." }
        val identity = root.getJSONObject("identity")
        val sourceJson = root.getJSONObject("source")
        val patternJson = root.getJSONObject("pattern")
        val board = identity.getString("board")
        val grade = identity.getInt("grade")
        val stream = identity.getString("stream")
        val subject = identity.getString("subject")
        val session = identity.getString("session")
        require(grade in 9..10) { "Only grades 9 and 10 are supported." }
        require(stream in setOf("MATHEMATICS", "GENERAL_MATHEMATICS")) { "Use MATHEMATICS or GENERAL_MATHEMATICS as stream." }
        val sourceId = sourceJson.getString("sourceId")
        val source = QuestionSourceEntity(
            sourceId = sourceId,
            issuer = sourceJson.getString("issuer"),
            title = sourceJson.getString("title"),
            sourceUrl = sourceJson.optString("sourceUrl").takeIf { it.isNotBlank() },
            sourceType = sourceJson.getString("sourceType"),
            session = sourceJson.optString("session").takeIf { it.isNotBlank() },
            pageReference = sourceJson.optString("pageReference").takeIf { it.isNotBlank() },
            // Imports can never self-certify official status.
            verificationStatus = "NEEDS_REVIEW"
        )
        require(listOf(source.sourceId, source.issuer, source.title, source.sourceType).all { it.isNotBlank() }) {
            "Source ID, issuer, title and sourceType are required."
        }
        val patternId = patternJson.getString("patternId")
        val pattern = PaperPatternEntity(
            patternId = patternId, board = board, grade = grade, stream = stream,
            subject = subject, session = session, version = patternJson.optInt("version", 1),
            totalMarks = patternJson.getInt("totalMarks"),
            durationMinutes = patternJson.optInt("durationMinutes").takeIf { it > 0 },
            verificationStatus = "NEEDS_REVIEW", sourceId = sourceId,
            sourcePage = patternJson.optString("sourcePage").takeIf { it.isNotBlank() }
        )
        val slotsJson = patternJson.getJSONArray("slots")
        val slots = (0 until slotsJson.length()).map { i ->
            val s = slotsJson.getJSONObject(i)
            PatternSlotEntity(
                patternId = patternId,
                slotId = s.getString("slotId"),
                chapterId = s.optString("chapterId").takeIf { it.isNotBlank() },
                sloId = s.optString("sloId").takeIf { it.isNotBlank() },
                questionType = s.optString("questionType").takeIf { it.isNotBlank() },
                marks = s.getInt("marks"),
                displayOrder = s.optInt("displayOrder", i),
                groupId = s.optString("groupId").takeIf { it.isNotBlank() },
                bundleId = if (schemaVersion >= 5) s.optString("bundleId").takeIf { it.isNotBlank() } else null,
                bundleChoiceGroupId = s.optString("bundleChoiceGroupId").takeIf { it.isNotBlank() }
            )
        }
        val choiceGroupsJson = patternJson.optJSONArray("choiceGroups")
        val choiceGroups = if (choiceGroupsJson == null) emptyList() else (0 until choiceGroupsJson.length()).map { i ->
            val g = choiceGroupsJson.getJSONObject(i)
            ChoiceGroupEntity(patternId, g.getString("groupId"), g.getInt("offeredCount"),
                g.getInt("attemptCount"), g.getInt("marksPerQuestion"), g.optInt("displayOrder", i))
        }
        val quotasJson = patternJson.optJSONArray("quotas")
        val quotas = if (quotasJson == null) emptyList() else (0 until quotasJson.length()).map { i ->
            val q = quotasJson.getJSONObject(i)
            PatternQuotaEntity(patternId, q.getString("dimension"), q.getString("value"), q.getInt("minimum"), q.getInt("maximum"))
        }
        require(quotas.all { it.dimension in setOf("CHAPTER", "SLO", "COGNITIVE_LEVEL", "DIFFICULTY") && it.value.isNotBlank() && it.minimum >= 0 && it.maximum >= it.minimum && it.maximum <= slots.size }) { "Invalid pattern metadata quota." }
        require(quotas.map { it.dimension to it.value }.distinct().size == quotas.size) { "Duplicate pattern metadata quota." }
        val exclusionsJson = patternJson.optJSONArray("exclusions")
        val exclusions = if (exclusionsJson == null) emptyList() else (0 until exclusionsJson.length()).map { i ->
            val e = exclusionsJson.getJSONObject(i)
            SyllabusExclusionEntity(patternId, e.getString("type"), e.getString("value"),
                e.optString("reason").takeIf { it.isNotBlank() }, e.optString("sourcePage").takeIf { it.isNotBlank() })
        }
        require(exclusions.all { it.exclusionType in setOf("CHAPTER", "SLO") && it.value.isNotBlank() }) { "Syllabus exclusions must use CHAPTER or SLO and a nonblank value." }
        require(exclusions.map { it.exclusionType to it.value }.distinct().size == exclusions.size) { "Duplicate syllabus exclusions." }
        val bundleGroupsJson = patternJson.optJSONArray("bundleChoiceGroups")
        val bundleChoiceGroups = if (bundleGroupsJson == null) emptyList() else (0 until bundleGroupsJson.length()).map { i ->
            val g = bundleGroupsJson.getJSONObject(i)
            val ids = g.getJSONArray("bundleIds")
            val bundleIds = (0 until ids.length()).map { ids.getString(it) }
            BundleChoiceGroupEntity(
                patternId = patternId,
                groupId = g.getString("groupId"),
                offeredBundleCount = g.getInt("offeredBundleCount"),
                attemptBundleCount = g.getInt("attemptBundleCount"),
                marksPerBundle = g.getInt("marksPerBundle"),
                bundleIdsJson = org.json.JSONArray(bundleIds).toString(),
                displayOrder = g.optInt("displayOrder", i)
            )
        }
        require(bundleChoiceGroups.map { it.groupId }.distinct().size == bundleChoiceGroups.size)
        require(bundleChoiceGroups.all { it.groupId.isNotBlank() && it.offeredBundleCount > 0 &&
            it.attemptBundleCount in 1..it.offeredBundleCount && it.marksPerBundle > 0 &&
            org.json.JSONArray(it.bundleIdsJson).length() == it.offeredBundleCount &&
            it.displayOrder >= 0
        }) { "Invalid paired-question choice group." }
        require(slots.filter { it.bundleChoiceGroupId != null }.all { slot ->
            slot.bundleId != null && bundleChoiceGroups.any { it.groupId == slot.bundleChoiceGroupId }
        }) { "A paired-question slot references an unknown group or has no bundle ID." }
        require(slots.none { it.groupId != null && it.bundleChoiceGroupId != null }) {
            "A slot cannot belong to both a single-question and paired-question choice group."
        }
        bundleChoiceGroups.forEach { group ->
            val ids = (0 until org.json.JSONArray(group.bundleIdsJson).length()).map {
                org.json.JSONArray(group.bundleIdsJson).getString(it)
            }.toSet()
            val groupSlots = slots.filter { it.bundleChoiceGroupId == group.groupId }
            require(groupSlots.mapNotNull { it.bundleId }.toSet() == ids) {
                "Every offered paired question must have its declared bundle parts."
            }
            require(groupSlots.groupBy { it.bundleId }.values.all { parts ->
                parts.size >= 2 && parts.sumOf { it.marks } == group.marksPerBundle
            }) { "Each paired question must contain at least two parts and match marksPerBundle." }
        }
        val questionsJson = root.getJSONArray("questions")
        val questions = (0 until questionsJson.length()).map { i ->
            val q = questionsJson.getJSONObject(i)
            QuestionEntity(
                questionId = q.getString("questionId"), board = board, grade = grade, stream = stream,
                subject = subject, session = session, chapterId = q.getString("chapterId"),
                topic = q.optString("topic"), sloId = q.optString("sloId"),
                cognitiveLevel = q.optString("cognitiveLevel", "NOT_SPECIFIED"),
                difficulty = q.optString("difficulty", "NOT_SPECIFIED"),
                questionType = q.getString("questionType"), marks = q.getInt("marks"),
                questionText = q.getString("questionText"), answerText = q.optString("answerText").takeIf { it.isNotBlank() },
                summative = q.optBoolean("summative", false), verificationStatus = "NEEDS_REVIEW",
                copyrightStatus = q.optString("copyrightStatus", "UNKNOWN"), sourceId = sourceId,
                sourcePage = q.optString("sourcePage").takeIf { it.isNotBlank() },
                semanticFingerprint = q.optString("semanticFingerprint", q.getString("questionText").trim().lowercase().hashCode().toString()),
                selectionWeight = q.optDouble("selectionWeight", 1.0)
            )
        }
        require(slots.map { it.slotId }.distinct().size == slots.size) { "Duplicate pattern slot IDs." }
        val requiredBundles = slots.filter { it.bundleId != null }.groupBy { it.bundleId!! }
        require(requiredBundles.values.all { it.size >= 2 }) { "Required paired bundles need at least two parts per bundle." }
        require(slots.none { it.groupId != null && it.bundleId != null }) {
            "A slot cannot be both an optional choice and a required paired-bundle part."
        }
        require(slots.mapNotNull { it.groupId }.all { id -> choiceGroups.any { it.groupId == id } }) {
            "A slot references an unknown choice group."
        }
        require(choiceGroups.map { it.groupId }.distinct().size == choiceGroups.size) { "Duplicate choice group IDs." }
        require(choiceGroups.all { it.groupId.isNotBlank() && it.offeredCount > 0 && it.attemptCount in 1..it.offeredCount && it.marksPerQuestion > 0 }) { "Invalid choice group counts or marks." }
        choiceGroups.forEach { group ->
            val groupSlots = slots.filter { it.groupId == group.groupId }
            require(groupSlots.size == group.offeredCount) { "Group ${group.groupId}: offeredCount does not match its slots." }
            require(groupSlots.all { it.marks == group.marksPerQuestion }) { "Group ${group.groupId}: slot marks differ from marksPerQuestion." }
        }
        require(slots.all { it.groupId == null || choiceGroups.any { group -> group.groupId == it.groupId } }) {
            "Slots cannot reference groups unless matching choiceGroups are defined."
        }
        val fixedMarks = slots.filter { it.groupId == null && it.bundleChoiceGroupId == null }.sumOf { it.marks }
        val attemptedSingleChoiceMarks = choiceGroups.sumOf { it.attemptCount * it.marksPerQuestion }
        val attemptedBundleChoiceMarks = bundleChoiceGroups.sumOf { it.attemptBundleCount * it.marksPerBundle }
        require(fixedMarks + attemptedSingleChoiceMarks + attemptedBundleChoiceMarks == pattern.totalMarks) {
            "Pattern totalMarks must equal fixed marks plus attempted single-question and paired-question choice marks."
        }
        require(questions.all { it.selectionWeight.isFinite() && it.selectionWeight in 0.1..10.0 }) { "Question selectionWeight must be a finite value from 0.1 to 10.0." }
        require(questions.map { it.questionId }.distinct().size == questions.size) { "Duplicate question IDs." }
        require(pattern.totalMarks > 0 && pattern.version > 0) { "Invalid pattern marks or version." }
        require(listOf(board, stream, subject, session, sourceId).all { it.isNotBlank() }) { "Identity and source identifiers cannot be blank." }
        require(slots.all { it.slotId.isNotBlank() }) { "Pattern slot IDs cannot be blank." }
        require(slots.mapNotNull { it.groupId }.all { id -> choiceGroups.any { it.groupId == id } }) { "A slot references an unknown choice group." }
        require(questions.all { it.questionId.isNotBlank() && it.questionText.isNotBlank() }) { "Question IDs and question text cannot be blank." }
        require(patternId.isNotBlank()) { "Pattern ID cannot be blank." }
        require(pattern.sourceId == source.sourceId) { "Pattern must reference the package source." }
        require(pattern.board == board && pattern.grade == grade && pattern.stream == stream &&
            pattern.subject == subject && pattern.session == session) { "Pattern identity must exactly match the package identity." }
        require(slots.all { it.patternId == patternId && it.marks > 0 && it.displayOrder >= 0 }) {
            "Pattern slots must belong to this pattern and have positive marks and non-negative display order."
        }
        require(choiceGroups.all { it.patternId == patternId && it.displayOrder >= 0 }) {
            "Choice groups must belong to this pattern and have non-negative display order."
        }
        require(questions.all {
            it.board == board && it.grade == grade && it.stream == stream && it.subject == subject &&
                it.session == session && it.sourceId == source.sourceId && it.marks > 0 &&
                it.chapterId.isNotBlank() && it.questionType.isNotBlank() && it.semanticFingerprint.isNotBlank()
        }) { "Every question must match the package identity and include valid marks, chapter, type and fingerprint." }
        require(quotas.all { it.patternId == patternId } && exclusions.all { it.patternId == patternId }) {
            "All quotas and exclusions must belong to this pattern."
        }
        require(source.session.isNullOrBlank() || source.session == session) {
            "Source session conflicts with the package session."
        }
        val warnings = mutableListOf<String>()
        if (source.session.isNullOrBlank()) warnings += "Source session is unspecified; confirm it against the original document."
        if (source.sourceUrl.isNullOrBlank()) warnings += "Source URL is missing; verify the source manually."
        val duplicateFingerprints = questions.groupBy { it.semanticFingerprint }.filterValues { it.size > 1 }.keys
        if (duplicateFingerprints.isNotEmpty()) warnings += "${duplicateFingerprints.size} semantic duplicate fingerprint(s) detected; review before official use."
        if (questions.any { it.sloId.isBlank() }) warnings += "Some questions have no SLO mapping."
        if (questions.any { !it.summative }) warnings += "Some questions are not marked summative."
        return Parsed(source, pattern, slots, questions, choiceGroups, quotas, exclusions, bundleChoiceGroups, warnings)
    }
}
