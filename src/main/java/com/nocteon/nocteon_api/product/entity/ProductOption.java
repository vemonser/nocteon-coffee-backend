package com.nocteon.nocteon_api.product.entity;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

import com.nocteon.nocteon_api.common.entity.BaseEntity;
import com.nocteon.nocteon_api.product.enums.ProductType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "product_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductOption extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @jakarta.persistence.ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "product_option_applies_to",
            joinColumns = @jakarta.persistence.JoinColumn(name = "option_id"))
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @jakarta.persistence.Column(name = "product_type", nullable = false, length = 20)
    @Builder.Default
    private Set<ProductType> appliesTo = new HashSet<>(Arrays.asList(ProductType.values()));

    @OneToMany(mappedBy = "option", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ProductOptionTranslation> translations = new HashSet<>();

    @OneToMany(mappedBy = "option", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ProductOptionValue> values = new HashSet<>();

    @ManyToMany(mappedBy = "options")
    @Builder.Default
    private Set<Product> products = new HashSet<>();

}
