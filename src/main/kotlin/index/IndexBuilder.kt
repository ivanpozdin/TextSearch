package index

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.useLines
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Builds a text index for a given folder in a file system.
 *
 * @property directory for indexing.
 * @property cs is a coroutine scope, where class' coroutines will be executed.
 *
 * @constructor creates IndexBuilder for given directory, which wil be ready to index on the build() method.
 */
class IndexBuilder(private val directory: String, private val cs: CoroutineScope) {
    private val indexTable: ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Path, Boolean>> = ConcurrentHashMap()

    @Volatile
    private var keepIndexing = true

    companion object {
        const val TRIGRAM_LENGTH = 3
        private const val PROGRESS_BAR_LENGTH = 10
        private const val CANCEL_MESSAGE = "Indexing was cancelled."
        private const val UPDATE_PROGRESS_PERCENT = 0.03
        private const val HUNDRED = 100
    }

    /**
     * Cancels indexing.
     */
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

    private val flow = MutableStateFlow(0)

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun updateProgress(channel: Channel<Boolean>, filesNumber: Int): Job = cs.launch {
        try {
            var processedFiles = 0
            while (keepIndexing && !channel.isClosedForReceive) {
                channel.receive()
                processedFiles++

                if (processedFiles - flow.value >= UPDATE_PROGRESS_PERCENT * filesNumber) {
                    flow.emit(processedFiles)
                }
            }
        } catch (_: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
        }
    }

    private suspend fun printProgress(filesNumber: Int): Job = cs.launch {
        try {
            flow.collect { processedFiles ->
                val progressPercentage = Math.round(HUNDRED * processedFiles / filesNumber.toDouble()).toInt()
                val processedPart = "#".repeat(progressPercentage / PROGRESS_BAR_LENGTH)
                val remainingPart = "_".repeat(PROGRESS_BAR_LENGTH - progressPercentage / PROGRESS_BAR_LENGTH)
                val progressBar = processedPart + remainingPart

                print("Indexing progress: $progressBar $progressPercentage%\r")
            }
        } finally {
            if (keepIndexing) {
                println("Indexing finished: ${"#".repeat(PROGRESS_BAR_LENGTH)} 100%")
            } else {
                println(CANCEL_MESSAGE)
            }
        }
        // todo print progress once a second
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

    /**
     * Indexes directory.
     *
     * @return is an Index instance, which is indexing result.
     * @throws CancellationException when the indexing is cancelled.
     */
    suspend fun build(): Index {
        val paths = getPaths()
        val progressChannel = Channel<Boolean>()
        val progressUpdating = updateProgress(progressChannel, paths.size)
        val progressPrinting = printProgress(paths.size)
        try {
            paths.map { path ->
                cs.async {
                    indexFile(path, progressChannel)
                }
            }.awaitAll()
        } finally {
            progressChannel.close()
        }
        progressUpdating.join()
        progressPrinting.cancelAndJoin()

        if (!keepIndexing) throw CancellationException(CANCEL_MESSAGE)
        return Index(indexTable)
    }
}
