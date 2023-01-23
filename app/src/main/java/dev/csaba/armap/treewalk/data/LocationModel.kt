package dev.csaba.armap.treewalk.data

import com.google.android.gms.maps.model.LatLng
import dev.csaba.armap.treewalk.helpers.haversineInKm

data class LocationModel(
    val gpsLocation: LatLng,
    val kind: ObjectKind,
    var distance: Double = 0.0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocationModel

        if (gpsLocation != other.gpsLocation) return false
        if (kind != other.kind) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gpsLocation.hashCode()
        result = 31 * result + kind.hashCode()
        return result
    }

    fun distanceFrom(lat: Double, lon: Double) {
        distance = haversineInKm(gpsLocation.latitude, gpsLocation.longitude, lat, lon)
    }
}
