package com.nocteon.nocteon_api.common.service;

import java.net.URI;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.cloudinary.config.CloudinaryProperties;

import lombok.RequiredArgsConstructor;

/**
 * Allowlist sanitizer for admin-authored rich text (the journal body, and the product
 * story that reuses the same editor).
 *
 * <p>The journal body is HTML produced by Quill and stored verbatim. Rendering it with
 * {@code innerHTML} is only safe if the stored value is already sanitized, so the
 * contract is: <strong>every HTML string this application persists has passed through
 * {@link #sanitize(String)}</strong>. Readers may then insert the value as HTML; they
 * must not treat it as trusted for anything else.
 *
 * <p>The allowlist is deliberately the same as the editor toolbar plus the table tags a
 * future toolbar may add. Anything outside it is removed, attributes not listed are
 * removed, and {@code a[href]} is limited to {@code https} and {@code mailto} — the two
 * schemes a blog post legitimately links with. {@code img[src]} is limited to our own
 * Cloudinary account and the {@code nocteon/} folder, so a stolen admin session cannot
 * hotlink or beacon through a third-party host.
 *
 * <p>Removed markup is dropped rather than escaped: a {@code <script>} body is discarded,
 * never shown as text.
 */
@Service
@RequiredArgsConstructor
public class HtmlSanitizer {

    /** Cloudinary's delivery host. Assets uploaded by {@code CloudinaryService} live here. */
    static final String CDN_HOST = "res.cloudinary.com";

    /** Folder prefix every upload from this application is written under. */
    private static final String CDN_FOLDER = "/nocteon/";

    private static final Safelist ALLOWLIST = new Safelist()
            .addTags(
                    "p", "h1", "h2", "h3", "h4", "h5", "h6",
                    "strong", "b", "em", "i", "u", "s",
                    "a", "ul", "ol", "li", "blockquote", "code", "pre",
                    "img", "br", "hr",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "title")
            .addAttributes("th", "colspan", "rowspan", "scope")
            .addAttributes("td", "colspan", "rowspan")
            .addProtocols("a", "href", "https", "mailto")
            .addProtocols("img", "src", "https")
            // A link the reader clicks must not hand the opener back to the target page.
            // Not enforced for images: Cloudinary serves them, nothing opens a window.
            .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    private final CloudinaryProperties cloudinaryProperties;

    /**
     * Sanitize one HTML fragment.
     *
     * @param html the raw editor output; {@code null} and blank values pass through unchanged
     * @return the same content with disallowed tags and attributes removed
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }

        String cleaned = Jsoup.clean(html, "", ALLOWLIST, outputSettings());

        // Protocol checks in Safelist are necessary but not sufficient for the two
        // attributes that can point at another origin: JSoup validates the scheme, not
        // the host. Re-check both here so the rule lives in one readable place.
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings().prettyPrint(false);

        for (Element link : document.select("a[href]")) {
            if (!isAllowedLink(link.attr("href"))) {
                // Keep the words, drop the anchor: an <a> with no href is still tabbable
                // noise and looks like a link the stylesheet will colour.
                link.unwrap();
            }
        }
        for (Element image : document.select("img[src]")) {
            if (!isOwnMediaUrl(image.attr("src"))) {
                image.remove();
            }
        }

        return document.body().html();
    }

    private static Document.OutputSettings outputSettings() {
        return new Document.OutputSettings().prettyPrint(false);
    }

    private static boolean isAllowedLink(String href) {
        String lower = href.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("mailto:");
    }

    /**
     * True when {@code url} points at this account's Cloudinary uploads.
     *
     * <p>Compared on the parsed URI, not on prefixes: {@code https://res.cloudinary.com@evil.example/}
     * starts with the CDN host as a string but its host is {@code evil.example}.
     */
    private boolean isOwnMediaUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        if (!CDN_HOST.equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.contains(CDN_FOLDER)) {
            return false;
        }
        String cloudName = cloudinaryProperties.getCloudName();
        // A blank cloud name is the local/test configuration. The folder rule still
        // scopes the URL to uploads this application made.
        return cloudName == null
                || cloudName.isBlank()
                || path.startsWith("/" + cloudName + "/");
    }
}
