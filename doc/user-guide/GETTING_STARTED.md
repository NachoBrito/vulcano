# Getting Started with VulcanoDB

This guide will help you set up VulcanoDB and perform your first operations.

## Prerequisites

- Java 21 or higher (uses virtual threads)
- Maven or Gradle for dependency management

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>es.nachobrito</groupId>
    <artifactId>vulcanodb-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Initializing the Database

VulcanoDB supports different storage backends. You can choose the one that best fits your needs using the
`VulcanoDb.builder()`.

### 1. In-Memory Storage

Perfect for testing, small datasets, or applications where persistence isn't required.

```java
import es.nachobrito.vulcanodb.core.VulcanoDb;

var db = VulcanoDb.builder().build();
```

### 2. Persistent Storage (Axon Engine)

For production use where data must persist between restarts. Axon also supports high-performance HNSW indexing for
vectors.

```java
import es.nachobrito.vulcanodb.core.VulcanoDb;
import es.nachobrito.vulcanodb.core.store.axon.AxonDataStore;
import java.nio.file.Path;

Path dataPath = Path.of("./my-vdb-data");

var axon = AxonDataStore.builder()
        .withDataFolder(dataPath)
        .withVectorIndex("embedding") // Enable HNSW index for the "embedding" field
        .build();

try (var db = VulcanoDb.builder().withDataStore(axon).build()) {
    // Use the database
} // Database is closed automatically
```

## Basic Operations

### Adding a Document

```java
var doc = Document.builder()
        .withStringField("id", "1")
        .withStringField("content", "Hello VulcanoDB")
        .withVectorField("embedding", new float[]{0.1f, 0.2f, 0.3f})
        .build();

db.add(doc);
```

### Searching

```java
var query = VulcanoDb.queryBuilder()
        .isSimilarTo(new float[]{0.1f, 0.2f, 0.3f}, "embedding")
        .build();

var results = db.search(query, 5); // Return top 5 matches

results.documents().forEach(resDoc -> {
    System.out.println("Match: " + resDoc.getStringValue("content"));
    System.out.println("Score: " + resDoc.score());
});
```

## Next Steps

- Learn more about [Documents and Fields](DOCUMENTS.md)
- Explore complex [Querying and Search](QUERIES.md) options
