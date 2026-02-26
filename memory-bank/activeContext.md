# Active Context

## Current work focus
Implementing a new high-performance `KeyValueStore` based on the Tucana architecture (USENIX ATC '16).

## Recent changes
- **Architecture Planning**: Initiated the TDD approach for the Tucana-based `KeyValueStore`.
- **String Field Indexing**: Implemented `StringIndexHandler` using a persistent `InvertedIndex`.
- **Binary Optimized WAL**: Integrated high-performance binary logging and automatic crash recovery.

## Next steps
- **Tucana Implementation (TDD)**:
    - Define core interfaces: `TucanaIndex`, `TucanaStorage`, `TucanaBuffer`.
    - Create `TucanaKeyValueStore` implementation stub.
    - Write comprehensive unit tests in `TucanaKeyValueStoreTest`.
- **WAL Robustness**: Implement background checkpointing to truncate the WAL and manage disk space.
- **RAG API Layer**:
    - Design and implement `Collection` and `Schema` management classes.
    - Create `Embedder` abstractions for seamless integration with ONNX and other embedding models.

## Active decisions and considerations
- **Tucana Architecture**: Leveraging B&epsilon;-trees and Java's Foreign Function & Memory (FFM) API for a write-optimized, low-latency store.
- **TDD Approach**: Defining interfaces and tests before implementation to ensure a robust and well-designed store.
- **Indexing Strategy**: Using a hash-based inverted index for strings currently. While O(1) for exact matches, partial matches require term iteration. A future B-Tree implementation could optimize range and prefix queries.
- **Atomic Persistence**: Standardized the use of `rawSize` in headers to guarantee identical read/write payloads, crucial for both main storage and WAL consistency.
- **Concurrency Model**: Continuing to leverage Java 21 Virtual Threads as the primary mechanism for scaling ingestion and query performance.

## Important patterns and preferences
- **Defensive Storage**: Header-driven length validation and atomic memory reservation are now established patterns for all persistent structures.
- **Package Organization**: Auxiliary indexing classes (e.g., `InvertedIndex`) are located in specialized sub-packages (e.g., `es.nachobrito.vulcanodb.core.store.axon.index.string`).
- **Tucana Namespace**: All Tucana-related components will reside in `es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana`.

## Learnings and project insights
- Header-based length calculations in paged storage must account for internal alignment padding to avoid reading junk data.
- Atomic reservation in memory-mapped files requires a "reserve-then-calculate" approach with safety margins to handle dynamic alignment needs in a thread-safe manner.
- Verified that the Tucana implementation was previously listed as "completed" in the memory bank despite not existing in the codebase.
