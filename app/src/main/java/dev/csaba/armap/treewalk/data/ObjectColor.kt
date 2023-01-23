package dev.csaba.armap.treewalk.data

enum class ObjectColor {
    RED,
    GREEN,
    BLUE;

    companion object {
        fun getColor(kind: ObjectKind) = when(kind) {
            ObjectKind.GARDEN -> RED
            ObjectKind.TREE -> RED
            ObjectKind.TREES -> RED
            ObjectKind.POST -> GREEN
        }
    }
}
