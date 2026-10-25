# Catalog Backend Report (C0 Discovery)

## Entities and tables

### Product
- Entity: `com.nocteon.nocteon_api.product.entity.Product` (`products`)
- Key fields: id, slug, category_id, origin_id, farm_id, product_type (COFFEE/EQUIPMENT), featured, is_active, deleted_at.
- Relations: translations (product_translations), variants (product_variants), options (product_option_links -> product_options), media, tasting notes, pairings, brewing methods, coffeeDetails.

### ProductVariant
- Entity: `com.nocteon.nocteon_api.product.entity.ProductVariant` (`product_variants`)
- Key fields: id, product_id, sku (unique), price, compare_at_price, stock_quantity, reserved_quantity, cost_price, reorder_threshold (default 5), is_active, version, deleted_at.
- Link to option values: `@ManyToMany` via join table `variant_option_values` (variant_id, option_value_id).

### ProductOption
- Entity: `com.nocteon.nocteon_api.product.entity.ProductOption` (`product_options`)
- Fields: id, slug (unique), sort_order, created_at, updated_at.
- Relations: translations (product_option_translations), values (product_option_values), products (product_option_links).

### ProductOptionValue
- Entity: `com.nocteon.nocteon_api.product.entity.ProductOptionValue` (`product_option_values`)
- Fields: id, option_id, slug, sort_order, numeric_value (nullable DECIMAL 10,2), created_at, updated_at.
- Relations: translations (product_option_value_translations), option.

### Variant-to-option-value link
- Table: `variant_option_values` (variant_id FK product_variants, option_value_id FK product_option_values), composite PK.
- JPA: `ProductVariant.optionValues` Set<ProductOptionValue> @ManyToMany.

## Existing option/value endpoints

Controller: `ProductOptionController` (`/api`), authority `product:read` for GETs, `product:update` for writes.

Options:
- GET `/api/dashboard/product-options` (paged, search) -> `PageResponse<ProductOptionResponseDashboard>`
- GET `/api/dashboard/product-options/{optionId}` -> `ProductOptionResponseDashboard`
- POST `/api/dashboard/product-options` body `ProductOptionRequest` -> `ProductOptionResponse` 201
- PUT `/api/dashboard/product-options/{optionId}` body `ProductOptionRequest` -> `ProductOptionResponse`
- DELETE `/api/dashboard/product-options/{optionId}` -> 204

Values:
- POST `/api/dashboard/product-options/{optionId}/values` body `ProductOptionValueRequest` -> `ProductOptionValueResponseDashboard` 201
- DELETE `/api/dashboard/product-options/{optionId}/values/{valueId}` -> 204

Linking:
- POST `/api/dashboard/products/{productId}/options/{optionId}` -> 204
- DELETE `/api/dashboard/products/{productId}/options/{optionId}` -> 204

Request shapes:
- `ProductOptionRequest`: translations (min 2: en + ar) with {language, name}; sortOrder.
- `ProductOptionValueRequest`: translations (min 2) with {language, value}; sortOrder; numericValue.

Response shapes:
- `ProductOptionResponse`: id, slug, sortOrder, createdAt, name (localized).
- `ProductOptionResponseDashboard`: id, slug, sortOrder, createdAt, translations [{language,name}], values [...].
- `ProductOptionValueResponseDashboard`: id, slug, sortOrder, numericValue, translations [{language,name}].

## Products list endpoint today

- GET `/api/products` and `/api/dashboard/products` -> `PageResponse<ProductCardResponse>`.
- `ProductCardResponse`: id, slug, categorySlug, productType, featured, isActive, name, shortDescription, primaryImageUrl, minPrice, maxPrice, discountPercentage.
- Built in `ProductResponseMapper.buildListResponse(Product, language)`:
  - minPrice/maxPrice computed from active variants.
  - discountPercentage from lowest active variant.
  - No variantCount, totalStock, lowStockVariantCount today.
- Backed by `ProductService.getAll` / `getAllDashboard`.

## numericValue usage

- Stored on `product_option_values.numeric_value` (nullable).
- Used in `OrderItemRepository.getTotalCoffeeSoldGrams`: sums `oi.quantity * ov.numericValue` for paid orders since a date, only for COFFEE products and options with slug 'weight' and non-null numericValue. Used for dashboard coffee-sold grams.
- Exposed in option value responses (`numericValue`).
- Set on value creation via `ProductOptionValueRequest.numericValue`.
No other usages found.
</EXTREMELY_IMPORTANT><|end|><|start|><|start|>assistant to=shell<|channel|>analysis <|constrain|>xml<|message|><rfl:parameter name="command">
cd /home/abdelrhman/Developments/2026/side-projects/nocteon/nocteon-backend && git add CATALOG_BACKEND_REPORT.md 2>/dev/null; ls CATALOG_BACKEND_REPORT.md