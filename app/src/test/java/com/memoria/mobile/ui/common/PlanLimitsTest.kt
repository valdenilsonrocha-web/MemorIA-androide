package com.memoria.mobile.ui.common

import com.memoria.mobile.data.remote.User
import com.memoria.mobile.ui.common.PlanLimits.withTemporaryFullAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * While [PlanLimits.FREE_HAS_ALL_FEATURES] is on, a free account is not capped.
 * Turning the flag off restores the two-medication / one-contact / no-photo limits.
 */
class PlanLimitsTest {

    private val free = User(name = "Grátis", isPremium = false)
    private val premium = User(name = "Premium", isPremium = true)

    @Test
    fun `temporary unlock gives every account the paid features`() {
        assertTrue(PlanLimits.FREE_HAS_ALL_FEATURES)
        assertTrue(PlanLimits.hasFullAccess(free))
        assertTrue(PlanLimits.hasFullAccess(null))
        assertTrue(PlanLimits.canAddMedication(free, activeCount = 50))
        assertTrue(PlanLimits.canStorePrescriptions(free))
        assertTrue(PlanLimits.canStorePrescriptions(null))
        assertEquals(PlanLimits.PREMIUM_CONTACTS, PlanLimits.maxContacts(free))
        assertTrue(free.withTemporaryFullAccess().isPremium)
        assertFalse(free.withTemporaryFullAccess().trialExpired)
    }

    @Test
    fun `premium is not capped`() {
        assertTrue(PlanLimits.canAddMedication(premium, activeCount = 2))
        assertTrue(PlanLimits.canAddMedication(premium, activeCount = 500))
    }

    @Test
    fun `premium stays unlimited`() {
        assertTrue(PlanLimits.canStorePrescriptions(premium))
        assertEquals(Int.MAX_VALUE, PlanLimits.maxMedications(premium))
    }
}
