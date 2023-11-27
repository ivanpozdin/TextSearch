package indexBuilder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.forEachLine

class IndexBuilder(private val directory: String) {
    private val indexTable: ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Path, Boolean>> = ConcurrentHashMap()

    companion object {
        const val TRIGRAM_LENGTH = 3
    }

    init {
        index()
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

    private fun index() {
        val paths = mutableListOf<Path>()

        Files.walk(Paths.get(directory)).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { path ->
                paths.add(path)
            }
        }
        runBlocking(Dispatchers.Default) {
            paths.forEach { path ->
                launch {
                    indexFile(path)
                }
            }
        }
    }

    fun getDocuments(trigram: String): Set<Path>? {
        return indexTable[trigram]?.toSet()
    }
}
