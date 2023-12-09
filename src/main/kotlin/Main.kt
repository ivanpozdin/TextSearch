import index.IndexBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import search.SearchQueryExecutor
import kotlin.io.path.Path

fun main() {
    val directory = "/Users/ivanpozdin/Desktop"
    runBlocking(Dispatchers.Default) {
        val indexBuilder = IndexBuilder(directory, cs = this)
        val index = indexBuilder.build()
        val searchQueryExecutor = SearchQueryExecutor(index)
        searchQueryExecutor.getFilesAndLinesWith("Ivan").forEach {
            println("${it.lineNumber} ${Path(it.path).fileName}")
        }
    }
}
