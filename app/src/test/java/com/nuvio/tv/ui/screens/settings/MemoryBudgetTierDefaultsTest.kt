package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.data.local.BufferSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBudgetTierDefaultsTest {

    @Test
    fun `default buffer engine follows low ram tier`() {
        if (MemoryBudget.isLowRamTier) {
            assertFalse(MemoryBudget.defaultBufferEngineEnabled())
            assertEquals(BufferSettings.DEFAULT_MIN_BUFFER_MS_LOW_RAM, MemoryBudget.defaultMinBufferMs())
            assertEquals(BufferSettings.DEFAULT_MAX_BUFFER_MS_LOW_RAM, MemoryBudget.defaultMaxBufferMs())
        } else {
            assertTrue(MemoryBudget.defaultBufferEngineEnabled())
            assertEquals(BufferSettings.DEFAULT_MIN_BUFFER_MS_HIGH_RAM, MemoryBudget.defaultMinBufferMs())
            assertEquals(BufferSettings.DEFAULT_MAX_BUFFER_MS_HIGH_RAM, MemoryBudget.defaultMaxBufferMs())
        }
    }
}
