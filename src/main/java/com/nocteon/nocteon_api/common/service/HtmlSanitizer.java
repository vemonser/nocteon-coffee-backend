package com.nocteon.nocteon_api.common.service;

import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Providers whose embed player may be framed. Everything else is not a video: an arbitrary
     * iframe is a page inside the article, not a clip.
     */
    private static final Set<String> VIDEO_HOSTS = Set.of(
            "www.youtube.com", "youtube.com", "m.youtube.com",
            "www.youtube-nocookie.com", "youtube-nocookie.com",
            "player.vimeo.com");

    private static final Safelist ALLOWLIST = new Safelist()
            .addTags(
                    "p", "h1", "h2", "h3", "h4", "h5", "h6",
                    "strong", "b", "em", "i", "u", "s",
                    "a", "ul", "ol", "li", "blockquote", "code", "pre",
                    "img", "br", "hr", "iframe",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "src", "alt", "title")
            .addAttributes("iframe", "src", "class", "allowfullscreen", "frameborder", "width", "height", "title")
            .addAttributes("th", "colspan", "rowspan", "scope")
            .addAttributes("td", "colspan", "rowspan")
            .addProtocols("a", "href", "https", "mailto")
            .addProtocols("img", "src", "https")
            .addProtocols("iframe", "src", "https")
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

        // Quill 2 renders a code block as nested divs; convert that to the pre/code pair the
        // allowlist (and the reader's stylesheet) understands, so the code survives a save.
        Document source = Jsoup.parseBodyFragment(html);
        source.outputSettings().prettyPrint(false);
        for (Element container : source.select("div.ql-code-block-container")) {
                String code = container.select("div.ql-code-block").stream()
                                .map(Element::wholeText)
                                .collect(Collectors.joining("\n"));
                Element pre = source.createElement("pre");
                pre.appendElement("code").text(code);
                container.replaceWith(pre);
        }

        String cleaned = Jsoup.clean(source.body().html(), "", ALLOWLIST, outputSettings());

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
        for (Element frame : document.select("iframe")) {
            if (!isAllowedVideo(frame.attr("src"))) {
                frame.remove();
                continue;
            }
            // Quill's own class is the only one the reader's stylesheet needs; anything else
            // is presentation smuggled through an embed.
            String keptClass = java.util.Arrays.stream(frame.attr("class").split("\\s+"))
                    .filter(token -> token.equals("ql-video"))
                    .findFirst()
                    .orElse("");
            if (keptClass.isEmpty()) {
                frame.removeAttr("class");
            } else {
                frame.attr("class", keptClass);
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

    private static boolean isAllowedVideo(String src) {
        URI uri;
        try {
            uri = URI.create(src);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        // `https://evil@www.youtube.com/embed/x` has an allowed host but carries userinfo.
        if (uri.getUserInfo() != null) {
            return false;
        }
        // An explicit non-default port is not the provider's player.
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        if (!VIDEO_HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        // Exactly one id segment: /embed/<id> or /video/<id>.
        return path.matches("^/(embed|video)/[A-Za-z0-9_-]+/?$");
    }

    private static boolean isAllowedLink(String href) {
        URI uri;
        try {
            uri = URI.create(href);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if ("mailto".equals(scheme)) {
            String address = uri.getRawSchemeSpecificPart();
            return address != null && !address.isBlank();
        }
        // `https://trusted.example@evil.example/` starts with a trusted-looking string but
        // resolves to evil.example: userinfo is never something an article link needs.
        return "https".equals(scheme)
                && uri.getHost() != null
                && !uri.getHost().isBlank()
                && uri.getUserInfo() == null;
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
        if (uri.getUserInfo() != null) {
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
        // No cloud name means no uploads can happen from this application at all. Refusing
        // images here keeps the rule honest instead of accepting any account's asset in a
        // configuration where the account cannot be checked.
        if (cloudName == null || cloudName.isBlank()) {
            return false;
        }
        // `/other-account/...` must not pass: the first segment is the account.
        return path.startsWith("/" + cloudName + "/") && path.contains(CDN_FOLDER);
    }
}
