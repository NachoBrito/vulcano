# Active Context

## Current work focus
Integrating and optimizing the Write-Ahead Log (WAL) for `AxonDataStore` and preparing for the next phase of RAG-focused API development.

## Recent changes
- **Binary Optimized WAL**: Implemented `WalManager`, `WalEntry`, and `WalSerializer` using high-performance binary serialization.
- **Crash Recovery**: Integrated automatic recovery in `AxonDataStore.initialize()` which replays pending WAL entries.
- **Data Types Support**: Verified that WAL and `AxonDataStore` correctly handle all document types including high-dimensional vectors and matrices.
- **Concurrency Enhancements**: Validated `AxonDataStore` robustness under concurrent load using Virtual Threads.

## Next steps
- **WAL Robustness**: Implement background checkpointing to truncate the WAL and manage disk space.
- **RAG API Layer**:
    - Design and implement `Collection` and `Schema` management classes.
    - Create `Embedder` abstractions for seamless integration with ONNX and other embedding models.
    - Implement a high-level `KnowledgeStore` or similar facade for simplified RAG workflows.
- **Query Optimization**: Enhance the `QueryCompiler` to better handle hybrid searches (combining HNSW vector search with attribute filters).

## Active decisions and considerations
- **Storage Format**: Sticking with paged memory-mapped files via `MemorySegment` for its performance and direct control over memory usage.
- **WAL Strategy**: Using a single WAL for all operations in a `DataStore` to simplify recovery and ensure consistent snapshots.
- **API Design**: Moving towards a more user-friendly API that abstracts away manual vectorization, aligning with "RAG-first" goals.

## Important patterns and preferences
- **Performance First**: Favoring binary serialization and direct memory access over high-level abstractions for core storage components.
- **Safety Second**: Ensuring data durability via WAL is a top priority, even at a slight performance cost (mitigated by binary optimization).

## Learnings and project insights
- The `MemorySegment` API provides excellent performance for paged storage but requires careful management of page boundaries.
- Binary serialization is significantly faster and more compact than text-based formats for vector data, which is critical for WAL performance.
