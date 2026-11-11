package com.daviddokakis.mountaineer

import android.content.Context
import android.content.SharedPreferences

enum class OneTile { ETA, DIST, GAP }
enum class TwoTiles { ETA, DIST, GAP, BOTH }
enum class PrSymbol { MEDAL, BADGE }

data class Prefs(
    val oneTile: OneTile = OneTile.ETA,
    val twoTiles: TwoTiles = TwoTiles.BOTH,
    val halfName: Boolean = true,
    val halfPr: Boolean = true,
    val halfGap: Boolean = true,
    val halfEta: Boolean = true,
    val symbol: PrSymbol = PrSymbol.MEDAL,
    /** One-for-all bike weight with bottles and bags, kg; null: the standard [Physics.BIKE_KG]. */
    val bikeKg: Double? = null,
    /** A weight per ride profile instead of one for all (Settings › Time to top). */
    val bikePerProfile: Boolean = false,
    /** The ride profiles used on this Karoo, each with its own bike weight (null: the one for all). */
    val profiles: List<BikeProfile> = emptyList(),
    /** Use the wind forecast for the time to top. */
    val wind: Boolean = true,
    /** Improve over time: log climbs on the Karoo and carry what they taught into the next. */
    val improve: Boolean = false,
) {
    /** The bike weight for the ride profile [profileId] (null: unknown). */
    fun bikeKgFor(profileId: String?): Double {
        val base = bikeKg ?: Physics.BIKE_KG
        if (!bikePerProfile) return base
        return profiles.firstOrNull { it.id == profileId }?.kg ?: base
    }
}

/** A Karoo ride profile seen on this Karoo, with the bike weight the rider gave it. */
data class BikeProfile(val id: String, val name: String, val kg: Double?)

/** The rider's choices, stored on the Karoo (app-private). Written by the settings screen. */
object Settings {
    private const val FILE = "mountaineer"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(context: Context): Prefs {
        val p = prefs(context)
        val d = Prefs()
        return Prefs(
            oneTile = enumOr(p.getString("one_tile", null), d.oneTile),
            twoTiles = enumOr(p.getString("two_tiles", null), d.twoTiles),
            halfName = p.getBoolean("half_name", d.halfName),
            halfPr = p.getBoolean("half_pr", d.halfPr),
            halfGap = p.getBoolean("half_gap", d.halfGap),
            halfEta = p.getBoolean("half_eta", d.halfEta),
            symbol = enumOr(p.getString("pr_symbol", null), d.symbol),
            bikeKg = if (p.contains("bike_kg")) p.getFloat("bike_kg", 0f).toDouble() else null,
            bikePerProfile = p.getBoolean("bike_per_profile", d.bikePerProfile),
            profiles = readProfiles(p.getString("profiles", null)),
            wind = p.getBoolean("wind", d.wind),
            improve = p.getBoolean("improve", d.improve),
        )
    }

    fun put(context: Context, key: String, v: Enum<*>) = prefs(context).edit().putString(key, v.name).apply()
    fun put(context: Context, key: String, v: Boolean) = prefs(context).edit().putBoolean(key, v).apply()

    /** The one-for-all bike weight; null: back to the standard. */
    fun setBikeKg(context: Context, kg: Double?) = prefs(context).edit().apply {
        if (kg == null) remove("bike_kg") else putFloat("bike_kg", kg.toFloat())
    }.apply()

    /** A ride profile's bike weight; null: it uses the one for all. */
    fun setProfileKg(context: Context, id: String, kg: Double?) {
        val list = read(context).profiles.map { if (it.id == id) it.copy(kg = kg) else it }
        prefs(context).edit().putString("profiles", writeProfiles(list)).apply()
    }

    /** Improve over time: how much faster than the model this ride profile climbs (1.0: not learned yet). */
    fun learned(context: Context, profileId: String?): Double =
        prefs(context).getFloat("learned_" + (profileId ?: "default"), 1f).toDouble()

    fun setLearned(context: Context, profileId: String?, factor: Double) =
        prefs(context).edit().putFloat("learned_" + (profileId ?: "default"), factor.toFloat()).apply()

    /** Remember a ride profile the rider picked on the Karoo (new ones are added, names kept current). */
    fun recordProfile(context: Context, id: String, name: String) {
        val list = read(context).profiles
        val known = list.firstOrNull { it.id == id }
        if (known != null && known.name == name) return
        val next = if (known == null) list + BikeProfile(id, name, null) else list.map { if (it.id == id) it.copy(name = name) else it }
        prefs(context).edit().putString("profiles", writeProfiles(next)).apply()
    }

    private fun readProfiles(json: String?): List<BikeProfile> = try {
        val a = org.json.JSONArray(json ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            BikeProfile(o.getString("id"), o.getString("name"), if (o.has("kg")) o.getDouble("kg") else null)
        }
    } catch (e: Exception) { emptyList() }

    private fun writeProfiles(list: List<BikeProfile>): String = org.json.JSONArray().apply {
        list.forEach { b -> put(org.json.JSONObject().put("id", b.id).put("name", b.name).apply { b.kg?.let { put("kg", it) } }) }
    }.toString()

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
