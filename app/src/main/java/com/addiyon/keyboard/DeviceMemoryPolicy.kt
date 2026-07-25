package com.addiyon.keyboard

internal object DeviceMemoryPolicy {
    const val LOW_RAM_MAX_BYTES: Long = 1024L * 1024L * 1024L

    fun isLowRam(systemLowRam: Boolean, totalMemoryBytes: Long): Boolean =
        systemLowRam || totalMemoryBytes in 1..LOW_RAM_MAX_BYTES
}
