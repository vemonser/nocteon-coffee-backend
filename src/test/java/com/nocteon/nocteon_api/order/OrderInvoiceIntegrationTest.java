package com.nocteon.nocteon_api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.address.entity.Address;
import com.nocteon.nocteon_api.address.repository.AddressRepository;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.repository.UserRepository;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.category.entity.Category;
import com.nocteon.nocteon_api.category.repository.CategoryRepository;
import com.nocteon.nocteon_api.mail.entity.EmailLog;
import com.nocteon.nocteon_api.mail.enums.EmailStatus;
import com.nocteon.nocteon_api.mail.enums.EmailType;
import com.nocteon.nocteon_api.mail.repository.EmailLogRepository;
import com.nocteon.nocteon_api.mail.service.BrevoClient;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderItem;
import com.nocteon.nocteon_api.order.enums.OrderStatus;
import com.nocteon.nocteon_api.order.invoice.InvoiceData;
import com.nocteon.nocteon_api.order.repository.OrderItemRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;
import com.nocteon.nocteon_api.order.service.OrderInvoiceService;
import com.nocteon.nocteon_api.payment.enums.PaymentMethod;
import com.nocteon.nocteon_api.payment.enums.PaymentStatus;
import com.nocteon.nocteon_api.product.entity.Product;
import com.nocteon.nocteon_api.product.entity.ProductTranslation;
import com.nocteon.nocteon_api.product.entity.ProductVariant;
import com.nocteon.nocteon_api.product.enums.ProductType;
import com.nocteon.nocteon_api.product.repository.ProductRepository;
import com.nocteon.nocteon_api.product.repository.ProductVariantRepository;
import com.nocteon.nocteon_api.support.TestJwtKeys;

/**
 * FE-16: the invoice PDF and the confirmation resend.
 *
 * <p>These are the four things a unit test cannot prove:
 *
 * <ul>
 *   <li>the endpoint answers a real PDF (magic bytes, content type, filename)
 *       and the JSON envelope is not in the way;</li>
 *   <li>the document says what the ORDER stored — the line prices, the
 *       discount, the shipping and the total of the day it was placed;</li>
 *   <li>a customer cannot read someone else's invoice, while an admin can;</li>
 *   <li>a resend really writes an {@code email_log} row (one per press) and
 *       answers with the backend's own sentence.</li>
 * </ul>
 *
 * <p>The provider is mocked: {@link BrevoClient} is the one collaborator whose
 * real implementation is an HTTP call to someone else's server, and a test that
 * needs the internet is not a test.
 */
