package com.oratoria.backend.admin.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.oratoria.backend.admin.report.AdminReportService.DifficultiesReport;
import com.oratoria.backend.admin.report.AdminReportService.ExercisesReport;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class AdminReportPdf {
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/La_Paz"));
    static final Map<String, String> LEVELS = Map.of("BASIC", "Básico", "INTERMEDIATE", "Intermedio", "ADVANCED", "Avanzado");
    static final Color BRAND = new Color(37, 52, 120);
    static final Color STRIPE = new Color(241, 243, 249);

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND);
    private final Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BRAND);
    private final Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private final Font muted = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(95, 99, 112));
    private final Font headerCell = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private final Font cell = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

    public byte[] exercises(ExercisesReport report) {
        return document("Ejercicios completados", report.periodDays(), report.generatedAt(), document -> {
            document.add(new Paragraph("Prácticas completadas: " + report.totalCompleted() + " · Prácticas libres (sin ejercicio): "
                    + report.freePractice() + " · Usuarios con prácticas completadas: " + report.activeUsers(), body));
            section(document, "Detalle por ejercicio");
            if (report.items().isEmpty()) {
                document.add(new Paragraph("No hay ejercicios completados en este periodo.", body));
                return;
            }
            PdfPTable table = table(new float[]{4, 2.2f, 1.6f, 1.4f, 1.3f, 1.5f, 1.5f},
                    List.of("Ejercicio", "Categoría", "Nivel", "Completados", "Usuarios", "Puntaje prom.", "Minutos"));
            int index = 0;
            for (var item : report.items()) {
                row(table, index++, item.title(), item.category(), LEVELS.getOrDefault(item.difficulty(), item.difficulty()),
                        String.valueOf(item.completed()), String.valueOf(item.users()),
                        item.averageScore() == null ? "—" : String.valueOf(Math.round(item.averageScore())), String.valueOf(item.practiceMinutes()));
            }
            document.add(table);
        });
    }

    public byte[] difficulties(DifficultiesReport report) {
        return document("Dificultades frecuentes", report.periodDays(), report.generatedAt(), document -> {
            document.add(new Paragraph("Evaluaciones con evidencia suficiente: " + report.evaluations()
                    + ". Un puntaje es bajo cuando es menor a " + Math.round(AdminReportService.LOW_SCORE) + ".", body));
            section(document, "Criterios de la rúbrica (del más débil al más fuerte)");
            if (report.criteria().isEmpty()) {
                document.add(new Paragraph("No hay evaluaciones con evidencia suficiente en este periodo.", body));
            } else {
                PdfPTable table = table(new float[]{3, 2, 2, 2, 2}, List.of("Criterio", "Promedio", "Evaluaciones", "Puntajes bajos", "% bajos"));
                int index = 0;
                for (var item : report.criteria()) {
                    row(table, index++, item.label(), item.averageScore() + "/100", String.valueOf(item.evaluations()),
                            String.valueOf(item.lowScores()), item.lowScoreRate() + " %");
                }
                document.add(table);
            }
            section(document, "Muletillas más usadas");
            if (report.frequentFillers().isEmpty()) {
                document.add(new Paragraph("No se registraron muletillas en este periodo.", body));
            } else {
                PdfPTable table = table(new float[]{4, 2}, List.of("Muletilla", "Veces"));
                int index = 0;
                for (var item : report.frequentFillers()) row(table, index++, item.filler(), String.valueOf(item.count()));
                document.add(table);
            }
        });
    }

    private byte[] document(String name, int days, Instant generatedAt, Consumer<Document> content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, out);
        document.addTitle(name + " - OratoriaAI");
        document.open();
        document.add(new Paragraph("OratoriaAI · " + name, title));
        document.add(new Paragraph((days == 0 ? "Todo el historial" : "Últimos " + days + " días")
                + " · Generado el " + DATE_TIME.format(generatedAt) + " · Reporte administrativo", muted));
        content.accept(document);
        document.close();
        return out.toByteArray();
    }

    private void section(Document document, String text) {
        Paragraph paragraph = new Paragraph(text, heading);
        paragraph.setSpacingBefore(16);
        paragraph.setSpacingAfter(6);
        document.add(paragraph);
    }

    private PdfPTable table(float[] widths, List<String> headers) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : headers) {
            PdfPCell pdfCell = new PdfPCell(new Phrase(header, headerCell));
            pdfCell.setBackgroundColor(BRAND);
            pdfCell.setPadding(5);
            pdfCell.setBorder(Rectangle.NO_BORDER);
            table.addCell(pdfCell);
        }
        return table;
    }

    private void row(PdfPTable table, int index, String... values) {
        for (String value : values) {
            PdfPCell pdfCell = new PdfPCell(new Phrase(value, cell));
            pdfCell.setPadding(4);
            pdfCell.setBorder(Rectangle.NO_BORDER);
            if (index % 2 == 1) pdfCell.setBackgroundColor(STRIPE);
            table.addCell(pdfCell);
        }
    }
}
