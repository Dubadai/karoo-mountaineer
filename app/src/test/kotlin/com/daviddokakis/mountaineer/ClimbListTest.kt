package com.daviddokakis.mountaineer

import io.hammerhead.karooext.models.OnNavigationState.NavigationState.Climb
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Karoo drops a climb from its list once it starts; we keep it (ride 2026-11-10). */
class ClimbListTest {
    private fun climb(start: Double) = Climb(startDistance = start, length = 510.0, grade = 4.7, totalElevation = 23.0)

    @Test fun droppedClimbsAreKept() {
        val known = listOf(climb(25620.0))
        assertEquals(known, ClimbSession.mergeClimbs(known, emptyList()))
    }

    @Test fun newClimbsJoinInRouteOrder() {
        val merged = ClimbSession.mergeClimbs(listOf(climb(9000.0)), listOf(climb(25620.0), climb(3000.0)))
        assertEquals(listOf(3000.0, 9000.0, 25620.0), merged.map { it.startDistance })
    }

    @Test fun sameClimbIsNotDoubled() {
        val merged = ClimbSession.mergeClimbs(listOf(climb(25620.0)), listOf(climb(25620.4)))
        assertEquals(1, merged.size)
    }
}

/** The route test 2026-11-10 (Mallorca 312): during a reroute the Karoo listed the climbs on its new way's distances. */
class RerouteListTest {
    private fun c(start: Double, len: Double, grade: Double) = Climb(startDistance = start, length = len, grade = grade, totalElevation = len * grade / 100)
    private val route = listOf(c(70710.0, 4440.0, 4.8), c(78870.0, 990.0, 3.0), c(81630.0, 6480.0, 4.6), c(91110.0, 3000.0, 4.4))
    private val rerouted = listOf(c(600.0, 5100.0, 5.0), c(20670.0, 4470.0, 4.7), c(28860.0, 960.0, 3.0), c(31620.0, 6480.0, 4.6), c(41070.0, 3000.0, 4.4))

    @Test fun findsTheShift() {
        val shift = ClimbSession.frameShift(route, rerouted)!!
        assertEquals(50025.0, shift, 30.0)
    }

    @Test fun movesItBackAndDropsTheDetourClimb() {
        val shift = ClimbSession.frameShift(route, rerouted)!!
        val merged = ClimbSession.mergeClimbs(route, ClimbSession.shifted(route, rerouted, shift))
        assertEquals(4, merged.size)   // not 4 + 5, and not the 5.1 km climb on the way back
    }

    @Test fun sameDistancesNoShift() {
        assertEquals(0.0, ClimbSession.frameShift(route, route.drop(1))!!, 0.1)
    }
}

/** Our own off-route check: metres from a position to the route line. */
class RouteLineTest {
    // a straight 1 km line east along 39.8° N (Mallorca)
    private val line = listOf(Triple(0.0, 39.8, 2.80), Triple(1000.0, 39.8, 2.8117))

    @Test fun onTheLine() = assertEquals(0.0, ClimbSession.distanceToLine(line, 39.8, 2.805), 1.0)

    @Test fun fiftyMetresNorth() = assertEquals(50.0, ClimbSession.distanceToLine(line, 39.8 + 50 / 111_320.0, 2.805), 1.0)

    @Test fun pastTheEndCountsFromTheEnd() = assertEquals(100.0, ClimbSession.distanceToLine(line, 39.8, 2.8117 + 100 / (111_320.0 * kotlin.math.cos(Math.toRadians(39.8)))), 2.0)
}
