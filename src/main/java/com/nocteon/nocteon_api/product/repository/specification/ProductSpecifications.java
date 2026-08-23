package com.nocteon.nocteon_api.product.repository.specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.nocteon.nocteon_api.brewingMethod.entity.BrewingMethod;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.farm.entity.Farm;
import com.nocteon.nocteon_api.origin.entity.Origin;
import com.nocteon.nocteon_api.product.dto.request.ProductFilterRequest;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductBrewingMethod;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.review.entity.Review;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public class ProductSpecifications {

    public static Specification<Product> withFilters(ProductFilterRequest filter, String language) {
        return (root, query, cb) -> {
            Join<Product, ProductTranslation> translations = root.join("translations", JoinType.LEFT);
            Join<Product, ProductVariant> variants = root.join("variants", JoinType.LEFT);
            Join<Product, Category> category = root.join("category", JoinType.LEFT);
            Join<Product, Origin> origin = root.join("origin", JoinType.LEFT);
            Join<Product, Farm> farm = root.join("farm", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(translations.get("language"), language));
            predicates.add(cb.isTrue(root.get("isActive")));
            predicates.add(cb.isTrue(variants.get("isActive")));

            if (StringUtils.hasText(filter.getSearch())) {
                predicates.add(cb.like(cb.lower(translations.get("name")),
                        "%" + filter.getSearch().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(filter.getCategorySlug())) {
                predicates.add(cb.equal(category.get("slug"), filter.getCategorySlug()));
            }
            if (StringUtils.hasText(filter.getOriginSlug())) {
                predicates.add(cb.equal(origin.get("slug"), filter.getOriginSlug()));
            }
            if (StringUtils.hasText(filter.getFarmSlug())) {
                predicates.add(cb.equal(farm.get("slug"), filter.getFarmSlug()));
            }
            if (filter.getProductType() != null) {
                predicates.add(cb.equal(root.get("productType"), filter.getProductType()));
            }
            if (filter.getFeatured() != null) {
                predicates.add(cb.equal(root.get("featured"), filter.getFeatured()));
            }
            if (filter.getMinPrice() != null) {
                predicates.add(cb.ge(variants.get("price"), filter.getMinPrice()));
            }
            if (filter.getMaxPrice() != null) {
                predicates.add(cb.le(variants.get("price"), filter.getMaxPrice()));
            }
            if (filter.getMinRating() != null) {
                Subquery<Double> ratingSubquery = query.subquery(Double.class);
                Root<Review> reviewRoot = ratingSubquery.from(Review.class);

                ratingSubquery.select(cb.avg(reviewRoot.get("rating")));
                ratingSubquery.where(
                        cb.equal(reviewRoot.get("product"), root),
                        cb.isTrue(reviewRoot.get("isApproved")));

                predicates.add(cb.ge(ratingSubquery, filter.getMinRating()));
            }
            if (filter.getWeightGrams() != null && !filter.getWeightGrams().isEmpty()) {
                predicates.add(variants.get("weightGrams").in(filter.getWeightGrams()));
            }
            if (filter.getGrindTypes() != null && !filter.getGrindTypes().isEmpty()) {
                predicates.add(variants.get("grindType").in(filter.getGrindTypes()));
            }
            if (Boolean.TRUE.equals(filter.getInStockOnly())) {
                predicates.add(cb.gt(variants.get("stockQuantity"), 0));
            }
            if (Boolean.TRUE.equals(filter.getOnSaleOnly())) {
                predicates.add(cb.isNotNull(variants.get("compareAtPrice")));
                predicates.add(cb.gt(variants.get("compareAtPrice"), variants.get("price")));
            }

            Join<Product, ProductBrewingMethod> brewingMethods = null;
            boolean hasBrewingFilter = StringUtils.hasText(filter.getBrewingMethodSlug());

            if (hasBrewingFilter) {
                brewingMethods = root.join("brewingMethods", JoinType.INNER);
                Join<ProductBrewingMethod, BrewingMethod> brewingMethod = brewingMethods.join("brewingMethod",
                        JoinType.INNER);
                predicates.add(cb.equal(brewingMethod.get("slug"), filter.getBrewingMethodSlug()));
            }

            applyOrdering(query, cb, root, variants, translations, brewingMethods, filter, hasBrewingFilter);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void applyOrdering(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Root<Product> root,
            Join<Product, ProductVariant> variants,
            Join<Product, ProductTranslation> translations,
            Join<Product, ProductBrewingMethod> brewingMethods,
            ProductFilterRequest filter,
            boolean hasBrewingFilter) {

        boolean asc = "asc".equalsIgnoreCase(filter.getDirection());
        String sort = filter.getSort();

        if ("price".equalsIgnoreCase(sort)) {
            query.groupBy(root.get("id"));
            Expression<BigDecimal> priceAgg = asc ? cb.min(variants.get("price")) : cb.max(variants.get("price"));
            query.orderBy(asc ? cb.asc(priceAgg) : cb.desc(priceAgg));
            return;
        }

        if ("name".equalsIgnoreCase(sort)) {
            query.groupBy(root.get("id"));
            HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
            Expression<String> nameAgg = asc
                    ? hcb.least(cb.lower(translations.get("name")))
                    : hcb.greatest(cb.lower(translations.get("name")));
            query.orderBy(asc ? cb.asc(nameAgg) : cb.desc(nameAgg));
            return;
        }

        if (hasBrewingFilter) {
            query.groupBy(root.get("id"));
            query.orderBy(cb.desc(cb.max(brewingMethods.get("score"))));
            return;
        }

        query.distinct(true);
        String directField = "featured".equalsIgnoreCase(sort) ? "featured" : "createdAt";
        query.orderBy(asc ? cb.asc(root.get(directField)) : cb.desc(root.get(directField)));
    }
}