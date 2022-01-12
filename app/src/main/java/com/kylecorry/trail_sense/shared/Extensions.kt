package com.kylecorry.trail_sense.shared

import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.system.GeoUri
import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.andromeda.location.IGPS
import com.kylecorry.sol.math.SolMath.roundPlaces
import com.kylecorry.sol.math.Vector2
import com.kylecorry.trail_sense.MainActivity
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.navigation.beacons.domain.Beacon
import com.kylecorry.trail_sense.navigation.paths.domain.PathPoint
import com.kylecorry.trail_sense.shared.database.Identifiable
import java.time.Duration
import java.time.ZonedDateTime

fun Fragment.requireMainActivity(): MainActivity {
    return requireActivity() as MainActivity
}

fun Fragment.requireBottomNavigation(): BottomNavigationView {
    return requireActivity().findViewById(R.id.bottom_navigation)
}

fun IGPS.getPathPoint(pathId: Long): PathPoint {
    return PathPoint(
        -1,
        pathId,
        location,
        altitude,
        time
    )
}

fun PixelCoordinate.toVector2(): Vector2 {
    return Vector2(x, y)
}

fun Vector2.toPixel(): PixelCoordinate {
    return PixelCoordinate(x, y)
}

fun <T : Identifiable> Array<T>.withId(id: Long): T? {
    return firstOrNull { it.id == id }
}

fun <T : Identifiable> Collection<T>.withId(id: Long): T? {
    return firstOrNull { it.id == id }
}

fun Fragment.alertNoCameraPermission() {
    Alerts.toast(
        requireContext(),
        getString(R.string.camera_permission_denied),
        short = false
    )
}

fun SeekBar.setOnProgressChangeListener(listener: (progress: Int, fromUser: Boolean) -> Unit) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            listener(progress, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
        }

    })
}

fun GeoUri.Companion.from(beacon: Beacon): GeoUri {
    val params = mutableMapOf(
        "label" to beacon.name
    )
    if (beacon.elevation != null) {
        params["ele"] = beacon.elevation.roundPlaces(2).toString()
    }

    return GeoUri(beacon.coordinate, null, params)
}

fun hoursBetween(first: ZonedDateTime, second: ZonedDateTime): Float {
    return Duration.between(first, second).seconds / 3600f
}

// TODO: Move this into sol
fun findExtrema(start: Float, end: Float, increment: Float, fn: (x: Float) -> Float): List<Extrema> {
    val extrema = mutableListOf<Extrema>()
    var previous = start - increment
    var x = start
    var next = fn(x)
    while (x <= end){
        val y = next
        next = fn(x + increment)
        val isHigh = previous < y && next < y
        val isLow = previous > y && next > y

        if (isHigh) {
            extrema.add(Extrema(Vector2(x, y), true))
        }

        if (isLow) {
            extrema.add(Extrema(Vector2(x, y), false))
        }

        previous = y
        x += increment
    }
    return extrema
}


data class Extrema(val point: Vector2, val isHigh: Boolean)