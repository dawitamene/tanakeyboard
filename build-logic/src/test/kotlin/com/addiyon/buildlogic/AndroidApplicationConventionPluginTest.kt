package com.addiyon.buildlogic

import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidApplicationConventionPluginTest {
    @Test
    fun sharedApkNameUsesProductAndTwelveHourTime() {
        val date = Date(67_680_000L)

        assertEquals(
            "textrevamp-06:48PM.apk",
            sharedApkFileName(
                productName = "textrevamp",
                date = date,
                timeZone = TimeZone.getTimeZone("UTC")
            )
        )
    }
}
