package dev.csaba.armap.treewalk.data

enum class ObjectShape {
    MAP_PIN,
    DOWN_ARROW,
    ARROW,
    WATERING_CAN;

    companion object {
        fun getShape(kind: ObjectKind) = when(kind) {
            ObjectKind.GARDEN -> MAP_PIN
            ObjectKind.TREE -> MAP_PIN
            ObjectKind.TREES -> MAP_PIN
            ObjectKind.POST -> DOWN_ARROW
            ObjectKind.ARROW -> ARROW
            ObjectKind.WATERING_CAN -> WATERING_CAN
        }
    }
}
