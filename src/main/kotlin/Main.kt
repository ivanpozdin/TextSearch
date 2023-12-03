import index.IndexBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import search.SearchQueryExecutor

fun main() {
    val directory = "/Users/ivanpozdin/KotlinProjects/TextSearch/SampleDirectory"
    runBlocking(Dispatchers.Default) {
        val indexBuilder = IndexBuilder(directory, cs = this)
        val index = indexBuilder.build()
        val searchQueryExecutor = SearchQueryExecutor(index)
        searchQueryExecutor.getFilesAndLinesWith("of ").forEach {
            println(it)
        }
    }
}
