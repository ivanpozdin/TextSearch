package index

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Result of directory indexing. Can be used to search for files containing given trigram.
 *
 * @param indexTable is a hashtable with trigrams and files where trigrams are found.
 */
class Index(private val indexTable: ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Path, Boolean>>) {
    /**
     * Find occurrences of given trigram.
     *
     * @param trigram is a word of 3 letters, occurrences of which are needed to be found in the directory.
     * @return a set of all paths where a given trigram is found.
     * @throws IllegalArgumentException when the length of provided string is not 3.
     */
    fun getDocuments(trigram: String): Set<Path>? {
        require(trigram.length == 3)
        return indexTable[trigram]?.toSet()
    }
}
