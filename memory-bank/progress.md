# Progress

## What works
- **Modular Core Architecture**: Separate modules for core database, MCP server, and testing.
- **Axon Storage Engine**: High-performance disk-based storage with paged memory mapping.
- **HNSW Indexing**: Scalable approximate nearest neighbor search with persistent paged graph storage.
- **ACID Persistence**: Write-Ahead Log (WAL) with binary optimization and crash recovery.
- **Document Model**: Support for complex document shapes with multiple field types (String, Int, Vector, Matrix).
- **Concurrent Query Engine**: Multi-threaded execution using Java 21 Virtual Threads and physical plan optimization.
- **MCP Integration**: Exposing database tools to AI agents.

## What's left to build
- **RAG-First API**:
    - **Collections & Schemas**: Higher-level management of related documents.
    - **Automated Embedding**: `Embedder` interface and ONNX-based implementations for text-to-vector conversion.
    - **Simple Knowledge API**: Streamlined methods for adding text and querying knowledge.
- **Storage & Robustness**:
    - **WAL Checkpointing**: Background process to truncate the log and optimize disk usage.
    - **Index Compaction**: Tools to reorganize paged files for better locality.
- **Hybrid Search**:
    - Better optimization for queries combining vector similarity with complex boolean filters.
- **Performance Tuning**:
    - Group commits for WAL to improve high-frequency ingestion.
    - Specialized SIMD-aware similarity metrics.

## Current status
- **Binary Optimized WAL** is fully integrated and tested for crash consistency.
- Database provides **Durability and Atomicity** for all core operations.
- Transitioning focus from storage-layer robustness to **RAG-layer usability**.

## Known issues
- WAL can grow indefinitely without manual deletion; background checkpointing is required.
- Ingestion latency is affected by per-operation WAL syncs (fix: group commits).

## Evolution of project decisions
- **Durability Requirement**: Added WAL after recognizing the need for crash-safe operations in production environments.
- **Binary Format**: Switched from string-based logging to binary serialization to handle high-dimensional vector data efficiently.
- **Paged Scalability**: Adopted paged structures for HNSW to support datasets that exceed available RAM.
