package com.addiyon.buildlogic.dictionary

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.sql.Connection
import java.util.zip.GZIPInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
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
            ngramsDat = ngramsDat.get().asFile,
            output = outputDb.get().asFile,
            mode = mode,
            prefixLength = maxPrefixLength.get(),
        )
    }

    private fun buildDatabase(
        wordsDat: File,
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
                        word TEXT NOT NULL,
                        freq INTEGER NOT NULL,
                        key  TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute("CREATE INDEX idx_words_key ON words(key)")
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
                    CREATE TABLE vocab(
                        id   INTEGER PRIMARY KEY,
                        text TEXT NOT NULL,
                        key  TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute("CREATE INDEX idx_vocab_key ON vocab(key)")
                statement.execute(
                    """
                    CREATE TABLE bigrams(
                        ctx     INTEGER NOT NULL,
                        succ    INTEGER NOT NULL,
                        weight  INTEGER NOT NULL,
                        casing  INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    "CREATE INDEX idx_bigrams_ctx ON bigrams(ctx, weight DESC, succ, casing)"
                )
                statement.execute(
                    """
                    CREATE TABLE trigrams(
                        ctx     INTEGER NOT NULL,
                        succ    INTEGER NOT NULL,
                        weight  INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    "CREATE INDEX idx_trigrams_ctx ON trigrams(ctx, weight DESC, succ)"
                )
            }
            connection.autoCommit = false
            loadWords(connection, wordsDat, mode)
            populatePrefixTop(connection, prefixLength)
            loadNgrams(connection, ngramsDat, mode)
            connection.commit()
            connection.autoCommit = true
            connection.createStatement().use { it.execute("VACUUM") }
        }
    }

    private fun populatePrefixTop(connection: Connection, maxPrefixLength: Int) {
        val sources = (1..maxPrefixLength).joinToString("\nUNION ALL\n") { length ->
            "SELECT substr(key, 1, $length), word, freq FROM words WHERE length(key) >= $length"
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
        }
    }

    private fun loadWords(connection: Connection, wordsDat: File, mode: String) {
        val sql = "INSERT INTO words(word, freq, key) VALUES (?, ?, ?)"
        val data = GZIPInputStream(ByteArrayInputStream(wordsDat.readBytes()))
        val reader = BufferedReader(data.reader(Charsets.UTF_8))
        var count = 0
        connection.prepareStatement(sql).use { statement ->
            reader.useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val word = line.substring(0, tab)
                    val frequency = line.substring(tab + 1).toIntOrNull() ?: continue
                    statement.setString(1, word)
                    statement.setInt(2, frequency)
                    statement.setString(3, normalize(word, mode))
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
            GZIPInputStream(ByteArrayInputStream(ngramsDat.readBytes())).buffered()
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
            "INSERT INTO vocab(id, text, key) VALUES (?, ?, ?)"
        ).use { statement ->
            for ((index, word) in vocab.withIndex()) {
                statement.setInt(1, index)
                statement.setString(2, word)
                statement.setString(3, normalize(word, mode))
                statement.addBatch()
            }
            statement.executeBatch()
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
