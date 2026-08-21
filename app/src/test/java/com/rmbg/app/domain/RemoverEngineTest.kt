package com.rmbg.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoverEngineTest {
    @Test
    fun testEngineProperties() {
        assertTrue(RemoverEngine.MEDIAPIPE.isOffline)
        assertTrue(RemoverEngine.ONNX_U2NET.isOffline)
        assertEquals(false, RemoverEngine.CLOUD_API.isOffline)
    }
}
