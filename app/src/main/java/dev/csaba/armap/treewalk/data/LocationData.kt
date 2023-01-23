package dev.csaba.armap.treewalk.data

import com.google.android.gms.maps.model.LatLng

data class LocationData(
    val gpsLocation: LatLng,
    val geoFenceNW: LatLng,
    val geoFenceSE: LatLng,
    val kind: ObjectKind,
    val height: String,
    val width: String,
    val scientificName: String,
    val englishData: LocalizedData,
    val spanishData: LocalizedData,
    val locationModel: LocationModel,
    val nwGeoFenceModel: LocationModel,
    val neGeoFenceModel: LocationModel,
    val seGeoFenceModel: LocationModel,
    val swGeoFenceModel: LocationModel,
    var visited: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocationData

        if (gpsLocation != other.gpsLocation) return false
        if (geoFenceNW != other.geoFenceNW) return false
        if (geoFenceSE != other.geoFenceSE) return false
        if (kind != other.kind) return false
        if (height != other.height) return false
        if (width != other.width) return false
        if (englishData != other.englishData) return false
        if (spanishData != other.spanishData) return false
        if (locationModel != other.locationModel) return false
        if (nwGeoFenceModel != other.nwGeoFenceModel) return false
        if (neGeoFenceModel != other.neGeoFenceModel) return false
        if (seGeoFenceModel != other.seGeoFenceModel) return false
        if (swGeoFenceModel != other.swGeoFenceModel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gpsLocation.hashCode()
        result = 31 * result + geoFenceNW.hashCode()
        result = 31 * result + geoFenceSE.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + englishData.hashCode()
        result = 31 * result + spanishData.hashCode()
        result = 31 * result + locationModel.hashCode()
        result = 31 * result + nwGeoFenceModel.hashCode()
        result = 31 * result + neGeoFenceModel.hashCode()
        result = 31 * result + seGeoFenceModel.hashCode()
        result = 31 * result + swGeoFenceModel.hashCode()
        return result
    }

    fun getLocalizedTitle(language: String): String {
        return when (language) {
            "es" -> spanishData.title
            else -> englishData.title
        }
    }
}
