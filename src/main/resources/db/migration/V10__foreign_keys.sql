-- All foreign keys, after every table exists.

ALTER TABLE ONLY public.addresses
    ADD CONSTRAINT addresses_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.brewing_method_translations
    ADD CONSTRAINT brewing_method_translations_brewing_method_id_fkey FOREIGN KEY (brewing_method_id) REFERENCES public.brewing_methods(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.broadcast_recipients
    ADD CONSTRAINT fk_broadcast_recipients_broadcast FOREIGN KEY (broadcast_id) REFERENCES public.broadcasts(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.broadcast_recipients
    ADD CONSTRAINT fk_broadcast_recipients_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT cart_items_cart_id_fkey FOREIGN KEY (cart_id) REFERENCES public.carts(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.cart_items
    ADD CONSTRAINT cart_items_product_variant_id_fkey FOREIGN KEY (product_variant_id) REFERENCES public.product_variants(id);

ALTER TABLE ONLY public.carts
    ADD CONSTRAINT carts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.category_translations
    ADD CONSTRAINT category_translations_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.coffee_details
    ADD CONSTRAINT coffee_details_coffee_variety_id_fkey FOREIGN KEY (coffee_variety_id) REFERENCES public.coffee_varieties(id);

ALTER TABLE ONLY public.coffee_details
    ADD CONSTRAINT coffee_details_processing_method_id_fkey FOREIGN KEY (processing_method_id) REFERENCES public.processing_methods(id);

ALTER TABLE ONLY public.coffee_details
    ADD CONSTRAINT coffee_details_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.coffee_details
    ADD CONSTRAINT coffee_details_roast_level_id_fkey FOREIGN KEY (roast_level_id) REFERENCES public.roast_levels(id);

ALTER TABLE ONLY public.coffee_variety_translations
    ADD CONSTRAINT coffee_variety_translations_coffee_variety_id_fkey FOREIGN KEY (coffee_variety_id) REFERENCES public.coffee_varieties(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.farm_translations
    ADD CONSTRAINT farm_translations_farm_id_fkey FOREIGN KEY (farm_id) REFERENCES public.farms(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.farms
    ADD CONSTRAINT farms_origin_id_fkey FOREIGN KEY (origin_id) REFERENCES public.origins(id);

ALTER TABLE ONLY public.journal_category_translations
    ADD CONSTRAINT journal_category_translations_journal_category_id_fkey FOREIGN KEY (journal_category_id) REFERENCES public.journal_categories(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.journal_post_tags
    ADD CONSTRAINT journal_post_tags_journal_post_id_fkey FOREIGN KEY (journal_post_id) REFERENCES public.journal_posts(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.journal_post_translations
    ADD CONSTRAINT journal_post_translations_journal_post_id_fkey FOREIGN KEY (journal_post_id) REFERENCES public.journal_posts(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.journal_posts
    ADD CONSTRAINT journal_posts_author_id_fkey FOREIGN KEY (author_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.journal_posts
    ADD CONSTRAINT journal_posts_journal_category_id_fkey FOREIGN KEY (journal_category_id) REFERENCES public.journal_categories(id);

ALTER TABLE ONLY public.login_activities
    ADD CONSTRAINT login_activities_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_product_variant_id_fkey FOREIGN KEY (product_variant_id) REFERENCES public.product_variants(id);

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_promo_code_id_fkey FOREIGN KEY (promo_code_id) REFERENCES public.promo_codes(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.order_notes
    ADD CONSTRAINT order_notes_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.order_refunds
    ADD CONSTRAINT order_refunds_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.order_refunds
    ADD CONSTRAINT order_refunds_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT order_status_history_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_address_id_fkey FOREIGN KEY (address_id) REFERENCES public.addresses(id);

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_promo_code_id_fkey FOREIGN KEY (promo_code_id) REFERENCES public.promo_codes(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.origin_translations
    ADD CONSTRAINT origin_translations_origin_id_fkey FOREIGN KEY (origin_id) REFERENCES public.origins(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.pairing_translations
    ADD CONSTRAINT pairing_translations_pairing_id_fkey FOREIGN KEY (pairing_id) REFERENCES public.pairings(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.processing_method_translations
    ADD CONSTRAINT processing_method_translations_processing_method_id_fkey FOREIGN KEY (processing_method_id) REFERENCES public.processing_methods(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_brewing_methods
    ADD CONSTRAINT product_brewing_methods_brewing_method_id_fkey FOREIGN KEY (brewing_method_id) REFERENCES public.brewing_methods(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_brewing_methods
    ADD CONSTRAINT product_brewing_methods_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_journal_posts
    ADD CONSTRAINT product_journal_posts_journal_post_id_fkey FOREIGN KEY (journal_post_id) REFERENCES public.journal_posts(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_journal_posts
    ADD CONSTRAINT product_journal_posts_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_media
    ADD CONSTRAINT product_media_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_option_applies_to
    ADD CONSTRAINT product_option_applies_to_option_id_fkey FOREIGN KEY (option_id) REFERENCES public.product_options(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_option_links
    ADD CONSTRAINT product_option_links_option_id_fkey FOREIGN KEY (option_id) REFERENCES public.product_options(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_option_links
    ADD CONSTRAINT product_option_links_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_option_translations
    ADD CONSTRAINT product_option_translations_option_id_fkey FOREIGN KEY (option_id) REFERENCES public.product_options(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_option_value_translations
    ADD CONSTRAINT product_option_value_translations_option_value_id_fkey FOREIGN KEY (option_value_id) REFERENCES public.product_option_values(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_option_values
    ADD CONSTRAINT product_option_values_option_id_fkey FOREIGN KEY (option_id) REFERENCES public.product_options(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_pairings
    ADD CONSTRAINT product_pairings_pairing_id_fkey FOREIGN KEY (pairing_id) REFERENCES public.pairings(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_pairings
    ADD CONSTRAINT product_pairings_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_tasting_notes
    ADD CONSTRAINT product_tasting_notes_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_tasting_notes
    ADD CONSTRAINT product_tasting_notes_tasting_note_id_fkey FOREIGN KEY (tasting_note_id) REFERENCES public.tasting_notes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_translations
    ADD CONSTRAINT product_translations_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_variants
    ADD CONSTRAINT product_variants_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id);

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_farm_id_fkey FOREIGN KEY (farm_id) REFERENCES public.farms(id);

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_origin_id_fkey FOREIGN KEY (origin_id) REFERENCES public.origins(id);

ALTER TABLE ONLY public.promo_code_categories
    ADD CONSTRAINT promo_code_categories_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.promo_code_categories
    ADD CONSTRAINT promo_code_categories_promo_code_id_fkey FOREIGN KEY (promo_code_id) REFERENCES public.promo_codes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.promo_code_redemptions
    ADD CONSTRAINT promo_code_redemptions_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.promo_code_redemptions
    ADD CONSTRAINT promo_code_redemptions_promo_code_id_fkey FOREIGN KEY (promo_code_id) REFERENCES public.promo_codes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.promo_code_redemptions
    ADD CONSTRAINT promo_code_redemptions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.roast_level_translations
    ADD CONSTRAINT roast_level_translations_roast_level_id_fkey FOREIGN KEY (roast_level_id) REFERENCES public.roast_levels(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_name_fkey FOREIGN KEY (role_name) REFERENCES public.roles(name) ON DELETE CASCADE;

ALTER TABLE ONLY public.shipping_zone_cities
    ADD CONSTRAINT shipping_zone_cities_shipping_zone_id_fkey FOREIGN KEY (shipping_zone_id) REFERENCES public.shipping_zones(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.tasting_note_translations
    ADD CONSTRAINT tasting_note_translations_tasting_note_id_fkey FOREIGN KEY (tasting_note_id) REFERENCES public.tasting_notes(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_linked_accounts
    ADD CONSTRAINT user_linked_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_profiles
    ADD CONSTRAINT user_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.variant_option_values
    ADD CONSTRAINT variant_option_values_option_value_id_fkey FOREIGN KEY (option_value_id) REFERENCES public.product_option_values(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.variant_option_values
    ADD CONSTRAINT variant_option_values_variant_id_fkey FOREIGN KEY (variant_id) REFERENCES public.product_variants(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.verification_codes
    ADD CONSTRAINT verification_codes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.wishlist_items
    ADD CONSTRAINT wishlist_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.wishlist_items
    ADD CONSTRAINT wishlist_items_wishlist_id_fkey FOREIGN KEY (wishlist_id) REFERENCES public.wishlists(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.wishlists
    ADD CONSTRAINT wishlists_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
