package com.addiyon.buildlogic.dictionary

import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.sql.Connection
import java.util.zip.GZIPInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.sqlite.SQLiteDataSource

abstract class GenerateDictionaryDatabase : DefaultTask() {
    @get:Input
    abstract val languageId: Property<String>

    @get:Input
    abstract val normalization: Property<String>

    @get:Input
    abstract val maxPrefixLength: Property<Int>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val wordsDat: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val lexemesDat: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ngramsDat: RegularFileProperty

    @get:OutputFile
    abstract val outputDb: RegularFileProperty

    @TaskAction
    fun generate() {
        val mode = normalization.get()
        require(mode == NORMALIZATION_ETHIOPIC || mode == NORMALIZATION_LATIN_LOWERCASE) {
            "Unsupported normalization '$mode' for ${languageId.get()}"
        }
        require(maxPrefixLength.get() > 0) {
            "maxPrefixLength must be positive for ${languageId.get()}"
        }
        buildDatabase(
            wordsDat = wordsDat.get().asFile,
            lexemesDat = lexemesDat.orNull?.asFile,
            ngramsDat = ngramsDat.get().asFile,
            output = outputDb.get().asFile,
            mode = mode,
            prefixLength = maxPrefixLength.get(),
        )
    }

    private fun buildDatabase(
        wordsDat: File,
        lexemesDat: File?,
        ngramsDat: File,
        output: File,
        mode: String,
        prefixLength: Int,
    ) {
        output.parentFile.mkdirs()
        if (output.exists()) output.delete()
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${output.absolutePath}"
        }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=OFF")
                statement.execute("PRAGMA synchronous=OFF")
                statement.execute("PRAGMA temp_store=MEMORY")
                statement.execute("PRAGMA application_id=${DictionaryDatabaseFormat.APPLICATION_ID}")
                statement.execute("PRAGMA user_version=${DictionaryDatabaseFormat.SCHEMA_VERSION}")
                statement.execute(
                    """
                    CREATE TABLE words(
                        key           TEXT PRIMARY KEY,
                        display       TEXT,
                        freq          INTEGER NOT NULL,
                        ngram_id      INTEGER,
                        ngram_display TEXT
                    ) WITHOUT ROWID
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE morph_lexemes(
                        kind     INTEGER NOT NULL,
                        form     TEXT NOT NULL,
                        features TEXT NOT NULL,
                        PRIMARY KEY(kind, form, features)
                    ) WITHOUT ROWID
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE prefix_top(
                        prefix TEXT NOT NULL,
                        rank   INTEGER NOT NULL,
                        word   TEXT NOT NULL,
                        freq   INTEGER NOT NULL,
                        PRIMARY KEY(prefix, rank)
                    ) WITHOUT ROWID
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE fuzzy_top(
                        length INTEGER NOT NULL,
                        rank   INTEGER NOT NULL,
                        key    TEXT NOT NULL,
                        word   TEXT NOT NULL,
                        freq   INTEGER NOT NULL,
                        PRIMARY KEY(length, rank)
                    ) WITHOUT ROWID
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE bigrams(
                        ctx     INTEGER NOT NULL,
                        succ    INTEGER NOT NULL,
                        weight  INTEGER NOT NULL,
                        casing  INTEGER NOT NULL,
                        PRIMARY KEY(ctx, weight DESC, succ, casing)
                    ) WITHOUT ROWID
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE trigrams(
                        ctx     INTEGER NOT NULL,
                        succ    INTEGER NOT NULL,
                        weight  INTEGER NOT NULL,
                        PRIMARY KEY(ctx, weight DESC, succ)
                    ) WITHOUT ROWID
                    """.trimIndent()
                )
            }
            connection.autoCommit = false
            loadWords(connection, wordsDat, mode)
            lexemesDat?.let { loadLexemes(connection, it) }
            populatePrefixTop(connection, prefixLength)
            populateFuzzyTop(connection)
            loadNgrams(connection, ngramsDat, mode)
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE UNIQUE INDEX idx_words_ngram_id ON words(ngram_id) " +
                        "WHERE ngram_id IS NOT NULL"
                )
            }
            connection.commit()
            connection.autoCommit = true
            connection.createStatement().use { it.execute("VACUUM") }
        }
    }

    private fun populatePrefixTop(connection: Connection, maxPrefixLength: Int) {
        val sources = (1..maxPrefixLength).joinToString("\nUNION ALL\n") { length ->
            "SELECT substr(key, 1, $length), COALESCE(display, key), freq " +
                "FROM words WHERE length(key) >= $length"
        }
        connection.createStatement().use { statement ->
            statement.execute(
                """
                INSERT INTO prefix_top(prefix, rank, word, freq)
                WITH prefix_candidates(prefix, word, freq) AS (
                    $sources
                ),
                ranked AS (
                    SELECT
                        prefix,
                        word,
                        freq,
                        row_number() OVER (
                            PARTITION BY prefix
                            ORDER BY freq DESC, word ASC
                        ) AS rank
                    FROM prefix_candidates
                )
                SELECT prefix, rank, word, freq
                FROM ranked
                WHERE rank <= ${DictionaryDatabaseFormat.PREFIX_TOP_LIMIT}
                """.trimIndent()
            )
            statement.execute(
                """
                INSERT INTO prefix_top(prefix, rank, word, freq)
                SELECT '', rank, word, freq
                FROM (
                    SELECT
                        COALESCE(display, key) AS word,
                        freq,
                        row_number() OVER (ORDER BY freq DESC, key ASC) AS rank
                    FROM words
                )
                WHERE rank <= ${DictionaryDatabaseFormat.GLOBAL_TOP_LIMIT}
                """.trimIndent()
            )
        }
    }

    private fun populateFuzzyTop(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                INSERT INTO fuzzy_top(length, rank, key, word, freq)
                WITH ranked AS (
                    SELECT
                        length(key) AS length,
                        key,
                        COALESCE(display, key) AS word,
                        freq,
                        row_number() OVER (
                            PARTITION BY length(key)
                            ORDER BY freq DESC, key ASC
                        ) AS rank
                    FROM words
                )
                SELECT length, rank, key, word, freq
                FROM ranked
                WHERE rank <= ${DictionaryDatabaseFormat.FUZZY_TOP_PER_LENGTH}
                """.trimIndent()
            )
        }
    }

    private fun loadWords(connection: Connection, wordsDat: File, mode: String) {
        val sql = "INSERT INTO words(key, display, freq) VALUES (?, ?, ?)"
        val data = GZIPInputStream(wordsDat.inputStream().buffered())
        val reader = BufferedReader(data.reader(Charsets.UTF_8))
        var count = 0
        connection.prepareStatement(sql).use { statement ->
            reader.useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val word = line.substring(0, tab)
                    val frequency = line.substring(tab + 1).toIntOrNull() ?: continue
                    require(frequency > 0) { "Frequency must be positive for '$word'" }
                    val key = normalize(word, mode)
                    statement.setString(1, key)
                    if (word == key) statement.setNull(2, java.sql.Types.VARCHAR)
                    else statement.setString(2, word)
                    statement.setInt(3, frequency)
                    statement.addBatch()
                    count++
                    if (count % 10000 == 0) statement.executeBatch()
                }
            }
            statement.executeBatch()
        }
    }

    private fun loadLexemes(connection: Connection, lexemesDat: File) {
        val sql = "INSERT INTO morph_lexemes(kind, form, features) VALUES (?, ?, ?)"
        val data = GZIPInputStream(lexemesDat.inputStream().buffered())
        val reader = BufferedReader(data.reader(Charsets.UTF_8))
        var count = 0
        connection.prepareStatement(sql).use { statement ->
            reader.useLines { lines ->
                for (line in lines) {
                    val fields = line.split('\t', limit = 3)
                    if (fields.size != 3) continue
                    val kind = fields[0].toIntOrNull() ?: continue
                    require(fields[1].isNotEmpty()) { "Lexeme form must not be empty" }
                    statement.setInt(1, kind)
                    statement.setString(2, fields[1])
                    statement.setString(3, fields[2])
                    statement.addBatch()
                    count++
                    if (count % 10000 == 0) statement.executeBatch()
                }
            }
            statement.executeBatch()
        }
    }

    private fun loadNgrams(connection: Connection, ngramsDat: File, mode: String) {
        val data = DataInputStream(
            GZIPInputStream(ngramsDat.inputStream().buffered()).buffered()
        )
        val magic = ByteArray(4)
        data.readFully(magic)
        require(magic.contentEquals(byteArrayOf(0x41, 0x4E, 0x47, 0x4D))) {
            "bad ngram magic"
        }
        val version = data.readUnsignedByte()
        require(version in 1..3) { "unsupported ngram version $version" }
        val hasCasing = version >= 3
        val vocabSize = data.readInt()
        val vocab = Array(vocabSize) {
            val length = data.readUnsignedShort()
            val bytes = ByteArray(length)
            data.readFully(bytes)
            String(bytes, Charsets.UTF_8)
        }
        connection.prepareStatement(
            """
            UPDATE words
            SET
                ngram_id = ?,
                ngram_display = CASE
                    WHEN COALESCE(display, key) = ? THEN NULL
                    ELSE ?
                END
            WHERE key = ?
            """.trimIndent()
        ).use { statement ->
            for ((index, word) in vocab.withIndex()) {
                statement.setInt(1, index)
                statement.setString(2, word)
                statement.setString(3, word)
                statement.setString(4, normalize(word, mode))
                statement.addBatch()
                if ((index + 1) % 10000 == 0) statement.executeBatch()
            }
            statement.executeBatch()
        }
        val assignedVocab = connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT count(*) FROM words WHERE ngram_id IS NOT NULL"
            ).use { result ->
                require(result.next())
                result.getInt(1)
            }
        }
        require(assignedVocab == vocabSize) {
            "N-gram vocabulary contains ${vocabSize - assignedVocab} words missing from the dictionary"
        }

        val bigramCount = data.readInt()
        val bigramContexts = IntArray(bigramCount) { data.readInt() }
        val bigramOffsets = IntArray(bigramCount + 1) { data.readInt() }
        val bigramTotal = bigramOffsets.last()
        val bigramSuccessors = IntArray(bigramTotal) { data.readInt() }
        val bigramWeights = ByteArray(bigramTotal)
        data.readFully(bigramWeights)
        val bigramCasing = if (hasCasing) {
            ByteArray(bigramTotal).also { data.readFully(it) }
        } else {
            null
        }

        val trigramCount = data.readInt()
        val trigramContexts = LongArray(trigramCount) { data.readLong() }
        val trigramOffsets = IntArray(trigramCount + 1) { data.readInt() }
        val trigramTotal = trigramOffsets.last()
        val trigramSuccessors = IntArray(trigramTotal) { data.readInt() }
        val trigramWeights = ByteArray(trigramTotal)
        data.readFully(trigramWeights)

        connection.prepareStatement(
            "INSERT INTO bigrams(ctx, succ, weight, casing) VALUES (?, ?, ?, ?)"
        ).use { statement ->
            for (index in 0 until bigramCount) {
                for (successorIndex in bigramOffsets[index] until bigramOffsets[index + 1]) {
                    statement.setInt(1, bigramContexts[index])
                    statement.setInt(2, bigramSuccessors[successorIndex])
                    statement.setInt(3, bigramWeights[successorIndex].toInt() and 0xFF)
                    val casing = bigramCasing?.get(successorIndex)?.toInt()?.and(0xFF) ?: 0
                    statement.setInt(4, casing)
                    statement.addBatch()
                }
            }
            statement.executeBatch()
        }
        connection.prepareStatement(
            "INSERT INTO trigrams(ctx, succ, weight) VALUES (?, ?, ?)"
        ).use { statement ->
            for (index in 0 until trigramCount) {
                for (successorIndex in trigramOffsets[index] until trigramOffsets[index + 1]) {
                    statement.setLong(1, trigramContexts[index])
                    statement.setInt(2, trigramSuccessors[successorIndex])
                    statement.setInt(3, trigramWeights[successorIndex].toInt() and 0xFF)
                    statement.addBatch()
                }
            }
            statement.executeBatch()
        }
    }

    private fun normalize(value: String, mode: String): String = when (mode) {
        NORMALIZATION_ETHIOPIC -> EthiopicNormalizer.normalize(value)
        NORMALIZATION_LATIN_LOWERCASE -> buildString(value.length) {
            for (character in value) append(character.lowercaseChar())
        }
        else -> error("Unsupported normalization '$mode'")
    }

    companion object {
        const val NORMALIZATION_ETHIOPIC = "ethiopic"
        const val NORMALIZATION_LATIN_LOWERCASE = "latin-lowercase"
    }
}
