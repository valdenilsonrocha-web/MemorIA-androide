package com.memoria.mobile.ui.common

import com.memoria.mobile.data.remote.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The free plan is enforced only on the phone — the backend caps caregivers but
 * NOT medications or prescriptions — so these rules are the whole gate. A
 * regression here gives away paid features silently, which is exactly the kind
 * of bug that does not announce itself.
 */
class PlanLimitsTest {

    private val free = User(name = "Grátis", isPremium = false)
    private val premium = User(name = "Premium", isPremium = true)

    @Test
    fun `free plan allows exactly two medications`() {
        assertTrue(PlanLimits.canAddMedication(free, activeCount = 0))
        assertTrue(PlanLimits.canAddMedication(free, activeCount = 1))
        assertFalse(PlanLimits.canAddMedication(free, activeCount = 2))
        assertFalse(PlanLimits.canAddMedication(free, activeCount = 7))
    }

    @Test
    fun `premium is not capped`() {
        assertTrue(PlanLimits.canAddMedication(premium, activeCount = 2))
        assertTrue(PlanLimits.canAddMedication(premium, activeCount = 500))
    }

    /** A logged-out or failed `me()` must not unlock the paid tier. */
    @Test
    fun `unknown user is treated as free`() {
        assertEquals(PlanLimits.FREE_MEDICATIONS, PlanLimits.maxMedications(null))
        assertFalse(PlanLimits.canAddMedication(null, activeCount = 2))
    }

    @Test
    fun `prescription storage is premium only`() {
        assertFalse(PlanLimits.canStorePrescriptions(free))
        assertFalse(PlanLimits.canStorePrescriptions(null))
        assertTrue(PlanLimits.canStorePrescriptions(premium))
    }
}
