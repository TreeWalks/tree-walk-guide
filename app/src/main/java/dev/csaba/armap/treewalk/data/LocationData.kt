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
    val scientificAlternates: String,
    val englishData: LocalizedData,
    val spanishData: LocalizedData,
    val locationModel: LocationModel,
    val nwGeoFenceModel: LocationModel,
    val neGeoFenceModel: LocationModel,
    val seGeoFenceModel: LocationModel,
    val swGeoFenceModel: LocationModel,
    var phylum: String,
    var evergreen: Boolean,
    var climateAction: Boolean,
    var pollution: Boolean,
    var traffic: Boolean,
    var droughtTolerant: Boolean,
    var californiaNative: Boolean,
    var visited: Boolean = false,
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
        if (scientificName != other.scientificName) return false
        if (scientificAlternates != other.scientificAlternates) return false
        if (englishData != other.englishData) return false
        if (spanishData != other.spanishData) return false
        if (locationModel != other.locationModel) return false
        if (nwGeoFenceModel != other.nwGeoFenceModel) return false
        if (neGeoFenceModel != other.neGeoFenceModel) return false
        if (seGeoFenceModel != other.seGeoFenceModel) return false
        if (swGeoFenceModel != other.swGeoFenceModel) return false
        if (phylum != other.phylum) return false
        if (evergreen != other.evergreen) return false
        if (climateAction != other.climateAction) return false
        if (pollution != other.pollution) return false
        if (traffic != other.traffic) return false
        if (droughtTolerant != other.droughtTolerant) return false
        if (californiaNative != other.californiaNative) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gpsLocation.hashCode()
        result = 31 * result + geoFenceNW.hashCode()
        result = 31 * result + geoFenceSE.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + scientificName.hashCode()
        result = 31 * result + scientificAlternates.hashCode()
        result = 31 * result + englishData.hashCode()
        result = 31 * result + spanishData.hashCode()
        result = 31 * result + locationModel.hashCode()
        result = 31 * result + nwGeoFenceModel.hashCode()
        result = 31 * result + neGeoFenceModel.hashCode()
        result = 31 * result + seGeoFenceModel.hashCode()
        result = 31 * result + swGeoFenceModel.hashCode()
        result = 31 * result + phylum.hashCode()
        result = 31 * result + evergreen.hashCode()
        result = 31 * result + climateAction.hashCode()
        result = 31 * result + pollution.hashCode()
        result = 31 * result + traffic.hashCode()
        result = 31 * result + droughtTolerant.hashCode()
        result = 31 * result + californiaNative.hashCode()
        return result
    }

    fun getLocalizedTitle(language: String): String {
        return when (language) {
            "es" -> spanishData.title
            else -> englishData.title
        }
    }

    fun getLocalizedDescription(language: String): String {
        return when (language) {
            "es" -> spanishData.description
            else -> englishData.description
        }
    }

    fun getLocalizedUrl(language: String): String {
        return when (language) {
            "es" -> spanishData.url
            else -> englishData.url
        }
    }

    fun getLocalizedFunFact(language: String): String {
        return when (language) {
            "es" -> spanishData.funFact
            else -> englishData.funFact
        }
    }
}
