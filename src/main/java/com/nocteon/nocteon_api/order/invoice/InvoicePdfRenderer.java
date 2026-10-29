package com.nocteon.nocteon_api.order.invoice;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.Chunk;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Draws an {@link InvoiceData} as an A4 PDF.
 *
 * <p>Three rules the layout follows:
 *
 * <ol>
 *   <li><strong>Everything is a table cell.</strong> OpenPDF shapes Arabic in
 *       the {@code ColumnText} path, which a {@link PdfPCell} with
 *       {@code RUN_DIRECTION_RTL} goes through — a {@code Paragraph} added
 *       straight to the document does not, and prints the letters isolated and
 *       in logical order (verified before choosing this library, see
 *       DECISIONS.md). So every line of text, including the labels and the
 *       address, is a cell.</li>
 *   <li><strong>RTL mirrors the columns.</strong> A cell's run direction fixes
 *       the text, not the table, so an Arabic invoice adds its row cells in
 *       reverse: the "Item" column ends up on the right.</li>
 *   <li><strong>Money is the store's money.</strong> Unit prices and line
 *       totals are the ones stored on the order, formatted by
 *       {@link InvoiceData#money}. Nothing is recomputed here.</li>
 * </ol>
 */
@Component
public class InvoicePdfRenderer {

    private static final Color TEXT = new Color(0x1B, 0x1F, 0x24);
    private static final Color MUTED = new Color(0x5F, 0x68, 0x75);
    private static final Color RULE = new Color(0xDF, 0xE3, 0xE8);
    private static final Color HEADER_BG = new Color(0xF4, 0xF5, 0xF6);

    private static final float BODY = 9.5f;
    private static final float SMALL = 8.5f;

    /**
     * Render the invoice.
     *
     * @return the PDF bytes, ready to be written to the response
     */
    public byte[] render(InvoiceData data) {
        BaseFont regular = InvoiceFonts.regular();
        BaseFont bold = InvoiceFonts.bold();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 44, 44, 42, 44);
        PdfWriter.getInstance(document, out);
        document.open();

        try {
            document.add(header(data, regular, bold));
            document.add(parties(data, regular, bold));
            document.add(items(data, regular, bold));
            document.add(totals(data, regular, bold));
            document.add(payment(data, regular, bold));
            document.add(footer(data, regular));
            document.close();
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("The invoice could not be rendered", e);
        }

        return out.toByteArray();
    }

    // ─── blocks ──────────────────────────────────────────────────────────────

    /** Store name and the word "Invoice", with the order it belongs to. */
    private PdfPTable header(InvoiceData data, BaseFont regular, BaseFont bold) {
        PdfPTable table = table(2, new float[] { 55, 45 }, data);

        PdfPCell left = bareCell();
        Phrase name = new Phrase(data.storeName() == null ? "Nocteon" : data.storeName(), font(bold, 19, TEXT));
        left.addElement(name);
        left.addElement(new Phrase(data.labels().invoice(), font(regular, BODY, MUTED)));

        PdfPCell right = bareCell();
        right.setHorizontalAlignment(align(data));
        right.addElement(new Phrase(data.labels().orderNumber(), font(regular, SMALL, MUTED)));
        right.addElement(new Phrase(keepLeftToRight(data, data.orderNumber()), font(bold, 13, TEXT)));
        right.addElement(new Phrase(
                data.labels().placedOn() + " " + data.labels().dateTime(data.placedAt()),
                font(regular, SMALL, MUTED)));

        addRow(table, data, left, right);
        table.setSpacingAfter(16);
        return table;
    }

    /** Who the invoice is for, and what the order says about itself. */
    private PdfPTable parties(InvoiceData data, BaseFont regular, BaseFont bold) {
        PdfPTable table = table(2, new float[] { 50, 50 }, data);

        PdfPCell customer = bareCell();
        customer.setPaddingRight(12);
        customer.setPaddingLeft(12);
        customer.setPaddingTop(10);
        customer.setPaddingBottom(10);
        customer.setBackgroundColor(HEADER_BG);
        customer.addElement(new Phrase(data.labels().billedTo(), font(bold, SMALL, MUTED)));
        customer.addElement(line(data.customerName(), bold, BODY));
        customer.addElement(line(keepLeftToRight(data, data.customerEmail()), regular, SMALL));
        customer.addElement(line(keepLeftToRight(data, data.customerPhone()), regular, SMALL));
        for (String addressLine : data.addressLines()) {
            customer.addElement(line(addressLine, regular, SMALL));
        }

        PdfPCell details = bareCell();
        details.setPaddingRight(12);
        details.setPaddingLeft(12);
        details.setPaddingTop(10);
        details.setPaddingBottom(10);
        details.setBackgroundColor(HEADER_BG);
        details.addElement(new Phrase(data.labels().orderDetails(), font(bold, SMALL, MUTED)));
        details.addElement(field(data.labels().orderNumber(),
                keepLeftToRight(data, data.orderNumber()), regular, bold));
        details.addElement(field(data.labels().status(), data.labels().orderStatus(data.status()), regular, bold));
        details.addElement(field(data.labels().method(), data.labels().paymentMethod(data.paymentMethod()), regular,
                bold));
        details.addElement(field(data.labels().paymentStatus(), data.labels().paymentStatus(data.paymentStatus()),
                regular, bold));

        addRow(table, data, customer, details);
        table.setSpacingAfter(16);
        return table;
    }

    /** The lines of the order: name + SKU, count, unit price, line total. */
    private PdfPTable items(InvoiceData data, BaseFont regular, BaseFont bold) {
        PdfPTable table = table(4, new float[] { 42, 9, 24, 25 }, data);
        table.setHeaderRows(1);

        table.addCell(headCell(data.labels().item(), bold, Element.ALIGN_LEFT, data));
        table.addCell(headCell(data.labels().quantity(), bold, Element.ALIGN_CENTER, data));
        table.addCell(headCell(data.labels().unitPrice(), bold, Element.ALIGN_RIGHT, data));
        table.addCell(headCell(data.labels().amount(), bold, Element.ALIGN_RIGHT, data));

        if (data.lines().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase(data.labels().noItems(), font(regular, BODY, MUTED)));
            empty.setColspan(4);
            empty.setPadding(10);
            empty.setBorder(Rectangle.BOTTOM);
            empty.setBorderColor(RULE);
            empty.setHorizontalAlignment(align(data));
            empty.setRunDirection(direction(data));
            table.addCell(empty);
            return table;
        }

        for (InvoiceData.Line line : data.lines()) {
            // The name, and the SKU under it: the identifier support uses when
            // the customer reads the invoice over the phone.
            PdfPCell name = bodyCell(data);
            name.addElement(new Phrase(nullSafe(line.name()), font(bold, BODY, TEXT)));
            if (line.sku() != null && !line.sku().isBlank()) {
                name.addElement(new Phrase(keepLeftToRight(data, line.sku()), font(regular, SMALL, MUTED)));
            }

            PdfPCell quantity = bodyCell(data);
            quantity.setHorizontalAlignment(Element.ALIGN_CENTER);
            quantity.addElement(new Phrase(integer(line.quantity()), font(regular, BODY, TEXT)));

            PdfPCell unit = bodyCell(data);
            unit.setHorizontalAlignment(Element.ALIGN_RIGHT);
            unit.addElement(new Phrase(data.money(line.unitPrice()), font(regular, BODY, TEXT)));

            PdfPCell total = bodyCell(data);
            total.setHorizontalAlignment(Element.ALIGN_RIGHT);
            total.addElement(new Phrase(data.money(line.lineTotal()), font(bold, BODY, TEXT)));

            // The quantity and unit price are numbers: they keep their place
            // even on an Arabic invoice, so a column can be read downwards.
            addRow(table, data, name, quantity, unit, total);
        }

        table.setSpacingAfter(10);
        return table;
    }

    /** Subtotal, discount, shipping and the total the store recorded. */
    private PdfPTable totals(InvoiceData data, BaseFont regular, BaseFont bold) {
        PdfPTable outer = table(2, new float[] { 55, 45 }, data);
        outer.addCell(bareCell());

        boolean rtl = data.labels().rightToLeft();
        PdfPTable inner = new PdfPTable(rtl ? new float[] { 45, 55 } : new float[] { 55, 45 });
        inner.setWidthPercentage(100);
        // The totals mirror with the rest: an Arabic invoice reads "total …
        // amount", not "amount … total".
        inner.setRunDirection(direction(data));
        inner.addCell(totalLabel(data.labels().subtotal(), regular, data));
        inner.addCell(totalValue(data.money(data.subtotal()), regular, data));
        if (data.discount() != null && data.discount().signum() != 0) {
            inner.addCell(totalLabel(data.labels().discount(), regular, data));
            inner.addCell(totalValue(data.money(data.discount()), regular, data));
        }
        inner.addCell(totalLabel(data.labels().shipping(), regular, data));
        inner.addCell(totalValue(data.money(data.shipping()), regular, data));

        // The grand total is separated by a hairline rule, not by colour:
        // black and white printing must still show the break.
        PdfPCell totalLabel = totalLabel(data.labels().total(), bold, data);
        totalLabel.setBorder(Rectangle.TOP);
        totalLabel.setBorderColor(RULE);
        PdfPCell totalValue = totalValue(data.money(data.total()), bold, data);
        totalValue.setBorder(Rectangle.TOP);
        totalValue.setBorderColor(RULE);
        inner.addCell(totalLabel);
        inner.addCell(totalValue);

        PdfPCell innerCell = new PdfPCell(inner);
        innerCell.setBorder(Rectangle.NO_BORDER);
        innerCell.setPadding(0);
        outer.addCell(innerCell);

        outer.setSpacingAfter(16);
        return outer;
    }

    /** How the order was (or was not) paid. */
    private PdfPTable payment(InvoiceData data, BaseFont regular, BaseFont bold) {
        PdfPTable table = table(2, new float[] { 50, 50 }, data);

        PdfPCell left = bareCell();
        left.addElement(new Phrase(data.labels().payment(), font(bold, SMALL, MUTED)));
        left.addElement(field(data.labels().method(), data.labels().paymentMethod(data.paymentMethod()), regular, bold));
        left.addElement(field(data.labels().paymentStatus(), data.labels().paymentStatus(data.paymentStatus()),
                regular, bold));

        PdfPCell right = bareCell();
        right.addElement(new Phrase(" ", font(regular, SMALL, MUTED)));
        right.addElement(field(data.labels().reference(),
                data.transactionReference() == null || data.transactionReference().isBlank()
                        ? data.labels().notRecorded()
                        : keepLeftToRight(data, data.transactionReference()),
                regular, bold));
        right.addElement(field(data.labels().paidOn(),
                data.paidAt() == null ? data.labels().notRecorded() : data.labels().dateTime(data.paidAt()),
                regular, bold));

        addRow(table, data, left, right);
        table.setSpacingAfter(6);
        return table;
    }

    /** When this file was produced — the one date the invoice adds itself. */
    private PdfPTable footer(InvoiceData data, BaseFont regular) {
        PdfPTable table = table(1, new float[] { 100 }, data);
        PdfPCell cell = bareCell();
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColor(RULE);
        cell.setPaddingTop(8);
        cell.setHorizontalAlignment(align(data));
        cell.addElement(new Phrase(
                data.labels().generatedOn() + " " + data.labels().dateTime(data.generatedAt()),
                font(regular, SMALL, MUTED)));
        table.addCell(cell);
        return table;
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /**
     * A table whose columns follow the LOGICAL order of the cells.
     *
     * <p>An RTL {@link PdfPTable} mirrors two things: the cells, and the widths
     * array. Mirroring only one of them is how a table ends up laying a narrow
     * column over the longest text — so the widths are reversed here for RTL,
     * and then "the first width belongs to the first cell" is true in both
     * languages.
     */
    private PdfPTable table(int columns, float[] widths, InvoiceData data) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        boolean rtl = data.labels().rightToLeft();
        float[] applied = widths.clone();
        if (rtl) {
            for (int i = 0; i < applied.length / 2; i++) {
                float swap = applied[i];
                applied[i] = applied[applied.length - 1 - i];
                applied[applied.length - 1 - i] = swap;
            }
        }
        try {
            table.setWidths(applied);
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("Invalid invoice column widths", e);
        }
        table.setRunDirection(direction(data));
        return table;
    }

    /**
     * Add one row of cells in reading (logical) order.
     *
     * <p>Deliberately NOT reversed by hand: an RTL {@link PdfPTable} already
     * mirrors its cells, so the first cell added ends up rightmost. Reversing
     * here as well cancels the table's mirroring and puts the header columns
     * over the wrong data — the trap that made the first draft print an item
     * name under "Amount".
     */
    private void addRow(PdfPTable table, InvoiceData data, PdfPCell... cells) {
        for (PdfPCell cell : cells) {
            table.addCell(cell);
        }
    }

    private PdfPCell headCell(String text, BaseFont bold, int alignment, InvoiceData data) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(bold, SMALL, MUTED)));
        cell.setBackgroundColor(HEADER_BG);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setPaddingTop(7);
        cell.setPaddingBottom(7);
        cell.setPaddingLeft(6);
        cell.setPaddingRight(6);
        cell.setHorizontalAlignment(alignment);
        cell.setRunDirection(direction(data));
        return cell;
    }

    private PdfPCell bodyCell(InvoiceData data) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setPaddingTop(7);
        cell.setPaddingBottom(7);
        cell.setPaddingLeft(6);
        cell.setPaddingRight(6);
        cell.setRunDirection(direction(data));
        cell.setHorizontalAlignment(align(data));
        return cell;
    }

    private PdfPCell bareCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private PdfPCell totalLabel(String text, BaseFont baseFont, InvoiceData data) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(baseFont, BODY, MUTED)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(3);
        cell.setPaddingBottom(3);
        cell.setHorizontalAlignment(data.labels().rightToLeft() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setRunDirection(direction(data));
        return cell;
    }

    private PdfPCell totalValue(String text, BaseFont baseFont, InvoiceData data) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(baseFont, BODY, TEXT)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(3);
        cell.setPaddingBottom(3);
        cell.setHorizontalAlignment(data.labels().rightToLeft() ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
        cell.setRunDirection(direction(data));
        return cell;
    }

    /** "Label: value" on one line, the label in bold. */
    private Phrase field(String label, String value, BaseFont regular, BaseFont bold) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label + ": ", font(bold, SMALL, MUTED)));
        phrase.add(new Chunk(nullSafe(value), font(regular, SMALL, TEXT)));
        return phrase;
    }

    private Phrase line(String value, BaseFont baseFont, float size) {
        return new Phrase(nullSafe(value), font(baseFont, size, TEXT));
    }

    private Font font(BaseFont baseFont, float size, Color color) {
        return new Font(baseFont, size, Font.NORMAL, color);
    }

    private int direction(InvoiceData data) {
        return data.labels().rightToLeft() ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR;
    }

    private int align(InvoiceData data) {
        return data.labels().rightToLeft() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
    }

    /**
     * Keep a value in left-to-right reading order inside an Arabic line.
     *
     * <p>A phone number, an e-mail, a SKU or a reference are not Arabic text: a
     * leading "+" or a trailing "-SKU" is a NEUTRAL character, and the bidi
     * algorithm in a right-to-left paragraph moves it to the other end of the
     * value — "+201000000000" would print as "20100000000+", which is a different
     * (and dialable) string. A leading LRM (U+200E) anchors the run; it has no
     * glyph, so nothing is printed for it.
     */
    private static String keepLeftToRight(InvoiceData data, String value) {
        if (value == null || value.isBlank() || !data.labels().rightToLeft()) {
            return value;
        }
        return "\u200E" + value;
    }

    /** A row the store left empty prints as nothing, never as "null". */
    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String integer(int value) {
        return String.valueOf(value);
    }
}
