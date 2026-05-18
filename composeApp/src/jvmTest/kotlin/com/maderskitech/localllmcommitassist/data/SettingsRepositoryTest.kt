package com.maderskitech.localllmcommitassist.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryTest {

    @Test
    fun modelContextWindow_persistsPerModelAndCanBeRemoved() {
        val repository = SettingsRepository()
        val modelName = "test-model-context-window"

        repository.setModelContextWindow(modelName, null)
        repository.setModelContextWindow(modelName, 16_384)

        assertEquals(16_384, repository.getModelContextWindow(modelName))

        repository.setModelContextWindow(modelName, null)

        assertNull(repository.getModelContextWindow(modelName))
    }

    @Test
    fun modelContextWindow_ignoresNonPositiveValues() {
        val repository = SettingsRepository()
        val modelName = "test-model-invalid-context-window"

        repository.setModelContextWindow(modelName, 8_192)
        repository.setModelContextWindow(modelName, 0)
        assertNull(repository.getModelContextWindow(modelName))

        repository.setModelContextWindow(modelName, 4_096)
        repository.setModelContextWindow(modelName, -1)
        assertNull(repository.getModelContextWindow(modelName))
    }

    @Test
    fun modelContextWindow_forBlankModelUsesDefaultSlot() {
        val repository = SettingsRepository()

        repository.setModelContextWindow("", null)
        repository.setModelContextWindow("", 32_768)

        assertEquals(32_768, repository.getModelContextWindow(""))

        repository.setModelContextWindow("", null)
        assertNull(repository.getModelContextWindow(""))
    }
}
