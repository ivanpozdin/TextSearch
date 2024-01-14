package search

import index.Index
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.forEachLine
import kotlin.io.path.pathString

/**
 * Represent one search result of a SearchQueryExecutor: a path where a string was found and a line number in this file.
 *
 * @param path represents path to file where some searched string is present.
 * @param lineNumber represents a line number in the file above where some searched string is present.
 */
data class SearchResult(val path: String, val lineNumber: Int)

/**
 * Can search for words of length >= 3 in the directory by providing an Index instance.
 *
 * @param index is an instance of Index class for searching trigrams in directory.
 */
class SearchQueryExecutor(private val index: Index) {
    companion object {
        private const val TRIGRAM_LENGTH = 3
    }

    /**
     * Find a list of files with line numbers where a given string occurs.
     *
     * @param string, occurrences of which are needed to be found.
     * @return a list of SearchResult's with a file path and a line number of the string occurrence.
     */
    fun getFilesAndLinesWith(string: String): List<SearchResult> = runBlocking(Dispatchers.Default) {
        require(string.length >= TRIGRAM_LENGTH)

        val trigrams = getTrigrams(string)

        val trigramsDocs = trigrams.map { trigram -> index.getDocuments(trigram) ?: emptySet() }

        val documentsIntersection = intersect(trigramsDocs)
        if (documentsIntersection.isEmpty()) return@runBlocking listOf()

        val paths = documentsIntersection.map { path -> path }

        val searches = paths.map { path ->
            async {
                searchStringInFile(path, string)
            }
        }
        return@runBlocking searches.mapNotNull { it.await() }
    }

    private fun searchStringInFile(path: Path, string: String): SearchResult? {
        try {
            var lineNumber = 0

            path.forEachLine { line ->
                lineNumber++
                if (line.contains(string)) {
                    return SearchResult(path.pathString, lineNumber)
                }
            }
        } catch (_: java.nio.charset.MalformedInputException) {
            return null
        }
        return null
    }

    private fun getTrigrams(string: String): List<String> {
        val trigrams = mutableListOf<String>()
        for (i in TRIGRAM_LENGTH..string.length) {
            trigrams.add(string.substring(i - TRIGRAM_LENGTH, i))
        }
        return trigrams
    }

    private fun intersect(sets: List<Set<Path>>): Set<Path> {
        if (sets.isEmpty()) throw IndexOutOfBoundsException("lists must have at list 1 list in it.")
        if (sets.size == 1) return sets.first()

        return sets.reduce { intersection, set ->
            intersection.intersect(set)
        }
    }
}
