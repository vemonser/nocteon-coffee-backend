# دليل بناء مزايا الذكاء الاصطناعي — الشرح الكامل

> **English:** [`ai-build-guide.md`](ai-build-guide.md) — نفس الدليل بالإنجليزي.
> **الكونسبت والكود والتكامل:** [`ai-architecture.md`](ai-architecture.md)

هذا المستند هو السجل الكامل لكل ما مرّ على المشروع منذ لحظة طلبك إضافة الذكاء الاصطناعي: ما
المطلوب، ما تمّ اتخاذه من قرارات ولماذا، ما بُني فعليًا، كيف تشغّله من الصفر، والأخطاء التي ظهرت
أثناء العمل.

كُتب هذا المستند بحيث تكون قادر على إعادة بناء كل شيء من قاعدة بيانات فارغة.

- [١. المطلوب](#١-المطلوب)
- [٢. القرارات والبدائل المرفوضة](#٢-القرارات-والبدائل-المرفوضة)
- [٣. البنية البرمجية](#٣-البنية-البرمجية)
- [٤. التشغيل من الصفر](#٤-التشغيل-من-الصفر)
- [٥. الأخطاء التي وجدناها](#٥-الأخطاء-التي-وجدناها)
- [٦. أخطاء ارتكبتها أثناء البناء](#٦-أخطاء-ارتكبتها-أثناء-البناء)
- [٧. الوضع الحالي](#٧-الوضع-الحالي)

---

## ١. المطلوب

| الطلب | التفصيل |
|---|---|
| بحث دلالي في المنتجات | `?semantic=true` على `GET /api/products` الموجودة — **من غير** تغيير شكل الاستجابة |
| منتجات مشابهة | `GET /api/products/{slug}/similar` |
| مساعد محادثة | يعمل على الكتالوج فقط، بالعربي والإنجليزي |
| **من غير AI محلي إجباري** | الـ APIs لازم تكون مستضافة |
| خطة مجانية | مشروع بورتفوليو |
| **يشتغل من غير أي مفاتيح** | شرط أساسي مش رفاهية |
| استضافة مجانية | من غير GPU ومن غير volume للنماذج |

---

## ٢. القرارات والبدائل المرفوضة

### ٢.١ الـ embeddings من Gemini، والـ chat من Groq

الـ embeddings بيتم حسابها بـ `gemini-embedding-2` مع `output_dimensionality=768`.

**ليه 768 بالذات؟** لأن المشروع أصلاً معرّف عمود الـvector بتاعه `vector(768)`. يعني لو الموديل
أطلع نفس العرض، **مفيش تغيير في الجدول ولا إعادة بناء**.

**المرفوض: Gemini للـ chat.** الخطة المجانية بتسمح بـ ٢٠ طلب في اليوم تقريبًا.

**المرفوض: Groq للـ embeddings.** مفيش عنده API للـembeddings.

**المرفوض: `nomic-embed-text` على Ollama.** كان الاختيار الأصلي وكان **سيئ بشكل قابل للقياس**:
نسبة الإصابة الأولى **0.154** — أسوأ بكتير من عشرة موديلات جربناها — مقابل **0.997** لـ Gemini.

### ٢.٢ الـ fingerprint اللي بيخلّي تغيير الموديل آمن

تلات موديلات بيبعتوا ٧٦٨ بعد — **مش ممكن تفرّقهم من البيانات المخزّنة**. تبدّل الموديل، وكل
الـvectors الموجودة بقت **قرّرة**، ومن غير ولا error ولا warning.

`EmbeddingModelFingerprint` بيخلط اسم الـprovider والموديل وعدد الأبعاد جوه الـ `contentHash`.

### ٢.٣ Fallback مش محتاج حاجة

| المفتاح موجود | الـ chat | الـ embeddings |
|---|---|---|
| `GROQ_API_KEY` | Groq | — |
| `GEMINI_API_KEY` | — | Gemini |
| ولا مفتاح خالص | Ollama المحلي | Ollama المحلي |

المهم: **Ollama دلوقتي اختياري** ومقفول ورا profile في `docker-compose.yml`، فمش بينزّل صورته
(نحو ١ جيجا) ومفيش موديلات (نحو ٢ جيجا) غير لما تطلب:

```bash
docker compose up -d                        # Postgres + Redis بس
docker compose --profile local-ai up -d     # مع Ollama لو محتاجه
```

محتاج الـprofile بس لما **الاتنين مفاتيح** يبقوا فاضيين.

### ٢.٤ موديل الـ chat ما ينفعش يكون موديل تفكير

ده **قيد صحّة مش تفضيل جودة**، وأخطر حاجة اتعلمناها. شوف [القسم ٥.٤](#٥٤-المساعد-كان-يعتذر-بدل-ما-يجاوب).

| الموديل | النتيجة |
|---|---|
| `llama-3.1-8b-instant` | Enterprise بس |
| `llama-3.3-70b-versatile` | Enterprise بس |
| `openai/gpt-oss-20b` | موديل تفكير — بيكسر الأدوات |
| `openai/gpt-oss-120b` | موديل تفكير — نفس المشكلة |
| **`qwen/qwen3.8-27b`** | **شغال** |

### ٢.٥ الـ IDs بتاع المنتجات فضلت `bigint`

Spring AI's `PgVectorStore` بيعمل `UUID.fromString()` على كل document id. فالحل هو **UUID ثابت
مشتق من الـslug**، وده **فقط** لمعرّفات جدول الـvectors:

```
products.id         → bigint   1,2,3,4,5,6,7     ← زي ما هو، زي ما الـ API بيرجّعه
vector_store.id     → uuid     edafab23-43fc-... ← مشتق من الـslug
  └─ metadata.productId → 1,2,3,4                ← الـ id الحقيقي، متحفظ هنا
```

---

## ٣. البنية البرمجية

```
GET /api/products?semantic=true
  └─ AiSearchService
       ├─ semanticCandidates()  → EmbeddingModel → PgVectorStore (HNSW، cosine)
       └─ ProductSearchPredicate → بحث بالكلمة على كل الصفات
     └─ HybridRanker: 0.6 × دلالي + 0.4 × كلماتي

GET /api/products/{slug}/similar
  └─ SimilarProductService
       ├─ المسار الدلالي لما البحث يكون مفعّل
       └─ AttributeSimilarityFallback غير كده

POST /api/ai/chat
  └─ AiChatService → ChatClient
       └─ AiChatFunctions — تلات أدوات
     └─ AiChatSessionStore → Redis
```

### عقد الأدوات ذي الـ ٣ حالات

ده أهم قرار في الـchatbot، وموجود **منعاً للهلوسنة**:

| نتيجة الأداة | الموديل لازم يعمل إيه |
|---|---|
| `SEARCH_UNAVAILABLE` | يقول البحث مش متاح، ويقترح **ولا حاجة** تانية |
| لستة فاضية | يقول مالقيتش حاجة و**يقف** — من غير "however" ولا "ممكن تجرب كمان" |
| فيه نتايج | يجاوب من النتايج دي بس |

**لو دمجنا الحالة الأولى والتانية في واحدة، الموديل هيملا الفاضي من الذاكرة ويخترع.**

اتأكدت عمليًا: طلبت منه منتج متخيل (*"Blue Nebula Decaf from Atlantis"*)، **رفض يخترعه**، ورجّع
صفر بطاقات.

---

## ٤. التشغيل من الصفر

### ٤.١ المطلوب قبل ما تبدأ

- Java 21 — مثبّت في `.mise.toml`
- Docker

```bash
mise install
```

### ٤.٢ البنية التحتية

`docker-compose.yml` بيشغّل خدمتين افتراضياً:

```yaml
db:
  image: pgvector/pgvector:pg16     # مش postgres:16-alpine
```

صورة `postgres:16-alpine` **مالهاش pgvector**. اتأكدت بنفسي:

```
select name from pg_available_extensions where name = 'vector';
-- 0 صفوف
CREATE EXTENSION vector;
-- ERROR: extension "vector" is not available
```

**pgvector إضافة، مش جزء من PostgreSQL الأساسي.**

```bash
docker compose up -d          # Postgres + Redis بس، من غير تحميل موديلات
```

ولو محتاج الـfallback المحلي:

```bash
docker compose --profile local-ai up -d
docker compose exec ollama ollama list
#   nomic-embed-text:latest
#   qwen2.5:3b-instruct:latest
```

محتاج الخطوة دي بس لما **الاتنين مفاتيح** يبقوا فاضيين.

### ٤.٣ الإعدادات

```bash
cp .env.example .env
```

After على الأقل:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/nocteon_db
GEMINI_API_KEY=...      # الـ embeddings — اختياري
GROQ_API_KEY=...        # الـ chat — اختياري
```

> `GROQ_CHAT_MODEL` **ما ينفعش** يكون موديل `gpt-oss-*`. التشغيل دلوقتي **بيرفضه outright**.

### ٤.٤ شغّله

```bash
AI_ENABLED=true AI_SEARCH_ENABLED=true AI_CHATBOT_ENABLED=true ./mvnw spring-boot:run
```

**مفيش خطوة reindex يدوي.** على قاعدة بيانات فاضية، الـindex بتتبني لوحدها أول تشغيل:

```
ProductIndexBootstrap : Built the AI vector index on startup: indexed=7, skipped=0, took=3905ms
```

محتاج `POST /api/dashboard/ai/reindex` بس عشان **تصلّح** الـindex.

### ٤.٥ اتأكد إنه شغال

```bash
curl "http://localhost:8080/api/products?semantic=true&search=chocolatey%20espresso&size=3"
curl -H "Accept-Language: ar" \
     "http://localhost:8080/api/products?semantic=true&search=%D8%B4%D9%88%D9%83%D9%88%D9%84%D8%A7%D8%AA&size=3"
curl http://localhost:8080/api/products/ethiopia-yirgacheffe/similar
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" -H "Accept-Language: ar" \
  -d '{"message":"عايز قهوة شوكولاتة","sessionId":"s1","locale":"ar"}'
```

---

## ٥. الأخطاء التي وجدناها

كل bug في القسم ده كان موجود والاختبارات **خضرا**.

### ٥.١ التطبيق كان مش هيشتغل أصلاً

`EmbeddingModelFingerprint` كان عنده **constructor اتنين** من غير `@Autowired`. أول ما أي class يبقى
عنده أكتر من constructor، **Spring مش بيعرف يختار**، فبيروح يدور على واحد فاضي — ومفيش.

**٤٧٩ اختبار عدّوا طول الوقت.** كلهم بيعملوا mock للـ context.

### ٥.٢ أول reindex حقيقي كان هيفشل

```
java.lang.IllegalArgumentException: Invalid UUID string: product:ethiopia-yirgacheffe
```

**البحث الدلالي ما كتبش صف واحد في vector store حقيقي، ولا مرة.**

### ٥.٣ القهوة الأربعة كانوا متطابقين دلالياً

| الاستعلام | إثيوبيا | كولومبيا | الفرق |
|---|---|---|---|
| `ethiopian coffee` | 0.772 | 0.680 | **0.092** ✅ |
| `chocolate notes` | 0.7153 | 0.7151 | **0.0002** ❌ |

فرق 0.0002 يعني عمليًا صفر. الـembedding model كان شغال ممتاز — المشكلة إن النصوص كانت متشابهة.

> **القاعدة:** البحث الدلالي بيقارن **نص**، مش **جودة**. لو النصوص متشابهة، النتيجة هتكون متشابهة.
> وأحياناً المشكلة في البيانات مش في الكود.

### ٥.٤ المساعد كان يعتذر بدل ما يجاوب

```
400: 'messages.2' : property 'reasoning_content' is unsupported
```

`gpt-oss-20b` **موديل تفكير**. بيرجع `reasoning_content` مع كل رد. ولما Spring AI يعيد تشغيل الرد
عشان ينفّذ الأداة، Groq يرفضه.

الجزء اللي خدعني: الأسئلة اللي **ما استخدمتش** أداة كانت شغالة. فكان يبدو إن المشكلة "بتحصل
أحيانًا".

| المحاولة | النتيجة |
|---|---|
| `reasoning-effort: low` كـ property | **config ميت.** مفيش ولا class في `spring-ai-openai-2.0.1` بيبعته |
| `extra-body.reasoning_effort` | مفيش تأثير |
| **تغيير الموديل** | **صح** |

### ٥.٥ المساعد كان بيستجوب بدل ما يساعد

```
"I want a chocolatey, full-bodied coffee for espresso"
  → "What roast do you prefer?"                    صفر منتجات
```

*الإصلاح:* اتقلب لـ"دوّر الأول".

### ٥.٦ البحث بالكلمة كان بيرجّع 500

**ده اتعمل على البرانش ده**، وهو مسار البحث العادي من غير أي AI:

```
GET /api/products?search=chocolate   →   500
```

**٣٧ اختبار عدّوا والـendpoint بيرجّع 500.**

*الإصلاح:* الـ joins اتحولت من `join(X.class, ...)` لـ attribute-path.

### ٥.٧ قاعدة بيانات جديدة كان الـindex بتاعها فاضي

التطبيق كان بيشتغل والـhealth check أخضر، بس البحث شغال **نص نص** — والchatbot بيقول "مالقيتش قهوة
شوكولاتة" والكتالوج فيه ٤.

### ٥.٨ عيوب في البيانات الأولية

| العيب | قبل | بعد |
|---|---|---|
| كيس ٥٠٠ جرام بيكلف ٨٪ أكتر من ٢٥٠ جرام | `1.08x` | `2.12x` |
| `espresso` و `v60` متسجلين **أنواع طحن** | هما طرق تحضير | `v60` → `pour-over` |
| الأربعة قهوة بيشاركوا نفس الصورة | نفس الصورة ×١٢ | صورة لكل منتج |
| نص placeholder | *"Premium specialty coffee"* | نص حقيقي |

عيب الأسعار كان بيوصل للزبون — المساعد بيقرأ الأسعار من الداتابيز.

---

## ٦. أخطاء ارتكبتها أثناء البناء

**١. عدّلت migration متعمل عليه commit في `main`.** لو اتعمل، كل مطور هيحتاج reset. اتمسك قبل
الـcommit ورجعت للنسخة الأصلية، وحوّلت الشغل لـ migration جديدة.

> تصحيحات البيانات الأولية لازم **دايمًا** تكون migration جديدة لقدام.

**٢. اخترعت قيم UUID في اختبار.** تلات قيم من الذاكرة، كلها غلط.

**٣. كتبت تعليق غلط في كود production.** ادّيت إن حاجة معينة هي الحل، والحل كان تاني.

**٤. جملتين SQL عملت صفر بصمت.** اسمين غلط طلعوا **صفر صفوف، ما رفعوش error، وقالوا نجاح**.

**٥. كررت كلام ما اتحققتش منه.** قلت إن Render ماعندهاش pgvector — والوثائق بتقول العكس.

**٦. `CROSS JOIN` من غير شرط.** كل منتج بياخد صور كل المنتجات. الـ unique constraint كشفها مرتين.

**٧. كتبت تلات ملفات توثيق وماتعملتلهمش commit.** `git add -A` شغال وقال نجاح، بس الملفات
ماتدخلتش في الـ index. عاشت على القرص بس، واتدمّرت لما الـworktree اتشال — وسابتلنا تلات روابط
مكسورة في الـreadme. اتكشفت لما تأكدت إن كل رابط `docs/...` في الـreadme بيفتح.

---

## ٧. الوضع الحالي

| | |
|---|---|
| Commits | ٣٨ |
| ملفات AI في الـ main | ٣٤ |
| ملفات اختبار AI | ٢٠ |
| Migrations مضافة | ٣ |
| الاختبارات | ٤٩٩، كلها خضرا |

### متأكدين إنه شغال

- البحث الدلالي، إنجليزي وعربي، على قاعدة pgvector حقيقية
- `/similar`، مع الـfallback بالـattributes والـAI مقفول
- المساعد على Groq: منتجات حقيقية، slugs حرفيًا، رد بالعربي، **يرفض المنتجات المتخيلة**
- قاعدة بيانات مبنية من الصفر: ١٥ migration، pgvector مفعّل، الـindex مبني لوحده
- **Ollama غايب تماماً**: التطبيق بيشتغل وGemini شغالة بصفر محاولات اتصال فاشلة

### لسه فاضل

1. **النصوص والأسعار بيانات تجريبية.** اتكتبت عشان الكتالوج يبقى منطقي وقابل للبحث، مش كـ data
   تجارية موثوقة. **لازم صاحب المتجر يراجعها.**
2. **مفيش تنظيف للـindex.** لو منتج اتمسح، الـvector بتاعه ما بيتمسحش.
3. **مفيش اختبار دقة آلي.** الـ٢٠ استعلام في `docs/ai-golden-queries.md` اتقاسوا بإيد، وكانوا
   متقاسين على **موديل الـembeddings القديم**.
4. **العملة مش متظبطة.** مافيهوش عمود عملة، والأرقام من غير وحدة.

---

## أهم حاجة تستاهل تتذكرها

**الاختبارات الخضرا مش دليل إن البرنامج شغال.**

٤٩٨ اختبار عدّوا والتطبيق مكانش بيفتح، والبحث بالكلمة بيرجّع 500، والمساعد بيعتذر بدل ما
يجاوب، والأسعار غلط، وأربع قهوة متطابقة. اتنين منهم مكانوش هيبانوا غير لما الداتابيز بقت **فاضية
خالص**.

اكتب الاختبار اللي تقدر. وبعدين شغّل النظام.