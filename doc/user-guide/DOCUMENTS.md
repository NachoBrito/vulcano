# Documents and Fields

In VulcanoDB, data is organized into **Documents**. Each document is a collection of named **Fields**, and each field contains a value of a specific type.

## Document Structure

Documents are immutable and should be created using the `DocumentBuilder`. Every document is assigned a unique `DocumentId` automatically upon creation, which you can use for later retrieval or removal.

## Supported Field Types

VulcanoDB supports the following field types:

| Type | Java Type | Description |
|------|-----------|-------------|
| **String** | `java.lang.String` | For text content, categories, or identifiers. |
| **Integer** | `java.lang.Integer` | For counts, ages, or any numeric ID. |
| **Vector** | `float[]` | High-dimensional embeddings for similarity search. |
| **Matrix** | `float[][]` | Collections of vectors or other matrix-like data. |

## Creating Documents

Use `Document.builder()` to create a new document:

```java
import es.nachobrito.vulcanodb.core.document.Document;

Document doc = Document.builder()
        .withStringField("title", "The Matrix")
        .withIntegerField("year", 1999)
        .withStringField("genre", "Sci-Fi")
        .withVectorField("embedding", new float[]{0.12f, -0.05f, 0.88f, ...})
        .build();
```

## Accessing Field Values

When you retrieve a document from the database (e.g., via a search), you can access its fields using type-specific getter methods:

```java
// Assuming 'doc' is a ResultDocument or Document
String title = doc.getStringValue("title");
Integer year = doc.getIntegerValue("year");
float[] embedding = doc.getVectorValue("embedding");
```

## Document Management

### Adding Documents

You can add documents individually or in bulk (if supported by the implementation):

```java
db.add(doc);
```

### Removing Documents

To remove a document, you need its `DocumentId`:

```java
import es.nachobrito.vulcanodb.core.document.DocumentId;

DocumentId id = doc.id();
db.remove(id);
```

## Best Practices

- **Consistent Schemas**: While VulcanoDB is flexible, keeping a consistent field structure across documents of the same "type" will make querying much easier.
- **Index Selection**: When using the Axon storage engine, fields used for vector similarity or high-frequency string filtering should be explicitly indexed:
    - For vectors: `.withVectorIndex("embedding")`
    - For strings: `.withStringIndex("status")`
- **Large Strings**: For very large text content, consider storing only the metadata and a reference/summary in VulcanoDB, especially if you are using it primarily for vector search.
