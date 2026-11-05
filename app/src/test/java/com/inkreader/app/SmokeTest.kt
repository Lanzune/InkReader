package com.inkreader.app

import com.inkreader.kernel.api.KernelVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun kernelApiVersionIsV1() {
        assertEquals(1, KernelVersion.API)
    }
}
