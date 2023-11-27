package searchQueryExecutor

import indexBuilder.IndexBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File

data class SearchResult(val path: String, val lineNumber: Int)

class SearchQueryExecutor(private val index: IndexBuilder) {

    fun getFilesAndLinesWith(string: String): List<SearchResult>? = runBlocking(Dispatchers.Default) {
        if (string.length < 3) throw Error("Only strings of length >= 3 are allowed.")

        val trigrams = getTrigrams(string)
        val trigramsDocs = trigrams.map { trigram -> index.getDocuments(trigram) ?: emptySet() }

        val documentsIntersection = intersect(trigramsDocs)
        if (documentsIntersection.isEmpty()) return@runBlocking null

        val paths = documentsIntersection.map { docId -> index.getDocPath(docId)!! }

        val searches = paths.map { path ->
            async {
                getSearchResult(path, string)
            }
        }
        return@runBlocking searches.mapNotNull { it.await() }
    }

    private fun getSearchResult(path: String, string: String): SearchResult? {
        File(path).readLines().forEachIndexed { index, line ->
            if (line.contains(string)) return SearchResult(path, index + 1)
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

    private fun intersect(sets: List<Set<Int>>): Set<Int> {
        if (sets.isEmpty()) throw IndexOutOfBoundsException("lists must have at list 1 list in it.")
        if (sets.size == 1) return sets[0]

        return sets.reduce { intersection, set ->
            intersection.intersect(set)
        }
    }
}