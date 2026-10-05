# AI golden queries, chatbot scenarios and load results

Recorded **2026-10-05** against the `ai-integration` worktree at `4f68a56` plus the two
blocking fixes described in [Verification blockers](#verification-blockers).

Everything in this file is copied from a live run. Nothing is projected, modelled or
hand-written to fill a gap. Where something could not be run, it says **BLOCKED** and
gives the reason.

---

## The machine this was recorded on

| | |
|---|---|
| CPU | AMD Ryzen 5 4600H, 6 cores / 12 threads, **no GPU** |
| RAM | 22 GB |
| Ollama | 0.34.3, native (not in Docker), model inference on CPU |
| Postgres | 18.6, native, **no `vector` extension and none available** |
| Redis | 7, native, no password |
| Chat model | `qwen2.5:3b-instruct`, Q4_K_M, 3.1B params, 2.0 GB resident |
| Embed model | `nomic-embed-text`, 274 MB on disk |
| App | `./mvnw spring-boot:run`, JDK 21.0.2 |
| Catalog | **7 products** — 4 coffees, 3 non-coffee equipment items |

The catalog is the seed from `V102__seed_products_complete.sql` and is the entire
catalog: `ethiopia-yirgacheffe`, `colombia-huila`, `kenya-nyeri`, `brazil-mogiana`,
`hario-v60`, `chemex-6cup`, `aeropress`.

Two facts about that catalog dominate everything below:

1. **Four coffees.** There is one light roast, one washed, one Gesha. Relevance ranking
   over four items cannot be graded meaningfully.
2. **All four coffees share the tasting notes Chocolate / Caramel / Berry**, so the
   documents `ProductDocumentFactory` produces are near-identical apart from origin and
   roast. This is seed data, not a product bug, but it makes the vector round trip
   untestable even in principle on this dataset.

---

## Verification blockers

Two defects made the application **impossible to boot**. Both are fixed in this branch;
without them nothing else in this document could be measured.

### 1. `AiChatSessionStore` injected a Jackson 2 `ObjectMapper`

```
Parameter 1 of constructor in
  com.nocteon.nocteon_api.ai.service.AiChatSessionStore
required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper'
that could not be found.
```

Spring Boot 4 auto-configures **Jackson 3** (`tools.jackson.databind.json.JsonMapper`).
Jackson 2 (`com.fasterxml.jackson.core:jackson-databind:2.21.4`) is on the classpath
only transitively, via `io.jsonwebtoken:jjwt-jackson`, and no bean of that type is ever
registered. Every `spring-boot:run`, every packaged boot and every `@SpringBootTest`
would have failed.

`RestAuthenticationEntryPoint` already used `tools.jackson.databind.ObjectMapper`;
`AiChatSessionStore` was the only Jackson 2 holdout. Fixed by switching the import.
**This defect was invisible to the test suite because `NocteonApiApplicationTests` is
excluded from the 343-test run** — the whole suite is unit tests with Mockito, and no
test constructs the Spring context. Restoring that coverage is worth a follow-up.

### 2. `searchProducts(String, int)` made every tool call fail

```
java.lang.IllegalArgumentException: java.lang.NullPointerException: Cannot invoke
  "java.lang.Number.intValue()" because the return value of
  "sun.invoke.util.ValueConversions.primitiveConversion(...)" is null
  at java.base/jdk.internal.reflect.Method.invoke(Method.java:580)
  at org.springframework.ai.tool.method.MethodToolCallback.callMethod(...:186)
  at org.springframework.ai.model.tool.DefaultToolCallingManager.executeToolCall(...:325)
  at org.springframework.ai.chat.client.advisor.ToolCallingAdvisor.adviseCall(...:182)
```

`ValueConversions.primitiveConversion` returns `null` only when the value handed to a
primitive parameter is `null` — so Spring AI passed a `null` where the tool method wanted
an `int`. Because the failure happens inside the tool-calling loop it discards the whole
turn, not the one argument: `AiChatService` caught it and returned the degraded
"Our coffee guide is taking a short break" reply.

It is not a rare edge case. Against this exact system prompt,
`qwen2.5:3b-instruct` **omitted `limit` on 4 of 5** `search_products` calls:

| Probe | `search_products` arguments from the live model |
|---|---|
| `What coffee for a V60 pour over?` | `{"query": "V60 pour over"}` — no `limit` |
| `Recommend a fruity light roast coffee` | `{"query": "fruity light roast"}` — no `limit` |
| `Tell me about Ethiopian coffee` | `{"query": "Ethiopian coffee"}` — no `limit` |
| `I want a gift for someone who loves espresso` | *(no tool call emitted at all)* |
| Same question, short system prompt instead | `{"query": "espresso", "limit": 3}` |

Fixed by taking `Integer limit` and defaulting a missing value, so the omission costs a
default rather than the visitor's answer. The underlying library behaviour — a primitive
tool parameter turning a missing JSON key into a turn-killing NPE — is worth reporting
upstream.

### What is still BLOCKED

| Blocked | Why |
|---|---|
| Semantic search relevance (the 20-query golden set) | Needs a live `PgVectorStore`. `pg_available_extensions` has no `vector`; no sudo; Docker daemon down. See [the round trip](#the-vector-round-trip). |
| `POST /api/dashboard/ai/reindex` and `/stats` | Need `ai:manage`. The seeded admin's Argon2i hash is in `V101__seed_users.sql` but the plaintext password is not documented anywhere in the repo. Both return **401** unauthenticated. |
| Index staleness after reindex (`stale: 0`) | Depends on reindex. |
| Blender/import path validation | Not part of this task. |

---

## The 20 golden queries

Each query was sent twice — `semantic=true` and `semantic=false` — to
`GET /api/products?search=<q>&semantic=<flag>&size=10&page=0`, with
`Accept-Language` matching the query language.

### The keyword baseline is empty for all twenty

Every one of the twenty queries returned **`totalElements: 0`, HTTP 200** with
`semantic=false`. That is not an AI result — it is the pre-existing keyword search.

`ProductSpecifications.withFilters` builds the search predicate as:

```java
cb.like(cb.lower(translations.get("name")), "%" + filter.getSearch().toLowerCase() + "%")
```

A `LIKE` over the **whole search string against the translated product name only**. So
`"ethiopian coffee"` looks for the literal substring `ethiopian coffee` in
`Ethiopia Yirgacheffe` and finds nothing, and `"قهوة إثيوبية"` looks for that phrase in
`إثيوبيا يرقاشيف` and finds nothing. Single tokens do work — `Ethiopia` → 1 hit,
`Yirgacheffe` → 1 hit, `v60` → 1 hit.

So there is **no keyword baseline to improve on**, and `semantic=true` returns the same
empty set because the vector path is blocked. The comparison the brief asks for cannot be
run, and no number in this table should be read as a relevance judgement.

| # | Lang | Query | `semantic=false` | `semantic=true` | Verdict |
|---|---|---|---|---|---|
| 1 | EN | `fruity light roast` | 200, 0 results | 200, 0 results | **BLOCKED** — no pgvector; keyword baseline also empty |
| 2 | EN | `chocolatey dark roast` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 3 | EN | `ethiopian coffee` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 4 | EN | `best for espresso` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 5 | EN | `sweet and floral` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 6 | EN | `coffee for pour over` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 7 | EN | `fruity and tea-like` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 8 | EN | `bold and smoky` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 9 | EN | `coffee with caramel notes` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 10 | EN | `bright and citrusy` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 11 | AR | `قهوة إثيوبية` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 12 | AR | `تحميص فاتح` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 13 | AR | `قهوة بالشوكولاتة` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 14 | AR | `مناسبة للإسبريسو` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 15 | AR | `قهوة زهرية` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 16 | AR | `قهوة مقطّرة` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 17 | AR | `محمصة غامقة` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 18 | AR | `نكهة فواكه` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 19 | AR | `قهوة برية` | 200, 0 results | 200, 0 results | **BLOCKED** — same |
| 20 | AR | `قهوة خفيفة` | 200, 0 results | 200, 0 results | **BLOCKED** — same |

**0 of 20 improved.** That is not a 0/20 quality score — it is 20 unmeasurable rows. The
spec's "fewer than 15/20 improve" gate cannot be evaluated until there is a pgvector host
with a catalog larger than four coffees.

### Arabic returns Arabic names — verified, separately

The brief also asks to confirm Arabic queries yield Arabic `name` values. This is a
translation question, not a search question, so it was checked against the unfiltered
catalog listing with `Accept-Language: ar`, where 7 of 7 products came back localized:

```
ethiopia-yirgacheffe | إثيوبيا يرقاشيف
colombia-huila      | كولومبيا هويلا
kenya-nyeri         | كينيا نييري
brazil-mogiana      | البرازيل موجيانا
hario-v60           | هاريو V60
chemex-6cup         | كيميكس 6 أكواب
aeropress           | إيروبرس
```

PASS.

---

## The vector round trip

`pg_available_extensions` has no `vector`, and no `vector_store` table exists:

```
 name | default_version | installed_version
------+-----------------+-------------------
(0 rows)

 vector_extension_installed  0
 vector_store_table          0
```

`V103__enable_pgvector.sql` is a single `CREATE EXTENSION IF NOT EXISTS vector;` and it
has not been applied. **This was left that way deliberately: applying it would have
written a failed Flyway row into the developer's `nocteon_db`.** Every run in this
document booted with `--spring.flyway.enabled=false` and
`--spring.ai.vectorstore.pgvector.initialize-schema=false` as a diagnostic override. Both
are runtime arguments only; neither is committed.

### What the enabled-but-unreachable path actually does

With `AI_ENABLED=true AI_SEARCH_ENABLED=true` and no pgvector:

```
GET /api/products?search=Ethiopia&semantic=true   → 200, 1 keyword result
GET /api/products/ethiopia-yirgacheffe/similar   → 200, attribute-fallback rail
```

Server log, message-only by design:

```
WARN AiSearchService : Semantic search unavailable, falling back to keyword only:
  PreparedStatementCallback; bad SQL grammar [SELECT *, embedding <=> ? AS distance
  FROM public.vector_store WHERE embedding <=> ? < ?  ORDER BY distance LIMIT ? ]
```

This is the designed degradation, and it holds: a downed vector store costs ranking, not
the page. No 5xx on either endpoint.

It also proves the **application-to-Ollama embedding path works**. The failure is a SQL
grammar error against `public.vector_store`, which means the query was already built —
`nomic-embed-text` was reached, returned a vector, and `PgVectorStore` handed it to the
database.

---

## The 768-dimension check

The schema declares `vector(768)` (`spring.ai.vectorstore.pgvector.dimensions: 768`). A
dimension mismatch would fail at insert time, on the first reindex, on a machine nobody
was watching. Checked directly against the live model:

```bash
curl -s http://localhost:11434/api/embed \
  -d '{"model":"nomic-embed-text","input":["fruity light roast"]}'
```

```
num embeddings: 1
embedding 0 dimensions: 768
```

**PASS.** `nomic-embed-text` emits exactly 768 float32 dimensions, matching the schema.

---

## The rollback proof

Spec §14 requires that turning the AI off leaves the storefront working. Verified with
`AI_ENABLED=false AI_SEARCH_ENABLED=false AI_CHATBOT_ENABLED=false`:

```
GET /api/products/ethiopia-yirgacheffe/similar?limit=8          → 200
```

```
brazil-mogiana   score 0.5   Notes: Chocolate, Caramel, Berry · Process: Washed · Category: Coffee
colombia-huila   score 0.4   Notes: Chocolate, Caramel, Berry · Category: Coffee
kenya-nyeri      score 0.4   Notes: Chocolate, Caramel, Berry · Category: Coffee
hario-v60        score 0.0   More from our collection
chemex-6cup      score 0.0   More from our collection
aeropress        score 0.0   More from our collection
```

Same call with `Accept-Language: ar`:

```
brazil-mogiana   البرازيل موجيانا    النكهات: شوكولاتة, كراميل, توت · المعالجة: مغسولة · الفئة: القهوة
colombia-huila   كولومبيا هويلا     النكهات: شوكولاتة, كراميل, توت · الفئة: القهوة
kenya-nyeri      كينيا نييري        النكهات: شوكولاتة, كراميل, توت · الفئة: القهوة
hario-v60        هاريو V60          المزيد من مجموعتنا
chemex-6cup      كيميكس 6 أكواب    المزيد من مجموعتنا
aeropress        إيروبرس            المزيد من مجموعتنا
```

**PASS.** 200, six results, reasons present and localized, source product excluded. No
500, no empty array.

Chat with the flags off is a 503 with a localized message, which is the intended
"the assistant is not available" answer rather than a degradation:

```
POST /api/ai/chat  → 503  "AI features are temporarily unavailable"
POST /api/ai/chat  → 503  "ميزات الذكاء الاصطناعي غير متاحة مؤقتًا"   (Accept-Language: ar)
```

### One quality problem the rollback proof exposed

The attribute fallback ranked **three non-coffee items into a coffee product's rail** at
score 0.0 with the neutral reason `More from our collection`. A product page for the
Yirgacheffe recommending a Chemex is not a *correctness* failure — the reason is honest
and the score is zero — but it is a poor recommendation on the one page where a shopper is
already looking at something specific.

Two contributing causes, both visible here:

* All four coffees share identical tasting notes in the seed data, so no coffee scores
  above 0.5 and everything else collapses to the neutral bucket.
* `AttributeSimilarityFallback` appears to score a zero-overlap candidate rather than
  dropping it. Worth a look, but it is a product decision rather than a defect.

---

## The five chatbot scenarios

Run against the live `qwen2.5:3b-instruct` through `POST /api/ai/chat`, with
`AI_ENABLED=true AI_CHATBOT_ENABLED=true` and **`AI_SEARCH_ENABLED=false`** — the vector
store is unavailable on this host, so `search_products` correctly returns `[]` for every
call. The model therefore had real tools and no data, which is exactly the degraded case
worth testing.

Every reply below is verbatim. `products` is the product rail the endpoint returned;
it was empty in all five, because `search_products` returned nothing.

### Scenario 1 — `What coffee for a V60 pour over?` (should name searched products)

**PASS** (2.4 s)

> I couldn't find any specific coffee for V60 pour over. You might want to explore our
> regular coffee offerings for the perfect match.

Named products: none. No fabrication. **But** this scenario is not fully satisfied — the
requirement is "must name products returned by `search_products`", and `search_products`
returned nothing, so the scenario degrades to "must not invent". See
[the fabrication rate](#the-fabrication-rate) for the run where this same scenario failed.

### Scenario 2 — `I want a gift for someone who loves espresso` (2–3 products, reasons)

**PARTIAL FAIL** (1.6 s)

> I couldn't find any specific espresso gifts in our catalog. However, we have a great
> espresso coffee that might suit their taste. Would you like to know more about it?

It states it found nothing and then asserts, one sentence later, that the catalog has an
espresso coffee it can offer — an uncatalogued product claim. It invented no name and no
price, and it asked rather than asserted a recommendation. Still a violation of "If a
tool returns no matches, say that you could not find one. Do not suggest something else
from memory."

### Scenario 3 — `What does washed processing mean?` (educational, no invented products)

**PASS** (0.65 s)

> Washed processing involves removing the coffee cherry from the bean and then washing the
> beans to remove the remaining cherry residue. This method is common for producing
> high-quality coffee with a clean cup.

Correct, no products, no fabrication. Note the model did **not** call `get_brewing_guide`;
the question is about processing rather than a brewing method, so no tool was required.

### Scenario 4 — `اقترح لي قهوة مناسبة للإسبريسو` (Arabic reply, Arabic product names)

**FAIL** (0.35 s)

> دليل القهوة لدينا في إجازة قصيرة. تصفح المجموعة في هذه الأثناء.

The degraded apology, in Arabic. **All four Arabic attempts across every run failed this
way.** The cause is upstream of the application: Ollama returns HTTP 200 with
`message.content: ""`, no `tool_calls`, and `eval_count: 23` — the model generated 23
tokens and Ollama surfaced neither text nor a parseable tool call.

The same question **without** tools produces fluent Arabic, so the model is not the
problem in isolation:

> من المهم أن تعرف أن الإسبريسو هو أقدم وأبسط أنواع القهوة، ويكون خالياً من الأكواب…

(nonsense about lemon syrup, but Arabic and fluent). And with a short system prompt the
same Arabic-shaped question emits a correct tool call. The full `AiChatService.SYSTEM_PROMPT`
appears to knock the 3B model off its tool-calling rails. **Arabic chatbot support is not
usable with this model and this prompt.**

### Scenario 5 — `What's the status of my order?` (must decline, point to support)

**PASS** (0.44 s)

> I'm sorry, but I don't have access to that information. You'll need to contact support
> for your order status.

Declined, pointed at support, named no order, no product, no delivery date. This is
spec Review Focus item 3 and it holds.

---

## The fabrication rate

This is the finding that matters most, so it was measured rather than observed once.
`temperature` is 0.7, so a single reply proves nothing. 16 live turns across four
prompts, scanning every reply for coffees, varieties and roasters that are not in this
catalog.

| Prompt | Runs | Degraded apology | Turned to fabrication |
|---|---|---|---|
| `What coffee for a V60 pour over?` | 4 | 0 | **2 of 4** |
| `I want a gift for someone who loves espresso` | 4 | 3 | 0 of 1 usable |
| `Recommend a bold dark roast coffee for espresso` | 4 | 0 | **3 of 4** |
| `اقترح لي قهوة مناسبة للإسبريسو` | 4 | 4 | 0 of 0 usable |

**5 of 12 usable English turns named products that do not exist**, against an explicit
instruction not to. Verbatim:

> I couldn't find any specific coffee designed for V60 pour over. **However, I recommend
> trying a bright and fruity coffee like Blue Moon Bourbon.** It's a good choice for a V60
> as it balances acidity and body well. Another option is a medium roast like **Santos**,
> which offers a smooth flavor profile suitable for this brewing method.

> I couldn't find any direct matches for V60 pour over. **However, I recommend checking out
> the Ethiopian Sidamo or the Guatemalan Pacaya.** Both are popular for V60 and should suit
> your brewing style.

> I couldn't find any coffee specifically described as bold, dark, and for espresso. How
> about trying a **Sumatra Mandheling**? It's a robust bean with a bold body that works
> well in espresso. Another option is a **Colombia Supremo**, which is also known for its
> bold profile.

> I couldn't find any coffees that match your request exactly. Let's try searching for a
> bold dark roast that might work well for espresso. How about the **Ethiopian Yirgacheffe
> from Blue Bottle Coffee**? It's bold and has a pleasant acidity.

> I couldn't find a specific match for a bold dark roast espresso. However, I found a
> robust dark roast coffee that might suit your preference: **slatka-9**. It's a good
> choice for espresso.

And with the search flag on but the vector store unavailable:

> I could not find any specific coffee designed for V60 pour over. Here are a couple of
> options that would work well for this method:
>
> 1. **Blue Bottle Coffee** — it's a great all-around option with a balanced profile.
> 2. **Intelligentsia** — known for their high-quality beans, it offers a smooth and
>    flavorful pour over experience.

### Slug cross-check, as the brief requires

```
$ for s in slatka-9 blue-moon-bourbon colombia-suprema ethiopia-yirgacheffe; do
    printf "%-24s " "$s"; curl -s -o /dev/null -w "%{http_code}\n" "…/api/products/$s"; done

slatka-9                 404   ← fabricated by the model
blue-moon-bourbon        404   ← fabricated by the model
colombia-suprema         404   ← fabricated by the model
ethiopia-yirgacheffe     200   ← real
```

**`slatka-9` is a fabricated product slug**, verbatim, in a customer-facing reply. That is
the single clearest failure of the brief's scenario 1.

### Reading this honestly

Three things bound what this proves.

* **The tool returned `[]` for every call.** Every fabrication above follows the pattern
  "I couldn't find X. However, I recommend Y" — the model honours the first half of the
  rule and violates the second half in the next sentence. Whether it behaves the same way
  when a tool returns **real** slugs is untested, and that path is blocked here.
* **The architecture cannot prevent this.** `AiChatFunctions` makes facts unavailable by
  giving the model no other source — but a name invented from the model's own weights is
  not a fact it can read, so "no way to make it up" only covers prices and availability,
  not names. The prompt is the only guardrail for names, and on a 3B model it does not hold.
* **`products` was empty every time.** The storefront would render the fabrication as
  prose with no card to contradict it. Nothing in the response would let a client tell a
  real recommendation from an invented one.

The English path is not shippable on `qwen2.5:3b-instruct` with this prompt. The Arabic
path does not function at all.

---

## Load check

`docs/ai-load.js` is the k6 script. **k6 is not installed on this machine**, so the numbers
below come from two Node scripts driving the same endpoints, the same concurrency and the
same `X-Real-IP` scheme the k6 script uses. They are not k6 numbers and are labelled as
such.

One thing every run hit first: `RateLimitFilter` applies the general
**200 requests/minute per IP** limit to everything not in `EXCLUDED_PATHS`, and
`/api/ai/chat` is not excluded. A load run from one address therefore measures the rate
limiter — the first attempt returned 50 × 200 and 43 659 × 429, with a p95 of 9 ms for a
stream of rejections. `RateLimitFilter` honours `X-Real-IP`, so both scripts and the k6
script vary it per VU. **Anyone reproducing this must do the same or they will measure 429s.**

### Search — 200 requests, 10 concurrency

| Flags | p50 | p95 | p99 | max | 5xx |
|---|---|---|---|---|---|
| `AI_SEARCH_ENABLED=false` | 32 ms | **142 ms** | 552 ms | 602 ms | 0 / 200 |
| `AI_SEARCH_ENABLED=true` (no pgvector) | 88 ms | **4 458 ms** | 6 948 ms | 9 128 ms | 0 / 200 |

Both 200 / 200 HTTP 200. **PASS** with the feature off, against the spec's 200 ms p95.

**FAIL** with the flag on: 4.5 s p95, **22× over target**. Every request embeds through
Ollama on CPU and then waits for a `vector_store` query that cannot succeed, so the tail is
embedding queueing, not ranking. This is the *degraded* number, not the intended one — with
a working pgvector the SQL would succeed and remove that part of the cost, but the CPU embed
per request remains and is the larger half.

### Chat — 50 concurrent sessions, 2 minutes (the brief's target load)

| | 10 VUs / 60 s | 50 VUs / 120 s |
|---|---|---|
| Completed turns | 112 | 212 |
| HTTP 200 | 112 (100 %) | 212 (100 %) |
| 5xx / transport errors | 0 | 0 |
| Degraded apology rate | 37.5 % | **43.9 %** |
| p50 | 5 389 ms | 29 248 ms |
| p95 | 10 081 ms | **48 958 ms** |
| p99 | 10 838 ms | 58 291 ms |

**FAIL** against "chat p95 first-token < 3 s" by **16×** at the target concurrency. No 5xx
at all, which is the one target met.

This endpoint is non-streaming, so the measured duration is time-to-complete-reply rather
than time-to-first-token; first token arrives earlier but the shopper cannot see anything
until the whole reply is done, so complete-reply is the number that matters.

Hardware, recorded rather than used to lower the target: **AMD Ryzen 5 4600H, 6 cores /
12 threads, no GPU**, Ollama 0.34.3 CPU inference, `qwen2.5:3b-instruct` Q4_K_M at 2.0 GB
resident. 50 concurrent 3B inferences on 6 CPU cores cannot reach 3 s, and no threshold was
adjusted to hide it. The target is reachable with a GPU host or a smaller model, and is not
reachable here.

---

## Summary

| Check | Result |
|---|---|
| Application boots | **FAIL before the fixes in this branch; PASS after** |
| `mvn test` (343 baseline) | **PASS**, 344 after the added test |
| `nomic-embed-text` emits 768 dimensions | **PASS** |
| `/similar` attribute fallback, AI off, EN + AR | **PASS** |
| `POST /api/ai/chat` 503 with flags off, EN + AR | **PASS** |
| Vector failure degrades to keyword, no 5xx | **PASS** |
| Arabic search returns Arabic names | **PASS** |
| Scenario 3 (educational) | **PASS** |
| Scenario 5 (account data declined) | **PASS** |
| Scenario 1 (names searched products) | **PARTIAL** — correct, but no products existed to name |
| Scenario 2 (2–3 products with reasons) | **PARTIAL FAIL** — makes an uncatalogued product claim |
| Scenario 4 (Arabic reply) | **FAIL** — Ollama returns empty content for all 4 attempts |
| Slug hallucination | **FAIL** — `slatka-9`, a fabricated slug, in a live reply |
| 20-query semantic relevance | **BLOCKED** — no pgvector, and the keyword baseline is empty too |
| Reindex / index stats / staleness | **BLOCKED** — needs `ai:manage`, seeded admin password unknown |
| Search p95 < 200 ms | **PASS** off / **FAIL** 4 458 ms on |
| Chat p95 < 3 s | **FAIL** 48 958 ms at the target load |
| 5xx under 1 % | **PASS** — zero 5xx across every run |