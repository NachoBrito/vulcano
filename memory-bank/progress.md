# Progress

## What works
- **Modular Core Architecture**: Separate modules for core database, MCP server, and testing.
- **Axon Storage Engine**: High-performance disk-based storage with paged memory mapping and thread-safe atomic data logging.
- **HNSW Indexing**: Scalable approximate nearest neighbor search with persistent paged graph storage.
- **Attribute Indexing**: Full implementation of `StringIndexHandler` for persistent string filtering across all boolean operators.
- **ACID Persistence**: Write-Ahead Log (WAL) with binary optimization and crash recovery.
- **Document Model**: Support for complex document shapes with multiple field types (String, Int, Vector, Matrix).
- **Concurrent Query Engine**: Multi-threaded execution using Java 21 Virtual Threads and physical plan optimization.
- **MCP Integration**: Exposing database tools to AI agents.
- **Tucana Key-Value Store Initial Layer**:
    - [x] Defined interfaces for `TucanaIndex`, `TucanaStorage`, and `TucanaBuffer`.
    - [x] Implemented `TucanaKeyValueStore` with delegation logic and serialization.
    - [x] Implemented offset-based retrieval and architectural `getOffsetStream()`.
    - [x] Implemented `PagedTucanaBuffer` for scalable FFM-based memory management.
    - [x] Verified logic with comprehensive tests in `TucanaKeyValueStoreTest` and `PagedTucanaBufferTest`.

## What's left to build
- **Tucana Key-Value Store Core Implementation**: 
    - [ ] Implement `TucanaIndex` using B&epsilon;-trees.
    - [ ] Implement `TucanaStorage` with segment management and CoW.
- **RAG-First API**:
    - **Collections & Schemas**: Higher-level management of related documents.
    - **Automated Embedding**: `Embedder` interface and ONNX-based implementations for text-to-vector conversion.
    - **Simple Knowledge API**: Streamlined methods for adding text and querying knowledge.
- **Storage & Robustness**:
    - **WAL Checkpointing**: Background process to truncate the log and optimize disk usage.
    - **Index Compaction**: Tools to reorganize paged files for better locality.
    - **B-Tree Indexing**: Implementation of a sorted index for optimized range and prefix queries.
- **Hybrid Search**:
    - Further optimization for queries combining high-dimensional vector similarity with complex boolean and range filters.

## Current status
- **Binary Optimized WAL** is fully integrated and tested for crash consistency.
- **String Field Indexing** is completed and integrated into the query engine.
- **Tucana Key-Value Store** facade logic and scalable paging memory architecture are completed and verified via TDD.
- Moving towards the concrete implementation of the B&epsilon;-tree and physical segment storage.

## Known issues
- WAL can grow indefinitely without manual deletion; background checkpointing is required.
- Ingestion latency is affected by per-operation WAL syncs (fix: group commits).

## Evolution of project decisions
- **Durability Requirement**: Added WAL after recognizing the need for crash-safe operations in production environments.
- **Binary Format**: Switched from string-based logging to binary serialization to handle high-dimensional vector data efficiently.
- **Paged Scalability**: Adopted paged structures for HNSW to support datasets that exceed available RAM.
- **Attribute Indexing**: Integrated persistent inverted indexes into the core storage layer to support efficient attribute-based filtering alongside vector search.
- **Tucana Integration**: Decided to implement a write-optimized key-value store based on Tucana to further optimize ingestion and memory mapping using the FFM API.
- **Encoding Standard**: Enforced UTF-8 for all string/byte conversions across the storage layer.
- **Scalable Paging**: Shifted from single-segment buffers to a multi-page architecture (`PagedTucanaBuffer`) to support unlimited storage growth.
