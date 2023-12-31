package index

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

class IndexBuilder(private val directory: String, private val cs: CoroutineScope) {
    private val indexTable: ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Path, Boolean>> = ConcurrentHashMap()

    @Volatile
    private var keepIndexing = true

    companion object {
        const val TRIGRAM_LENGTH = 3
        const val PROGRESS_BAR_LENGTH = 10
        const val CANCEL_MESSAGE = "Indexing was cancelled."
    }

    fun cancel() {
        keepIndexing = false
    }

    private suspend fun indexFile(file: Path, progressChannel: Channel<Boolean>) {
        try {
            file.useLines { sequence ->
                try {
                    sequence.iterator().forEachRemaining { line ->
                        if (line.length < TRIGRAM_LENGTH) return@forEachRemaining
                        for (i in TRIGRAM_LENGTH..line.length) {
                            val trigram = line.substring(i - TRIGRAM_LENGTH, i)
                            val set = indexTable.computeIfAbsent(trigram) {
                                ConcurrentHashMap.newKeySet()
                            }
                            set.add(file)
                        }
                        if (!keepIndexing) {
                            throw CancellationException(CANCEL_MESSAGE)
                        }
                    }
                } catch (_: CancellationException) {
                }
            }
            if (keepIndexing) progressChannel.send(true)
        } catch (_: java.nio.charset.MalformedInputException) {
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun printProgress(channel: Channel<Boolean>, filesNumber: Int): Job {
        return cs.launch {
            try {
                var processedFiles = 0
                while (keepIndexing && !channel.isClosedForReceive) {
                    channel.receive()
                    processedFiles++

                    val progressPercentage = Math.round(processedFiles / filesNumber.toDouble() * 100).toInt()

                    val processedPart = "#".repeat(progressPercentage / PROGRESS_BAR_LENGTH)
                    val remainingPart = "_".repeat(PROGRESS_BAR_LENGTH - progressPercentage / PROGRESS_BAR_LENGTH)
                    val progressBar = processedPart + remainingPart
                    print("Indexing progress: $progressBar $progressPercentage%\r")
                }

            } catch (_: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
            } finally {
                if (keepIndexing) {
                    println("Indexing finished: ${"#".repeat(PROGRESS_BAR_LENGTH)} 100%")
                } else {
                    println(CANCEL_MESSAGE)
                }
            }
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
        return paths.toList()
    }

    suspend fun build(): Index {
        val paths = getPaths()
        val progressChannel = Channel<Boolean>()
        val progressPrinting = printProgress(progressChannel, paths.size)

        paths.map { path ->
            cs.async {
                indexFile(path, progressChannel)
            }
        }.awaitAll()
        progressChannel.close()
        progressPrinting.join()
        if (!keepIndexing) throw CancellationException(CANCEL_MESSAGE)
        return Index(indexTable)
    }
}
