package dev.csaba.armap.treewalk.helpers

// Thanks zsmb
// https://stackoverflow.com/questions/50078266/zip-3-lists-of-equal-length

/**
 * Returns a list of lists, each built from elements of all lists with the same indexes.
 * Output has length of shortest input list.
 */
inline fun <T> zipMulti(vararg lists: List<T>): List<List<T>> {
    return zipMulti(*lists, transform = { it })
}

/**
 * Returns a list of values built from elements of all lists with same indexes using provided [transform].
 * Output has length of shortest input list.
 */
inline fun <T, V> zipMulti(vararg lists: List<T>, transform: (List<T>) -> V): List<V> {
    val minSize = lists.map(List<T>::size).min() ?: return emptyList()
    val list = ArrayList<V>(minSize)

    val iterators = lists.map { it.iterator() }
    var i = 0
    while (i < minSize) {
        list.add(transform(iterators.map { it.next() }))
        i++
    }

    return list
}
