package com.oratoria.backend.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.oratoria.backend.report.ReportService.PerformanceReport;
import com.oratoria.backend.report.ReportService.SessionRow;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PdfReportRenderer {
    static final ZoneId ZONE = ZoneId.of("America/La_Paz");
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZONE);
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
    static final Color BRAND = new Color(37, 52, 120);
    static final Color MUTED = new Color(95, 99, 112);
    static final Color STRIPE = new Color(241, 243, 249);
    static final Map<String, String> TYPES = Map.of("PRESENTATION", "Presentación", "INTERVIEW", "Entrevista", "DEBATE", "Debate",
            "SALES", "Ventas", "PROJECT_DEFENSE", "Defensa de proyecto", "PITCH", "Pitch", "IMPROVISATION", "Improvisación");

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND);
    private final Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BRAND);
    private final Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private final Font muted = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
    private final Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private final Font kpi = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND);
    private final Font headerCell = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private final Font cell = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

    public byte[] render(PerformanceReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, out);
        document.addTitle("Reporte de desempeño - OratoriaAI");
        document.addAuthor("OratoriaAI");
        document.open();

        document.add(new Paragraph("OratoriaAI · Reporte de desempeño", title));
        document.add(new Paragraph(report.userName() + " · " + report.email() + " · Plan " + report.plan(), body));
        String period = ReportService.periodLabel(report.periodDays())
                + (report.periodStart() == null ? "" : " (desde " + DATE.format(report.periodStart()) + ")");
        document.add(new Paragraph(period + " · Generado el " + DATE_TIME.format(report.generatedAt()), muted));

        section(document, "Resumen");
        PdfPTable kpis = new PdfPTable(4);
        kpis.setWidthPercentage(100);
        kpi(kpis, "Prácticas", String.valueOf(report.totalSessions()));
        kpi(kpis, "Evaluadas", String.valueOf(report.evaluatedSessions()));
        kpi(kpis, "Minutos de práctica", String.valueOf(report.practiceMinutes()));
        kpi(kpis, "Simulaciones", String.valueOf(report.simulations()));
        kpi(kpis, "Puntaje promedio", score(report.averageScore()));
        kpi(kpis, "Mejor puntaje", score(report.bestScore()));
        kpi(kpis, "Evolución", report.scoreChange() == null ? "—" : (report.scoreChange() > 0 ? "+" : "") + report.scoreChange());
        kpi(kpis, "", "");
        document.add(kpis);

        section(document, "Criterios evaluados");
        if (report.criteria().isEmpty()) {
            document.add(new Paragraph("Todavía no hay evaluaciones con evidencia suficiente en este periodo.", body));
        } else {
            PdfPTable table = table(new float[]{3, 2, 2}, List.of("Criterio", "Promedio", "Evaluaciones"));
            int index = 0;
            for (var criterion : report.criteria()) {
                row(table, index++, criterion.label(), criterion.average() + "/100", String.valueOf(criterion.evaluations()));
            }
            document.add(table);
            document.add(new Paragraph("Fortalezas: " + list(report.strengths()), body));
            document.add(new Paragraph("Aspectos a mejorar: " + list(report.areasToImprove()), body));
        }

        section(document, "Sesiones del periodo");
        if (report.sessions().isEmpty()) {
            document.add(new Paragraph("No hay prácticas registradas en este periodo.", body));
        } else {
            PdfPTable table = table(new float[]{2, 4, 2.4f, 1.4f, 1.4f, 1.4f, 1.4f},
                    List.of("Fecha", "Ejercicio", "Tipo", "Duración", "Puntaje", "PPM", "Muletillas"));
            int index = 0;
            for (SessionRow session : report.sessions()) {
                row(table, index++, DATE.format(session.createdAt()), session.exercise() == null ? "Práctica libre" : session.exercise(),
                        TYPES.getOrDefault(session.trainingType(), "—"),
                        session.durationSeconds() == null ? "—" : session.durationSeconds() + " s", score(session.score()),
                        session.wordsPerMinute() == null ? "—" : String.valueOf(Math.round(session.wordsPerMinute())),
                        session.fillerWords() == null ? "—" : String.valueOf(session.fillerWords()));
            }
            document.add(table);
            if (report.sessions().size() == ReportService.SESSION_ROWS) {
                document.add(new Paragraph("Se muestran las " + ReportService.SESSION_ROWS + " prácticas más recientes del periodo.", muted));
            }
        }

        section(document, "Siguiente paso recomendado");
        document.add(new Paragraph(report.nextStep(), body));
        Paragraph note = new Paragraph("Los puntajes provienen de la evaluación automática de OratoriaAI y orientan la práctica; no constituyen una calificación académica.", muted);
        note.setSpacingBefore(18);
        document.add(note);
        document.close();
        return out.toByteArray();
    }

    private void section(Document document, String text) {
        Paragraph paragraph = new Paragraph(text, heading);
        paragraph.setSpacingBefore(16);
        paragraph.setSpacingAfter(6);
        document.add(paragraph);
    }

    private void kpi(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(6);
        cell.addElement(new Paragraph(value, kpi));
        cell.addElement(new Paragraph(label, muted));
        table.addCell(cell);
    }

    private PdfPTable table(float[] widths, List<String> headers) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerCell));
            cell.setBackgroundColor(BRAND);
            cell.setPadding(5);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
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

    private static String score(Double value) { return value == null ? "—" : String.valueOf(Math.round(value)); }

    private static String list(List<String> values) { return values.isEmpty() ? "—" : String.join(", ", values); }
}
