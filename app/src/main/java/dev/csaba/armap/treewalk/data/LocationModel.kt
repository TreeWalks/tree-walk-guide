package dev.csaba.armap.treewalk.data

import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Anchor
import com.google.ar.core.Earth
import dev.csaba.armap.treewalk.helpers.haversineInKm

data class LocationModel(
    val gpsLocation: LatLng,
    val kind: ObjectKind,
    val visible: Boolean = false,
    val modelMatrix: FloatArray = FloatArray(16),
    val modelViewMatrix: FloatArray = FloatArray(16),
    val modelViewProjectionMatrix: FloatArray = FloatArray(16), // projection x view x model
    var anchor: Anchor? = null,
    var distance: Double = 0.0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocationModel

        if (gpsLocation != other.gpsLocation) return false
        if (kind != other.kind) return false
        if (!modelMatrix.contentEquals(other.modelMatrix)) return false
        if (!modelViewMatrix.contentEquals(other.modelViewMatrix)) return false
        if (!modelViewProjectionMatrix.contentEquals(other.modelViewProjectionMatrix)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gpsLocation.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + modelMatrix.contentHashCode()
        result = 31 * result + modelViewMatrix.contentHashCode()
        result = 31 * result + modelViewProjectionMatrix.contentHashCode()
        return result
    }

    fun distanceFrom(lat: Double, lon: Double) {
        distance = haversineInKm(gpsLocation.latitude, gpsLocation.longitude, lat, lon)
    }

    fun addAnchor(earth: Earth, hoverHeight: Double) {
        anchor?.detach()

        // The rotation quaternion of the anchor in EUS coordinates.
        val qx = 0f
        val qy = 0f
        val qz = 0f
        val qw = 1f
        anchor = earth.resolveAnchorOnTerrain(
            gpsLocation.latitude,
            gpsLocation.longitude,
            hoverHeight,
            qx, qy, qz, qw
        )
    }
}
