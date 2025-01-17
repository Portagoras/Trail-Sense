package com.kylecorry.trail_sense.settings.ui

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import com.kylecorry.andromeda.core.topics.generic.asLiveData
import com.kylecorry.andromeda.fragments.AndromedaPreferenceFragment
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.astronomy.infrastructure.receivers.SunsetAlarmReceiver
import com.kylecorry.trail_sense.shared.QuickActionUtils
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem

class AstronomySettingsFragment : AndromedaPreferenceFragment() {

    private lateinit var prefs: UserPreferences
    private var prefleftButton: ListPreference? = null
    private var prefrightButton: ListPreference? = null


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.astronomy_preferences, rootKey)
        val userPrefs = UserPreferences(requireContext())
        prefs = userPrefs

        prefleftButton = list(R.string.pref_astronomy_quick_action_left)
        prefrightButton = list(R.string.pref_astronomy_quick_action_right)

        val actions = QuickActionUtils.astronomy(requireContext())
        val actionNames = actions.map { QuickActionUtils.getName(requireContext(), it) }
        val actionValues = actions.map { it.id.toString() }

        prefleftButton?.entries = actionNames.toTypedArray()
        prefrightButton?.entries = actionNames.toTypedArray()

        prefleftButton?.entryValues = actionValues.toTypedArray()
        prefrightButton?.entryValues = actionValues.toTypedArray()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val alertTimePrefKey = getString(R.string.pref_sunset_alert_time)
        val alertPrefKey = getString(R.string.pref_sunset_alerts)

        PreferencesSubsystem.getInstance(requireContext()).preferences.onChange.asLiveData()
            .observe(viewLifecycleOwner) {
                if (it == alertTimePrefKey){
                    restartSunsetAlerts(false)
                } else if (it == alertPrefKey){
                    restartSunsetAlerts(true)
                }
            }
    }

    private fun restartSunsetAlerts(shouldRequestPermissions: Boolean){
        if (!prefs.astronomy.sendSunsetAlerts) {
            return
        }

        SunsetAlarmReceiver.enable(this, shouldRequestPermissions)
    }

}