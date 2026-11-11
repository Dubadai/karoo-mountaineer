package com.daviddokakis.mountaineer

import com.dubadai.karoo.common.*
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Mountaineer settings, from the Karoo's app list, built from the shared settings kit (SettingsKit.kt;
 * karoo-starter/docs/design/settings-kit): what one tile and two tiles show, which lines the half
 * screen shows, time to top (bike weight, wind, improve over time), Strava, then the app rows.
 * The Example shows the size of the setting last changed. Design: docs/design/tiny-climb.
 */
class SettingsActivity : Activity() {

    private lateinit var form: LinearLayout
    private lateinit var example: ExampleCard
    private var exampleSize = Size.ONE

    private enum class Size(val w: Int, val h: Int, val label: Int) {
        ONE(238, 168, R.string.example_one), TWO(478, 168, R.string.example_two), HALF(478, 348, R.string.example_half)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }
        example = ExampleCard(this)
        setContentView(
            SettingsLayout(
                this,
                back = settingsBack(getString(R.string.back)) { finish() },
                barTitle = getString(R.string.settings),
                title = settingsTitle(getString(R.string.app_short_name)),
                card = example,
                form = form,
                pageColor = getColor(R.color.settings_bg),
                textColor = getColor(R.color.settings_title),
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun showExample(prefs: Prefs) {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val sz = exampleSize
        val full = ClimbRenderer(this).render(EXAMPLE_STATE, prefs, sz.w, sz.h, night)
        val h = dp(if (sz == Size.HALF) 150 else 84)
        example.show(Bitmap.createScaledBitmap(full, h * sz.w / sz.h, h, true), getString(sz.label))
    }

    private fun render() {
        val prefs = Settings.read(this)
        showExample(prefs)
        form.removeAllViews()

        // What the field shows
        section(R.drawable.ic_settings_one_tile, R.string.setting_one, R.string.setting_one_help,
            listOf(R.string.show_eta to OneTile.ETA, R.string.show_dist to OneTile.DIST, R.string.show_gap to OneTile.GAP),
            prefs.oneTile) { exampleSize = Size.ONE; Settings.put(this, "one_tile", it) }

        section(R.drawable.ic_settings_tiles, R.string.setting_two, R.string.setting_two_help,
            listOf(R.string.show_eta_short to TwoTiles.ETA, R.string.show_dist_short to TwoTiles.DIST,
                R.string.show_gap to TwoTiles.GAP, R.string.show_both to TwoTiles.BOTH),
            prefs.twoTiles) { exampleSize = Size.TWO; Settings.put(this, "two_tiles", it) }

        form.addView(settingsHeading(R.drawable.ic_settings_top_row, getString(R.string.setting_half)))
        halfSwitch(R.drawable.ic_settings_climber, R.string.half_name, prefs.halfName, "half_name")
        halfSwitch(R.drawable.ic_settings_pr, R.string.half_pr, prefs.halfPr, "half_pr")
        halfSwitch(R.drawable.ic_settings_timer, R.string.half_gap, prefs.halfGap, "half_gap")
        halfSwitch(R.drawable.ic_settings_clock, R.string.half_eta, prefs.halfEta, "half_eta")
        form.addView(settingsHelp(getString(R.string.setting_half_help)))
        form.addView(settingsDivider())

        // Data
        timeToTop(prefs)

        // Accounts
        form.addView(settingsHeading(R.drawable.ic_settings_pr, getString(R.string.strava_title)))
        form.addView(settingsInfoRow(R.drawable.ic_settings_info, getString(R.string.strava_help)))

        settingsAppRows(form, repo = "karoo-mountaineer") { render() }
    }

    private fun <T> section(icon: Int, title: Int, helpText: Int, options: List<Pair<Int, T>>, selected: T, onPick: (T) -> Unit) {
        form.addView(settingsHeading(icon, getString(title)))
        form.addView(settingsSegmented(options.map { (label, v) -> getString(label) to v }, selected) { onPick(it); render() })
        form.addView(settingsHelp(getString(helpText)))
        form.addView(settingsDivider())
    }

    /** One of the half screen's lines, on or off; the Example shows the half screen. */
    private fun halfSwitch(icon: Int, title: Int, on: Boolean, key: String) {
        form.addView(settingsSwitchRow(icon, getString(title), on) { exampleSize = Size.HALF; Settings.put(this, key, it); render() })
    }

    /** Time to top (round 47): bike weight (one for all or per ride profile), wind, improve over time. */
    private fun timeToTop(prefs: Prefs) {
        form.addView(settingsHeading(R.drawable.ic_settings_speed, getString(R.string.setting_time)))
        form.addView(settingsSubLabel(getString(R.string.setting_bike)))
        form.addView(settingsSegmented(listOf(getString(R.string.bike_one) to false, getString(R.string.bike_profiles) to true), prefs.bikePerProfile) {
            Settings.put(this, "bike_per_profile", it); render()
        })
        val base = prefs.bikeKg ?: Physics.BIKE_KG
        if (!prefs.bikePerProfile) {
            weightRow(getString(R.string.bike_all), prefs.bikeKg, Physics.BIKE_KG) { Settings.setBikeKg(this, it) }
            form.addView(settingsHelp(getString(R.string.bike_one_help)))
        } else if (prefs.profiles.isEmpty()) {
            form.addView(settingsHelp(getString(R.string.bike_profiles_none)))
        } else {
            prefs.profiles.forEach { pr -> weightRow(pr.name, pr.kg, base) { Settings.setProfileKg(this, pr.id, it) } }
            form.addView(settingsHelp(getString(R.string.bike_profiles_help)))
        }
        form.addView(settingsSwitchRow(R.drawable.ic_settings_wind, getString(R.string.setting_wind), prefs.wind) { Settings.put(this, "wind", it); render() })
        form.addView(settingsHelp(getString(R.string.setting_wind_help)))
        form.addView(settingsSwitchRow(R.drawable.ic_settings_target, getString(R.string.setting_improve), prefs.improve) { Settings.put(this, "improve", it); render() })
        form.addView(settingsHelp(getString(R.string.setting_improve_help)).apply { setPadding(0, dp(10), 0, 0) })
        form.addView(settingsInfoRow(R.drawable.ic_settings_lock, getString(R.string.privacy)))
        form.addView(settingsDivider())
    }

    /**
     * A bike weight (the kit's number row): name, a short field with the [standard] in grey, kg.
     * Typing saves it (blank: back to the standard); under 6.8 kg the kit's OK note cheers.
     */
    private fun weightRow(name: String, kg: Double?, standard: Double, onSave: (Double?) -> Unit) {
        val note = settingsInfoRow(R.drawable.ic_feather, getString(R.string.weenie), SettingsTone.OK).apply {
            visibility = if (kg != null && kg < WEENIE_KG) View.VISIBLE else View.GONE
        }
        form.addView(
            settingsNumberRow(
                R.drawable.ic_settings_ascent, name,
                value = kg?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "",
                unit = getString(R.string.kg),
                hint = String.format(java.util.Locale.US, "%.1f", standard),
            ) { typed ->
                val t = typed.trim().replace(',', '.')
                val v = t.toDoubleOrNull()
                when {
                    t.isEmpty() -> { onSave(null); note.visibility = View.GONE }
                    v != null && v in MIN_KG..MAX_KG -> { onSave(v); note.visibility = if (v < WEENIE_KG) View.VISIBLE else View.GONE }
                }
            },
        )
        form.addView(note)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        val EXAMPLE_STATE get() = ExampleClimb.state
        /** Under this (the UCI minimum, kg): "Oh nice, a weight weenie build!" */
        const val WEENIE_KG = 6.8
        const val MIN_KG = 2.0
        const val MAX_KG = 40.0
    }
}
