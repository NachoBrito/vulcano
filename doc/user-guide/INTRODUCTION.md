# VulcanoDB Documentation

VulcanoDB is a high-performance vector database system designed for efficient storage, indexing, and querying of vector
data.

## Table of Contents

1. [Introduction](INTRODUCTION.md)
2. [Getting Started](GETTING_STARTED.md)
3. [Documents and Fields](DOCUMENTS.md)
4. [Querying and Search](QUERIES.md)

## Core Features

- **Vector Similarity Search**: Fast semantic search using HNSW (Hierarchical Navigable Small World) graphs.
- **Flexible Document Model**: Store documents with various field types including String, Integer, Vector, and Matrix.
- **Persistence Options**: Choose between high-speed in-memory storage or persistent disk-based storage using the Axon
  engine.
- **Advanced Querying**: Combine vector search with logical filters (AND, OR, NOT) and field-specific operators.
- **High Performance**: Optimized for multi-threaded execution using Java virtual threads.

## Quick Example

```java
// Initialize the database
var db = VulcanoDb.builder().build();

// Create a document
var doc = Document.builder()
        .withStringField("title", "VulcanoDB")
        .withVectorField("embedding", new float[]{0.1f, 0.2f, 0.3f})
        .build();

// Add it to the DB
db.

add(doc);

// Search for similar documents
var query = VulcanoDb.queryBuilder()
        .isSimilarTo(new float[]{0.15f, 0.25f, 0.35f}, "embedding")
        .build();

var results = db.search(query, 10);
```

For more details, please refer to the specific sections in the table of contents above.
