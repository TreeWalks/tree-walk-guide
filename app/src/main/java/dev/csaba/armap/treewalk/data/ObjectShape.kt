package dev.csaba.armap.treewalk.data

enum class ObjectShape {
    MAP_PIN,
    DOWN_ARROW;

    companion object {
        fun getShape(kind: ObjectKind) = when(kind) {
            ObjectKind.GARDEN -> MAP_PIN
            ObjectKind.TREE -> MAP_PIN
            ObjectKind.TREES -> MAP_PIN
            ObjectKind.POST -> DOWN_ARROW
        }
    }
}
