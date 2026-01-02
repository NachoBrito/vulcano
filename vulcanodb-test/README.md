# Test and example applications

This module contains several examples and demonstrations about VulcanoDB:

## HNSW Index Performance

The [HnswIndexPerformance](./src/main/java/es/nachobrito/vulcanodb/HnswIndexPerformance.java) class is a demonstration
of the HNSW vector index performance. It will create a VulcanoDB instance, and store documents generated from a sample
data set containing [Rotten Tomatoes movie reviews](./src/main/resources/rotten-tomatoes-reviews.txt). Each document
contains:

- The original review as a String
- The text embedding of the original text, generated with
  an [embedding model](./src/main/java/es/nachobrito/vulcanodb/Embedding.java)

The text embedding is stored twice in each document, with different field identifiers. One of them is indexed, the other
is not. Then, the same query is evaluated against both fields to compare the time taken to find the 10 best matches.

Use the following commands to run this example:

- `mvn clean install`
- `cd vulcanodb-test`
- `mvn compile exec:java -Dexec.mainClass="es.nachobrito.vulcanodb.HnswIndexPerformance"`

The output will look similar to this:

```text
Building db (data folder: /tmp/vulcanodb-test9777460824705212072) ...
Generating documents...
Storing 10662 documents...
Generating embedding for the query...
Non-indexed search:
[0.83] b0089fc7-ab3c-4b85-8daf-7d422a9f9567 -> a slick , skillful little horror film .
[0.80] 39a5e68b-171d-45f3-a3fa-8a9354512dba -> one of the best silly horror movies of recent memory , with some real shocks in store for unwary viewers .
[0.79] e4bcf721-412a-4392-8919-5e3a551d51f9 -> this is a superior horror flick .
[0.77] 6416021c-c35e-4c90-8f2e-8092ca1c7ba3 -> an exceedingly clever piece of cinema . another great ‘what you don't see' is much more terrifying than what you do see thriller , coupled with some arresting effects , incandescent tones and stupendous performances
[0.77] 0783c1fb-8719-40d5-a211-bead28e9a228 -> clever , brutal and strangely soulful movie .
[0.77] 414227e8-e428-494c-8bbd-9ef9608d9fb7 -> a chilling movie without oppressive gore .
[0.74] a8b28cb8-4884-46b1-9a58-f910286ab829 -> marshall puts a suspenseful spin on standard horror flick formula .
[0.74] c90ecce2-72e4-4321-9436-af5657dd3558 -> the entire movie establishes a wonderfully creepy mood .
[0.73] e95c46b6-4e44-4fa8-894b-1d595bc7ed2e -> a deftly entertaining film , smartly played and smartly directed .
[0.73] 449979ab-56ef-45b0-befc-4062f0ca68e6 -> if nothing else , this movie introduces a promising , unusual kind of psychological horror .
Non-indexedd time: 372 ms
Index search: 
[0.87] 39a5e68b-171d-45f3-a3fa-8a9354512dba -> one of the best silly horror movies of recent memory , with some real shocks in store for unwary viewers .
[0.86] e4bcf721-412a-4392-8919-5e3a551d51f9 -> this is a superior horror flick .
[0.84] 0783c1fb-8719-40d5-a211-bead28e9a228 -> clever , brutal and strangely soulful movie .
[0.83] a8b28cb8-4884-46b1-9a58-f910286ab829 -> marshall puts a suspenseful spin on standard horror flick formula .
[0.83] c90ecce2-72e4-4321-9436-af5657dd3558 -> the entire movie establishes a wonderfully creepy mood .
[0.82] e95c46b6-4e44-4fa8-894b-1d595bc7ed2e -> a deftly entertaining film , smartly played and smartly directed .
[0.82] 65105ae3-32fd-449a-b0ec-414a8d487abf -> my little eye is the best little " horror " movie i've seen in years .
[0.81] e8635913-5f1f-474c-9ad2-9a46ea725483 -> a properly spooky film about the power of spirits to influence us whether we believe in them or not .
[0.81] d0a79c1c-cd0b-41f1-b48b-afdca5121811 -> an elegant film with often surprising twists and an intermingling of naiveté and sophistication .
[0.81] 190ea188-c21e-4def-be0f-822b47b97b18 -> a perceptive , good-natured movie .
Index time: 22 ms.
Removing data folder /tmp/vulcanodb-test9777460824705212072
```

## VulcanoCli

This sample application creates an interactive shell you can use to index documents in a local folder, and then issuing
queries to find the most relevant files.

Use the following sequence to run the application:

- `mvn clean install`
- `cd vulcanodb-test`
- `mvn compile exec:java -Dexec.mainClass="es.nachobrito.vulcanodb.VulcanoCli"`

You will see this prompt:

```text
[VulcanoCli] Creating VulcanoDB instance witn data folder: /tmp/vulcanodb-test10694380951260025198
[VulcanoCli] Enter your query, I'll find relevant documents within the provided folder.
[VulcanoCli] You can also use the following commands:
[VulcanoCli] - load -> to load documents from a folder.
[VulcanoCli] - exit -> to close VulcanoDB.
```

Prepare a folder with text documents, and use the `load` command to index all of them. Then, just introduce a query to
get a list of the most relevant files.