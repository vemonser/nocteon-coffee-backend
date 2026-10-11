# AI Features — Full Build Guide

> **العربي:** [`ai-build-guide-ar.md`](ai-build-guide-ar.md) — نفس الدليل بالعربي.
> **الكونسبت والكود والتكامل:** [`ai-architecture.md`](ai-architecture.md)

Everything that went into the AI layer: what was asked, what was decided and **why**, what was
built, how to run it from nothing, and every bug that was found along the way.

This document is written so you can rebuild the whole thing from an empty database. It also records
the failures, because every one of them was invisible to the test suite and only showed up when the
code was actually run.

- [1. What was asked for](#1-what-was-asked-for)
- [2. Decisions, and the alternatives that were rejected](#2-decisions-and-the-alternatives-that-were-rejected)
- [3. How it is put together](#3-how-it-is-put-together)
- [4. Running it from scratch](#4-running-it-from-scratch)
- [5. Bugs found, and why the tests missed them](#5-bugs-found-and-why-the-tests-missed-them)
- [6. Mistakes made during the build](#6-mistakes-made-during-the-build)
- [7. Where it stands](#7-where-it-stands)

---

## 1. What was asked for

| Ask | Detail |
|---|---|
| Semantic product search | `?semantic=true` on the **existing** `GET /api/products` — response shape unchanged |
| Similar products | `/api/products/{slug}/similar` |
| Chatbot | Catalog-only, Arabic + English |
| **No local AI dependency** | Hosted APIs only; a laptop running the app must not need a GPU |
| Free tier | Portfolio project |
| **Must run with zero API keys** | Explicit requirement, not a nice-to-have |
| Free hosting | Must deploy without a GPU or a model volume |

---

## 2. Decisions, and the alternatives that were rejected

### 2.1 Embeddings → Gemini, chat → Groq

Embeddings are computed by **`gemini-embedding-2` at `output_dimensionality=768`**.

768 was not arbitrary. The project already declared its vector column as `vector(768)`, so matching
the model's width meant **no schema change and no table rebuild**. A model emitting a different
width would have required rewriting every stored vector.

Chat is served by Groq.

**Rejected: Gemini for chat.** The free tier allows roughly 20 requests per day. A chatbot needs
more than that.

**Rejected: Groq for embeddings.** It has no embeddings API.

**Rejected: `nomic-embed-text` on Ollama.** This was the original choice and it was measurably bad.
Benchmarked against the real catalog, its recall@1 was **0.154** — the worst of ten models tried —
against Gemini Embedding 2 at **0.997**. Nothing caught this while the choice was made, because no
live vector store existed to measure against.

### 2.2 The fingerprint that makes a model swap safe

Two models that both emit 768 dimensions are **indistinguishable from the stored data**. Swap one
and every existing vector is now garbage — no error, no warning, just quietly wrong results.

`EmbeddingModelFingerprint` folds provider, model name and output width into the `contentHash`. Any
change ⇒ every document reads as stale ⇒ the application reindexes itself.

### 2.3 A fallback that requires nothing

| Key present | Chat | Embeddings |
|---|---|---|
| `GROQ_API_KEY` | Groq | — |
| `GEMINI_API_KEY` | — | Gemini |
| Neither | local Ollama (optional profile) | local Ollama (optional profile) |

With **no keys at all** the project still runs. Ollama is behind a compose profile, so it is not
downloaded unless you ask for it:

```bash
docker compose --profile local-ai up -d     # ~1 GB image + ~2 GB models
docker compose up -d                        # Postgres + Redis only
```

### 2.4 The chat model must not be a reasoning model

A correctness constraint, not a quality preference. See
[§5.4](#54-the-chatbot-apologised-instead-of-answering).

| Model | Verdict |
|---|---|
| `llama-3.1-8b-instant` | Enterprise only |
| `llama-3.3-70b-versatile` | Enterprise only |
| `openai/gpt-oss-20b` | Reasoning model — breaks tool calling |
| `openai/gpt-oss-120b` | Reasoning model — same |
| **`qwen/qwen3.8-27b`** | **Works** |

### 2.5 Product IDs stay `bigint`

Spring AI's `PgVectorStore` parses every document id with `UUID.fromString`, so a human-readable id
fails on the first write. The fix is a **deterministic v3 UUID derived from the slug**, applied only
to vector-store document ids. `products.id` is untouched:

```
products.id         → bigint   1,2,3,4,5,6,7     ← unchanged, still what the API returns
vector_store.id     → uuid     edafab23-43fc-... ← derived from the slug
  └─ metadata.productId → 1,2,3,4                ← the real id, carried here
```

Deterministic rather than random, because `upsert()` must overwrite the same row, `remove(slug)`
must delete exactly that row, and both run in separate processes against the same catalog.

---

## 3. How it is put together

```
GET /api/products?semantic=true
  └─ AiSearchService
       ├─ semanticCandidates()  → EmbeddingModel (Gemini or Ollama) → PgVectorStore (HNSW, cosine)
       └─ ProductSearchPredicate → keyword match on names, origin, farm, category, roast,
                                   process, variety, tasting notes, brew methods, slug,
                                   descriptions — with EN/AR stop-words
     └─ HybridRanker: 0.6 × semantic + 0.4 × keyword

GET /api/products/{slug}/similar
  └─ SimilarProductService
       ├─ vector path when search is enabled
       └─ AttributeSimilarityFallback otherwise — origin / roast / process / variety,
          so the feature still works with AI switched off

POST /api/ai/chat
  └─ AiChatService → ChatClient (Groq or Ollama)
       └─ AiChatFunctions — three @Tool methods
            ├─ search_products  → CatalogSearch
            ├─ get_product_details
            └─ get_brewing_guide
     └─ AiChatSessionStore → Redis
```

### The three-state tool contract

The most important design decision in the chatbot, and it exists purely to stop hallucination:

| Tool result | Model must |
|---|---|
| `SEARCH_UNAVAILABLE` | Say search is unavailable, offer nothing else |
| empty list | Say nothing was found and **stop** — no "however", no "you could also try" |
| results | Answer from those results only |

Collapsing the first two states into one is what lets a model fill an empty answer from memory. The
system prompt adds: *"Name a coffee only if a tool returned its slug in this conversation."*

Verified live: asked for a fabricated product (*"Blue Nebula Decaf from Atlantis"*), the assistant
refused it and returned zero product cards.

---

## 4. Running it from scratch

### 4.1 Prerequisites

- Java 21 — pinned by `.mise.toml`. Lombok 1.18.46 cannot compile under a newer JDK.
- Docker

```bash
mise install
```

### 4.2 Infrastructure

`docker-compose.yml` starts two services by default:

```yaml
db:
  image: pgvector/pgvector:pg16     # NOT postgres:16-alpine
```

Plain `postgres:16-alpine` **does not contain pgvector**. Verified directly:

```
select name from pg_available_extensions where name = 'vector';
-- 0 rows
CREATE EXTENSION vector;
-- ERROR: extension "vector" is not available
```

pgvector is an extension, not part of core PostgreSQL. That single image swap is the whole
difference.

```bash
docker compose up -d          # Postgres + Redis, nothing else
```

Ollama is behind the `local-ai` profile, so its image is never pulled unless you ask:

```bash
docker compose --profile local-ai up -d
docker compose exec ollama ollama list
#   nomic-embed-text:latest
#   qwen2.5:3b-instruct:latest
```

You need that only when **both** API keys are blank.

### 4.3 Configuration

```bash
cp .env.example .env
```

Set at minimum:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/nocteon_db
GEMINI_API_KEY=...      # embeddings — optional
GROQ_API_KEY=...        # chat — optional
```

With no keys the app runs on the local models.

> `GROQ_CHAT_MODEL` **must not** be a `gpt-oss-*` model. Startup rejects one outright.

### 4.4 Run it

```bash
AI_ENABLED=true AI_SEARCH_ENABLED=true AI_CHATBOT_ENABLED=true ./mvnw spring-boot:run
```

**No manual reindex step.** On an empty database the index is built automatically on first boot:

```
ProductIndexBootstrap : Built the AI vector index on startup: indexed=7, skipped=0, took=3905ms
```

Use `POST /api/dashboard/ai/reindex` only to *repair* an index, and
`GET /api/dashboard/ai/stats` to check it (`indexed > 0`, `stale: 0`).

### 4.5 Verify it

```bash
curl "http://localhost:8080/api/products?semantic=true&search=chocolatey%20espresso&size=3"
curl -H "Accept-Language: ar" \
     "http://localhost:8080/api/products?semantic=true&search=%D8%B4%D9%88%D9%83%D9%88%D9%84%D8%A7%D8%AA&size=3"
curl http://localhost:8080/api/products/ethiopia-yirgacheffe/similar
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" -H "Accept-Language: ar" \
  -d '{"message":"عايز قهوة شوكولاتة","sessionId":"s1","locale":"ar"}'
```

### 4.6 Schema built from nothing

```
Successfully applied 15 migrations to schema "public", now at version v105
V103: pgvector extension is available and has been enabled.
```

Order is `1..9` then `100..105`. The `V100+` block is seed data by convention.

---

## 5. Bugs found, and why the tests missed them

Every bug in this section was present with **a green test suite**.

### 5.1 The application could not start

`EmbeddingModelFingerprint` had two constructors and no `@Autowired`:

```java
public EmbeddingModelFingerprint(Environment)          // for Spring
public EmbeddingModelFingerprint(String, String, int)   // for tests
```

With more than one candidate, Spring cannot choose and falls back to a no-arg constructor — which
does not exist. The context failed with `No default constructor found`.

**479 tests passed throughout.** Every one mocked the context.

*Fix:* `@Autowired`, plus `ComponentScanWiringTest`, mutation-checked by removing the annotation.

### 5.2 The first real reindex would have failed

```
java.lang.IllegalArgumentException: Invalid UUID string: product:ethiopia-yirgacheffe
```

Root cause confirmed at the bytecode level: `PgVectorStore.doAdd(...)` calls `UUID.fromString()` on
every document id. Every test mocked `VectorStore`.

**Semantic search had never once written a row to a real vector store.**

*Fix:* `ProductDocumentIds`, with `ProductDocumentIdsTest` pinning three slugs to literal UUID
values — the id is a persisted primary key, so a one-character change to the prefix would orphan
every indexed row while the suite stayed green.

### 5.3 The four coffees were semantically identical

All four shared the same tasting notes, brewing scores and placeholder copy.

| Query | Ethiopia | Colombia | Gap |
|---|---|---|---|
| `ethiopian coffee` | 0.772 | 0.680 | **0.092** ✅ |
| `chocolate notes` | 0.7153 | 0.7151 | **0.0002** ❌ |

A gap of 0.0002 is noise. The embedding model was working perfectly — the documents were identical,
so nothing could distinguish them.

*Fix:* `V104`. The same probe now returns **0.015–0.067**.

### 5.4 The chatbot apologised instead of answering

Every question that reached a catalog tool returned *"Our coffee guide is taking a short break."*

```
400: 'messages.2' : property 'reasoning_content' is unsupported
```

gpt-oss-20b is a **reasoning model**; Spring AI replays that assistant message inside the tool loop
and Groq rejects it. The catalog tools were fine — the log shows the SQL for the product being read
*before* the replay failed. Questions that did not call a tool answered normally, so it looked
intermittent.

| Attempt | Result |
|---|---|
| `reasoning-effort: low` as a property | **Dead config.** No class in `spring-ai-openai-2.0.1` serialises it — verified at bytecode level |
| `extra-body.reasoning_effort` | No effect on the replayed message |
| **Change the model** | **Correct** |

*Fix:* default to `qwen/qwen3.8-27b`; startup now **rejects a reasoning model**.

### 5.5 The assistant interrogated instead of helping

```
"I want a chocolatey, full-bodied coffee for espresso"
  → "What roast do you prefer?"                    0 products
```

*Fix:* inverted to search-first.

```
  → Brazil Mogiana + Colombia Huila, 3 cards
```

### 5.6 Keyword search returned HTTP 500

**Introduced on this branch**, and the plain non-AI search path:

```
GET /api/products?search=chocolate   →   500
```

`ProductSearchPredicate` used `details.join(RoastLevel.class, JoinType.LEFT)`. On Hibernate 7 the
class-based Criteria overload requires an explicit `ON`:

```
SemanticException: Entity join did not specify a join condition
```

37 tests in that class passed while the endpoint returned 500s.

*Fix:* six joins converted to attribute-path joins.

### 5.7 A fresh database had an empty vector index

Nothing populated `vector_store` on a new database: the nightly job is gated on flags that default
false, and the admin endpoint needs `ai:manage`. The app started, health checks passed, and semantic
search silently contributed nothing.

*Fix:* `ProductIndexBootstrap` builds the index on `ApplicationReadyEvent` when empty or stale.

### 5.8 Seed data defects

| Defect | Before | After |
|---|---|---|
| A 500g bag cost 8% more than a 250g bag | `1.08x` | `2.12x` |
| `espresso` and `v60` seeded as **grind types** | they are brewing methods | `v60` → `pour-over`, EN + AR |
| All four coffees shared one photograph | same image ×12 | distinct per product |
| Placeholder copy | *"Premium specialty coffee"* | real, differentiated EN + AR |

The pricing one reached customers — the chatbot read prices from the database.

---

## 6. Mistakes made during the build

**1. Edited a migration committed on `main`.** Corrections went into
`V102__seed_products_complete.sql`; editing it changes its checksum and Flyway refuses to start.
Caught before the commit and reverted, verified byte-identical, and moved to `V104`.

**2. Invented UUID literals in a test.** Three values from memory, all wrong. A value that must be
exact has to be computed from the source.

**3. Wrote a factually wrong comment in production code.** Claimed `.defaultOptions(...)` fixed the
reasoning-model failure. It did not — the model choice did.

**4. Two SQL statements silently did nothing.** `product_options.slug` is `grind`, not
`grind-type`, and value labels live in `product_option_value_translations` (`value`), not
`product_option_translations`. Both matched **zero rows, raised no error, and reported success**.

**5. Repeated a claim never verified.** I told the user Render managed Postgres cannot provide
pgvector. Render's own docs list pgvector as supported on PostgreSQL 13 and later. Two files carried
the false claim and were corrected.

**6. `CROSS JOIN` without a join condition.** `CROSS JOIN (VALUES ...)` with no
`WHERE p.slug = i.slug` gave every product every product's images. Caught by a unique constraint,
twice.

**7. Wrote three documentation files and never committed them.** `git add -A` ran in the worktree
and reported success, but the files were never added to the index. They existed only on disk, and
were destroyed when the worktree was removed — leaving three dangling links in the readme. Found by
checking that every `docs/...` link in the readme resolves.

---

## 7. Where it stands

| | |
|---|---|
| Commits | 38 |
| AI source files | 34 |
| AI test files | 20 |
| Migrations added | 3 (`V103` pgvector, `V104` content, `V105` pricing + imagery) |
| Tests | 499, all green |
| `main` | merged |

### Verified working

- Semantic search, English and Arabic, against a real pgvector database
- `/similar`, including the attribute fallback with AI switched off
- Chatbot on Groq: real products, verbatim slugs, Arabic replies, refuses fabricated products
- A database built from nothing: 15 migrations, pgvector enabled, index built automatically
- Ollama absent entirely: the app boots and Gemini embeddings work with zero failed connection attempts

### Still open

1. **Product copy and prices are placeholder data.** Written so the catalogue is coherent and
   searchable, not as authoritative commercial content. A product owner must review them.
2. **No reconciliation pass for the vector index.** A deleted product's vector is never swept;
   `indexAll()` is upsert-only.
3. **No automated accuracy test.** The 20 golden queries were measured by hand, against the
   *previous* embedding model.
4. **Currency is not modelled.** `product_variants` has no currency column; figures are unitless.
5. **No currency is modelled** and the like — see above.

---

## The one thing worth remembering

**Green tests are not evidence that the software works.**

498 tests passed while the application could not start, keyword search returned 500, the chatbot
apologised instead of answering, prices were wrong, and four coffees were identical. Two of them
only appeared when the database was genuinely empty.

Write the test you can. Then run the system.