package com.alexm.mynut.data.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlamaNativeSmokeTest {

    @Test
    fun nativePingReturnsExpectedConstant() {
        assertEquals(42, LlamaNative.nativePing())
    }
}
