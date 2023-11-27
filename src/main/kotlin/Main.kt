import indexBuilder.IndexBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import searchQueryExecutor.SearchQueryExecutor

fun main() {
    val directory = "/Users/ivanpozdin/KotlinProjects/TextSearch/SampleDirectory"
    val indexBuilder = IndexBuilder(directory)

    val searchQueryExecutor = SearchQueryExecutor(indexBuilder)
    searchQueryExecutor.getFilesAndLinesWith("of ")?.forEach {
        println(it)
    }
}

