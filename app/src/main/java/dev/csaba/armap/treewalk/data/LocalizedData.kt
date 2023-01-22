package dev.csaba.armap.treewalk.data

data class LocalizedData(
    val title: String,
    val description: String,
    val content: String,
    val url: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LocalizedData

        if (title != other.title) return false
        if (description != other.description) return false
        if (content != other.content) return false
        if (url != other.url) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + url.hashCode()
        return result
    }
}
