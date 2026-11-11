package com.daviddokakis.mountaineer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeToTopTest {

    private val steady = ClimbProfile(null, 1000.0, (0..10).map { it * 7.0 })   // 1 km at 7 %

    @Test fun `speed and power agree in still air`() {
        val v = Physics.speed(250.0, 7.0, 84.0)
        assertEquals(250.0, Physics.power(v, 7.0, 84.0), 0.5)
    }

    @Test fun `a headwind slows you and a tailwind speeds you up`() {
        val still = Physics.speed(250.0, 7.0, 84.0)
        assertTrue(Physics.speed(250.0, 7.0, 84.0, headwindMs = 5.0) < still)
        assertTrue(Physics.speed(250.0, 7.0, 84.0, headwindMs = -5.0) > still)
    }

    @Test fun `wind from where you ride to is a headwind`() {
        val w = Wind(speedMs = 10.0, fromDeg = 0.0)
        assertTrue(Physics.headwind(w, headingDeg = 0.0) > 0)       // riding north into a north wind
        assertTrue(Physics.headwind(w, headingDeg = 180.0) < 0)     // riding south: tailwind
        assertEquals(0.0, Physics.headwind(w, headingDeg = 90.0), 0.01)
        assertEquals(0.0, Physics.headwind(null, headingDeg = 0.0), 0.0)
        assertEquals(0.0, Physics.headwind(w, headingDeg = null), 0.0)
    }

    @Test fun `a heavier bike takes longer`() {
        val light = Physics.timeToTop(steady, 0.0, 250.0, 75.0 + 6.5)
        val heavy = Physics.timeToTop(steady, 0.0, 250.0, 75.0 + 12.0)
        assertTrue(heavy > light)
    }

    @Test fun `a headwind on the climb's direction makes it take longer`() {
        val north = steady.copy(headings = List(10) { 0.0 })
        val still = Physics.timeToTop(north, 0.0, 250.0, 84.0)
        val into = Physics.timeToTop(north, 0.0, 250.0, 84.0, wind = Wind(8.0, 0.0))
        val behind = Physics.timeToTop(north, 0.0, 250.0, 84.0, wind = Wind(8.0, 180.0))
        assertTrue(into > still)
        assertTrue(behind < still)
    }

    @Test fun `the correction scales the time`() {
        val base = Physics.timeToTop(steady, 0.0, 250.0, 84.0)
        assertEquals(base / 1.1, Physics.timeToTop(steady, 0.0, 250.0, 84.0, speedFactor = 1.1), 0.5)
    }

    @Test fun `the correction learns that you ride faster than the model`() {
        val c = SpeedCorrection()
        repeat(300) { c.learn(actualMs = 4.4, modelMs = 4.0) }
        assertEquals(1.1, c.factor, 0.01)
    }

    @Test fun `the correction ignores standing still and wild ratios`() {
        val c = SpeedCorrection()
        repeat(100) { c.learn(actualMs = 0.3, modelMs = 4.0) }      // walking / stopped
        repeat(100) { c.learn(actualMs = 20.0, modelMs = 4.0) }     // GPS jump
        assertEquals(1.0, c.factor, 0.0)
    }

    @Test fun `the correction moves slowly`() {
        val c = SpeedCorrection()
        repeat(10) { c.learn(actualMs = 5.0, modelMs = 4.0) }
        assertTrue(c.factor < 1.1)
    }
}

class BikeWeightTest {
    @Test fun `the standard is 8 kg`() {
        assertEquals(8.0, Prefs().bikeKgFor(null), 0.0)
    }

    @Test fun `a custom weight replaces the standard`() {
        assertEquals(7.2, Prefs(bikeKg = 7.2).bikeKgFor("road"), 0.0)
    }

    @Test fun `per profile uses the profile's weight`() {
        val p = Prefs(bikePerProfile = true, bikeKg = 7.2, profiles = listOf(BikeProfile("gravel", "Gravel", 10.2)))
        assertEquals(10.2, p.bikeKgFor("gravel"), 0.0)
    }

    @Test fun `a profile without a weight uses the one for all`() {
        val p = Prefs(bikePerProfile = true, bikeKg = 7.2, profiles = listOf(BikeProfile("beta", "BETA", null)))
        assertEquals(7.2, p.bikeKgFor("beta"), 0.0)
        assertEquals(7.2, p.bikeKgFor("unknown"), 0.0)
    }

    @Test fun `profile weights are ignored with one for all`() {
        val p = Prefs(bikePerProfile = false, profiles = listOf(BikeProfile("gravel", "Gravel", 10.2)))
        assertEquals(8.0, p.bikeKgFor("gravel"), 0.0)
    }
}

class LearningTest {
    @Test fun `a new climb starts from what was learned`() {
        assertEquals(1.08, SpeedCorrection(start = 1.08).factor, 0.0)
    }

    @Test fun `a climb moves the learned factor part of the way`() {
        assertEquals(1.0 + 0.3 * 0.1, Learning.blend(old = 1.0, climb = 1.1, seconds = 600), 1e-9)
    }

    @Test fun `a short climb teaches nothing`() {
        assertEquals(1.05, Learning.blend(old = 1.05, climb = 1.3, seconds = 60), 0.0)
    }

    @Test fun `the learned factor stays within sane bounds`() {
        var f = 1.0
        repeat(50) { f = Learning.blend(f, 1.6, 900) }
        assertTrue(f <= 1.4)
    }
}
