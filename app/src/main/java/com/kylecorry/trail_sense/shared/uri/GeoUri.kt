package com.kylecorry.trail_sense.shared.uri

import android.net.Uri
import android.os.Parcelable
import com.kylecorry.sol.math.SolMath.roundPlaces
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.navigation.beacons.domain.Beacon
import kotlinx.parcelize.Parcelize

@Parcelize
data class GeoUri(
    val coordinate: Coordinate,
    val altitude: Float? = null,
    val queryParameters: Map<String, String> = mapOf()
) : Parcelable {

    val uri: Uri = Uri.parse(toString())

    override fun toString(): String {
        val base = "geo:${coordinate.latitude.roundPlaces(6)},${coordinate.longitude.roundPlaces(6)}"
        val elevation = if (altitude != null) ",${altitude.roundPlaces(2)}" else ""
        val query = if (queryParameters.isEmpty()) {
            ""
        } else {
            "?" + queryParameters.entries.joinToString("&") {
                Uri.encode(it.key) + "=" + Uri.encode(
                    it.value
                )
            }
        }

        return base + elevation + query
    }

    companion object {

        fun from(uri: Uri): GeoUri? {
            return parse(uri.toString())
        }

        fun from(beacon: Beacon): GeoUri {
            val params = mutableMapOf(
                "label" to beacon.name
            )
            if (beacon.elevation != null) {
                params["ele"] = beacon.elevation.roundPlaces(2).toString()
            }

            return GeoUri(beacon.coordinate, null, params)
        }

        fun parse(uriString: String): GeoUri? {
            val pattern =
                "geo:(-?[0-9]*\\.?[0-9]+),(-?[0-9]*\\.?[0-9]+)(?:,(-?[0-9]*\\.?[0-9]+))?(?:\\?(.*))?"
            val regex = Regex(pattern)

            val matches = regex.find(uriString)

            if (matches != null) {
                val lat = matches.groupValues[1].toDouble()
                val lng = matches.groupValues[2].toDouble()
                val altitude = matches.groupValues[3].toFloatOrNull()
                val query =
                    matches.groupValues[4].split("&").map { it.split("=").map { Uri.decode(it) } }

                val pairs = query.mapNotNull {
                    if (it.size == 2) {
                        it[0] to it[1]
                    } else {
                        null
                    }
                }

                return GeoUri(Coordinate(lat, lng), altitude, pairs.toMap())
            }

            return null
        }
    }

}