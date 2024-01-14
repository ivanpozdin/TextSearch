Task description:

Please implement a library for simple text search.

This library is supposed to consist of two parts: text index builder and search query executor.

**Text index builder should:**

1. Be able to build a text index for a given folder in a file system.
2. Show progress while building the index.
3. Build the index using several threads in parallel.
4. Be cancellable. It should be possible to interrupt indexing.
5. (Optional) Be incremental. It would be nice if the builder would be able to listen to the file system changes and update the index accordingly.

**Search query executor should:**

1. Find a position in files for a given string.
2. Be able to process search requests in parallel.

Please also cover the library with a set of unit-tests. Your code should not use third-party indexing libraries. To implement the library, you can use any JVM language and any build systems, but we would appreciate you choosing Kotlin and Gradle.
