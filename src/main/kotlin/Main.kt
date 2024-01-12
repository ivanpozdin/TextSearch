import index.IndexBuilder
import kotlinx.coroutines.*
import search.SearchQueryExecutor
import kotlin.io.path.Path

fun main() {
    val directory = "SampleDirectory"
    runBlocking {
        val cs = CoroutineScope(Dispatchers.Default)
        val indexBuilder = IndexBuilder(directory, cs)
        val indexAsync = cs.async {
            return@async indexBuilder.build()
        }
//        delay(4000)
//        indexBuilder.cancel()
        val index = indexAsync.await()
        val searchQueryExecutor = SearchQueryExecutor(index)
        searchQueryExecutor.getFilesAndLinesWith("Oppenheimer").forEach {
            println("${it.lineNumber} ${Path(it.path).fileName}")
        }
    }
}
