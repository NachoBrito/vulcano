# Active Context

## Current work focus
Implementing a new high-performance `KeyValueStore` based on the Tucana architecture (USENIX ATC '16).

## Recent changes
- **Tucana Key-Value Store Core Components**:
    - Implemented `AxonTucanaStorage`: A persistent, Copy-on-Write storage layer with atomic commits.
    - Implemented `Superblock`: Manages dual-header metadata (Epoch, Root, Checksum) for crash consistency.
    - Implemented `FilePageManager`: Handles on-demand memory mapping of a single database file using the FFM API.
    - Integrated these components into `TucanaKeyValueStore` and verified with `AxonTucanaStorageTest`.
- **Tucana Key-Value Store Refactoring for Separation of Concerns**:
    - Refactored `TucanaIndex` to strictly map keys to `long` offsets (using `OptionalLong`).
    - Enhanced `TucanaStorage` with a `write(byte[] data)` method, making it the sole owner of data persistence.
    - Optimized `TucanaStorage.read(long offset)` to return a `ByteBuffer`, eliminating redundant copies and `wrap()` calls.
    - Refactored `TucanaKeyValueStore` to follow a two-step persistence model: write data to storage first, then index the resulting offset.
    - Verified the new architecture with updated `TucanaKeyValueStoreTest` and confirmed 100% pass rate.
- **Tucana Key-Value Store Scalable Memory**:
    - Refactored `PagedTucanaBuffer` to encapsulate memory management via a new `PageManager` interface.
    - Removed direct `MemorySegment` exposure from `TucanaBuffer` to maintain strict abstraction and support unlimited growth.
    - Transitioned the memory layer to a multi-page architecture with on-demand page retrieval.
    - Verified paging and delegation logic with a comprehensive `PagedTucanaBufferTest` suite.
- **Tucana Key-Value Store Initial Implementation**: 
    - Defined architectural interfaces: `TucanaBuffer`, `TucanaStorage`, and `TucanaIndex`.
    - Created `TucanaKeyValueStore` implementation that delegates to the index and storage components.
    - Implemented numeric and array serialization using `ByteBuffer`.
    - Fixed SpotBugs `DM_DEFAULT_ENCODING` errors by enforcing `StandardCharsets.UTF_8` across the store and its tests.
- **TDD Workflow**: Developed `TucanaKeyValueStoreTest` first and iteratively implemented the store logic to pass all tests.

## Next steps
- **Implement Tucana Components**: 
    - Create a concrete implementation of `TucanaIndex` (B&epsilon;-tree).
- **WAL Robustness**: Implement background checkpointing to truncate the WAL and manage disk space.
- **RAG API Layer**:
    - Design and implement `Collection` and `Schema` management classes.
    - Create `Embedder` abstractions for seamless integration with ONNX and other embedding models.

## Active decisions and considerations
- **Strict Separation of Concerns**: Enforced a clear boundary where `TucanaIndex` only handles key-to-offset mappings (B&epsilon;-tree structure), while `TucanaStorage` manages all physical data blocks and node persistence.
- **Tucana API Optimization**: Optimized `TucanaStorage.read()` to return `ByteBuffer`, enabling direct deserialization of `Entry` objects without extra allocations.
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
- Using `ByteBuffer` as a return type for low-level storage reads significantly improves the performance of the serialization layer.
