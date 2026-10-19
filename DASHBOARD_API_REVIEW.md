# مراجعة Endpoints — مطابقة الداشبورد (nocteon-dashboard) مع الباك-اند

> **التاريخ:** 18 Oct 2026 — **مرجع الباك:** `main` @ `470023d` (مع تعديلات غير مدموجة على الشجرة من `fix/audit-findings`)
> **الهدف:** مراجعة كل الـ endpoints اللي الداشبورد بيستخدمها (المسار + الفعل + الباراميترز + شكل الاستجابة + الصلاحيات)، وتحديد اللي محتاج يتعدّل في الباك. **تقرير فقط — مفيش أي تعديل كود في الباك تم.**
>
> **المنهجية:** استخراج كل الـ mappings من ~35 Controller في الباك، واستخراج كل نداء `http.get/post/put/patch/delete` من الفرونت (~94 نداء)، ثم مقارنة: مسار/فعل/براميترز/DTO/صلاحية.

---

## 1) الخلاصة السريعة (TL;DR)

**الأخبار الجيدة:**
- **كل المسارات متطابقة تقريبًا** — مفيش نداء واحد من الفرونت لمسار غير موجود (الاستثناء الوحيد: `GET /api/dashboard/journal/{slug}` — بند #3)، ومفيش endpoint داشبورد معطّل في الباك بدون مقابل.
- غلاف الاستجابة `ApiResponse<T>` و `PageResponse<T>` متطابقين مع قراءات الفرونت (incl. `content/page/size/totalElements/totalPages/first/last`).
- شكل أخطاء الـ validation (`errors: [{field, message}]`) + ملفات الرسائل EN/AR مطابقين لما `notify.backendMessage` بيقرأه — يعني رسائل السيرفر بتوصل inline زي ما إحنا عامњين في الـ dialog.
- الـ bean validation الأساسي شغال: الاسم `@NotBlank @Size(2..100)`، واللغة إجبارية en+ar في `validateTranslations` — يعني "فئة باسم فاضي" بترفض صح برسالة.

**اللي محتاج يتعدّل:**

| # | الموضوع | الخطورة |
|---|---------|---------|
| 1 | مفيش أي validation للاسم المكرر — `POST` مكرر بيرجع **201** مع slug أوتوماتيك (`coffee-3`) بدل 409 | 🔴 HIGH |
| 2 | كل endpoints الكتابة (POST/PUT/image) للكيانات ثنائية اللغة بترجع **DTO مسطّح بدون `translations`** — نفس الكلاس اللي كسر صفحة تفاصيل الفئات | 🔴 HIGH |
| 3 | `GET /api/dashboard/journal/{slug}` **غير موجود أصلًا** — فورم تحرير المدونة بيفضل فاضي | 🔴 HIGH |
| 4 | دور `CUSTOMER` معاه `USER_READ`/`USER_UPDATE` → **أي عميل يقدر يفهرس كل المستخدمين** من `/api/dashboard/users` | 🔴 HIGH (أمني) |
| 5 | `dashboard:read` مش موجود في `MODERATOR` → صفحة الإحصائيات 403 للمحررين | 🟡 MED |
| 6 | تعارض guard الفرونت (`cart:manage`) مع صلاحية الباك للقائمة (`cart:read_all`) | 🟡 MED (تصليبه فرونت) |
| 7 | `restore()` مش بيعمل pre-check للتعارض — هتبقى مهمة بعد تنفيذ بند #1 وإلا هترجع 500 من قيد قاعدة البيانات | 🟡 MED |
| 8 | فرع ميت في `GlobalExceptionHandler`: بيفحص قيد اسمه `uk_category_name_language` والقيد الفعلي في الميجريشن اسمه `uk_category_language` | ⚪ LOW |
| 9 | ملاحظات معلوماتية (سقف `size @Max(100)`، أسماء أجزاء الـ multipart، حذف 204 بدون body، مابينج origins بدون slash بادئ) | ⚪ INFO |

---

## 2) مصفوفة المطابقة (المسارات والباراميترز)

| المجال (service الفرونت) | المسارات المستخدمة | الحالة |
|---|---|---|
| **Categories** | `GET /dashboard/categories`, `GET /{slug}`, `POST` (multipart `data`+`image`), `PUT /{slug}`, `POST /{slug}/image`, `DELETE /{slug}`, `GET /deleted`, `PATCH /{id}/restore`, `DELETE /{id}/permanent` | ✅ مسارات — ⚠️ بند #1 و#2 |
| **Farms / Origins / Pairings / Brewing / Processing / RoastLevels / TastingNotes / CoffeeVarieties** | نفس النمط كامل (list / `{slug}` / write / deleted / restore / permanent) | ✅ مسارات — ⚠️ بند #2. ملاحظة أسلوبية: mappings الـ Origin مكتوبة بدون slash بادئ (`"dashboard/origins/{slug}"`) — Spring بيحلها من `@RequestMapping("/api")` فبتشتغل، بس الأنضف توحيدها |
| **Journal** | `GET /dashboard/journal`, `POST`, `PUT /{slug}` + `POST /{slug}/cover` (multipart `data`+`image` — مطابق), `DELETE /{slug}`, `GET /deleted` | ✅ — **❌ ناقص `GET /dashboard/journal/{slug}`** (بند #3)، ⚠️ `JournalPostResponse` بدون `translations` (بند #2) |
| **Products** | list مع `featured`/`isActive`, `{slug}`, create/update (`request`+`files` — مطابق), `PATCH /{slug}/active\|featured` بـ `?value=`, `POST /{slug}/media` (`media`+`files` — مطابق), `DELETE /media/{mediaId}`, deleted/restore/permanent | ✅ كاملة — الـ `PUT` بيرجع `DashboardProductResponse` (النموذج المطلوب)، بس الـ `POST` و `POST /media` لسه مسطّحين (بند #2) |
| **ProductOptions** | id-based CRUD + values + ربط المنتجات بالأوپشنز | ✅ |
| **Broadcasts** | list / create / `GET {id}` / `DELETE {id}` | ✅ — مفيش `PUT` في الباك، والفرونت أصلاً create-only (بتعمل `throw` في الـ edit) — **متساقط ومطابق** |
| **ContactMessages** | list / `{id}` / read / reply / delete | ✅ |
| **Notifications** | list بـ `unreadOnly/page/size`, `unread-count`, `PATCH /{id}/read`, `PATCH /read-all` | ✅ |
| **Orders** | list / `{id}` / `PUT {id}/status` | ✅ |
| **Carts** | list بـ page/size/sort / `{id}` | ✅ — الباك كمان بيدعم `search`/`hasItems` (غير مستخدمين) |
| **Users** | list بـ `role/isActive/enabled` / `{id}` / POST / PUT / `toggle-active` | ✅ مسارات — 🔴 الصلاحيات فيها ثقب (بند #4) |
| **ShippingZones / PromoCodes** | id-based CRUD + deleted/restore/permanent + `promo-codes/analytics` | ✅ |
| **Reviews** | list / `{id}` / `approval` / delete — كلها بـ `review:moderate` (متطابقة مع guard الفرونت) | ✅ |
| **StoreSettings** | `GET /api/settings` (عام) + `PUT /dashboard/settings` | ✅ |
| **Dashboard Stats** | `stats/overview`, `stats/overview/compare`, `pending-reviews-count`, `unread-contact-count` بـ `?days=` | ✅ مسارات — 🟡 الصلاحية (بند #5) |
| **GlobalSearch** | `GET /dashboard/search` | ✅ |
| **Auth / Profile** | login, refresh, logout, logout-all, forgot-password, email-status + `GET /users/me`, `PUT me/profile`, `PUT me/avatar`, `PUT me/password` | ✅ |
| **Origins options** | `GET /dashboard/origins/options` | ✅ |

**الفلاتر المشتركة:** `BaseFilterRequest` بيفهّم `page` (min 0) و `size` (1..100) و `sort`/`direction`، و `LookupFilterRequest` بيوظّف `search` + `isActive` — مطابقة تمامًا لـ `buildBaseParams` في الفرونت (incl. `?size=` الجديد بتاعنا).

---

## 3) التفاصيل والتعديلات المقترحة

### 🔴 #1 — الاسم المكرر مش متحقق منه (فئات وكل الـ lookup entities)

**الواقع في الكود:**
- `CategoryServiceImpl.create` → `helper.generateUniqueSlug(englishName, existsBySlug)` و `LookupServiceHelper.generateUniqueSlug` بيكوّن slug فريد أوتوماتيك (`coffee`, `coffee-2`, `coffee-3`...) — **بدون أي رفض للاسم المكرر**.
- اتأكدنا بمحاولة حية سابقة: `POST` اسم مكرر → **201** مع slug بـ suffix.
- الميجريشن فيه قيد `uk_category_language` على `(category_id, language)` بس — ده بيمنع تكرار اللغة **داخل نفس الصف** فقط، مش التكرار بين الصفوف المختلفة.

**المطلوب:**
1. فحص في السيرفس (الأنضف مكانها `LookupServiceHelper.validateTranslations` — بيتدعى من كل الـ lookup create/update): لو الاسم (بالاعتماد-حساسية) مستخدم في صف **حي** (غير محذوف ناعمًا) بنفس اللغة → ارجع 409 أو 400 بـ `errors: [{field: "translations[n].name", message: ...}]`.
2. الرسالة جاهزة: `error.duplicate.translation = "A translation with this name already exists"` (و معناه العربي "يوجد ترجمة بهذا الاسم بالفعل") — موجود في `messages*.properties`، والفرونت بتعرضه inline في الـ dialog من غير أي تعديل عندنا.
3. لو حابب قيد على مستوى قاعدة البيانات: partial unique index على `(language, lower(name)) WHERE deleted_at IS NULL` — لازم partial لأن صف محذوف ناعمًا لازم يحتفظ باسمه (عشان الـ restore)، وتسمية القيد لازم تتطابق مع فرع الـ handler (بند #8).

### 🔴 #2 — endpoints الكتابة بترجع DTO مسطّح بدون `translations`

الجدول من الكود:

| Controller | DTO في القوائم/التفاصيل (GET) | DTO في الكتابة (POST/PUT/image) |
|---|---|---|
| Category | ✅ `DashboardCategoryResponse` (فيه translations) | ❌ `CategoryResponse` — **صفر** mention لـ translations |
| Farm / Origin / Pairing / Brewing / Processing / Roast / Tasting / CoffeeVariety | ✅ الـ Dashboard DTOs | ❌ المسطّح |
| Product | ✅ + `PUT` بيرجع `DashboardProductResponse` | ⚠️ `POST` و `POST /media` بيرجعوا `ProductResponse` المسطّح |
| Journal | ⚠️ القائمة والمفرد مسطّحين (الـ `/deleted` بس اللي بيرجع الـ Dashboard DTO) | ❌ مسطّح |

**الأثر على الفرونت:** أي صفحة تفاصيل بتعتمد الاستجابة مباشرة بعد الحفظ هتفضل بـ `translations` ناقصة — ده بالظبط اللي كان بيكسر صفحة الفئات وإحنا حطينا `refreshCategory` كـ workaround. باقي صفحات التفاصيل (7 كيانات) لسه فيها نفس الضعف — التصليب الحقيقي في الباك مش في الفرونت.

**المقترح:** اتبع نمط `ProductController.update` — خلي كل write endpoint داشبورد يرجع الـ `Dashboard*Response` (بالـ translations كاملة). الفرونت عندها نماذج جاهزة تتنايه.

### 🔴 #3 — `GET /api/dashboard/journal/{slug}` غير موجود

- الفرونت بتناديها في `journal-form.component.ts` (سطر 112: `this.journalService.getBySlug(slug)`) مع `catchError(() => of(null))` — يعني بهدوء بيرجع null والفورم بيتفتح **فاضي لما تحرر مقال موجود**.
- تعليق داخل الكود الفرونت (سطر 165) بيقول حرفيًا: "لو الـ getBySlug بيرجّع ترجمة واحدة بس… قولّي لو محتاج نعدّل الـ getBySlug/DTO عشان يرجع الكل وقت التعديل".

**المقترح:** أضف في `JournalController`:
```java
@GetMapping("/dashboard/journal/{slug}")
@PreAuthorize("hasAuthority('journal:read')")
public ResponseEntity<ApiResponse<JournalPostResponseDashboard>> getDashboardBySlug(@PathVariable String slug)
```
بنفس نمط `getDashboardBySlug` في `CategoryController` — بيرجّع الـ `translations` كاملة (en+ar) عشان الفورم十 bilingual يعبي الاتنين.

### 🔴 #4 — ثقب RBAC: الـ CUSTOMER معاه `user:read`

في `Role.java`:
```java
CUSTOMER(Set.of(
    ..., Permission.USER_READ, Permission.USER_UPDATE,   // ← هنا الخطر
    Permission.CART_MANAGE, ...
))
```
- `GET /api/dashboard/users` بيتقيّد بـ `hasAuthority('user:read')` — والـ **CUSTOMER معاه user:read** → أي حساب عميل يقدر يسحب قايمة كل المستخدمين (أيميلات، أدوار، حالة التفعيل...).
- `/api/users/me` ومسارات الـ profile مش محتاجة الصلاحية دي أصلًا (بيتأمّنوا بـ `@AuthenticationPrincipal` بس) — فإزالة`USER_READ` مش هتكسر حاجة.
- وياها: الـ dashboard نفسه عنده فجوة بوابة من جوه الفرونت (authGuard بيكفي بأي حساب) — موضوع معروف وموثّق عندنا، بس ده بيتحول من "تجربة استخدام سيئة" لـ "تسريب فعلي" لما الصلاحية نفسها بتسمح للعميل يقرا المستخدمين من الـ API مباشرة.

**المقترح:** شيل `USER_READ` و`USER_UPDATE` من مجموعة `CUSTOMER`. وياها مراجعة سريعة لأي صلاحية "قراءة" تانية مش ضرورية للـ storefront.

### 🟡 #5 — `dashboard:read` مش موجود في `MODERATOR`

- `DashboardController` بيتقيّد بـ `hasAuthority('dashboard:read')` — والـ `MODERATOR` مش معاه الصلاحية دي (في `Role.java`) → **403 لصفحة الإحصائيات** لأي محرر.
- الـ ADMIN مش متأثر (بياخد `Permission.values()` كلها).

**المقترح:** ضيف `DASHBOARD_READ` لكل `MODERATOR` — أو حوّل صلاحية الـ stats endpoints لشيء أوسع (مثلاً `order:read` بشرط إنها عند الـ moderator فعلًا — هي فعلًا معاه `ORDER_READ`).

### 🟡 #6 — تعارض guard الفرونت مع صلاحية الباك (carts)

- الفرونت: `permissionGuard('cart:manage')` على مسار `/dashboard/carts` — الباك: القايمة بتحتاج `cart:read_all`.
- الـ `CUSTOMER` معاه `cart:manage` (أصلها من عربية الـ storefront) — يعني **العميل بيعدي الـ guard ودخول الصفحة يفشل بـ 403** من أول request.

**المقترح:** ده تصليب فرونت (مليش دعوة): خلي guard الـ carts route يستخدم `cart:read_all` بدل `cart:manage`.

### 🟡 #7 — `restore()` بدون pre-check للتعارض

`CategoryServiceImpl.restore(id)` بيمسح `deletedAt` ويتّسي save مباشرة. حاليًا ده آمن مع الـ slug لأن `existsBySlug` مش بفلتر deleted (يعني صف في التراش بيكمل محتفظ بـ slug بتاعه وبيسرق slug حضورًا...) — بس **بعد ما تنفّذ بند #1 بـ partial unique index على الاسم**، الـ restore ممكن يكسر القيد ويرجع **500 بدل 409** لو اسم الصف المحذوف بقى مستخدم في صف حي.

**المقترح:** قبل الـ save في الـ restore، افحص تعارض الاسم/ـslug عامد وارجع 409 برسالة مفهومة (`error.duplicate.translation`) — مش استثناء DB.

### ⚪ #8 — فرع ميت في `GlobalExceptionHandler`

الـ handler بيفحص اسم القيد `uk_category_name_language` عشان يطلّع رسالة `error.duplicate.translation` — لكن القيد الموجود فعلًا في الميجريشن اسمه `uk_category_language` (على `(category_id, language)`). يعني الفرع ده مش هيتفعل حرفيًا لا يومًا. لما تنفّذ بند #1، **سمّي القيد الجديد بنفس ما الفرع بيتوقعه** (أو عدّل الفرع بنص الاسم الجديد) وفرعنا الموجود هيبقى شغال.

### ⚪ #9 — ملاحظات معلوماتية

- **سقف الحجم:** `BaseFilterRequest.size @Max(100)` — خياراتنا (10/20/50) داخل السقف بامتياز. لو يوم النظام طلب "هات كل الأسماء دفعة واحدة" لازم batching مش size ضخم.
- **تسمية أجزاء الـ multipart مقصودة ومتفق عليها:** lookup entities=`data`+`image`، products=`request`+`files`، media المنتجات=`media`+`files`، وأفاتار اليوزر=`file` — متطابقة تمامًا مع بناتوص الفرونت (اتأكدت من كل واحدة).
- **الحذف الناعم بيرجع 204 بدون body** — والفرونت بتتنايه (type `void`) — متساقط.
- **`DELETE /{id}/permanent` موجود للكيانات الـ soft-delete كلها** (categories, farms, origins, brewing, products, promo-codes, shipping, journal, ...) والفرونت بتستخدمه فعلاً — كويس.
- **تصليب لوغاريتمي للـ `restore()`:** فضّلنا بريح شكرًا للـ `findBySlugWithTranslations` — بس عايز اهتمام من ناحية بند #3.

---

## 4) ملحق — ملاحظات على الفرونت (تصليبات عنا عندنا، مش في الباك)

1. **تعليق قديم في `base-crud.service.ts`:** بيقول "Not implemented on purpose: DELETE .../permanent — no such endpoint exists" — التعليق **غلط**؛ الـ endpoint موجود لكل الكيانات الـ soft-delete والحذف النهائي شغال (اتأكدنا في e2e). العشان نحذف السطور دي.
2. **نداءات ميّتة في الفرونت:** `GET /api/dashboard/notifications/{id}/read` (طريقة GET) و `GET /api/dashboard/reviews/{id}/approval` — مش موجودة في الباك ولا مستخدمة في أي تيفي بجد — تتنضف أي وقت.
3. **`journal-form.component.ts`:** بعد ما بند #3 يتلحّق، الـ fallback لـ null هنشيله والفورم هيعبي الاتنين en+ar صح.

## 5) قائمة تشغيل "جاهزة للتنفيذ" (Team الباك)

1. `[HIGH]` رفض الاسم المكرر في `LookupServiceHelper.validateTranslations` (أو على مستوى السيرفس) برسالة `error.duplicate.translation` — بند #1.
2. `[HIGH]` خلي كل write endpoint داشبورد بيرجع الـ `Dashboard*Response` (بدأ بنقل أثر الـ `DashboardProductResponse` الي بالفعل مشي صح) — بند #2.
3. `[HIGH]` أضف `GET /dashboard/journal/{slug}` بيرجع الـ Dashboard DTO (بند #3).
4. `[HIGH]` شيل `USER_READ`/`USER_UPDATE` من مجموعة `CUSTOMER` (بند #4).
5. `[MED]` ضيف `DASHBOARD_READ` لـ `MODERATOR` (أو خفّف صلاحية الـ stats endpoints) — بند #5.
6. `[MED]` عوّض `restore()` بفحص تعارض الاسم/slug قبل الحفظ وبعد تنفيذ بند #1 (بند #7).
7. `[LOW]` وحّد تسمية القيد مع فرع الـ handler (بند #8)، ووحّد تسمية المسارات (slash بادئ في الـ origin mapping).

---
*كل مراجع الملفات من دراسة الكود الفعلي (مع العمل على شجرة `fix/audit-findings` بأحدث حالة). التقرير ده report-only — مفيش كود باك أو فرونت اتعديل بسبب.*
