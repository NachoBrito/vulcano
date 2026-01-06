# Low-Level Design Document: VulcanoDB

## 1. Overview
VulcanoDB is a high-performance vector database that integrates traditional document storage with advanced vector similarity search. This document describes the internal components, their relationships, and the core workflows of the system, focusing on the `AxonDataStore` implementation.

## 2. System Architecture

### 2.1 Component Overview
The system is divided into three main layers:
1.  **API Layer (`VulcanoDb`)**: Provides the main entry point for document management and searching.
2.  **Storage Engine Layer (`DataStore`)**: Implements persistence and indexing. `AxonDataStore` is the primary implementation.
3.  **Core Components**: Internal modules for query evaluation, document representation, and indexing algorithms.

### 2.2 Dependency Diagram
The following diagram illustrates the high-level dependencies between the main components of VulcanoDB.

```mermaid
graph TD
    VulcanoDb --> DataStore
    AxonDataStore -- implements --> DataStore
    AxonDataStore --> DocumentPersister
    AxonDataStore --> WalManager
    AxonDataStore --> IndexHandler
    AxonDataStore --> QueryExecutor

    DefaultDocumentPersister -- implements --> DocumentPersister
    DefaultDocumentPersister --> FieldDiskStore
    DefaultDocumentPersister --> KeyValueStore

    FieldDiskStore --> KeyValueStore

    HnswIndexHandler -- implements --> IndexHandler
    HnswIndexHandler --> HnswIndex

    QueryExecutor --> QuerySplitter
    QueryExecutor --> QueryCompiler
    QueryExecutor --> VectorizedRunner
    
    VectorizedRunner --> ExecutionContext
    ExecutionContext --> DocumentPersister
```

## 3. Storage Layer Design

### 3.1 AxonDataStore
`AxonDataStore` is the heart of the persistence layer. It coordinates document storage, write-ahead logging (WAL), and indexing.

- **WAL (`WalManager`)**: Records every `add` or `remove` operation before execution to ensure durability and recovery.
- **Document Persister (`DocumentPersister`)**: Handles the physical storage of documents.
- **Index Registry**: Keeps track of which fields are indexed (e.g., HNSW for vector fields).

### 3.2 Document Persistence
The `DefaultDocumentPersister` decomposes documents into individual fields:
- **Dictionary**: A `KeyValueStore` that maps external `DocumentId` to an `internalId` and stores the `DocumentShape`.
- **FieldDiskStore**: Manages a set of `KeyValueStore` instances, one per field name and type combination.

## 4. Query Evaluation Design

The query evaluation follows a multi-phase pipeline designed for speed and efficiency.

### 4.1 Query Pipeline
1.  **Optimization (Logical Phase)**: `QuerySplitter` analyzes the logical query tree and splits it into two parts:
    - **Index Tree**: Parts of the query that can be satisfied using indexes (e.g., vector similarity).
    - **Residual Tree**: Parts of the query that require a full scan of the candidate documents.
2.  **Compilation (Physical Phase)**: `QueryCompiler` transforms logical nodes into physical operators:
    - `BitmapOperator`: Executable code for index lookups returning sets of `docId`.
    - `DocumentMatcher`: Functional predicates for document attribute filtering.
3.  **Execution Phase**:
    - `BitmapOperator` produces a candidate set of `docId` using bitset math (Roaring Bitmaps).
    - `VectorizedRunner` performs a scan over the candidates, applying the `DocumentMatcher` and ranking results.

### 4.2 Sequence Diagram: Document Search
This diagram shows the flow of a search request through the system.

```mermaid
sequenceDiagram
    participant App
    participant VDB as VulcanoDb
    participant ADS as AxonDataStore
    participant QE as QueryExecutor
    participant QS as QuerySplitter
    participant QC as QueryCompiler
    participant VR as VectorizedRunner

    App->>VDB: search(query, maxResults)
    VDB->>ADS: search(query, maxResults)
    ADS->>QE: execute(logicalRoot, maxResults)
    
    activate QE
    QE->>QS: split(logicalRoot)
    QS-->>QE: SplitResult(indexTree, residualTree)
    
    QE->>QC: compileIndex(indexTree)
    QC-->>QE: BitmapOperator
    
    QE->>QC: compileResidual(residualTree)
    QC-->>QE: DocumentMatcher
    
    QE->>VR: run(candidates, residualMatcher, context, maxResults)
    activate VR
    VR-->>QE: QueryResult
    deactivate VR
    
    QE-->>ADS: QueryResult
    deactivate QE
    
    ADS-->>VDB: QueryResult
    VDB-->>App: QueryResult
```

## 5. Indexing Design

### 5.1 HNSW Indexing
Vector fields are indexed using a Hierarchical Navigable Small World (HNSW) graph.

- **`HnswIndexHandler`**: Orchestrates the interaction between the data store and the HNSW graph.
- **`HnswIndex`**: The core implementation of the HNSW algorithm.
- **`documentIdMap`**: Maintains a mapping between HNSW internal IDs and the database's `internalId`.

### 5.2 Sequence Diagram: Document Insertion
This diagram illustrates how a document is persisted and indexed.

```mermaid
sequenceDiagram
    participant App
    participant VDB as VulcanoDb
    participant ADS as AxonDataStore
    participant WAL as WalManager
    participant DP as DocumentPersister
    participant HIH as HnswIndexHandler

    App->>VDB: add(document)
    VDB->>ADS: add(document)
    
    ADS->>WAL: recordAdd(document)
    WAL-->>ADS: txId
    
    ADS->>DP: write(document)
    activate DP
    DP-->>ADS: DocumentWriteResult(internalId)
    deactivate DP
    
    loop For each indexed field
        ADS->>HIH: index(internalId, document)
        HIH->>HIH: insert into HnswIndex
    end
    
    ADS->>WAL: commit(txId)
    ADS-->>VDB: void
    VDB-->>App: void
```

## 6. Technical Considerations
- **Concurrency**: `AxonDataStore` uses virtual threads (via `ExecutorProvider`) for asynchronous operations like background indexing and concurrent field writes.
- **Durability**: The WAL ensures that even if the system crashes during document persistence, it can recover its state upon restart.
- **Performance**: Use of Roaring Bitmaps for index set operations and vectorized scanning for residual matching minimizes CPU and IO overhead.
