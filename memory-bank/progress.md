# Progress

## What works
- Core modular architecture (Core, MCP, Test).
- Document storage and retrieval with `AxonDataStore`.
- HNSW indexing for vector similarity search.
- **Optimized Write-Ahead Log (WAL)** implementation with **binary serialization**, supporting all data types (vectors, matrices, etc.).
- Multi-client interface via MCP Module.

## What's left to build
- **RAG-First API**:
    - `Collection` and `Schema` management.
    - Built-in embedding model integration (`Embedder` interface).
    - High-level "Add Text" and "Query Knowledge" APIs.
- **Advanced Query Features**:
    - Hybrid search (Vector + Filter) optimization.
    - Query compilation improvements.
- **Reliability & Performance**:
    - Memory-mapped file optimizations for HNSW.
    - Background checkpointing and log rotation for WAL.
- **MCP Expansion**:
    - Dynamic indexing tools for LLMs.

## Current status
- Completed binary-optimized WAL integration.
- Database is now crash-consistent for all supported data types.
- Planning the RAG-focused higher-level abstractions.

## Known issues
- Ingestion performance can be further improved by batching and WAL group commits.
- Recovery re-plays the whole uncommitted log at startup; log rotation will be needed for long-running systems.

## Evolution of project decisions
- Transitioned from simple persistence to ACID-compliant writes using WAL.
- Adopted high-performance binary serialization for the WAL to handle vector data efficiently.
