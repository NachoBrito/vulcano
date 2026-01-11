# Querying and Search

VulcanoDB provides a powerful, fluent API for searching documents based on vector similarity and traditional field
filtering.

## Basic Vector Search

The most common use case is finding documents similar to a given vector (semantic search).

```java
float[] targetVector = getEmbedding("my search query");

var query = VulcanoDb.queryBuilder()
        .isSimilarTo(targetVector, "embedding")
        .build();

var results = db.search(query, 10);
```

## Combined Queries (Hybrid Search)

You can combine vector search with boolean filters to narrow down your results. By default, multiple conditions added to
a `QueryBuilder` are combined using the **AND** operator.

```java
var query = VulcanoDb.queryBuilder()
        .isSimilarTo(targetVector, "embedding")
        .isEqual("Sci-Fi", "genre")
        .isGreaterThan(1990, "year")
        .build();
```

## Available Operators

### Vector Search

- `isSimilarTo(float[] vector, String fieldName)`: Matches documents similar to the vector in the specified field.
- `allSimilarTo(float[] vector, List<String> fieldNames)`: Matches if all specified fields are similar to the vector.
- `anySimilarTo(float[] vector, List<String> fieldNames)`: Matches if any of the specified fields are similar to the
  vector.

### String Filtering

String filtering is highly efficient when a field is indexed using `.withStringIndex("fieldName")`.

- `isEqual(String value, String fieldName)`: Exact match.
- `startsWith(String prefix, String fieldName)`: Matches if field begins with the prefix.
- `endsWith(String suffix, String fieldName)`: Matches if field ends with the suffix.
- `contains(String value, String fieldName)`: Matches if field contains the substring.

### Integer Filtering

- `isEqual(Integer value, String fieldName)`
- `isLessThan(Integer value, String fieldName)`
- `isLessThanOrEqual(Integer value, String fieldName)`
- `isGreaterThan(Integer value, String fieldName)`
- `isGreaterThanOrEqual(Integer value, String fieldName)`

## Boolean Logic

### OR Operator

To use the **OR** operator for the top-level conditions:

```java
import es.nachobrito.vulcanodb.core.query.QueryOperator;

var query = VulcanoDb.queryBuilder()
        .withOperator(QueryOperator.OR)
        .isEqual("Action", "genre")
        .isEqual("Adventure", "genre")
        .build();
```

### NOT Operator

To exclude documents matching certain criteria:

```java
var excludeQuery = VulcanoDb.queryBuilder().isEqual("Horror", "genre");

var query = VulcanoDb.queryBuilder()
        .isSimilarTo(targetVector, "embedding")
        .not(excludeQuery)
        .build();
```

## Execution and Results

Execute your query using `db.search(query, limit)`. The result is a `QueryResult` object containing a collection of
`ResultDocument`s.

```java
QueryResult results = db.search(query, 5);

System.out.println("Total matches: " + results.totalHits());

for (ResultDocument resDoc : results.documents()) {
    System.out.println("ID: " + resDoc.id());
    System.out.println("Similarity Score: " + resDoc.score());
    System.out.println("Title: " + resDoc.getStringValue("title"));
}
```

*Note: The `score()` represents the similarity (usually cosine similarity), where higher values mean greater
similarity.*
