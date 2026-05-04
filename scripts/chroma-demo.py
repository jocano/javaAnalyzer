#!/usr/bin/env python3
"""
ChromaDB demo — insert a few text embeddings and query by similarity.

ChromaDB is running locally in Docker:
  docker start chromadb          (if not already running)
  URL: http://localhost:8001

The client uses ChromaDB's built-in sentence-transformer model
(all-MiniLM-L6-v2) to turn raw text into embedding vectors automatically,
so no external embedding API key is needed.

Usage:
  python3 scripts/chroma-demo.py
"""

import chromadb
from chromadb.utils.embedding_functions import DefaultEmbeddingFunction

# ── 1. Connect to the local ChromaDB server ──────────────────────────────────

client = chromadb.HttpClient(host="localhost", port=8001)
print(f"ChromaDB version : {client.get_version()}")
print(f"Heartbeat        : {client.heartbeat()}\n")

# ── 2. Embedding function ─────────────────────────────────────────────────────
# Uses ChromaDB's built-in default model (all-MiniLM-L6-v2 via ONNX runtime).
# Downloaded once on first run and cached at ~/.cache/chroma/onnx_models/.
# No external API key or extra packages needed.

ef = DefaultEmbeddingFunction()

# ── 3. Create (or reuse) a collection ─────────────────────────────────────────

COLLECTION = "java_concepts"
collection = client.get_or_create_collection(
    name=COLLECTION,
    embedding_function=ef,
    metadata={"description": "Java / Spring architecture concepts"},
)
print(f"Collection '{COLLECTION}' — existing docs: {collection.count()}")

# ── 4. Insert documents ───────────────────────────────────────────────────────

documents = [
    "A Spring @RestController handles HTTP requests and returns JSON responses.",
    "A Spring @Service contains business logic and is managed by the IoC container.",
    "A Spring @Repository abstracts database access using JPA or JDBC templates.",
    "Dependency injection wires beans together automatically via constructors or fields.",
    "Maven is a build tool that manages Java project dependencies and lifecycle.",
    "JPA entities are mapped to database tables via @Entity and @Table annotations.",
]

ids = [f"doc-{i}" for i in range(len(documents))]
metadatas = [
    {"layer": "web",         "framework": "Spring MVC"},
    {"layer": "service",     "framework": "Spring"},
    {"layer": "persistence", "framework": "Spring Data"},
    {"layer": "core",        "framework": "Spring"},
    {"layer": "build",       "framework": "Maven"},
    {"layer": "persistence", "framework": "JPA"},
]

# upsert is idempotent — safe to re-run the script
collection.upsert(documents=documents, ids=ids, metadatas=metadatas)
print(f"Upserted {len(documents)} documents. Total in collection: {collection.count()}\n")

# ── 5. Similarity search ──────────────────────────────────────────────────────

queries = [
    "How does Spring handle HTTP endpoints?",
    "Where is business logic placed in a Spring app?",
    "How do you connect to a database in Spring?",
]

for query in queries:
    print(f"Query : \"{query}\"")
    results = collection.query(
        query_texts=[query],
        n_results=2,
        include=["documents", "distances", "metadatas"],
    )
    for doc, dist, meta in zip(
        results["documents"][0],
        results["distances"][0],
        results["metadatas"][0],
    ):
        score = 1 - dist          # cosine: distance → similarity
        print(f"  [{score:.3f}]  {doc}")
        print(f"           layer={meta['layer']}, framework={meta['framework']}")
    print()

# ── 6. Fetch a specific document by ID ───────────────────────────────────────

print("Fetch doc-0 by ID:")
item = collection.get(ids=["doc-0"], include=["documents", "metadatas"])
print(f"  document : {item['documents'][0]}")
print(f"  metadata : {item['metadatas'][0]}")
