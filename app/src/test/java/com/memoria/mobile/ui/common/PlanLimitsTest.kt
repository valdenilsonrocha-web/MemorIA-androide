package com.memoria.mobile.ui.common

import com.memoria.mobile.data.remote.User
import com.memoria.mobile.ui.common.PlanLimits.withTemporaryFullAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The free plan, as on the website and the server: 1 medication with several times or
 * 2 with 1 time each, 1 emergency contact, no prescription photos and no AI assistant.
 */
class PlanLimitsTest {

    private val free = User(name = "Grátis", isPremium = false)
    private val premium = User(name = "Premium", isPremium = true)

    @Test
    fun `the emergency unlock is off`() {
        assertFalse(PlanLimits.FREE_HAS_ALL_FEATURES)
        assertFalse(PlanLimits.hasFullAccess(free))
        assertFalse(PlanLimits.hasFullAccess(null))
        assertFalse(free.withTemporaryFullAccess().isPremium)
    }

    @Test
    fun `free plan allows one medication with several times or two with one time each`() {
        assertTrue(PlanLimits.allowsMedication(free, others = emptyList(), previousTimes = null, nextTimes = 4))
        assertTrue(PlanLimits.allowsMedication(free, others = listOf(1), previousTimes = null, nextTimes = 1))
        assertFalse(PlanLimits.allowsMedication(free, others = listOf(1), previousTimes = null, nextTimes = 2))
        assertFalse(PlanLimits.allowsMedication(free, others = listOf(3), previousTimes = null, nextTimes = 1))
        assertFalse(PlanLimits.allowsMedication(free, others = listOf(1, 1), previousTimes = null, nextTimes = 1))
    }

    @Test
    fun `an account already over the limit may edit and reduce but not add`() {
        assertTrue(PlanLimits.allowsMedication(free, others = listOf(2), previousTimes = 2, nextTimes = 2))
        assertTrue(PlanLimits.allowsMedication(free, others = listOf(2), previousTimes = 2, nextTimes = 1))
        assertFalse(PlanLimits.allowsMedication(free, others = listOf(2), previousTimes = 2, nextTimes = 3))
    }

    @Test
    fun `free plan has no prescription photos and no assistant`() {
        assertFalse(PlanLimits.canStorePrescriptions(free))
        assertFalse(PlanLimits.canUseAssistant(free))
        assertEquals(PlanLimits.FREE_CONTACTS, PlanLimits.maxContacts(free))
    }

    @Test
    fun `premium is not capped`() {
        assertTrue(PlanLimits.allowsMedication(premium, others = List(30) { 4 }, previousTimes = null, nextTimes = 6))
        assertTrue(PlanLimits.canStorePrescriptions(premium))
        assertTrue(PlanLimits.canUseAssistant(premium))
        assertEquals(PlanLimits.PREMIUM_CONTACTS, PlanLimits.maxContacts(premium))
    }
}
