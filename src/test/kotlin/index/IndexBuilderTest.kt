package index

import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import search.SearchQueryExecutor
import search.SearchResult
import kotlin.test.assertFailsWith


class IndexBuilderTest {
    private val cs = CoroutineScope(Dispatchers.Default)

    private fun buildAndSearch(string: String, directory: String = "SampleDirectory") = runBlocking {
        val indexBuilder = IndexBuilder(directory, cs)
        val indexAsync = cs.async {
            return@async indexBuilder.build()
        }

        val index = indexAsync.await()
        val searchQueryExecutor = SearchQueryExecutor(index)
        return@runBlocking searchQueryExecutor.getFilesAndLinesWith(string)
    }

    @Test
    fun `Search word Oppenheimer, which is present only in 1 file`() {
        val result = buildAndSearch("Oppenheimer")
        val expected = listOf(SearchResult("SampleDirectory/Oppenheimer/oppenheimer.txt", 64))
        assertEquals(expected, result)
    }

    @Test
    fun `Search for word Oppenheimer in an empty directory`() {
        val result = buildAndSearch("Oppenheimer", "SampleDirectory/Subdirectory/EmptyDirectory")
        val expected = listOf<SearchResult>()
        assertEquals(expected, result)
    }

    @Test
    fun `Search for non-present string`() {
        val result = buildAndSearch("non-presentWord").toSet()
        val expected = setOf<SearchResult>()
        assertEquals(expected, result)
    }

    @Test
    fun `Search word first`() {
        val result = buildAndSearch("first").toSet()

        val expected = setOf(
            SearchResult("SampleDirectory/Oppenheimer/oppenheimer.txt", 157),
            SearchResult("SampleDirectory/a.txt", 4),
            SearchResult("SampleDirectory/b.txt", 8)
        )
        assertEquals(expected, result)

    }

    @Test
    fun `Text with Welcome but without first`() {
        val directory = "SampleDirectory"
        runBlocking {
            val cs = CoroutineScope(Dispatchers.Default)
            val indexBuilder = IndexBuilder(directory, cs)
            val indexAsync = cs.async {
                return@async indexBuilder.build()
            }

            val index = indexAsync.await()
            val searchQueryExecutor = SearchQueryExecutor(index)
            val notContainFirst = searchQueryExecutor.getFilesAndLinesWith("first").find { result ->
                result.path == "SampleDirectory/Subdirectory/Subsubdirectory/textWithoutFirst.txt"
            } == null

            val containsWelcome = searchQueryExecutor.getFilesAndLinesWith("Welcome").find { result ->
                result.path == "SampleDirectory/Subdirectory/Subsubdirectory/textWithoutFirst.txt"
            } != null

            assertTrue(notContainFirst && containsWelcome)
        }
    }

    @Test
    fun `When indexing is cancelled build() fails with CancellationException`() {
        assertFailsWith(CancellationException::class) {
            val directory = "SampleDirectory"
            runBlocking {
                val cs = CoroutineScope(Dispatchers.Default)
                val indexBuilder = IndexBuilder(directory, cs)
                val indexAsync = cs.async {
                    return@async indexBuilder.build()
                }
                indexBuilder.cancel()
                indexAsync.await()
            }
        }
    }

    @Test
    fun `Calling getDocuments() with non-three-letter word causes an IllegalArgumentException`() {
        assertFailsWith(IllegalArgumentException::class) {
            val directory = "SampleDirectory"
            runBlocking {
                val cs = CoroutineScope(Dispatchers.Default)
                val indexBuilder = IndexBuilder(directory, cs)
                val indexAsync = cs.async {
                    return@async indexBuilder.build()
                }
                val index = indexAsync.await()
                index.getDocuments("not trigram")
            }
        }


    }

    @Test
    fun `Index non-existing directory causes NoSuchFileException`() {
        assertFailsWith(java.nio.file.NoSuchFileException::class) {
            buildAndSearch("Oppenheimer", "WrongDirectory")
        }
    }

    @Test
    fun `Index file instead of directory where searched word is present`() {
        val result = buildAndSearch("Oppenheimer", "SampleDirectory/Oppenheimer/oppenheimer.txt")
        val expected = listOf(SearchResult("SampleDirectory/Oppenheimer/oppenheimer.txt", 64))
        assertEquals(expected, result)
    }
}