@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
@Transactional
class OrderInvoiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductVariantRepository variantRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private EmailLogRepository emailLogRepository;
    @Autowired
    private OrderInvoiceService invoiceService;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockitoBean
    private BrevoClient brevoClient;

    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    private User admin;
    private User customer;
    private User otherCustomer;
    private Order order;
    private String variantSku;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        admin = persistUser("invoice-admin-" + suffix, "ADMIN");
        customer = persistUser("invoice-customer-" + suffix, "CUSTOMER");
        otherCustomer = persistUser("invoice-stranger-" + suffix, "CUSTOMER");

        Category category = categoryRepository.save(Category.builder().slug("invoice-cat-" + suffix).isActive(true).build());
        Product product = Product.builder()
                .slug("invoice-product-" + suffix)
                .category(category)
                .productType(ProductType.COFFEE)
                .isActive(true)
                .build();
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("en").name("Ethiopia Yirgacheffe 250g").build());
        product.getTranslations().add(ProductTranslation.builder()
                .product(product).language("ar").name("قهوة إثيوبيا يرجاتشيف 250 جرام").build());
        productRepository.save(product);

        variantSku = "INV-" + suffix;
        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(variantSku)
                .price(new BigDecimal("650.00"))
                .stockQuantity(10)
                .isActive(true)
                .version(0L)
                .build());

        Address address = addressRepository.save(Address.builder()
                .user(customer)
                .fullName("Sara Ahmed")
                .phone("+201000000000")
                .street("12 Nile Street")
                .city("Cairo")
                .country("Egypt")
                .postalCode("11511")
                .isDefault(true)
                .build());

        order = orderRepository.save(Order.builder()
                .orderNumber("ORD-2026-" + suffix)
                .user(customer)
                .address(address)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("1260.00"))
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.ONLINE)
                .shippingCost(new BigDecimal("60.00"))
                .discountAmount(new BigDecimal("100.00"))
                .build());

        order.getItems().add(OrderItem.builder()
                .order(order)
                .productVariant(variant)
                .quantity(2)
                .unitPrice(new BigDecimal("650.00"))
                .totalPrice(new BigDecimal("1300.00"))
                .build());

        order = orderRepository.saveAndFlush(order);
        entityManager.flush();
        entityManager.clear();
    }

    // ─── the document ────────────────────────────────────────────────────────

    /** The endpoint answers a file a reader can open, not a JSON envelope. */
    @Test
    void theInvoiceIsAPdfNamedAfterTheOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/orders/{id}/invoice", order.getId())
                .with(user(principalOf(admin))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
        assertThat(body.length).isGreaterThan(2_000);

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).contains("attachment").contains("invoice-" + order.getOrderNumber() + ".pdf");
    }

    /** Arabic is a real document, not the English one with a different name. */
    @Test
    void theArabicInvoiceIsDrawnRightToLeftWithItsOwnLabels() throws Exception {
        byte[] english = mockMvc.perform(get("/api/dashboard/orders/{id}/invoice", order.getId())
                .param("lang", "en")
                .with(user(principalOf(admin))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        byte[] arabic = mockMvc.perform(get("/api/dashboard/orders/{id}/invoice", order.getId())
                .param("lang", "ar")
                .with(user(principalOf(admin))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(arabic, 0, 5)).isEqualTo("%PDF-");
        assertThat(arabic).isNotEqualTo(english);
    }

    /** The numbers are the order's, not the catalogue's: prices are snapshots. */
    @Test
    void theInvoicePrintsTheStoredPricesTotalsAndCustomer() {
        InvoiceData data = invoiceService.dataOf(order.getId(), "en", principalOf(admin));

        assertThat(data.orderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(data.customerName()).isEqualTo("Sara Ahmed");
        assertThat(data.customerEmail()).isEqualTo(customer.getEmail());
        assertThat(data.lines()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("Ethiopia Yirgacheffe 250g");
            assertThat(line.sku()).isEqualTo(variantSku);
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.unitPrice()).isEqualByComparingTo("650.00");
            assertThat(line.lineTotal()).isEqualByComparingTo("1300.00");
        });
        assertThat(data.subtotal()).isEqualByComparingTo("1300.00");
        assertThat(data.discount()).isEqualByComparingTo("100.00");
        assertThat(data.shipping()).isEqualByComparingTo("60.00");
        assertThat(data.total()).isEqualByComparingTo("1260.00");
        assertThat(data.currency()).isNotBlank();
        // Money is printed the way the dashboard prints it, currency first.
        assertThat(data.money(new BigDecimal("1260"))).startsWith(data.currency() + " ").endsWith("1,260.00");
    }

    /** The Arabic document carries the Arabic product name and labels. */
    @Test
    void theArabicDocumentUsesTheArabicLabelsAndProductName() {
        InvoiceData data = invoiceService.dataOf(order.getId(), "ar", principalOf(admin));

        assertThat(data.labels().rightToLeft()).isTrue();
        assertThat(data.labels().invoice()).isEqualTo("فاتورة");
        assertThat(data.lines()).singleElement()
                .extracting(InvoiceData.Line::name)
                .isEqualTo("قهوة إثيوبيا يرجاتشيف 250 جرام");
        // Statuses speak the dashboard's Arabic, not a second vocabulary.
        assertThat(data.labels().orderStatus(OrderStatus.CONFIRMED)).isEqualTo("مؤكد");
        assertThat(data.labels().paymentStatus(PaymentStatus.PAID)).isEqualTo("مدفوع");
    }

    // ─── access ──────────────────────────────────────────────────────────────

    /** An invoice is a customer's name, address and money: not a shared document. */
    @Test
    void aCustomerCannotDownloadSomeoneElsesInvoice() throws Exception {
        mockMvc.perform(get("/api/dashboard/orders/{id}/invoice", order.getId())
                .with(user(principalOf(otherCustomer))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCustomerCanDownloadTheirOwnInvoice() throws Exception {
        mockMvc.perform(get("/api/dashboard/orders/{id}/invoice", order.getId())
                .with(user(principalOf(customer))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    /** Holding some other permission is not holding this one. */
    @Test
    void theInvoiceNeedsTheOrderReadPermission() throws Exception {
        mockMvc.perform(get("/api/dashboard/orders/{id}/invoice", order.getId())
                .with(user("a-moderator-without-orders")
                        .authorities(new SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownOrderAnswersNotFound() throws Exception {
        mockMvc.perform(get("/api/dashboard/orders/{id}/invoice", 999_999_999L)
                .with(user(principalOf(admin))))
                .andExpect(status().isNotFound());
    }

    // ─── resend ──────────────────────────────────────────────────────────────

    /**
     * The resend writes a row in {@code email_log} and answers with the sentence
     * the dashboard shows.
     */
    @Test
    void resendingTheConfirmationWritesAnEmailLogRow() throws Exception {
        org.mockito.Mockito.when(brevoClient.sendTransactionalEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(EmailType.ORDER_CONFIRMATION),
                org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("provider-message-id");

        long before = emailLogRepository.countByRelatedEntityTypeAndRelatedEntityIdAndEmailType(
                "ORDER", order.getId(), EmailType.ORDER_CONFIRMATION);

        mockMvc.perform(post("/api/dashboard/orders/{id}/confirmation-email", order.getId())
                .with(user(principalOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientEmail").value(customer.getEmail()))
                .andExpect(jsonPath("$.data.emailStatus").value("SENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(customer.getEmail())));

        long after = emailLogRepository.countByRelatedEntityTypeAndRelatedEntityIdAndEmailType(
                "ORDER", order.getId(), EmailType.ORDER_CONFIRMATION);
        assertThat(after).isEqualTo(before + 1);

        EmailLog row = emailLogRepository
                .findByRelatedEntityTypeAndRelatedEntityIdIn("ORDER", List.of(order.getId())).stream()
                .filter(log -> log.getEmailType() == EmailType.ORDER_CONFIRMATION)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(row.getRecipientEmail()).isEqualTo(customer.getEmail());
        assertThat(row.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(row.getIdempotencyKey()).contains("RESEND");
    }

    /** Pressing it twice is two attempts, and the log says so. */
    @Test
    void resendingTwiceWritesTwoRows() throws Exception {
        org.mockito.Mockito.when(brevoClient.sendTransactionalEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(EmailType.ORDER_CONFIRMATION),
                org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn("provider-message-id");

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/dashboard/orders/{id}/confirmation-email", order.getId())
                    .with(user(principalOf(admin))))
                    .andExpect(status().isOk());
        }

        assertThat(emailLogRepository.countByRelatedEntityTypeAndRelatedEntityIdAndEmailType(
                "ORDER", order.getId(), EmailType.ORDER_CONFIRMATION)).isEqualTo(2);
    }

    /** A provider that refuses is reported, not swallowed. */
    @Test
    void aRefusedSendIsRecordedAndTheMessageSaysSo() throws Exception {
        org.mockito.Mockito.when(brevoClient.sendTransactionalEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(EmailType.ORDER_CONFIRMATION),
                org.mockito.ArgumentMatchers.anyMap()))
                .thenThrow(new com.nocteon.nocteon_api.mail.exception.BrevoApiException("Mailbox does not exist"));

        mockMvc.perform(post("/api/dashboard/orders/{id}/confirmation-email", order.getId())
                .with(user(principalOf(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailStatus").value("DEAD"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(customer.getEmail())));

        EmailLog row = emailLogRepository
                .findByRelatedEntityTypeAndRelatedEntityIdIn("ORDER", List.of(order.getId())).stream()
                .filter(log -> log.getEmailType() == EmailType.ORDER_CONFIRMATION)
                .findFirst()
                .orElseThrow();
        assertThat(row.getStatus()).isEqualTo(EmailStatus.DEAD);
        assertThat(row.getLastError()).contains("Mailbox does not exist");
    }

    @Test
    void resendingNeedsTheOrderUpdatePermission() throws Exception {
        mockMvc.perform(post("/api/dashboard/orders/{id}/confirmation-email", order.getId())
                .with(user(principalOf(customer))))
                .andExpect(status().isForbidden());
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private User persistUser(String name, String role) {
        return userRepository.save(User.builder()
                .username(name)
                .email(name + "@example.test")
                .password("x")
                .provider(com.nocteon.nocteon_api.auth.enums.Provider.LOCAL)
                .role(role)
                .enabled(true)
                .build());
    }

    /** The principal the security filter chain would build from this user. */
    private UserPrincipal principalOf(User user) {
        return UserPrincipal.create(userRepository.findById(user.getId()).orElseThrow());
    }
}
