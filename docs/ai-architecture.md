# AI Layer — Concept, Code, Experience and Integration

The companion to [`ai-build-guide.md`](ai-build-guide.md) (English) /
[`ai-build-guide-ar.md`](ai-build-guide-ar.md) (Arabic), which cover *what was decided and why*.
This one covers **how it is put together, what it does, and how to consume it.**

Every JSON payload in this document is a real captured response, not an illustration.

- [1. The concept](#1-the-concept)
- [2. The code](#2-the-code)
- [3. The experience](#3-the-experience)
- [4. Frontend integration](#4-frontend-integration)
- [5. Dashboard integration](#5-dashboard-integration)
- [6. Deployment integration](#6-deployment-integration)

---

## 1. The concept

### 1.1 The one idea

**The AI is an accelerator on top of the catalogue, never a replacement for it.**

Nothing in this layer is allowed to become a source of truth about the shop. Every price, every
stock level, every tasting note comes from PostgreSQL by way of an API. The model chooses *which*
products to surface and *how* to phrase it; it is never asked what a product costs.

### 1.2 Three principles behind every decision

**Search before asking.**
An assistant that opens with "what roast do you prefer?" has told the visitor it will not help yet,
when the catalogue could have answered immediately. The model searches on whatever vague detail it
was given and only asks when a search came back empty *and* the missing detail would change the
answer.

**Say nothing rather than guess.**
The system prompt forbids naming any product whose slug a tool did not return, and the tool contract
separates *"search is unavailable"* from *"nothing found"* so the model cannot fill an empty answer
with something plausible.

**Degrade, never break.**
Every capability is flag-gated. With AI off, endpoints return what they always returned. A failed
embedding call falls back to keyword ranking. A missing API key falls back to a local model. A
missing pgvector extension costs vector search and nothing else.

### 1.3 Bilingual by construction, not by translation

- The document projected into the vector store includes **every translation, labelled by its own
  language code**, so an Arabic query matches an Arabic `short_description`.
- Tasting notes, roast levels, processes and brewing methods contribute their **ASCII slugs**
  (`citrus`, `washed`, `v60`). That keeps the embedded text — and therefore `contentHash` —
  identical across locales, so switching the storefront language does not invalidate the index.
- The "why we picked this" reasons on `/similar` are assembled **server-side** from translated
  attribute names.
- Keyword search strips EN/AR stop-words, because Arabic particles attach to words and naive
  substring matching returns nothing.

### 1.4 What was deliberately not built

| Not built | Why |
|---|---|
| Orders, carts, refunds via chat | No tool, and the prompt refuses |
| A separate AI microservice | One flag and one package inside an existing modular monolith |
| Streaming responses | ~120-word replies; streaming would change the response shape for no gain |
| Per-request model selection | Provider is chosen once at startup by key presence |
| Automatic reconciliation of deleted products | Known gap — `indexAll()` is upsert-only |

---

## 2. The code

### 2.1 Package layout

```
com.nocteon.nocteon_api.ai
├── config/          provider selection, flags, the embedding-model fingerprint
├── controller/      three controllers, all thin
├── dto/request/     ChatRequest
├── dto/response/    ChatResponse, SimilarProductResponse, AiReindexResponse, AiIndexStatsResponse
├── exception/       AiUnavailableException
└── service/         everything that does work
```

`ProductSearchPredicate` is the exception: it lives in `product.repository.specification` because it
is product-domain query logic that happens to serve search.

### 2.2 The service classes and what each one owns

| Class | Responsibility | Why it is separate |
|---|---|---|
| `AiSearchService` | Facade over vector + keyword search | The only thing controllers know about |
| `SemanticProductSearch` | Embeds a query, queries pgvector | Returns candidate ids, never products |
| `ProductSearchPredicate` | Keyword matching over customer-facing attributes | 37 unit tests, no Spring context |
| `HybridRanker` | `0.6 × semantic + 0.4 × keyword` | Pure function |
| `SimilarProductService` | Vector path or attribute fallback | Owns the degradation decision |
| `AttributeSimilarityFallback` | Origin / roast / process / variety matching | Lets `/similar` work with AI off |
| `ProductDocumentFactory` | Projects a `Product` into document text + metadata | Pure projection, no I/O |
| `ProductDocumentIds` | The one definition of a vector document id | See [§2.4](#24-why-the-document-id-is-a-separate-concern) |
| `ProductIndexingService` | `indexAll()` / `upsert()` / `stats()` | Owns staleness via `contentHash` |
| `ProductIndexBootstrap` | Builds the index once on a fresh database | Closes the empty-index gap |
| `ProductIndexScheduler` | Nightly repair at 03:00 | Backstop; no-op when current |
| `AiChatService` | Builds the `ChatClient`, runs the turn, localises the fallback | Provider-agnostic |
| `AiChatFunctions` | The three `@Tool` methods | Delegates to `CatalogSearch` |
| `CatalogSearch` | The search tool's implementation | Where the hybrid ranker is invoked |
| `ProductCardBuilder` | Builds the cards both chat and `/similar` return | One card shape for both |
| `AiChatSessionStore` | Redis conversation memory | Fails soft — a Redis outage costs memory, not the answer |
| `EmbeddingTaskPrefix` | The retrieval envelope on both sides | One definition, so they cannot drift |

### 2.3 The hybrid ranking

```
final = 0.6 × semantic + 0.4 × keyword
```

Semantic alone returns near-identical scores for every coffee (measured: 0.7153 vs 0.7151) because
their text was nearly identical. Keyword alone misses synonyms entirely. Semantic is weighted
higher because it is the capability that justifies the feature.

### 2.4 Why the document id is a separate concern

`ProductDocumentIds` is the only place that maps a product to a vector-store row.

Spring AI's `PgVectorStore` parses every document id with `UUID.fromString`, so the value is a
**deterministic v3 UUID derived from the slug** — stable across restarts, so `upsert()` overwrites
rather than duplicates, and `remove(slug)` deletes exactly the row that `upsert()` wrote.

The human-readable identity moved into metadata:

```java
metadata.put("productId", product.getId());   // 1, 2, 3, 4 — the real id
metadata.put("slug", product.getSlug());
metadata.put("contentHash", ...);
metadata.put("embeddingModel", ...);
```

Every consumer reads identity from metadata. None of them parse the document id, which is why the id
can be opaque.

### 2.5 The content hash is the change detector

`contentHash = sha256(embeddingModel + "\n" + builtText)`

Any product edit makes the stored hash stale and the next `indexAll()` re-embeds exactly that
product. Verified live: editing one product's description produced `indexed=1, skipped=6` and a hash
change from `e34afb…` to `120bbb…`.

Because the hash also covers the fingerprint, swapping the embedding model invalidates every
document at once.

### 2.6 Provider selection without a bean

```java
String provider = hasGroqApiKey(environment) ? "openai" : "ollama";
```

This cannot be a `@Bean` or a YAML property: Spring AI gates every provider with
`matchIfMissing = true`, so two starters on the classpath both define a `ChatModel`; and YAML cannot
express "if and only if a key exists".

The same class **rejects a reasoning model at startup**, because one breaks tool calling on Groq and
the failure is otherwise invisible until a customer hits it.

---

## 3. The experience

### 3.1 What a visitor sees — captured responses

**Arabic, chocolatey coffee, no session supplied:**

```jsonc
{
  "data": {
    "reply": "عندك قهوتين شوكولاتة ممتازتين:\n\n- **brazil-mogiana**: تحميص متوسط غامق، نوتات شوكولاتة وكراميل ومكسرات. السعر 450–954، متوفرة.\n- **colombia-huila**: تحميص متوسط، شوكولاتة ومكسرات ولمسة تفاح. السعر 525–1113، متوفرة.",
    "products": [
      {
        "id": 4,
        "slug": "brazil-mogiana",
        "categorySlug": "coffee",
        "productType": "COFFEE",
        "featured": true,
        "name": "البرازيل موجيانا",
        "shortDescription": "قهوة شوكولاتة عميقة وبندق بحمضية منخفضة",
        "primaryImageUrl": "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=800",
        "minPrice": 450.0,
        "maxPrice": 954.0,
        "discountPercentage": 13,
        "active": true
      }
    ],
    "sessionId": "6df83ba2-32a8-4b21-be3c-b4ba6c5f8510"
  },
  "errors": null, "message": null, "success": true,
  "timestamp": "2026-10-10T14:22:31.402118Z"
}
```

**Text plus cards**, never text alone. The cards are the ground truth the sentence describes.

**English, similar products:**

```jsonc
{ "data": [
    { "product": { "id": 3, "slug": "kenya-nyeri", "name": "Kenya Nyeri" },
      "reason": "Notes: Citrus, Honey · Category: Coffee", "score": 0.862 },
    { "product": { "id": 2, "slug": "colombia-huila", "name": "Colombia Huila" },
      "reason": "Category: Coffee", "score": 0.828 }
  ], "success": true }
```

**The same endpoint with `Accept-Language: ar`:**

```jsonc
{ "product": { "name": "كينيا نييري" },
  "reason": "النكهات: حمضيات, عسل · الفئة: القهوة", "score": 0.862 }
```

### 3.2 The failure experience

| Situation | What the visitor gets |
|---|---|
| AI disabled entirely | `503` with a localised message |
| Chat model unreachable | A localised apology, `200 OK`, no exception |
| Embedding provider down | Keyword ranking still returns results |
| Search returned nothing | "I could not find one", and adding an alternative is forbidden |
| Product does not exist | Refused outright, zero cards |
| pgvector missing | Keyword search only; everything else unaffected |

**No AI failure ever produces a 500 to the visitor.**

Verified: a fabricated product request returned a refusal and zero cards.

### 3.3 Latency and cost shape

| Operation | Cost |
|---|---|
| `?semantic=true` | 1 embedding call + 1 vector query + 1 SQL |
| `/similar` | 1 embedding call + 1 vector query |
| Chat turn | 2–4 LLM round trips |
| Index rebuild | 1 embedding call **per product**, batched |

---

## 4. Frontend integration

> There is **no frontend in this repository.** This is the Spring Boot API. The storefront is a
> separate application (`FRONTEND_URL`, Angular on `:4200`, React on `:3000`).

### 4.1 Endpoints available to the storefront

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/api/products?semantic=true&search=…` | public | Semantic + hybrid search |
| `GET` | `/api/products/{slug}/similar` | public | Similar products with reasons |
| `POST` | `/api/ai/chat` | public | Send a message |
| `GET` | `/api/ai/chat/history?sessionId=…` | public | Replay the conversation |
| `DELETE` | `/api/ai/chat/session?sessionId=…` | public | Clear it |

All five are `permitAll()` in `SecurityConfig`.

### 4.2 The envelope

```jsonc
{ "data": …, "errors": null, "message": null, "success": true, "timestamp": "…" }
```

Check `success`, not HTTP status alone — the API returns `200` with a localised apology when the
chat model is down, deliberately, so a chatbot outage is not an error page.

### 4.3 Chat: the session contract

```ts
// sessionId is optional on the FIRST call only.
const res = await fetch(`${API}/api/ai/chat`, {
  method: "POST",
  headers: { "Content-Type": "application/json", "Accept-Language": "ar" },
  body: JSON.stringify({ message, sessionId, locale: "ar" }),
});
const { data } = await res.json();
sessionId = data.sessionId;   // ← persist this; the backend generates a UUID if you omit it
```

Keep it in `sessionStorage`, not `localStorage` — a stale key points at a conversation Redis may
already have dropped.

Locale resolves in this order: the `locale` field → the `Accept-Language` header →
`LocaleContextHolder`. Sending the header alone is sufficient.

### 4.4 Rendering advice

**Render `data.products` as cards, not as text parsed out of `data.reply`.** The reply is prose that
happens to contain slugs in backticks; the cards are meant to be rendered.

One card shape serves both features — `/similar` wraps the same object in `{product, reason, score}`:

```ts
{ id, slug, categorySlug, productType, featured, name, shortDescription,
  primaryImageUrl, minPrice, maxPrice, discountPercentage, active }
```

Render `score` as a relative confidence, never as a number. `reason` is a pre-built localised string;
do not re-render it from `score`.

### 4.5 Degradation the client must handle

| Case | Client behaviour |
|---|---|
| `success: false` on chat | Render the message; not an error dialog |
| `products: []` | Empty state — the catalogue genuinely has no match |
| `503` on chat | AI is off server-side; hide the chat widget |
| Semantic on, no results | Falls back to keyword automatically |

No client-side AI logic is required. That is the point of the server-side flags.

### 4.6 CORS

Configured through `CORS_ALLOWED_ORIGINS` (comma-separated).

---

## 5. Dashboard integration

Two admin endpoints requiring **a new permission: `ai:manage`** — deliberately not `product:update`,
because re-embedding the catalogue is an operational action, not a content edit. It is ADMIN-only:
`Role.ADMIN` is `Set.of(Permission.values())`, while `MODERATOR` and `CUSTOMER` list permissions
explicitly.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/dashboard/ai/reindex` | Re-embed everything stale |
| `GET` | `/api/dashboard/ai/stats` | Current index health |

Verified: without a token, `/api/dashboard/ai/stats` returns **401**.

```jsonc
// POST /api/dashboard/ai/reindex
{ "data": { "indexed": 4, "skipped": 3, "durationMs": 3144 }, "success": true }

// GET /api/dashboard/ai/stats
{ "data": { "indexed": 7, "total": 7, "stale": 0, "lastRunAt": "2026-10-10T14:22:31Z" }, "success": true }
```

### Reading the numbers

| `indexed` | `stale` | Meaning |
|---|---|---|
| 7 | 0 | Healthy. Nothing to do. |
| 0 | 4 | Content changed. Press reindex. |
| 0 | 0 | **Search is switched off.** No index is being maintained. |

That last row looks healthy if you only read `stale`, and is worth surfacing in a dashboard.

### When an operator needs to press reindex

| Trigger | Automatic? |
|---|---|
| Fresh deployment | **Yes** — `ProductIndexBootstrap` |
| Product created or edited | **Yes** — the mutation path calls `upsert()` |
| Embedding model changed | **Yes** — the fingerprint is inside `contentHash` |
| A previous run failed | Nightly at 03:00, or press reindex |
| Migrations that change indexed text | **No** — press reindex once |

---

## 6. Deployment integration

### 6.1 Required configuration

```yaml
# docker-compose.yml
db:
  image: pgvector/pgvector:pg16
```

```bash
# .env — both optional
GEMINI_API_KEY=…     # embeddings
GROQ_API_KEY=…       # chat
```

**Ollama is behind a compose profile**, so `docker compose up -d` starts Postgres and Redis and
never pulls its ~1 GB image:

```bash
docker compose up -d                        # no model downloads
docker compose --profile local-ai up -d     # the zero-key fallback
```

### 6.2 Flags

| Flag | Default | Effect |
|---|---|---|
| `AI_ENABLED` | `false` | Master switch |
| `AI_SEARCH_ENABLED` | `false` | `?semantic=true`, the vector path of `/similar` |
| `AI_CHATBOT_ENABLED` | `false` | The three `/api/ai/chat` endpoints |

### 6.3 Startup sequence, verified

Against a database created from nothing:

```
V103: pgvector extension is available and has been enabled.
Successfully applied 15 migrations to schema "public", now at version v105
ProductIndexBootstrap : Built the AI vector index on startup: indexed=7, skipped=0, took=3905ms
Started NocteonApiApplication in 14.4 seconds
```

And with **Ollama absent entirely** — no container, no image — the app booted, Gemini embeddings
worked, and the log contained **zero** failed connection attempts.

### 6.4 On Render

`render.yaml` declares all three flags on, `GEMINI_API_KEY` as `sync: false`, and pins
`postgresMajorVersion: "16"` because pgvector requires PostgreSQL 13 or later.

If `GEMINI_API_KEY` is unset the deployment still boots and search falls back to keyword-only. If
pgvector is missing, `V103` warns rather than failing.

### 6.5 Operational cost

| | Free tier |
|---|---|
| Groq chat | Generous; the only LLM consumer |
| Gemini embeddings | Once per product on reindex, once per query on search |
| pgvector | Local Docker image — no managed service required |

A full reindex of a 7-product catalogue took ~3.1 s. Cost scales with catalogue size, not traffic,
because searches do not re-embed.

---

## Related

| Document | Contents |
|---|---|
| [`ai-build-guide.md`](ai-build-guide.md) | Full build record in English, including all eight defects |
| [`ai-build-guide-ar.md`](ai-build-guide-ar.md) | The same guide in Arabic |
| [`ai-golden-queries.md`](ai-golden-queries.md) | 20 measured queries (10 EN, 10 AR) |
| [`ai-load.js`](ai-load.js) | k6 script for the search and chat paths |

> The golden-query figures were measured against the **previous** embedding model. Re-run them before
> quoting the numbers anywhere.