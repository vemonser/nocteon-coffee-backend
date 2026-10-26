package com.nocteon.nocteon_api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.nocteon.nocteon_api.NocteonApiApplication;
import com.nocteon.nocteon_api.auth.entity.User;
import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.notifications.entity.Notification;
import com.nocteon.nocteon_api.notifications.repository.NotificationRepository;
import com.nocteon.nocteon_api.support.TestJwtKeys;

@SpringBootTest(classes = NocteonApiApplication.class)
@AutoConfigureMockMvc
class NotificationPaginationIntegrationTest {

    private static final Long TEST_USER_ID = 9_000_001L;
    private static final String PILOT_TYPE = "PAGINATION_PILOT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    private UserPrincipal principal;
    private final List<Long> createdIds = new ArrayList<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
    }

    @BeforeEach
    void setUp() {
        // Dedicated recipient id: no seeded user owns these rows, so traversal
        // and counts cannot be affected by other tests or seed data.
        principal = UserPrincipal.create(User.builder()
                .id(TEST_USER_ID)
                .username("notification-pagination-test")
                .email("notification-pagination-test@example.test")
                .role("ADMIN")
                .enabled(true)
                .build());

        jdbcTemplate.update("DELETE FROM notifications WHERE recipient_user_id = ?", TEST_USER_ID);

        createdIds.clear();
        for (int i = 1; i <= 10; i++) {
            Notification saved = notificationRepository.save(Notification.builder()
                    .recipientUserId(TEST_USER_ID)
                    .type(PILOT_TYPE)
                    .entityType("test")
                    .entityId((long) i)
                    .params(null)
                    .build());
            createdIds.add(saved.getId());
        }

        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00.000001Z")),
                createdIds.get(0));
        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00.000002Z")),
                createdIds.get(1));

        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2025-01-01T00:00:00Z")),
                createdIds.get(8));
        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2025-01-01T00:00:00Z")),
                createdIds.get(9));
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdIds) {
            notificationRepository.deleteById(id);
        }
        jdbcTemplate.update("DELETE FROM notifications WHERE recipient_user_id = ?", TEST_USER_ID);
        createdIds.clear();
    }

    @Test
    void firstPageReturnsFiveRecords() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("page", "0")
                        .param("size", "5")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(5));
    }

    @Test
    void secondPageIsNonEmpty() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("page", "1")
                        .param("size", "5")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void omittedPageReturnsCursorResponse() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").exists())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getContentAsString().contains("\"nextCursor\"")))
                .andExpect(jsonPath("$.data.hasMore").exists());
    }

    @Test
    void cursorPaginationTraversesAllPagesWithoutDuplicates() throws Exception {
        List<Integer> allIds = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        int maxPages = maxTraversalPages(3);

        do {
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                    get("/api/dashboard/notifications").param("limit", "3").with(user(principal));
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }

            MvcResult result = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn();

            List<Integer> pageIds = JsonPath.read(result.getResponse().getContentAsString(),
                    "$.data.content[*].id");
            allIds.addAll(pageIds);

            List<String> pageCreatedAt = JsonPath.read(result.getResponse().getContentAsString(),
                    "$.data.content[*].createdAt");
            List<java.time.Instant> pageTimestamps = pageCreatedAt.stream()
                    .map(java.time.Instant::parse)
                    .toList();
            assertThat(pageTimestamps).isSortedAccordingTo(Comparator.reverseOrder());

            Object cursorValue = JsonPath.read(result.getResponse().getContentAsString(), "$.data.nextCursor");
            if (cursorValue != null) {
                assertThat(cursorValue).isInstanceOf(String.class);
            }
            cursor = cursorValue == null ? null : (String) cursorValue;
            boolean hasMore = JsonPath.read(result.getResponse().getContentAsString(), "$.data.hasMore");
            pages++;

            org.junit.jupiter.api.Assertions.assertTrue(pages <= maxPages, "Cursor traversal did not terminate");
        } while (cursor != null && !cursor.isBlank());

        List<Integer> createdIdValues = createdIds.stream().map(Long::intValue).toList();
        assertThat(allIds).containsAll(createdIdValues);
        assertThat(allIds.stream().filter(createdIdValues::contains).count())
                .isEqualTo(createdIdValues.size());
        assertThat(allIds).doesNotHaveDuplicates();
    }

    @Test
    void malformedCursorReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "not-a-cursor")
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cursorPreservesSubMillisecondTimestamps() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/notifications")
                        .param("limit", "100")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.content");

        Map<String, Object> firstMicrosecondRow = content.stream()
                .filter(row -> createdIds.get(0).equals(((Number) row.get("id")).longValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("First microsecond notification was not returned"));
        Map<String, Object> secondMicrosecondRow = content.stream()
                .filter(row -> createdIds.get(1).equals(((Number) row.get("id")).longValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Second microsecond notification was not returned"));

        assertThat(firstMicrosecondRow.get("createdAt").toString()).endsWith("000001Z");
        assertThat(secondMicrosecondRow.get("createdAt").toString()).endsWith("000002Z");
    }

    @Test
    void legacyMillisecondCursorReturnsNextPage() throws Exception {
        MvcResult firstResult = mockMvc.perform(get("/api/dashboard/notifications")
                        .param("limit", "1")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andReturn();

        String firstBody = firstResult.getResponse().getContentAsString();
        Integer firstId = JsonPath.read(firstBody, "$.data.content[0].id");
        String firstCreatedAt = JsonPath.read(firstBody, "$.data.content[0].createdAt");
        String legacyCursor = java.time.Instant.parse(firstCreatedAt).toEpochMilli() + "_" + firstId;

        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", legacyCursor)
                        .param("limit", "1")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").exists())
                .andExpect(jsonPath("$.data.hasMore").exists());
    }

    @Test
    void identicalTimestampsAreOrderedByIdAcrossPageBoundary() throws Exception {
        List<Integer> allIds = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        int maxPages = maxTraversalPages(1);

        do {
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                    get("/api/dashboard/notifications").param("limit", "1").with(user(principal));
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }

            MvcResult result = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn();

            List<Integer> pageIds = JsonPath.read(result.getResponse().getContentAsString(),
                    "$.data.content[*].id");
            allIds.addAll(pageIds);

            Object cursorValue = JsonPath.read(result.getResponse().getContentAsString(), "$.data.nextCursor");
            if (cursorValue != null) {
                assertThat(cursorValue).isInstanceOf(String.class);
            }
            cursor = cursorValue == null ? null : (String) cursorValue;
            pages++;
            org.junit.jupiter.api.Assertions.assertTrue(pages <= maxPages, "Cursor traversal did not terminate");
        } while (cursor != null && !cursor.isBlank());

        int firstTiePosition = allIds.indexOf(createdIds.get(8).intValue());
        int secondTiePosition = allIds.indexOf(createdIds.get(9).intValue());

        assertThat(firstTiePosition).isGreaterThanOrEqualTo(0);
        assertThat(secondTiePosition).isGreaterThanOrEqualTo(0);
        assertThat(Math.abs(firstTiePosition - secondTiePosition)).isEqualTo(1);
        assertThat(secondTiePosition).isLessThan(firstTiePosition);
        assertThat(allIds).doesNotHaveDuplicates();
    }

    @Test
    void finalCursorPageReportsExhaustion() throws Exception {
        String cursor = null;
        String lastBody = null;

        do {
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                    get("/api/dashboard/notifications").param("limit", "1").with(user(principal));
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }

            MvcResult result = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn();
            lastBody = result.getResponse().getContentAsString();
            Object finalCursorValue = JsonPath.read(lastBody, "$.data.nextCursor");
            if (finalCursorValue != null) {
                assertThat(finalCursorValue).isInstanceOf(String.class);
            }
            cursor = finalCursorValue == null ? null : (String) finalCursorValue;
        } while (cursor != null && !cursor.isBlank());

        org.junit.jupiter.api.Assertions.assertTrue(lastBody.contains("\"hasMore\":false"));
        org.junit.jupiter.api.Assertions.assertTrue(lastBody.contains("\"nextCursor\":null"));
    }

    @Test
    void unicodeDigitCursorReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "١٢٣_1")
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void numericOverflowCursorReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "999999999999999999999_1")
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedCursorIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "123_abc")
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyCursorIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "123_")
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyCursorUsesFirstPage() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "   ")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").exists())
                .andExpect(jsonPath("$.data.hasMore").exists());
    }

    @Test
    void unicodeDigitCursorIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "123_١")
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void legacyMillisecondCursorDoesNotReplaySubMillisecondBoundaryRow() throws Exception {
        long boundaryMillis = notificationRepository.findById(createdIds.get(0))
                .orElseThrow()
                .getCreatedAt()
                .toEpochMilli();
        String legacyCursor = boundaryMillis + "_" + createdIds.get(0);

        MvcResult result = mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", legacyCursor)
                        .param("limit", "100")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.data.content[*].id");
        assertThat(ids).doesNotContain(createdIds.get(0).intValue(), createdIds.get(1).intValue());
    }

    @Test
    void legacyMillisecondCursorWithMismatchedTimestampReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "0_" + createdIds.get(0))
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unreadOnlyCursorModeExcludesReadNotifications() throws Exception {
        Long readId = createdIds.get(2);
        jdbcTemplate.update("UPDATE notifications SET read_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-01-02T00:00:00Z")), readId);

        MvcResult result = mockMvc.perform(get("/api/dashboard/notifications")
                        .param("limit", "100")
                        .param("unreadOnly", "true")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.data.content[*].id");
        assertThat(ids).doesNotContain(readId.intValue());
        assertThat(ids).containsAnyOf(createdIds.get(3).intValue(), createdIds.get(4).intValue());
    }

    @Test
    void unreadOnlyPageModeExcludesReadNotifications() throws Exception {
        Long readId = createdIds.get(4);
        jdbcTemplate.update("UPDATE notifications SET read_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-01-02T00:00:00Z")), readId);

        MvcResult result = mockMvc.perform(get("/api/dashboard/notifications")
                        .param("page", "0")
                        .param("size", "100")
                        .param("unreadOnly", "true")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.data.content[*].id");
        assertThat(ids).doesNotContain(readId.intValue());
        assertThat(ids).containsAnyOf(createdIds.get(3).intValue(), createdIds.get(5).intValue());
    }

    @Test
    void positiveSizeOverridesLimitInCursorMode() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("size", "3")
                        .param("limit", "20")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3));
    }

    @Test
    void emptyTimestampCursorReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "_" + createdIds.get(0))
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeTimestampCursorReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "-1_" + createdIds.get(0))
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroCursorIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "2026-01-01T00:00:00Z_0")
                        .with(user(principal)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isoOffsetCursorIsAccepted() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("cursor", "2026-01-01T03:00:00+03:00_" + createdIds.get(0))
                        .param("limit", "5")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").exists());
    }

    @Test
    void zeroSizeUsesControllerDefault() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("page", "0")
                        .param("size", "0")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void oversizedSizeIsClampedToMaximum() throws Exception {
        mockMvc.perform(get("/api/dashboard/notifications")
                        .param("page", "0")
                        .param("size", "101")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    /**
     * Number of cursor pages needed to exhaust the current user's notifications,
     * plus a small allowance. Avoids assuming a fixed total row count.
     */
    private int maxTraversalPages(int pageSize) {
        long total = notificationRepository.findAll().stream()
                .filter(notification -> TEST_USER_ID.equals(notification.getRecipientUserId()))
                .count();
        return (int) ((total / pageSize) + 5);
    }
}
