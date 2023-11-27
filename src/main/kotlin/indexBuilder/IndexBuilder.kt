package indexBuilder

import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.forEachLine
import kotlin.io.path.pathString

class IndexBuilder(private val directory: String) {
    private val indexTable: ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Int, Boolean>> = ConcurrentHashMap()
    private val docIDds: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
    private val docPathsFromIds: ConcurrentHashMap<Int, String> = ConcurrentHashMap()

    init {
        fillDocIds()
        index()
    }

    private fun fillDocIds() {
        var id = 0
        Files.walk(Paths.get(directory)).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { path ->
                docIDds[path.pathString] = id
                docPathsFromIds[id] = path.pathString
                id++
            }
        }
    }

    private fun indexFile(file: Path) {
        val docId = docIDds[file.pathString]!!
        file.forEachLine { line ->
            if (line.length < 3) return@forEachLine
            for (i in 3..line.length) {
                val trigram = line.substring(i - 3, i)
                indexTable.putIfAbsent(trigram, ConcurrentHashMap.newKeySet())
                indexTable[trigram]?.add(docId)
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

    fun getDocPath(docId: Int): String? {
        return docPathsFromIds[docId]
    }

    fun getDocuments(trigram: String): Set<Int>? {
        return indexTable[trigram]?.toSet()
    }
}