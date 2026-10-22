package com.nocteon.nocteon_api.cart.entity;

import java.util.List;

import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;

import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.common.entity.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Table(name = "carts")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Cart extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    @BatchSize(size = 30)
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;

    /**
     * Lifecycle state of the cart. Updated by CartService on mutations and by
     * OrderService when an order is created from the cart.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    /**
     * Last time any item was added/updated/removed in this cart.
     * Updated by CartService on every mutation.
     */
    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    /**
     * If this cart was converted to an order, the order's id.
     * Set by OrderService when creating an order.
     */
    @Column(name = "converted_order_id")
    private Long convertedOrderId;
}