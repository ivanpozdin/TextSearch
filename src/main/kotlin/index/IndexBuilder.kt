package index

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.forEachLine

class IndexBuilder(private val directory: String, private val cs: CoroutineScope) {
    private val indexTable: ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Path, Boolean>> = ConcurrentHashMap()

    companion object {
        const val TRIGRAM_LENGTH = 3
    }

    private fun indexFile(file: Path) {
        file.forEachLine { line ->
            if (line.length < TRIGRAM_LENGTH) return@forEachLine
            for (i in TRIGRAM_LENGTH..line.length) {
                val trigram = line.substring(i - TRIGRAM_LENGTH, i)
                val set = indexTable.computeIfAbsent(trigram) { ConcurrentHashMap.newKeySet() }
                set.add(file)
            }
        }
    }

    suspend fun build(): Index {
        val paths = mutableListOf<Path>()

        withContext(Dispatchers.IO) {
            Files.walk(Paths.get(directory))
        }.use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { path ->
                paths.add(path)
            }
        }
        paths.map { path ->
            cs.async { indexFile(path) }
        }.awaitAll()
        return Index(indexTable)
    }
}
