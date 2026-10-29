package com.nocteon.nocteon_api.order.invoice;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import com.lowagie.text.pdf.BaseFont;

/**
 * The two font weights the invoice draws with, embedded in the PDF.
 *
 * <p><strong>Why a font travels in the jar.</strong> The built-in PDF fonts
 * (Helvetica and friends) have no Arabic glyphs at all: printing a customer
 * name or an item name in Arabic with them produces empty boxes or Latin-1
 * mojibake, which is the failure this feature is supposed to avoid. So one
 * Unicode font is embedded — IBM Plex Sans Arabic, SIL OFL 1.1 (the licence
 * ships next to the files in {@code resources/fonts/OFL.txt}) — because it
 * carries BOTH the Arabic block and the Latin one, so a single line that mixes
 * "قهوة Yirgacheffe 250 جرام" prints in one typeface instead of changing family
 * mid-word.
 *
 * <p>{@code IDENTITY_H} + {@code EMBEDDED}: the characters are written as
 * Unicode code points mapped to this font's own glyphs, and the font is
 * embedded (subset) in the file, so a reader that does not have it installed
 * still sees the invoice exactly as generated.
 *
 * <p>Fonts are parsed once per JVM ({@code cached = true}) — parsing a 240 KB
 * TTF per download would be paid on every click for no benefit.
 */
final class InvoiceFonts {

    private static final String REGULAR = "/fonts/IBMPlexSansArabic-Regular.ttf";
    private static final String BOLD = "/fonts/IBMPlexSansArabic-Bold.ttf";

    private InvoiceFonts() {
    }

    static BaseFont regular() {
        return load(REGULAR);
    }

    static BaseFont bold() {
        return load(BOLD);
    }

    private static BaseFont load(String resource) {
        try {
            return BaseFont.createFont(resource, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, read(resource), null);
        } catch (IOException | com.lowagie.text.DocumentException e) {
            // A missing or unreadable font is a broken build, not a bad request:
            // failing loudly here beats handing the customer a PDF without glyphs.
            throw new UncheckedIOException(new IOException("The invoice font " + resource + " could not be loaded", e));
        }
    }

    private static byte[] read(String resource) throws IOException {
        try (InputStream stream = InvoiceFonts.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing classpath resource: " + resource);
            }
            return stream.readAllBytes();
        }
    }
}
