package com.kylecorry.trail_sense.weather.infrastructure

import com.kylecorry.trail_sense.shared.QuickActionType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

interface IWeatherPreferences {
    val hasBarometer: Boolean
    var shouldMonitorWeather: Boolean
    val pressureSmoothing: Float
    var weatherUpdateFrequency: Duration
    val shouldShowDailyWeatherNotification: Boolean
    val shouldShowPressureInNotification: Boolean
    val shouldShowTemperatureInNotification: Boolean
    val useSeaLevelPressure: Boolean
    val seaLevelFactorInTemp: Boolean
    val pressureHistory: Duration
    val sendStormAlerts: Boolean
    val dailyForecastChangeThreshold: Float
    val hourlyForecastChangeThreshold: Float
    val stormAlertThreshold: Float
    var dailyWeatherLastSent: LocalDate
    val dailyWeatherIsForTomorrow: Boolean
    var dailyForecastTime: LocalTime
    val leftButton: QuickActionType
    val rightButton: QuickActionType
    val showColoredNotificationIcon: Boolean
}