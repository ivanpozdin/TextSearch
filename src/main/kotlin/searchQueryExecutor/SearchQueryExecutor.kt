package searchQueryExecutor

import indexBuilder.IndexBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.forEachLine
import kotlin.io.path.pathString

data class SearchResult(val path: String, val lineNumber: Int)

class SearchQueryExecutor(private val index: IndexBuilder) {

    fun getFilesAndLinesWith(string: String): List<SearchResult> = runBlocking(Dispatchers.Default) {
        require(string.length >= 3)

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
        var lineNumber = 0

        path.forEachLine { line ->
            lineNumber++
            if (line.contains(string)) {
                return SearchResult(path.pathString, lineNumber)
            }
        }
        return null
    }

    private fun getTrigrams(string: String): List<String> {
        val trigrams = mutableListOf<String>()
        for (i in 3..string.length) {
            trigrams.add(string.substring(i - 3, i))
        }
        return trigrams
    }

    private fun intersect(sets: List<Set<Path>>): Set<Path> {
        if (sets.isEmpty()) throw IndexOutOfBoundsException("lists must have at list 1 list in it.")
        if (sets.size == 1) return sets[0]

        return sets.reduce { intersection, set ->
            intersection.intersect(set)
        }
    }
}