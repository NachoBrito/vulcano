# Active Context

## Current work focus
Implementing a new high-performance `KeyValueStore` based on the Tucana architecture (USENIX ATC '16).

## Recent changes
- **Tucana Key-Value Store Scalable Memory**:
    - Refactored `PagedTucanaBuffer` to encapsulate memory management via a new `PageManager` interface.
    - Removed direct `MemorySegment` exposure from `TucanaBuffer` to maintain strict abstraction and support unlimited growth.
    - Transitioned the memory layer to a multi-page architecture with on-demand page retrieval.
    - Verified paging and delegation logic with a comprehensive `PagedTucanaBufferTest` suite.
- **Tucana Key-Value Store Implementation Progress**:
    - Implemented offset-based retrieval methods (`getStringAt`, `getIntAt`, etc.) in `TucanaKeyValueStore`.
    - Implemented `getOffsetStream()` by delegating to `TucanaIndex.allOffsets()`, ensuring architectural correctness by only returning live data offsets from the B&epsilon;-tree.
    - Updated `TucanaStorage` interface with `read(long offset)` and `TucanaIndex` with `allOffsets()`.
    - Added comprehensive unit tests for all new functionality in `TucanaKeyValueStoreTest`.
- **Tucana Key-Value Store Initial Implementation**: 
    - Defined architectural interfaces: `TucanaBuffer`, `TucanaStorage`, and `TucanaIndex`.
    - Created `TucanaKeyValueStore` implementation that delegates to the index and storage components.
    - Implemented numeric and array serialization using `ByteBuffer`.
    - Fixed SpotBugs `DM_DEFAULT_ENCODING` errors by enforcing `StandardCharsets.UTF_8` across the store and its tests.
- **TDD Workflow**: Developed `TucanaKeyValueStoreTest` first and iteratively implemented the store logic to pass all tests.

## Next steps
- **Implement Tucana Components**: 
    - Create a concrete implementation of `PageManager` (likely file-backed for persistence).
    - Create a concrete implementation of `TucanaIndex` (B&epsilon;-tree).
    - Create a concrete implementation of `TucanaStorage` (Segment-based with CoW).
- **WAL Robustness**: Implement background checkpointing to truncate the WAL and manage disk space.
- **RAG API Layer**:
    - Design and implement `Collection` and `Schema` management classes.
    - Create `Embedder` abstractions for seamless integration with ONNX and other embedding models.

## Active decisions and considerations
- **Strict Encapsulation**: Refactored `TucanaBuffer` to remove direct exposure of the underlying memory segments (`segment()`). All access is now strictly offset-based, allowing `PagedTucanaBuffer` and `PageManager` to manage physical storage without leaking implementation details.
- **Tucana Architecture**: Leveraging B&epsilon;-trees and Java's Foreign Function & Memory (FFM) API for a write-optimized, low-latency store.
- **Scalable Paging**: Adopted a paging system (`PagedTucanaBuffer`) as the primary memory abstraction. This allows the store to grow beyond single-segment limits while maintaining the performance benefits of direct memory access.
- **Live Data Offsets**: Decided to delegate `getOffsetStream()` to `TucanaIndex` rather than `TucanaStorage`. This ensures we only return offsets for data reachable in the current epoch, ignoring stale data produced by Tucana's Copy-on-Write mechanism.
- **Encoding Standard**: Enforced UTF-8 for all string conversions to prevent environment-dependent behavior and satisfy static analysis (SpotBugs).
- **Delegation Pattern**: `TucanaKeyValueStore` acts as a facade over `TucanaIndex` and `TucanaStorage`, simplifying testing and implementation of specific database features.
- **Production-Ready Mandate**: All future development must avoid "simple" or "naive" implementations. Every change is expected to be industrial-grade, prioritizing performance, concurrency (Virtual Threads), and robust persistent storage (Axon/Paged).
- **Atomic Persistence**: Standardized the use of `rawSize` in headers to guarantee identical read/write payloads, crucial for both main storage and WAL consistency.
- **Concurrency Model**: Continuing to leverage Java 21 Virtual Threads as the primary mechanism for scaling ingestion and query performance.

## Important patterns and preferences
- **Defensive Storage**: Header-driven length validation and atomic memory reservation are now established patterns for all persistent structures.
- **Package Organization**: Auxiliary indexing classes (e.g., `InvertedIndex`) are located in specialized sub-packages (e.g., `es.nachobrito.vulcanodb.core.store.axon.index.string`).
- **Tucana Namespace**: All Tucana-related components reside in `es.nachobrito.vulcanodb.core.store.axon.kvstore.tucana`.

## Learnings and project insights
- Enforcing charset encoding early prevents subtle bugs and ensures compliance with static analysis tools like SpotBugs.
- Mocking the internal interfaces allowed for rapid development and verification of the `KeyValueStore` logic before implementing the complex B&epsilon;-tree and FFM memory management logic.
