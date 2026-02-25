# Active Context

## Current work focus
Completing the high-performance attribute indexing system and ensuring storage layer atomicity under concurrent load.

## Recent changes
- **Tucana Key-Value Store**: Implemented a high-performance Java 25 implementation based on the USENIX ATC '16 paper.
    - **Architecture**: Leverages FFM API for direct memory-mapped access and Copy-on-Write (CoW) for persistence.
    - **Bε-tree Index**: Implemented a write-optimized index with leaf-level buffering, node splitting, and $O(\log N)$ binary pivot search.
    - **Robustness**: Integrated `VarHandle` for atomic memory access and CRC32 checksumming for superblock integrity.
    - **Scalability**: Added multi-segment storage with asynchronous background pre-allocation of 64MB segment files.
    - **Versioning**: Implemented epoch-based point-in-time queries (`getStringAtEpoch`) using the inherent CoW snapshotting.
- **String Field Indexing**: Implemented `StringIndexHandler` using a persistent `InvertedIndex`.
- **Binary Optimized WAL**: Integrated high-performance binary logging and automatic crash recovery.

## Next steps
- **Tucana Performance Tuning**: Develop JMH benchmarks to quantify the CPU efficiency gains of the new Tucana store against `AOLKeyValueStore`.
- **WAL Robustness**: Implement background checkpointing to truncate the WAL and manage disk space.
- **RAG API Layer**:
    - Design and implement `Collection` and `Schema` management classes.
    - Create `Embedder` abstractions for seamless integration with ONNX and other embedding models.

## Active decisions and considerations
- **Indexing Strategy**: Using a hash-based inverted index for strings currently. While O(1) for exact matches, partial matches require term iteration. A future B-Tree implementation could optimize range and prefix queries.
- **Atomic Persistence**: Standardized the use of `rawSize` in headers to guarantee identical read/write payloads, crucial for both main storage and WAL consistency.
- **Concurrency Model**: Continuing to leverage Java 21 Virtual Threads as the primary mechanism for scaling ingestion and query performance.

## Important patterns and preferences
- **Defensive Storage**: Header-driven length validation and atomic memory reservation are now established patterns for all persistent structures.
- **Package Organization**: Auxiliary indexing classes (e.g., `InvertedIndex`) are located in specialized sub-packages (e.g., `es.nachobrito.vulcanodb.core.store.axon.index.string`).

## Learnings and project insights
- Header-based length calculations in paged storage must account for internal alignment padding to avoid reading junk data.
- Atomic reservation in memory-mapped files requires a "reserve-then-calculate" approach with safety margins to handle dynamic alignment needs in a thread-safe manner.
