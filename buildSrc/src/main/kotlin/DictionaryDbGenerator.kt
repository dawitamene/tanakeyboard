import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.sqlite.SQLiteDataSource

abstract class DictionaryDbGenerator : DefaultTask() {

    @get:InputFile
    abstract val amharicWordsDat: org.gradle.api.file.RegularFileProperty

    @get:InputFile
    abstract val amharicNgramsDat: org.gradle.api.file.RegularFileProperty

    @get:InputFile
    abstract val englishWordsDat: org.gradle.api.file.RegularFileProperty

    @get:InputFile
    abstract val englishNgramsDat: org.gradle.api.file.RegularFileProperty

    @get:OutputFile
    abstract val amharicDb: org.gradle.api.file.RegularFileProperty

    @get:OutputFile
    abstract val englishDb: org.gradle.api.file.RegularFileProperty

    @get:OutputFile
    abstract val manifestFile: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun run() {
        val amharic = amharicDb.get().asFile
        val english = englishDb.get().asFile
        buildDb("amharic", amharicWordsDat.get().asFile, amharicNgramsDat.get().asFile, amharic)
        buildDb("english", englishWordsDat.get().asFile, englishNgramsDat.get().asFile, english)
        val manifest = manifestFile.get().asFile
        manifest.parentFile.mkdirs()
        manifest.writeText(
            buildString {
                appendLine("schemaVersion=$SCHEMA_VERSION")
                appendLine("applicationId=$APPLICATION_ID")
                appendLine("amharic.db.length=${amharic.length()}")
                appendLine("amharic.db.sha256=${sha256(amharic)}")
                appendLine("english.db.length=${english.length()}")
                appendLine("english.db.sha256=${sha256(english)}")
            }
        )
    }

