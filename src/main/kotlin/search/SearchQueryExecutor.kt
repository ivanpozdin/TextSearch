package search

import index.Index
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.forEachLine
import kotlin.io.path.pathString

data class SearchResult(val path: String, val lineNumber: Int)

class SearchQueryExecutor(private val index: Index) {
    companion object {
        const val TRIGRAM_LENGTH = 3
    }

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
