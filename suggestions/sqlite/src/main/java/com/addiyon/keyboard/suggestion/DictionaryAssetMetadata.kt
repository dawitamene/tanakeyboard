package com.addiyon.keyboard.suggestion

import java.io.InputStream

internal data class DictionaryAssetMetadata(
    val schemaVersion: Int,
    val applicationId: Int,
    val assets: Map<String, Asset>,
) {
    data class Asset(val length: Long, val sha256: String)

    fun asset(name: String): Asset =
        requireNotNull(assets[name]) { "Missing dictionary metadata for $name" }

    companion object {
        fun read(input: InputStream): DictionaryAssetMetadata {
            val values = LinkedHashMap<String, String>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val separator = line.indexOf('=')
                    if (separator <= 0) continue
                    values[line.substring(0, separator)] = line.substring(separator + 1)
                }
            }
            val schemaVersion = requireNotNull(values["schemaVersion"]?.toIntOrNull())
            val applicationId = requireNotNull(values["applicationId"]?.toIntOrNull())
            val assets = values.keys
                .filter { it.endsWith(".length") }
                .associate { lengthKey ->
                    val name = lengthKey.removeSuffix(".length")
                    val length = requireNotNull(values[lengthKey]?.toLongOrNull())
                    val sha256 = requireNotNull(values["$name.sha256"])
                    name to Asset(length, sha256)
                }
            return DictionaryAssetMetadata(schemaVersion, applicationId, assets)
        }
    }
}
