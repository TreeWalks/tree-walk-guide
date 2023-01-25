package dev.csaba.armap.treewalk.data

enum class ObjectShape {
    PINE,
    POST;

    companion object {
        fun getShape(kind: ObjectKind) = when(kind) {
            ObjectKind.GARDEN -> PINE
            ObjectKind.TREE -> PINE
            ObjectKind.TREES -> PINE
            ObjectKind.POST -> POST
        }
    }
}
