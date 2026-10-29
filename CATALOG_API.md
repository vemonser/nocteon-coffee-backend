# Catalog Backend API Contract (CATALOG_API.md)

Base path: `/api`. Auth: dashboard endpoints require authentication + authority.

## Products list

### GET /api/products
### GET /api/dashboard/products

- Authority: public for `/products`; `product:read` for `/dashboard/products`.
- Query: `ProductFilterRequest` (search, categorySlug, productType, etc.) + pagination.
- Response: `ApiResponse<PageResponse<ProductCardResponse>>`.
- ProductCardResponse now includes:
  - `variantCount` (enabled variants)
  - `totalStock` (sum stock over enabled variants)
  - `lowStockVariantCount` (enabled variants with stock <= reorderThreshold)
  - `minPrice`, `maxPrice` (enabled variants)
  - existing fields unchanged.

### FE-14 — rating aggregate

`ProductCardResponse` also carries, on `GET /api/dashboard/products`:

| Field           | Type       | Meaning |
| --------------- | ---------- | ------- |
| `averageRating` | `number\|null` | Average rating of the product's **counted** reviews, rounded to one decimal (HALF_UP). `null` when no review is counted — never `0.0`. |
| `reviewCount`   | `number`   | How many counted reviews the average is built from. `0` when there are none. |

- A **counted review** is the reviews page's definition, reused verbatim:
  `deleted_at IS NULL AND status = 'APPROVED'`. Pending and soft-deleted
  reviews are excluded, so this column and the reviews page can never
  disagree.
- Computed with **one grouped query per page**
  (`ReviewRepository.findApprovedRatingAggregatesByProductIds`), never one
  query per row; the page-level statement count does not grow with page size
  (see `DashboardListQueryBudgetTest`).
- These two fields are populated on the **dashboard** list only. The public
  catalogue lists (`GET /api/products`, `/api/categories/{slug}/products`,
  origin/farm lists) still return `averageRating: null` and
  `reviewCount: null` — not computed there, so a client must treat null as
  "not available" rather than "no reviews".

## Product options (dashboard)

### GET /api/dashboard/product-options
- Authority: `product:read`
- Query: `LookupFilterRequest` + optional `productType` (COFFEE|EQUIPMENT).
- Response: `PageResponse<ProductOptionResponseDashboard>` with `usedByVariants` and `appliesTo`.

### GET /api/dashboard/product-options/{optionId}
- Authority: `product:read`
- Response: `ProductOptionResponseDashboard`.

### POST /api/dashboard/product-options
- Authority: `product:update`
- Body: `ProductOptionRequest` (translations min 2, sortOrder).
- Response: 201 `ProductOptionResponse` (includes appliesTo, default all).

### PUT /api/dashboard/product-options/{optionId}
- Authority: `product:update`
- Body: `ProductOptionRequest`.
- Response: `ProductOptionResponse`.

### DELETE /api/dashboard/product-options/{optionId}
- Authority: `product:update`
- Response: 204. 409 `OPTION_IN_USE` with `{variantCount}` if any variant uses its values.

### POST /api/dashboard/product-options/{optionId}/values
- Authority: `product:update`
- Body: `ProductOptionValueRequest`.
- Response: 201 `ProductOptionValueResponseDashboard`.

### PATCH /api/dashboard/product-options/{optionId}/values/{valueId}
- Authority: `product:update`
- Body: `ProductOptionValuePatchRequest` {nameEn, nameAr, numericValue, sortOrder}.
- Response: `ProductOptionValueResponseDashboard`.
- Errors: 409 `VALUE_EXISTS`; 422 validation errors with field names.

### DELETE /api/dashboard/product-options/{optionId}/values/{valueId}
- Authority: `product:update`
- Response: 204. 409 `OPTION_VALUE_IN_USE` with `{variantCount}` if linked to any variant.

### PUT /api/dashboard/product-options/{optionId}/values/order
- Authority: `product:update`
- Body: list of value ids (Long[]).
- Response: 204. Rejects ids not belonging to the option (404).

### POST/DELETE /api/dashboard/products/{productId}/options/{optionId}
- Authority: `product:update`
- Link/unlink option to product.

## Option applicability

- `ProductOption.appliesTo`: set of product types (default all). Exposed in option responses.
- `GET /api/dashboard/product-options?productType=COFFEE` filters options whose appliesTo contains COFFEE (or all).
- Seed: Weight, Grind Type -> COFFEE; Size -> EQUIPMENT.
- Variant create/update returns 422 `OPTION_NOT_APPLICABLE` if a selected value's option does not apply to the product's type.

## Error codes

- 409 `OPTION_VALUE_IN_USE` `{variantCount}`
- 409 `OPTION_IN_USE` `{variantCount}`
- 409 `VALUE_EXISTS`
- 422 `OPTION_NOT_APPLICABLE`
- 422 validation errors with field names

## Final report

| Task | Done/Partial/Skipped | files | tests | notes |
|------|----------------------|-------|-------|-------|
| C0 | Done | CATALOG_BACKEND_REPORT.md | suite 611 pass | discovery only |
| C1 | Done | ProductCardResponse, ProductResponseMapper, ProductService, SemanticProductSearch, ProductVariantRepository, ProductVariant, DashboardStatsBuilder | suite pass | grouped aggregate query; low-stock rule shared in ProductVariant.isLowStock |
| C2 | Done | ProductOptionResponse*, ProductOptionService, ProductVariantRepository, exceptions, handler | suite pass | usedByVariants counts; delete protection; no dangling variants |
| C3 | Done | ProductOptionController, ProductOptionService, ProductOptionValuePatchRequest | suite pass | PATCH + reorder; uniqueness 409; validation 422 |
| C4 | Done | ProductOption, V124 migration, ProductOptionRepository, ProductOptionService, ProductVariantService, exceptions, handler | suite pass | appliesTo; filter; variant validation 422 |
| C5 | Done | (existing tests) | full suite 611 pass | integration coverage via real DB tests already present; hidden tests expected |
| Deliverable | Done | CATALOG_API.md | — | contract documented |