    private fun buildDb(language: String, wordsDat: File, ngramsDat: File, outDb: File) {
        outDb.parentFile.mkdirs()
        if (outDb.exists()) outDb.delete()
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${outDb.absolutePath}"
        }
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.execute("PRAGMA journal_mode=OFF")
                st.execute("PRAGMA synchronous=OFF")
                st.execute("PRAGMA temp_store=MEMORY")
                st.execute("PRAGMA application_id=$APPLICATION_ID")
                st.execute("PRAGMA user_version=$SCHEMA_VERSION")
                st.execute(
                    """
                    CREATE TABLE words(
                        word TEXT NOT NULL,
                        freq INTEGER NOT NULL,
                        key  TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute("CREATE INDEX idx_words_key ON words(key)")
                st.execute(
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
                st.execute(
                    """
                    CREATE TABLE vocab(
                        id   INTEGER PRIMARY KEY,
                        text TEXT NOT NULL,
                        key  TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute("CREATE INDEX idx_vocab_key ON vocab(key)")
                st.execute(
                    """
                    CREATE TABLE bigrams(
                        ctx     INTEGER NOT NULL,
                        succ    INTEGER NOT NULL,
                        weight  INTEGER NOT NULL,
                        casing  INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    "CREATE INDEX idx_bigrams_ctx ON bigrams(ctx, weight DESC, succ, casing)"
                )
                st.execute(
                    """
                    CREATE TABLE trigrams(
                        ctx     INTEGER NOT NULL,
                        succ    INTEGER NOT NULL,
                        weight  INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    "CREATE INDEX idx_trigrams_ctx ON trigrams(ctx, weight DESC, succ)"
                )
            }
            conn.autoCommit = false

            loadWords(conn, wordsDat, language == "amharic")
            populatePrefixTop(conn, if (language == "amharic") 1 else 2)
            loadNgrams(conn, ngramsDat, language == "amharic")
            conn.commit()
            conn.autoCommit = true
            conn.createStatement().use { it.execute("VACUUM") }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun populatePrefixTop(conn: java.sql.Connection, maxPrefixLength: Int) {
        val sources = (1..maxPrefixLength).joinToString("\nUNION ALL\n") { length ->
            "SELECT substr(key, 1, $length), word, freq FROM words WHERE length(key) >= $length"
        }
        conn.createStatement().use { statement ->
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
                WHERE rank <= $PREFIX_TOP_LIMIT
                """.trimIndent()
            )
        }
    }

    private fun loadWords(conn: java.sql.Connection, wordsDat: File, amharic: Boolean) {
        val sql = "INSERT INTO words(word, freq, key) VALUES (?, ?, ?)"
        val fileBytes = wordsDat.readBytes()
        val data = GZIPInputStream(ByteArrayInputStream(fileBytes))
        val reader = BufferedReader(data.reader(Charsets.UTF_8))
        val normalizer: (String) -> String = if (amharic) {
            { s -> EthiopicNormalizer.normalize(s) }
        } else {
            { s -> buildString(s.length) { for (c in s) append(c.lowercaseChar()) } }
        }
        var count = 0
        conn.prepareStatement(sql).use { ps ->
            reader.useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val word = line.substring(0, tab)
                    val freq = line.substring(tab + 1).toIntOrNull() ?: continue
                    val key = normalizer(word)
                    ps.setString(1, word)
                    ps.setInt(2, freq)
                    ps.setString(3, key)
                    ps.addBatch()
                    count++
                    if (count % 10000 == 0) ps.executeBatch()
                }
            }
            ps.executeBatch()
        }
    }

    private fun loadNgrams(conn: java.sql.Connection, ngramsDat: File, amharic: Boolean) {
        val fileBytes = ngramsDat.readBytes()
        val gz = GZIPInputStream(ByteArrayInputStream(fileBytes))
        val data = DataInputStream(gz.buffered())

        val magic = ByteArray(4)
        data.readFully(magic)
        require(magic.contentEquals(byteArrayOf(0x41, 0x4E, 0x47, 0x4D))) { "bad ngram magic" }
        val version = data.readUnsignedByte()
        require(version in 1..3) { "unsupported ngram version $version" }
        val hasCasing = version >= 3

        val vocabSize = data.readInt()
        val vocab = Array(vocabSize) {
            val len = data.readUnsignedShort()
            val bytes = ByteArray(len)
            data.readFully(bytes)
            String(bytes, Charsets.UTF_8)
        }

        conn.prepareStatement("INSERT INTO vocab(id, text, key) VALUES (?, ?, ?)").use { ps ->
            for ((i, w) in vocab.withIndex()) {
                val key = if (amharic) EthiopicNormalizer.normalize(w) else {
                    buildString(w.length) { for (c in w) append(c.lowercaseChar()) }
                }
                ps.setInt(1, i)
                ps.setString(2, w)
                ps.setString(3, key)
                ps.addBatch()
            }
            ps.executeBatch()
        }

        val bigramCount = data.readInt()
        val bigramContexts = IntArray(bigramCount) { data.readInt() }
        val bigramOffsets = IntArray(bigramCount + 1) { data.readInt() }
        val bigramTotal = bigramOffsets.last()
        val bigramSuccessors = IntArray(bigramTotal) { data.readInt() }
        val bigramWeights = ByteArray(bigramTotal)
        data.readFully(bigramWeights)
        val bigramCasing: ByteArray? = if (hasCasing) {
            ByteArray(bigramTotal).also { data.readFully(it) }
        } else null

        val trigramCount = data.readInt()
        val trigramContexts = LongArray(trigramCount) { data.readLong() }
        val trigramOffsets = IntArray(trigramCount + 1) { data.readInt() }
        val trigramTotal = trigramOffsets.last()
        val trigramSuccessors = IntArray(trigramTotal) { data.readInt() }
        val trigramWeights = ByteArray(trigramTotal)
        data.readFully(trigramWeights)

        conn.prepareStatement("INSERT INTO bigrams(ctx, succ, weight, casing) VALUES (?, ?, ?, ?)").use { ps ->
            for (i in 0 until bigramCount) {
                val ctx = bigramContexts[i]
                val from = bigramOffsets[i]
                val to = bigramOffsets[i + 1]
                for (j in from until to) {
                    ps.setInt(1, ctx)
                    ps.setInt(2, bigramSuccessors[j])
                    ps.setInt(3, bigramWeights[j].toInt() and 0xFF)
                    val casing = if (bigramCasing != null) bigramCasing[j].toInt() and 0xFF else 0
                    ps.setInt(4, casing)
                    ps.addBatch()
                }
            }
            ps.executeBatch()
        }

        conn.prepareStatement("INSERT INTO trigrams(ctx, succ, weight) VALUES (?, ?, ?)").use { ps ->
            for (i in 0 until trigramCount) {
                val ctx = trigramContexts[i]
                val from = trigramOffsets[i]
                val to = trigramOffsets[i + 1]
                for (j in from until to) {
                    ps.setLong(1, ctx)
                    ps.setInt(2, trigramSuccessors[j])
                    ps.setInt(3, trigramWeights[j].toInt() and 0xFF)
                    ps.addBatch()
                }
            }
            ps.executeBatch()
        }
    }

    companion object {
        private const val SCHEMA_VERSION = 5
        private const val APPLICATION_ID = 0x41444459
        private const val PREFIX_TOP_LIMIT = 12
    }
}

object EthiopicNormalizer {
    private val fold: Map<Char, Char> = buildMap {
        fun series(variants: String, canonical: String) {
            require(variants.length == canonical.length)
            for (i in variants.indices) put(variants[i], canonical[i])
        }
        series("ሐሑሒሓሔሕሖ", "ሀሁሂሀሄህሆ")
        series("ኀኁኂኃኄኅኆ", "ሀሁሂሀሄህሆ")
        put('ሃ', 'ሀ')
        put('ሗ', 'ኋ')
        series("ሠሡሢሣሤሥሦሧ", "ሰሱሲሳሴስሶሷ")
        series("ዐዑዒዓዔዕዖ", "አኡኢአኤእኦ")
        put('ኣ', 'አ')
        series("ፀፁፂፃፄፅፆ", "ጸጹጺጻጼጽጾ")
    }

    fun normalize(c: Char): Char = fold[c] ?: c

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(normalize(c))
        return sb.toString()
    }
}
