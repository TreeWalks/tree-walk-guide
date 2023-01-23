package dev.csaba.armap.treewalk.data

import java.util.*

enum class ObjectKind {
    GARDEN,
    TREE,
    TREES,
    POST;

    companion object {
        fun getByName(name: String) = valueOf(name.uppercase(Locale.getDefault()))
    }
}


