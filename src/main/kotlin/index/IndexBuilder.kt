package index

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.forEachLine

class IndexBuilder(private val directory: String, private val cs: CoroutineScope) {
    private val indexTable: ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Path, Boolean>> = ConcurrentHashMap()
    private var filesNumber: Int = 0

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

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun printProgress(channel: Channel<Boolean>) {
        cs.launch {
            var processedFiles = 0
            while (!channel.isClosedForReceive) {
                try {
                    channel.receive()
                    processedFiles++
                    val progress = Math.round(processedFiles / filesNumber.toDouble() * 100).toInt()
                    val progressBar = "#".repeat(progress / 10) + "_".repeat(10 - progress / 10)

                    print("Index progress: $progressBar $progress%\r")
                } catch (_: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                }
//                delay(5)
            }
            println("Index progress: ${"#".repeat(10)} 100%")
        }
    }

    private suspend fun getPaths(): List<Path> {
        val paths = mutableListOf<Path>()

        withContext(Dispatchers.IO) {
            Files.walk(Paths.get(directory))
        }.use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { path ->
                paths.add(path)
            }
        }
        filesNumber = paths.size
        return paths.toList()
    }

    suspend fun build(): Index {
        val paths = getPaths()
        val progressChannel = Channel<Boolean>()
        printProgress(progressChannel)

        paths.map { path ->
            cs.async {
                try {
                    indexFile(path)
                    progressChannel.send(true)
                } catch (_: java.nio.charset.MalformedInputException) {
                }
            }
        }.awaitAll()
        progressChannel.close()
        return Index(indexTable)
    }
}
