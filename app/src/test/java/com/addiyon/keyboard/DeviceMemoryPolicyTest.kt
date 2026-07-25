package com.addiyon.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMemoryPolicyTest {
    @Test
    fun systemLowRamFlagAlwaysSelectsLowRamPolicy() {
        assertTrue(DeviceMemoryPolicy.isLowRam(systemLowRam = true, totalMemoryBytes = 0L))
    }

    @Test
    fun oneGiBAndBelowSelectLowRamPolicy() {
        assertTrue(DeviceMemoryPolicy.isLowRam(false, 512L * 1024L * 1024L))
        assertTrue(DeviceMemoryPolicy.isLowRam(false, DeviceMemoryPolicy.LOW_RAM_MAX_BYTES))
    }

    @Test
    fun unknownOrAboveOneGiBDoesNotSelectLowRamPolicyWithoutSystemFlag() {
        assertFalse(DeviceMemoryPolicy.isLowRam(false, 0L))
        assertFalse(DeviceMemoryPolicy.isLowRam(false, DeviceMemoryPolicy.LOW_RAM_MAX_BYTES + 1L))
    }
}
