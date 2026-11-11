package com.daviddokakis.mountaineer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 48: the whole route, the close-up from 1 km before a climb, at least 2 km wide. */
class RouteViewTest {
    private val points = (0..1152).map { k -> k * 25.0 to 20.0 + k * 0.01 }   // 28.8 km
    private fun climb(len: Double) = ClimbProfile(null, len, (0..kotlin.math.ceil(len / 100).toInt()).map { it * 4.7 })
    private val climbs = listOf(6200.0 to climb(1400.0), 25620.0 to climb(510.0), 27000.0 to climb(2600.0))

    @Test fun farFromAClimbShowsTheRoadToItsTop() {
        val v = RouteViews.view(points, climbs, 28800.0, 21300.0, 1, onClimb = false)!!
        assertTrue(v.whole)
        assertTrue(v.fromM < 21300.0 && v.fromM > 20900.0); assertEquals(25620.0 + 510, v.toM, 0.1)
        assertEquals(25620.0, v.flagM!!, 0.1)
    }

    @Test fun closeUpFromOneKmBefore() {
        assertTrue(RouteViews.view(points, climbs, 28800.0, 25620.0 - 1001, 1, onClimb = false)!!.whole)
        assertTrue(!RouteViews.view(points, climbs, 28800.0, 25620.0 - 1000, 1, onClimb = false)!!.whole)
    }

    @Test fun shortClimbCloseUpIsTwoKmWideWithYouAndTheClimbInIt() {
        for (you in listOf(25620.0 - 1000, 25620.0 - 300, 25620.0 + 200, 25620.0 + 510)) {
            val v = RouteViews.view(points, climbs, 28800.0, you, 1, onClimb = you >= 25620)!!
            assertEquals(2000.0, v.toM - v.fromM, 0.1)
            assertTrue(you > v.fromM && you < v.toM)
            assertTrue(v.fromM <= 25620.0 && v.toM >= 25620.0 + 510)
        }
    }

    @Test fun longClimbBeforeItShowsAllOfItAndOnItAsBefore() {
        val before = RouteViews.view(points, climbs, 28800.0, 27000.0 - 800, 2, onClimb = false)!!
        assertTrue(before.fromM < 27000.0 - 800 && before.toM > 27000.0 + 2600)
        assertNull(RouteViews.view(points, climbs, 28800.0, 27000.0 + 300, 2, onClimb = true))
    }

    @Test fun unknownPositionShowsTheWholeRoute() {
        assertNotNull(RouteViews.view(points, climbs, 28800.0, null, 1, onClimb = false)!!.takeIf { it.whole })
    }

    @Test fun offRouteTheWayBackComesFirst() {
        val off = OffRoute(listOf(0.0 to 30.0, 900.0 to 60.0, 1800.0 to 25.0), 3000.0)
        val v = RouteViews.view(points, climbs, 28800.0, null, 0, onClimb = false, off = off)!!
        assertEquals(1200.0, v.youM!!, 0.1)                 // you: the rejoin point minus the way back
        assertEquals(1200.0, v.offFromM!!, 0.1); assertEquals(3000.0, v.offToM!!, 0.1)
        assertEquals(6200.0 + 1400, v.topM, 0.1)            // the road on to climb 1's top
    }

    @Test fun oneRuleForEveryDistance() {
        assertEquals("800" to "m", Fmt.distance(800.0))
        assertEquals("270" to "m", Fmt.distance(266.0))
        assertEquals("1.0" to "km", Fmt.distance(996.0))
        assertEquals("22.9" to "km", Fmt.distance(22890.0))
    }

    @Test fun lengthsInMetresUnderOneKm() {
        assertEquals("510 m", Fmt.length(510.0))
        assertEquals("1.0 km", Fmt.length(996.0))
        assertEquals("1.4 km", Fmt.length(1400.0))
    }
}
