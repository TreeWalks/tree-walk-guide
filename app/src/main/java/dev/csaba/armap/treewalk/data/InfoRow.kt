package dev.csaba.armap.treewalk.data

data class InfoRow(
    val label: String,
    val value: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InfoRow

        if (label != other.label) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}
