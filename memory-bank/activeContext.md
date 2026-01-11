# Active Context

## Current work focus
Completing the high-performance attribute indexing system and ensuring storage layer atomicity under concurrent load.

## Recent changes
- **String Field Indexing**: Implemented `StringIndexHandler` using a persistent `InvertedIndex`. Added full support for `EQUALS`, `STARTS_WITH`, `ENDS_WITH`, and `CONTAINS` operators.
- **DataLog Data Integrity**: Standardized on storing `rawSize` in entry headers and refined `readBytes` to account for internal alignment padding. This fixed corruption issues when reading string IDs.
- **Concurrency Fixes**: Resolved a race condition in `DataLog` by implementing atomic space reservation (`getAndAdd`) with safety margins, ensuring thread-safe concurrent document ingestion via virtual threads.
- **Binary Optimized WAL**: Integrated high-performance binary logging and automatic crash recovery.

## Next steps
- **WAL Robustness**: Implement background checkpointing to truncate the WAL and manage disk space.
- **RAG API Layer**:
    - Design and implement `Collection` and `Schema` management classes.
    - Create `Embedder` abstractions for seamless integration with ONNX and other embedding models.
- **Query Optimization**: Further refine the `QueryCompiler` for hybrid searches, potentially adding a sorted index (B-Tree) for faster prefix matches.

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
