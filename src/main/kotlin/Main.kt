import index.IndexBuilder
import kotlinx.coroutines.*
import search.SearchQueryExecutor
import kotlin.io.path.Path

fun main() {
    val directory = "/Users/ivanpozdin/Desktop"
    runBlocking {
        val cs = CoroutineScope(Dispatchers.Default)
        val indexBuilder = IndexBuilder(directory, cs)
        val indexAsync = cs.async {
            return@async indexBuilder.build()
        }
//        delay(4000)
//        cs.cancel()
        val index = indexAsync.await()
        val searchQueryExecutor = SearchQueryExecutor(index)
        searchQueryExecutor.getFilesAndLinesWith("wall").forEach {
            println("${it.lineNumber} ${Path(it.path).fileName}")
        }
    }
}
