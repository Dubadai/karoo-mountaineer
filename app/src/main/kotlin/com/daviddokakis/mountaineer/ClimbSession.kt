package com.daviddokakis.mountaineer

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRidePage
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideProfile
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnNavigationState.NavigationState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Live climb data. Climbs and the elevation profile come from the route the rider loaded
 * ([OnNavigationState], karoo-ext 1.1.6+); where you are on it from the Karoo's own climb fields
 * (distance from bottom / to top) with distance along the route as a fallback; the PR from the
 * Karoo's Strava Live Segments. Everything is on one thread.
 */
class ClimbSession(private val context: android.content.Context, private val karoo: KarooSystemService) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val _state = MutableStateFlow(ClimbState())
    val state: StateFlow<ClimbState> = _state

    /**
     * The ride page on screen (null until the Karoo says). With the field on two pages the Karoo can
     * show one page's picture in the other's tile, so only the views on this page draw (ClearWind's
     * finding, design-assets/karoo-type/README.md "Keeping a tile steady").
     */
    private val _activePage = MutableStateFlow<RideProfile.Page?>(null)
    val activePage: StateFlow<RideProfile.Page?> = _activePage

    // Raw inputs
    private var climbs: List<Pair<Double, ClimbProfile>> = emptyList()   // route start (m) to profile
    /** Every climb the Karoo listed for the route in [routeKey], also those it dropped once started. */
    private var seenClimbs: List<NavigationState.Climb> = emptyList()
    private var routeKey: Pair<Int, Double?>? = null
    private var points: List<Pair<Double, Double>> = emptyList()   // the route's (distance, elevation)
    /** The route's line: distance along it (m), latitude, longitude. For directions and the wind's place. */
    private var geo: List<Triple<Double, Double, Double>> = emptyList()
    private var wind: Wind? = null
    private var windFor: Pair<Double, Double>? = null
    private var windAt = 0L
    private val correction = SpeedCorrection()
    private var lastLog = 0L
    private var profileId: String? = null
    /** The climb being ridden (index into [climbs]) and what it has measured so far. */
    private var climbIndex: Int? = null
    private var climbLog: ClimbLog? = null
    private var routeDistance: Double? = null
    private var toDestination: Double? = null
    private var fromBottom: Double? = null
    private var toTop: Double? = null
    private var speed10s: Double? = null
    private var power30s: Double? = null
    private var prTime: Double? = null
    private var prDelta: Double? = null
    private var weightKg: Double = 75.0
    private var powerEstimate: Double? = null
    private var lastOnClimb: ClimbState? = null
    private var toppedAt = 0L
    private var toppedAlong: Double? = null
    private var toppedSummary: ClimbSummary? = null
    private var toppedReal = true
    // Off route (round 55): the Karoo's flag, its way back to the route and where it rejoins, heights for it
    private var onRoute: Boolean? = null
    private var altitude: Double? = null
    private var rejoinKey: String? = null
    private var rejoinRaw: Double? = null
    /** How far the Karoo's latest climb list is shifted from the route's distances (a reroute; 0: none). */
    private var frameOffset = 0.0
    /** Distance to destination seen in this navigation: losing it means off route (route test 2026-11-10). */
    private var hadDestination = false
    private var rejoinGeo: List<Triple<Double, Double, Double>> = emptyList()
    private var rejoinHeights: List<Pair<Double, Double>>? = null
    private var lastOffLog: String? = null
    private var lastNavFlags: String? = null
    /** Our own check: off the route line by more than [OFF_LINE_M] for [OFF_LINE_MS] (null: on it, or unknown). */
    private var awaySince = 0L
    @Volatile private var ownOff = false
    /** Navigating (a route or to a destination): the Karoo's navigation bar is on screen (round 56). */
    @Volatile var navigating = false
        private set

    fun start() {
        scope.launch {
            karoo.consumerFlow<OnNavigationState>().collect { onNavigation(it.state) }
        }
        scope.launch {
            karoo.consumerFlow<ActiveRidePage>().collect { _activePage.value = it.page }
        }
        scope.launch {
            // The ride profile in use: its bike weight, and Settings lists the profiles used
            karoo.consumerFlow<io.hammerhead.karooext.models.ActiveRideProfile>().collect {
                profileId = it.profile.id
                Settings.recordProfile(context, it.profile.id, it.profile.name)
            }
        }
        scope.launch {
            karoo.consumerFlow<UserProfile>().collect { if (it.weight > 30) weightKg = it.weight.toDouble() }
        }
        stream(DataType.Type.DISTANCE_TO_DESTINATION) {
            toDestination = it.field(DataType.Field.DISTANCE_TO_DESTINATION)
            onRoute = it.field(DataType.Field.ON_ROUTE)?.let { v -> v > 0.5 }
            // the Karoo's raw flags, logged on every change, to see when it tells apps (route tests 2026-11-10)
            val raw = "streaming=${it is StreamState.Streaming} onRoute=${it.field(DataType.Field.ON_ROUTE)} " +
                "navState=${it.field(DataType.Field.NAVIGATION_STATE)} rerouting=${it.field(DataType.Field.REROUTING_ENABLED)} toDest=${toDestination != null}"
            if (raw != lastNavFlags) { lastNavFlags = raw; Timber.i("Karoo nav flags: $raw") }
        }
        scope.launch {
            // Our own off-route check (feedback, 2026-11-10: "the second the Karoo notices"): your GPS against the route line
            karoo.consumerFlow<io.hammerhead.karooext.models.OnLocationChanged>().collect { onLocation(it.lat, it.lng) }
        }
        stream(DataType.Type.PRESSURE_ELEVATION_CORRECTION) { altitude = it.field(DataType.Field.PRESSURE_ELEVATION) }
        stream(DataType.Type.CLIMB) {
            fromBottom = it.field(DataType.Field.DISTANCE_FROM_BOTTOM)
            toTop = it.field(DataType.Field.DISTANCE_TO_TOP)
        }
        stream(DataType.Type.SMOOTHED_10S_AVERAGE_SPEED) { speed10s = it.single() }
        stream(DataType.Type.SMOOTHED_30S_AVERAGE_POWER) { power30s = it.single() }
        stream(DataType.Type.SEGMENT_PR) {
            val v = it.field(DataType.Field.SEGMENT_PR_TIME)
            if (v != prTime) Timber.i("Segment PR raw: $v")
            prTime = v
        }
        stream(DataType.Type.SEGMENT_TIME_TO_PR) {
            val v = it.field(DataType.Field.SEGMENT_PR_DELTA_TIME)
            if (v != null && prDelta == null) Timber.i("Segment PR delta raw: $v")
            prDelta = v
        }
        scope.launch { windLoop() }
        scope.launch {
            while (isActive) {
                _state.value = compute().copy(powerW = power30s)
                delay(1000)
            }
        }
    }

    fun stop() = scope.cancel()

    /** A new climb: start from what earlier climbs taught (Improve over time), else from the physics. */
    private fun startClimb(index: Int, c: ClimbProfile, prefs: Prefs, mass: Double) {
        finishClimb()
        climbIndex = index
        correction.reset(if (prefs.improve) Settings.learned(context, profileId) else 1.0)
        climbLog = ClimbLog(android.os.SystemClock.elapsedRealtime(), c, mass, correction.factor, wind)
        Timber.i("Climb %d started: factor %.3f (improve %s)", index + 1, correction.factor, prefs.improve)
    }

    /** The climb is over (or left): learn from it and log it, on the Karoo only (Improve over time). */
    private fun finishClimb() {
        val log = climbLog ?: return
        climbLog = null; climbIndex = null
        val prefs = Settings.read(context)
        if (!prefs.improve) return
        val old = Settings.learned(context, profileId)
        val next = Learning.blend(old, correction.factor, log.seconds)
        Settings.setLearned(context, profileId, next)
        val secs = (android.os.SystemClock.elapsedRealtime() - log.startMs) / 1000
        try {
            val f = java.io.File(context.filesDir, "climb-log.csv")
            if (!f.exists()) f.writeText("date,profile,length_m,gain_m,mass_kg,wind_ms,wind_from,power_w,speed_ms,model_ms,eta_start_s,done_start_m,ridden_s,factor_start,factor_end,learned\n")
            f.appendText(String.format(java.util.Locale.US, "%s,%s,%.0f,%.0f,%.1f,%s,%s,%.0f,%.2f,%.2f,%s,%s,%d,%.3f,%.3f,%.3f\n",
                java.time.LocalDateTime.now().withNano(0), profileId ?: "", log.climb.lengthM, log.climb.gainM, log.mass,
                log.wind?.speedMs?.let { "%.1f".format(java.util.Locale.US, it) } ?: "", log.wind?.fromDeg?.toInt() ?: "",
                log.avgPower, log.avgSpeed, log.avgModel, log.etaAtStart ?: "", log.doneAtStart?.toInt() ?: "", secs,
                log.factorStart, correction.factor, next))
        } catch (e: Exception) { Timber.w(e, "Climb log not written") }
        Timber.i("Climb done: %d s with power, factor %.3f, learned %.3f -> %.3f", log.seconds, correction.factor, old, next)
    }

    private fun logTimeToTop(done: Double, p: Double?, v: Double?, head: Double, eta: Int?) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastLog < 30_000) return
        lastLog = now
        Timber.i("TimeToTop: at %.0f m, power %s W, speed %s m/s, head %.1f m/s, factor %.3f, eta %s s",
            done, p?.toInt(), v?.let { "%.1f".format(it) }, head, correction.factor, eta)
    }

    // ---- The route's line: directions and the wind's place --------------------------------

    /** The route polyline (precision 5) as (distance along, lat, lng). */
    private fun routeLine(encoded: String): List<Triple<Double, Double, Double>> {
        val ll = decodePairs(encoded, 1e5)
        val out = ArrayList<Triple<Double, Double, Double>>(ll.size)
        var d = 0.0
        ll.forEachIndexed { i, (la, ln) ->
            if (i > 0) d += distanceM(ll[i - 1].first, ll[i - 1].second, la, ln)
            out += Triple(d, la, ln)
        }
        return out
    }

    /** Where the route is [d] metres along it. */
    private fun positionAt(d: Double): Pair<Double, Double>? {
        if (geo.size < 2) return null
        val i = geo.indexOfFirst { it.first >= d }.let { if (it <= 0) 1 else it }
        val (d0, la0, ln0) = geo[i - 1]; val (d1, la1, ln1) = geo[i]
        val f = if (d1 - d0 < 0.01) 0.0 else ((d - d0) / (d1 - d0)).coerceIn(0.0, 1.0)
        return (la0 + f * (la1 - la0)) to (ln0 + f * (ln1 - ln0))
    }

    /** The direction of travel of each 100 m step of a climb, from 50 m before to 50 m after its middle. */
    private fun headings(start: Double, length: Double): List<Double>? {
        if (geo.size < 2) return null
        val n = kotlin.math.ceil(length / ClimbProfile.STEP_M).toInt()
        return (0 until n).map { k ->
            val mid = start + min(k * ClimbProfile.STEP_M + 50, length)
            val a = positionAt(mid - 50) ?: return null
            val b = positionAt(mid + 50) ?: return null
            bearing(a.first, a.second, b.first, b.second)
        }
    }

    private fun bearing(la1: Double, ln1: Double, la2: Double, ln2: Double): Double {
        val y = sin(Math.toRadians(ln2 - ln1)) * cos(Math.toRadians(la2))
        val x = cos(Math.toRadians(la1)) * sin(Math.toRadians(la2)) - sin(Math.toRadians(la1)) * cos(Math.toRadians(la2)) * cos(Math.toRadians(ln2 - ln1))
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun distanceM(la1: Double, ln1: Double, la2: Double, ln2: Double): Double {
        val dLat = (la2 - la1) * 111_320.0
        val dLng = (ln2 - ln1) * 111_320.0 * cos(Math.toRadians((la1 + la2) / 2))
        return sqrt(dLat * dLat + dLng * dLng)
    }

    // ---- Wind: the Open-Meteo forecast at the climb's middle, every 10 minutes ---------------

    private suspend fun windLoop() {
        while (scope.isActive) {
            val target = windTarget()
            val due = target != null && Settings.read(context).wind &&
                (windFor != target || android.os.SystemClock.elapsedRealtime() - windAt > WIND_REFRESH_MS)
            if (due && target != null) {
                val got = fetchWind(target.first, target.second)
                if (got != null) { wind = got; windFor = target; windAt = android.os.SystemClock.elapsedRealtime() }
                delay(if (got != null) WIND_CHECK_MS else WIND_RETRY_MS)
            } else delay(WIND_CHECK_MS)
        }
    }

    /** The middle of the climb you're on or the next one, rounded to ~1 km. */
    private fun windTarget(): Pair<Double, Double>? {
        val s = _state.value
        val c = s.climb ?: return null
        val start = climbs.getOrNull((s.number ?: return null) - 1)?.first ?: return null
        val (la, ln) = positionAt(start + c.lengthM / 2) ?: return null
        return (Math.round(la * 100) / 100.0) to (Math.round(ln * 100) / 100.0)
    }

    private suspend fun fetchWind(la: Double, ln: Double): Wind? {
        val url = String.format(java.util.Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=wind_speed_10m,wind_direction_10m&wind_speed_unit=ms", la, ln)
        val body = withTimeoutOrNull(30_000) { httpGet(url) } ?: run { Timber.w("Wind fetch failed"); return null }
        return try {
            val cur = org.json.JSONObject(body).getJSONObject("current")
            Wind(cur.getDouble("wind_speed_10m"), cur.getDouble("wind_direction_10m"))
                .also { Timber.i("Wind at the climb: %.1f m/s from %.0f°", it.speedMs, it.fromDeg) }
        } catch (e: Exception) { Timber.w(e, "Wind response not understood"); null }
    }

    /** GET through the Karoo (Wi-Fi or phone). Null on any failure. */
    private suspend fun httpGet(url: String): String? = suspendCancellableCoroutine { cont ->
        var id: String? = null
        id = karoo.addConsumer<OnHttpResponse>(
            OnHttpResponse.MakeHttpRequest("GET", url, mapOf("User-Agent" to "Mountaineer-Karoo"), waitForConnection = false),
            onError = { msg -> Timber.w("HTTP error: $msg"); if (cont.isActive) cont.resume(null) },
        ) { event ->
            val st = event.state
            if (st is HttpResponseState.Complete) {
                id?.let { karoo.removeConsumer(it) }
                val ok = st.statusCode in 200..299 && st.body != null
                if (cont.isActive) cont.resume(if (ok) st.body!!.decodeToString() else null)
            }
        }
        cont.invokeOnCancellation { id?.let { karoo.removeConsumer(it) } }
    }

    private fun stream(type: String, on: (StreamState) -> Unit) {
        scope.launch { karoo.streamDataFlow(type).collect { on(it) } }
    }

    private fun onNavigation(nav: NavigationState) {
        geo = when (nav) {
            is NavigationState.NavigatingRoute -> routeLine(nav.routePolyline)
            else -> emptyList()
        }
        val nowNavigating = nav is NavigationState.NavigatingRoute || nav is NavigationState.NavigatingToDestination
        if (!nowNavigating) hadDestination = false
        navigating = nowNavigating
        // the way back to the route (null when on route, or off route with breadcrumb navigation)
        val rejoin = (nav as? NavigationState.NavigatingRoute)?.rejoinPolyline
        if (rejoin != rejoinKey) {
            rejoinKey = rejoin
            rejoinRaw = (nav as? NavigationState.NavigatingRoute)?.rejoinDistance
            rejoinGeo = rejoin?.let(::routeLine) ?: emptyList()
            rejoinHeights = null
            val geoNow = rejoinGeo
            if (rejoin != null && geoNow.size >= 2 && geoNow.last().first >= MIN_WAY_BACK_M) scope.launch { fetchRejoinHeights(rejoin, geoNow) }
        }
        val (polyline, list, total) = when (nav) {
            is NavigationState.NavigatingRoute -> Triple(nav.routeElevationPolyline, nav.climbs, nav.routeDistance)
            is NavigationState.NavigatingToDestination -> Triple(nav.elevationPolyline, nav.climbs, null)
            else -> Triple(null, emptyList(), null)
        }
        routeDistance = total
        val points = polyline?.let { decodePairs(it, 10.0) } ?: emptyList()
        this.points = points
        // The Karoo drops a climb from its list the moment it starts (ride 2026-11-10): keep every
        // climb seen on this route, so the climb being ridden and the ones done stay ours.
        val key = when (nav) {
            is NavigationState.NavigatingRoute -> nav.routePolyline.hashCode() to total
            is NavigationState.NavigatingToDestination -> nav.polyline.hashCode() to null
            else -> null
        }
        // A reroute (route test 2026-11-10): the Karoo then lists the climbs counted from the start of its new
        // way (70.7 km became 0.6 + 20.1 km) while the elevation stays on the route's distances. Find that shift
        // from climbs both lists share, move the new list back onto the route, and drop climbs only on the detour.
        if (key != null && key == routeKey) {
            val shift = frameShift(seenClimbs, list)
            if (shift == null || kotlin.math.abs(shift) <= FRAME_SAME_M) { frameOffset = 0.0; seenClimbs = mergeClimbs(seenClimbs, list) }
            else if (fitError(list) < fitError(seenClimbs)) {
                // the new list is the one on the route's distances (the kept one came from a reroute): move the kept one
                frameOffset = 0.0
                seenClimbs = mergeClimbs(list.sortedBy { it.startDistance }, shifted(list, seenClimbs, -shift))
            } else { frameOffset = shift; seenClimbs = mergeClimbs(seenClimbs, shifted(seenClimbs, list, shift)) }
            if (shift != null && kotlin.math.abs(shift) > FRAME_SAME_M) Timber.i("Climb list shifted %.0f m (reroute), offset now %.0f", shift, frameOffset)
        } else { seenClimbs = list.sortedBy { it.startDistance }; frameOffset = 0.0 }
        routeKey = key
        climbs = seenClimbs.mapNotNull { c ->
            ClimbProfile.fromRoute(points, c.startDistance, c.length, null)
                // fix 5 (route test): the Karoo's own gain and grade for the numbers shown
                ?.copy(headings = headings(c.startDistance, c.length), karooGainM = c.totalElevation, karooGrade = c.grade)
                ?.let { c.startDistance to it }
        }
        correction.reset()
        Timber.i("Navigation: ${nav::class.simpleName}, ${points.size} elevation points, climbs=${list.map { "${it.startDistance.toInt()}+${it.length.toInt()} ${"%.1f".format(it.grade)}%" }}, kept ${seenClimbs.size}")
    }

    private fun compute(): ClimbState {
        // A route loaded but no climbs from the Karoo (round 59): it can't place you on the route yet
        // (rerouting: off route), or the route has none. Not "No Route".
        if (climbs.isEmpty()) return if (!navigating) ClimbState()
            else ClimbState(hasRoute = true, total = 0, offRoute = onRoute == false || toDestination == null, toFinishM = toDestination,
                route = RouteViews.whole(points, emptyList(), routeDistance, routeDistance?.let { r -> toDestination?.let { r - it } }))
        if (toDestination != null) hadDestination = true
        // Off route: the Karoo's flag, or (it can take a minute to say so, route test 2026-11-10) its distance
        // to destination gone while navigating. First, as its climb fields freeze then.
        // Already where the way back meets the route (0 m back; route test 2026-11-10): on the route there
        val wayBack = rejoinGeo.lastOrNull()?.first ?: 0.0
        val atRejoin = rejoinRaw?.takeIf { rejoinKey != null && wayBack < MIN_WAY_BACK_M }?.let { it + frameOffset }
        if (atRejoin == null && (ownOff || onRoute == false || (navigating && hadDestination && toDestination == null))) {
            lastOnClimb = null; powerEstimate = null; finishClimb()
            return offRouteState()
        }
        val along = atRejoin ?: routeDistance?.let { r -> toDestination?.let { r - it } }
        // On a climb: the Karoo's own climb fields first, else distance along the route.
        var index = -1
        var done: Double? = null
        val tt = toTop
        val fb = fromBottom
        if (tt != null && tt > 0 && fb != null) {
            index = climbs.indices.minByOrNull { i ->
                val (start, c) = climbs[i]
                if (along == null) kotlin.math.abs(c.lengthM - (fb + tt)) else kotlin.math.abs(start + fb - along)
            } ?: -1
            done = fb
        } else if (along != null && onRoute != false) {   // off route, distance along the route means nothing
            index = climbs.indexOfFirst { (start, c) -> along >= start && along <= start + c.lengthM }
            if (index >= 0) done = along - climbs[index].first
        }
        if (index >= 0 && done != null) {
            val c = climbs[index].second
            val prefs = Settings.read(context)
            val mass = weightKg + prefs.bikeKgFor(profileId)
            val w = wind.takeIf { prefs.wind }
            val gradeNow = c.grade(done, done + 100)
            val head = Physics.headwind(w, c.headings?.getOrNull((done / ClimbProfile.STEP_M).toInt()))
            val p = power30s?.takeIf { it > 30 }
                ?: speed10s?.takeIf { it > 1 }?.let { Physics.power(it, gradeNow, mass, head) }
            if (p != null && p > 30) powerEstimate = powerEstimate?.let { it * 0.9 + p * 0.1 } ?: p
            // Learn how the real speed compares with the model's, only with a power meter (without
            // one the power comes from the speed, so there's nothing to compare)
            val pw = power30s; val v = speed10s
            if (climbIndex != index) startClimb(index, c, prefs, mass)
            if (pw != null && pw > 50 && v != null) {
                val gradeRidden = c.grade(max(0.0, done - 50), done + 50)
                val model = Physics.speed(pw, gradeRidden, mass, head)
                correction.learn(v, model)
                climbLog?.add(pw, v, model, head)
            }
            val eta = powerEstimate?.let { Physics.timeToTop(c, done, it, mass, w, correction.factor).toInt() }
            climbLog?.let { if (it.etaAtStart == null && eta != null) { it.etaAtStart = eta; it.doneAtStart = done } }
            logTimeToTop(done, p, v, head, eta)
            return ClimbState(
                hasRoute = true, climb = c, doneM = done, number = index + 1, total = climbs.size,
                route = RouteViews.view(points, climbs, routeDistance, climbs[index].first + done, index, onClimb = true),
                afterTop = ClimbProfile.fromRoute(points, climbs[index].first + c.lengthM, c.lengthM * 0.2, null),
                etaSec = eta, prSec = prTime?.let(::seconds), gapSec = prDelta?.let(::seconds),
            ).also { lastOnClimb = it; toppedAt = 0L }
        }
        // Just over the top: keep the climb on screen for a minute (or 200 m) at its top, so the
        // rider sees SUMMIT REACHED before the field moves on to the next climb (round 19)
        val last = lastOnClimb
        if (last != null && last.climb != null && (last.doneM ?: 0.0) >= last.climb.lengthM - SUMMIT_NEAR_M) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (toppedAt == 0L) {
                toppedAt = now; toppedAlong = along
                // the summary (round 50): time since the climb started, average power with a power meter (30 s+), VAM
                toppedSummary = climbLog?.let { log ->
                    ClimbSummary.of(((now - log.startMs) / 1000).toInt(), last.climb.shownGainM, log.avgPower.takeIf { log.seconds >= 30 }?.roundToInt())
                }
                // not ridden for real (a jump; route test 2026-11-10: 0:07, VAM 136430): no summit minute
                toppedReal = toppedSummary?.let { it.timeSec >= SUMMARY_MIN_S && it.vamMh <= SUMMARY_MAX_VAM } ?: true
            }
            val ridden = along?.let { a -> toppedAlong?.let { a - it } } ?: 0.0
            if (toppedReal && now - toppedAt < SUMMIT_HOLD_MS && ridden < SUMMIT_HOLD_M) return last.copy(doneM = last.climb.lengthM, etaSec = 0, summary = toppedSummary)
        }
        lastOnClimb = null
        powerEstimate = null
        finishClimb()
        val next = along?.let { a -> climbs.indexOfFirst { it.first > a } } ?: 0
        // all climbs done (round 60): still the elevation map, the whole route
        if (next < 0) return ClimbState(hasRoute = true, total = climbs.size, toFinishM = toDestination,
            route = RouteViews.whole(points, climbs, routeDistance, along))
        val (start, c) = climbs[next]
        // the whole route, or the close-up from 1 km before the climb (round 48)
        return ClimbState(
            hasRoute = true, climb = c, nextInM = along?.let { start - it }, number = next + 1, total = climbs.size,
            route = RouteViews.view(points, climbs, routeDistance, along, next, onClimb = false),
        )
    }

    /**
     * Off route: with the Karoo's way back, the next climb after where it rejoins, DIST TO CLIMB = the
     * way back + the route from there, the graph with the way back in red. Without one (breadcrumb
     * navigation): OFF ROUTE and no distance.
     */
    private fun offRouteState(): ClimbState {
        val at = rejoinRaw?.let { it + frameOffset }   // on the route's distances (the Karoo gives it on its new way's)
        val off = if (at != null && rejoinGeo.size >= 2) {
            val len = rejoinGeo.last().first
            OffRoute(rejoinHeights ?: listOf(0.0 to (altitude ?: routeElevAt(at)), len to routeElevAt(at)), at)
        } else null
        val log = off?.let { "rejoin at %.0f m, way back %.0f m, heights %s, to destination %s".format(java.util.Locale.US,
            it.rejoinM, it.lengthM, if (rejoinHeights != null) "Open-Meteo" else "straight", toDestination?.toInt()) } ?: "no way back"
        if (log != lastOffLog) { lastOffLog = log; Timber.i("Off route: $log") }
        // No way back yet (breadcrumb, or the Karoo still working one out): we don't know where you are, so the
        // whole route with no dot and DIST TO FINISH "--" (route test 2026-11-10: the climb from before a jump was stale)
        if (off == null) return ClimbState(hasRoute = true, offRoute = true, toFinishM = toDestination, route = RouteViews.whole(points, climbs, routeDistance, null))
        val next = climbs.indexOfFirst { (start, c) -> start + c.lengthM > off.rejoinM }
        // no climb after the rejoin point (pass 3: the Karoo had turned the route round): the way back and the road on
        if (next < 0) return ClimbState(hasRoute = true, offRoute = true, toFinishM = toDestination,
            route = RouteViews.ahead(points, climbs, routeDistance, off) ?: RouteViews.whole(points, climbs, routeDistance, null))
        val (start, c) = climbs[next]
        return ClimbState(
            hasRoute = true, climb = c, number = next + 1, total = climbs.size, offRoute = true,
            nextInM = off?.let { it.lengthM + max(0.0, start - it.rejoinM) },
            route = off?.let { RouteViews.view(points, climbs, routeDistance, null, next, onClimb = false, off = it) },
        )
    }

    /** Your position against the route line: off it by more than [OFF_LINE_M] for [OFF_LINE_MS] is off route. */
    private fun onLocation(lat: Double, lng: Double) {
        val line = geo
        if (line.size < 2) { ownOff = false; awaySince = 0L; return }
        val away = distanceToLine(line, lat, lng)
        val now = android.os.SystemClock.elapsedRealtime()
        if (away <= OFF_LINE_M) {
            if (ownOff) Timber.i("Own check: back on the route (%.0f m from it)", away)
            ownOff = false; awaySince = 0L
        } else {
            if (awaySince == 0L) awaySince = now
            if (!ownOff && now - awaySince >= OFF_LINE_MS) { ownOff = true; Timber.i("Own check: off route (%.0f m from the route for %d s)", away, (now - awaySince) / 1000) }
        }
    }

    /** How badly [list] fits the route's elevation: mean |our gain − the Karoo's| / the Karoo's (a list on other distances fits badly). */
    private fun fitError(list: List<NavigationState.Climb>): Double {
        val errs = list.mapNotNull { c ->
            ClimbProfile.fromRoute(points, c.startDistance, c.length, null)?.let { kotlin.math.abs(it.gainM - c.totalElevation) / max(10.0, c.totalElevation) }
        }
        return if (errs.isEmpty()) Double.MAX_VALUE else errs.average()
    }

    /** The route's height [d] metres along it. */
    private fun routeElevAt(d: Double): Double {
        if (points.isEmpty()) return 0.0
        val i = points.indexOfFirst { it.first >= d }
        if (i <= 0) return points[if (i == 0) 0 else points.size - 1].second
        val (d0, e0) = points[i - 1]; val (d1, e1) = points[i]
        return if (d1 - d0 < 0.01) e0 else e0 + (d - d0) / (d1 - d0) * (e1 - e0)
    }

    /**
     * Heights for the way back from Open-Meteo's ground heights (as for the wind): up to 100 points
     * along it, rounded to ~100 m (enough for a 90 m height model). Kept only while it's still the way back.
     */
    private suspend fun fetchRejoinHeights(key: String, g: List<Triple<Double, Double, Double>>) {
        // the way back as it was when asked (route test 2026-11-10: it can be replaced, even emptied, before this runs)
        val len = g.lastOrNull()?.first ?: return
        if (g.size < 2 || len < MIN_WAY_BACK_M) return
        val n = min(100, max(2, (len / 25).toInt() + 1))
        val samples = (0 until n).map { k -> len * k / (n - 1) }
        val ll = samples.map { d ->
            val i = g.indexOfFirst { it.first >= d }.let { if (it <= 0) 1 else it }
            val (d0, la0, ln0) = g[i - 1]; val (d1, la1, ln1) = g[i]
            val f = if (d1 - d0 < 0.01) 0.0 else ((d - d0) / (d1 - d0)).coerceIn(0.0, 1.0)
            (la0 + f * (la1 - la0)) to (ln0 + f * (ln1 - ln0))
        }
        val url = "https://api.open-meteo.com/v1/elevation?latitude=" + ll.joinToString(",") { "%.3f".format(java.util.Locale.US, it.first) } +
            "&longitude=" + ll.joinToString(",") { "%.3f".format(java.util.Locale.US, it.second) }
        val body = withTimeoutOrNull(30_000) { httpGet(url) } ?: run { Timber.w("Way-back heights: no answer"); return }
        try {
            val a = org.json.JSONObject(body).getJSONArray("elevation")
            if (a.length() != samples.size || rejoinKey != key) return
            rejoinHeights = samples.mapIndexed { k, d -> d to a.getDouble(k) }
            Timber.i("Way-back heights: %d points, %.0f m long", samples.size, len)
        } catch (e: Exception) { Timber.w(e, "Way-back heights not understood") }
    }

    /** Segment times: the Karoo gives milliseconds (checked by size: a PR of 20 000+ is ms). */
    private fun seconds(v: Double): Int = (if (kotlin.math.abs(v) > 20_000) v / 1000 else v).toInt()

    /** What one climb measured, for Improve over time. Stays in memory until the climb ends. */
    private class ClimbLog(val startMs: Long, val climb: ClimbProfile, val mass: Double, val factorStart: Double, val wind: Wind?) {
        var seconds = 0; private var sumP = 0.0; private var sumV = 0.0; private var sumM = 0.0
        var etaAtStart: Int? = null; var doneAtStart: Double? = null
        fun add(p: Double, v: Double, model: Double, head: Double) { seconds++; sumP += p; sumV += v; sumM += model }
        val avgPower get() = if (seconds > 0) sumP / seconds else 0.0
        val avgSpeed get() = if (seconds > 0) sumV / seconds else 0.0
        val avgModel get() = if (seconds > 0) sumM / seconds else 0.0
    }

    companion object {
        /** The summit view: within this of the top counts as topped; held this long or this far past it. */
        private const val SUMMIT_NEAR_M = 60.0
        /** Climb lists: one climb if the starts are this close; lists this close count as the same distances. */
        private const val MATCH_START_M = 250.0
        private const val FRAME_SAME_M = 200.0
        /** A summit summary only for a climb ridden for real: at least this long, at most this VAM (m/h). */
        private const val SUMMARY_MIN_S = 60
        /** Our own off-route check: further than this from the route line, for this long. */
        const val OFF_LINE_M = 40.0
        private const val OFF_LINE_MS = 5_000L

        /** Metres from ([lat], [lng]) to the nearest point of [line] (distance along, lat, lng). */
        fun distanceToLine(line: List<Triple<Double, Double, Double>>, lat: Double, lng: Double): Double {
            val kx = 111_320.0 * cos(Math.toRadians(lat)); val ky = 111_320.0
            var best = Double.MAX_VALUE
            for (i in 1 until line.size) {
                val ax = (line[i - 1].third - lng) * kx; val ay = (line[i - 1].second - lat) * ky
                val bx = (line[i].third - lng) * kx; val by = (line[i].second - lat) * ky
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 < 1e-9) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                val px = ax + t * dx; val py = ay + t * dy
                best = min(best, px * px + py * py)
            }
            return sqrt(best)
        }

        /** A way back shorter than this: you're already at the route. */
        private const val MIN_WAY_BACK_M = 10.0
        private const val SUMMARY_MAX_VAM = 3000
        private const val SUMMIT_HOLD_MS = 60_000L
        private const val SUMMIT_HOLD_M = 200.0
        private const val WIND_REFRESH_MS = 10 * 60_000L
        private const val WIND_CHECK_MS = 15_000L
        private const val WIND_RETRY_MS = 60_000L

        /** The climbs [known] for a route plus any new ones in [latest] (by start), in route order. */
        fun mergeClimbs(known: List<NavigationState.Climb>, latest: List<NavigationState.Climb>): List<NavigationState.Climb> {
            val fresh = latest.filter { n -> known.none { same(it, n, 0.0) } }
            return (known + fresh).sortedBy { it.startDistance }
        }

        /** The same climb: starts within [MATCH_START_M] (after [shift]), length within 100 m (or 3 %), grade within 0.3. */
        private fun same(k: NavigationState.Climb, n: NavigationState.Climb, shift: Double) =
            kotlin.math.abs(k.startDistance - (n.startDistance + shift)) < MATCH_START_M &&
                kotlin.math.abs(k.length - n.length) <= max(100.0, k.length * 0.03) && kotlin.math.abs(k.grade - n.grade) <= 0.3

        /**
         * How far [latest] is shifted from [known] (metres to add to it): the shift most climbs both lists share
         * agree on (within 200 m; at least two, or the only one). Null: nothing in common.
         */
        fun frameShift(known: List<NavigationState.Climb>, latest: List<NavigationState.Climb>): Double? {
            if (known.isEmpty() || latest.isEmpty()) return null
            val shifts = latest.flatMap { n ->
                known.filter { k -> kotlin.math.abs(k.length - n.length) <= max(100.0, k.length * 0.03) && kotlin.math.abs(k.grade - n.grade) <= 0.3 }
                    .map { k -> k.startDistance - n.startDistance }
            }
            val best = shifts.maxByOrNull { s -> shifts.count { kotlin.math.abs(it - s) <= 200 } } ?: return null
            val agree = shifts.filter { kotlin.math.abs(it - best) <= 200 }.sorted()
            if (agree.size < min(2, latest.size)) return null
            return agree[agree.size / 2]
        }

        /** [latest] moved onto [known]'s distances by [shift], keeping only climbs [known] also has (not the detour's own). */
        fun shifted(known: List<NavigationState.Climb>, latest: List<NavigationState.Climb>, shift: Double): List<NavigationState.Climb> =
            latest.filter { n -> known.any { same(it, n, shift) } }.map { it.copy(startDistance = it.startDistance + shift) }

        /** Google polyline pairs (here distance, elevation) at [factor] (precision 1 = 10). */
        fun decodePairs(encoded: String, factor: Double): List<Pair<Double, Double>> {
            val out = ArrayList<Pair<Double, Double>>()
            var i = 0; var a = 0L; var b = 0L
            fun next(): Long {
                var shift = 0; var result = 0L
                while (true) {
                    val c = encoded[i++].code - 63
                    result = result or ((c and 0x1f).toLong() shl shift)
                    shift += 5
                    if (c < 0x20) break
                }
                return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            }
            while (i < encoded.length) {
                a += next(); if (i >= encoded.length) break
                b += next()
                out += a / factor to b / factor
            }
            return out
        }
    }
}
