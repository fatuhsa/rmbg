package com.rmbg.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoverEngineTest {
    @Test
    fun testEngineProperties() {
        assertEquals(2, RemoverEngine.values().size)
        assertTrue(RemoverEngine.MEDIAPIPE.isOffline)
        assertTrue(RemoverEngine.ONNX_U2NET.isOffline)
    }
}

