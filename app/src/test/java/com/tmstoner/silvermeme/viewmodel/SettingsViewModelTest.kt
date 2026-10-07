package com.tmstoner.silvermeme.viewmodel

import com.tmstoner.silvermeme.data.storage.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `parseCapacity accepts whole points in range`() {
        assertEquals(0, SettingsViewModel.parseCapacity("0"))
        assertEquals(13, SettingsViewModel.parseCapacity(" 13 "))
        assertEquals(SettingsViewModel.MAX_DAILY_CAPACITY, SettingsViewModel.parseCapacity("200"))
    }

    @Test
    fun `parseCapacity treats blank as the default`() {
        assertEquals(SettingsStore.DEFAULT_DAILY_CAPACITY, SettingsViewModel.parseCapacity(""))
    }

    @Test
    fun `parseCapacity rejects out of range and non numbers`() {
        assertNull(SettingsViewModel.parseCapacity("201"))
        assertNull(SettingsViewModel.parseCapacity("-1"))
        assertNull(SettingsViewModel.parseCapacity("ten"))
    }
}
