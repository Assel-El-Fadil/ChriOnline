package Client.util;

import Shared.DTO.OrderDTO;
import Shared.DTO.OrderItemDTO;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PdfGenerator {

    public static void generateInvoice(File file, OrderDTO order, List<OrderItemDTO> items, String customerName) throws IOException {
        try (PdfWriter writer = new PdfWriter(file)) {
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Brand Header
            document.add(new Paragraph("ChriOnline")
                    .setFontSize(24)
                    .setBold()
                    .setFontColor(new DeviceRgb(30, 64, 175)) // blue-800
                    .setTextAlignment(TextAlignment.LEFT));

            document.add(new Paragraph("INVOICE")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMarginTop(-30));

            document.add(new Paragraph("Reference: #" + order.referenceCode)
                    .setTextAlignment(TextAlignment.RIGHT));

            document.add(new Paragraph("\n"));

            // Company & Customer Info
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
            infoTable.setWidth(UnitValue.createPercentValue(100));

            infoTable.addCell(new Cell().add(new Paragraph("FROM:").setBold())
                    .add(new Paragraph("ChriOnline Inc.\n123 Commerce St, Casablanca\ncontact@chrionline.ma"))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));

            infoTable.addCell(new Cell().add(new Paragraph("BILL TO:").setBold())
                    .add(new Paragraph(customerName + "\nDate: " + order.createdAt))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.RIGHT));

            document.add(infoTable);
            document.add(new Paragraph("\n"));

            // Items Table
            Table table = new Table(UnitValue.createPercentArray(new float[]{40, 15, 20, 25}));
            table.setWidth(UnitValue.createPercentValue(100));

            // Header Row
            table.addHeaderCell(new Cell().add(new Paragraph("Product Name").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Qty").setBold().setTextAlignment(TextAlignment.CENTER)));
            table.addHeaderCell(new Cell().add(new Paragraph("Unit Price").setBold().setTextAlignment(TextAlignment.RIGHT)));
            table.addHeaderCell(new Cell().add(new Paragraph("Subtotal").setBold().setTextAlignment(TextAlignment.RIGHT)));

            for (OrderItemDTO item : items) {
                table.addCell(new Cell().add(new Paragraph(item.productName)));
                table.addCell(new Cell().add(new Paragraph(String.valueOf(item.quantity)).setTextAlignment(TextAlignment.CENTER)));
                table.addCell(new Cell().add(new Paragraph(String.format("%.2f MAD", item.unitPrice)).setTextAlignment(TextAlignment.RIGHT)));
                table.addCell(new Cell().add(new Paragraph(String.format("%.2f MAD", item.subtotal)).setTextAlignment(TextAlignment.RIGHT)));
            }

            document.add(table);
            document.add(new Paragraph("\n"));

            // Totals Section
            Paragraph totalPara = new Paragraph("TOTAL AMOUNT: " + String.format("%.2f MAD", order.totalAmount))
                    .setFontSize(14)
                    .setBold()
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontColor(new DeviceRgb(30, 64, 175));
            document.add(totalPara);

            document.add(new Paragraph("\n\n"));
            document.add(new Paragraph("Thank you for your business!")
                    .setItalic()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(new DeviceRgb(107, 114, 128)));

            document.close();
        }
    }
}
