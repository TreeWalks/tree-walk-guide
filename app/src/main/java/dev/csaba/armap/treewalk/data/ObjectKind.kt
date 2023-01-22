package dev.csaba.armap.treewalk.data

import java.util.*

enum class ObjectKind {
    GARDEN,
    TREE,
    TREES,
    POST,
    ARROW,
    WATERING_CAN;

    companion object {
        fun getByName(name: String) = valueOf(name.uppercase(Locale.getDefault()))
    }
}


