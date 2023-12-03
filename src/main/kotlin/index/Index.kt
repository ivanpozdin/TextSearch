package index

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class Index(private val indexTable: ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Path, Boolean>>) {
    fun getDocuments(trigram: String): Set<Path>? {
        return indexTable[trigram]?.toSet()
    }
}
