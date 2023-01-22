package dev.csaba.armap.treewalk.helpers

private fun cleanseString(inString: String): String {
    var outString = inString.trim()
    if (outString.startsWith("<p>") && outString.endsWith("</p>")) {
        outString = outString.substring(3, outString.length - 4)
    }

    return outString.trim()
}

/**
 * Processes pipe separated strings coming from the companion website
 */
fun splitAndCleanse(unSplitString: String): List<String> {
    return unSplitString.split("|").map { cleanseString(it) }
}
