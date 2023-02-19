package com.kylecorry.trail_sense.calibration.ui

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.sensors.Quality
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.time.Throttle
import com.kylecorry.andromeda.fragments.AndromedaPreferenceFragment
import com.kylecorry.andromeda.location.IGPS
import com.kylecorry.andromeda.sense.compass.ICompass
import com.kylecorry.sol.science.geology.Geology
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.declination.DeclinationFactory
import com.kylecorry.trail_sense.shared.sensors.SensorService
import com.kylecorry.trail_sense.shared.views.ColorPickerView


class CalibrateCompassFragment : AndromedaPreferenceFragment() {

    private lateinit var prefs: UserPreferences
    private val formatService by lazy { FormatService(requireContext()) }
    private lateinit var sensorService: SensorService
    private val throttle = Throttle(20)

    private lateinit var azimuthTxt: Preference
    private lateinit var legacyCompassSwitch: SwitchPreferenceCompat
    private lateinit var compassSmoothingBar: SeekBarPreference
    private lateinit var declinationTxt: Preference
    private lateinit var trueNorthSwitch: SwitchPreferenceCompat
    private lateinit var autoDeclinationSwitch: SwitchPreferenceCompat
    private lateinit var declinationOverrideEdit: EditTextPreference
    private lateinit var declinationFromGpsBtn: Preference
    private lateinit var calibrateBtn: Preference
    private lateinit var backgroundColor : Preference

    private lateinit var compass: ICompass
    private lateinit var gps: IGPS

    private var prevQuality = Quality.Unknown

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.compass_calibration, rootKey)

        setIconColor(Resources.androidTextColorSecondary(requireContext()))

        prefs = UserPreferences(requireContext())
        sensorService = SensorService(requireContext())

        compass = sensorService.getCompass()
        gps = sensorService.getGPS(false)
        bindPreferences()
    }

    private fun bindPreferences() {
        azimuthTxt = findPreference(getString(R.string.pref_holder_azimuth))!!
        legacyCompassSwitch = findPreference(getString(R.string.pref_use_legacy_compass))!!
        compassSmoothingBar = findPreference(getString(R.string.pref_compass_filter_amt))!!
        declinationTxt = findPreference(getString(R.string.pref_holder_declination))!!
        trueNorthSwitch = findPreference(getString(R.string.pref_use_true_north))!!
        autoDeclinationSwitch = findPreference(getString(R.string.pref_auto_declination))!!
        declinationOverrideEdit = findPreference(getString(R.string.pref_declination_override))!!
        declinationFromGpsBtn =
            findPreference(getString(R.string.pref_declination_override_gps_btn))!!
        calibrateBtn = findPreference(getString(R.string.pref_calibrate_compass_btn))!!
        backgroundColor = findPreference(getString(R.string.pref_compass_background_btn))!!

        declinationOverrideEdit.summary =
            getString(R.string.degree_format, prefs.declinationOverride)
        declinationOverrideEdit.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER.or(InputType.TYPE_NUMBER_FLAG_DECIMAL)
                .or(InputType.TYPE_NUMBER_FLAG_SIGNED)
        }

        trueNorthSwitch.setOnPreferenceClickListener {
            resetCompass()
            true
        }

        autoDeclinationSwitch.setOnPreferenceClickListener {
            update()
            true
        }

        declinationFromGpsBtn.setOnPreferenceClickListener {
            updateDeclinationFromGps()
            true
        }

        legacyCompassSwitch.setOnPreferenceClickListener {
            resetCompass()
            true
        }

        compassSmoothingBar.setOnPreferenceClickListener {
            resetCompass()
            true
        }

        calibrateBtn.setOnPreferenceClickListener {
            Alerts.dialog(
                requireContext(),
                getString(R.string.calibrate_compass_dialog_title),
                getString(
                    R.string.calibrate_compass_dialog_content, getString(android.R.string.ok)
                ),
                contentView = CompassCalibrationView.withFrame(
                    requireContext(),
                    height = Resources.dp(requireContext(), 200f).toInt()
                ),
                cancelText = null,
                cancelOnOutsideTouch = false
            )
            true
        }

        backgroundColor.setOnPreferenceClickListener {
            var colorPickerView: ColorPickerView = ColorPickerView(requireContext(), null)
            colorPickerView.setOnColorChangeListener { color ->
                if (color != null) {
                    prefs.compassBackgroundColor = color.color
                }else
                    prefs.compassBackgroundColor = Resources.color(requireContext(), R.color.compassBackground)
            }
            Alerts.dialog(
                requireContext(),
                "","",
                contentView = colorPickerView,
                cancelText = null,
                cancelOnOutsideTouch = false
            )
            true
        }
    }

    override fun onResume() {
        super.onResume()
        startCompass()
        if (!gps.hasValidReading) {
            gps.start(this::onLocationUpdate)
        }
    }

    override fun onPause() {
        super.onPause()
        stopCompass()
        gps.stop(this::onLocationUpdate)
        gps.stop(this::onUpdateDeclinationFromGpsCallback)
    }

    private fun onLocationUpdate(): Boolean {
        update()
        return false
    }

    private fun updateDeclinationFromGps() {
        if (gps.hasValidReading) {
            onUpdateDeclinationFromGpsCallback()
        } else {
            gps.start(this::onUpdateDeclinationFromGpsCallback)
        }
    }

    private fun getDeclination(): Float {
        return DeclinationFactory().getDeclinationStrategy(prefs, gps).getDeclination()
    }

    private fun onUpdateDeclinationFromGpsCallback(): Boolean {
        val declination = Geology.getGeomagneticDeclination(gps.location, gps.altitude)
        prefs.declinationOverride = declination
        declinationOverrideEdit.text = declination.toString()
        Alerts.toast(requireContext(), getString(R.string.declination_override_updated_toast))
        return false
    }

    private fun resetCompass() {
        stopCompass()
        compass = sensorService.getCompass()
        startCompass()
    }

    private fun startCompass() {
        compass.start(this::onCompassUpdate)
    }

    private fun stopCompass() {
        compass.stop(this::onCompassUpdate)
    }

    private fun onCompassUpdate(): Boolean {
        update()
        return true
    }

    private fun update() {

        if (throttle.isThrottled()) {
            return
        }

        if (prevQuality != Quality.Unknown && prevQuality != compass.quality) {
            if (compass.quality.ordinal > prevQuality.ordinal) {
                Alerts.toast(
                    requireContext(),
                    getString(R.string.compass_accuracy_improved, getCompassAccuracy())
                )
            }
            prevQuality = compass.quality
        }

        compass.declination = getDeclination()

        calibrateBtn.summary = getString(R.string.compass_reported_accuracy, getCompassAccuracy())
        azimuthTxt.summary = getString(R.string.degree_format, compass.bearing.value)
        declinationTxt.summary = getString(R.string.degree_format, compass.declination)
        declinationOverrideEdit.summary =
            getString(R.string.degree_format, prefs.declinationOverride)
    }


    private fun getCompassAccuracy(): String {
        return formatService.formatQuality(compass.quality)
    }


}