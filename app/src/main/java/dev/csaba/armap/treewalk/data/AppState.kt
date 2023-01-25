package dev.csaba.armap.treewalk.data

enum class AppState {
    INITIALIZING,
    LOOKING_FOR_CLOSEST_STOP,
    TARGETING_STOP,
    INVOKE_WATERING,
    WATERING_IN_PROGRESS,
    WATERING_FINISHED,
    // NON_AR_ACTIVITY,
}
