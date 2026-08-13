package com.addiyon.buildlogic.dictionary

internal object DictionaryDatabaseFormat {
    const val SCHEMA_VERSION = 11
    const val APPLICATION_ID = 0x41444459
    const val PREFIX_TOP_LIMIT = 12
    const val GLOBAL_TOP_LIMIT = 15
    const val FUZZY_TOP_PER_LENGTH = 512
    const val MORPH_SURFACE_STATS_LIMIT = 2_048
}
