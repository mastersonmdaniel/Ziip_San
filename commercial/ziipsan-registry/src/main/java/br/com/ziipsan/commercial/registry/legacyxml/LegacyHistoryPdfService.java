package br.com.ziipsan.commercial.registry.legacyxml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PDF sob demanda das contas antigas (P2.19), gerado DOS DADOS importados
 * pela P2.10 — nunca do XML de legado (o XML é o documento fiscal, que fica
 * na retenção da P2.18; a conta antiga em PDF é uma representação do
 * histórico). Por isso cada PDF carrega, em destaque e no rodapé, a
 * indicação exigida pelo aceite: foi gerado a partir do histórico
 * importado.
 *
 * <p><b>Escopo honesto do leiaute:</b> isto é o ponto de integração do PDF
 * — um documento PDF válido, mínimo e determinístico (escritor próprio,
 * sem dependência nova: nenhum módulo do projeto usa biblioteca de PDF),
 * com os valores do histórico linha a linha e o total conferível à mão. O
 * leiaute oficial da conta/DANFAG (marca do tenant, estrutura marcada,
 * acessibilidade) é do portal e da segunda via da P3.3/P3.20 — não é
 * inventado aqui.
 *
 * <p>Unidade sem nenhuma conta importada é erro explícito: gerar um PDF
 * vazio fingindo histórico seria inventar dado.
 *
 * <p><b>Paginação (correção A3 da revisão):</b> o histórico da P2.10 pode
 * ter até 120 meses — o PDF é PAGINADO (cabeçalho com a indicação de
 * histórico importado repetido em toda página, rodapé com total e
 * contagem na última), de modo que nenhuma conta cai fora da página em
 * silêncio. Valor faturado nulo no histórico (correção A9) é exibido como
 * "-" e fica FORA do total, declarado em nota no rodapé — nunca vira
 * "0,00" fingindo conta de valor zero; e contas da mesma unidade com
 * matrículas divergentes são erro explícito, não "a matrícula do
 * primeiro registro".
 */
public final class LegacyHistoryPdfService {

    /** Indicação do aceite, presente em todo PDF gerado por este serviço. */
    public static final String HISTORICO_IMPORTADO_NOTICE =
            "Documento gerado a partir do historico importado";

    /**
     * Contas por página: cabeçalho (7 linhas) + 42 contas + rodapé
     * (até 7 linhas, com a nota de contas sem valor) = 56 linhas no pior
     * caso; no escritor mínimo (primeira linha em y=800, entrelinha de
     * 14 pt) a última linha fica em y=30 — dentro da página A4.
     */
    static final int CONTAS_POR_PAGINA = 42;

    private static final DateTimeFormatter DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LegacyHistoryDataPort historyData;

    public LegacyHistoryPdfService(LegacyHistoryDataPort historyData) {
        this.historyData = Objects.requireNonNull(historyData,
                "Porta de dados importados da P2.10 é obrigatória (sem ela, não há PDF de conta antiga)");
    }

    /**
     * @return bytes de um PDF válido com as contas importadas da unidade
     * @throws IllegalStateException se a unidade não tiver nenhuma conta no
     *                               histórico importado da P2.10
     */
    public byte[] gerarPdf(UUID tenantId, String unitLegacyKey) {
        Objects.requireNonNull(tenantId, "Tenant do PDF é obrigatório");
        if (unitLegacyKey == null || unitLegacyKey.isBlank()) {
            throw new IllegalArgumentException("Chave legada da unidade é obrigatória para o PDF.");
        }
        List<LegacyHistoryDataPort.ImportedBill> bills = new ArrayList<>(
                Objects.requireNonNull(historyData.billsOfUnit(tenantId, unitLegacyKey),
                        "Porta da P2.10 devolveu lista nula de contas importadas"));
        if (bills.isEmpty()) {
            throw new IllegalStateException("Unidade " + unitLegacyKey
                    + " sem contas no historico importado (P2.10): PDF nao gerado.");
        }
        bills.sort(Comparator.comparing(LegacyHistoryDataPort.ImportedBill::competence));

        // Correção A9: a matrícula impressa tem de ser a da unidade — se o
        // histórico trouxer matrículas divergentes para a mesma chave
        // legada, o dado importado está inconsistente e o PDF não sai.
        String matricula = bills.get(0).matricula();
        if (bills.stream().anyMatch(b -> !Objects.equals(b.matricula(), matricula))) {
            throw new IllegalStateException("Unidade " + unitLegacyKey
                    + " com matriculas divergentes no historico importado (P2.10):"
                    + " PDF nao gerado (conferir a importacao).");
        }

        List<String> header = List.of(
                "CONTA DE AGUA - HISTORICO IMPORTADO",
                HISTORICO_IMPORTADO_NOTICE + " (P2.10/P2.19)",
                "Origem dos documentos: " + LegacyXmlRetentionPort.ORIGIN_LEGACY_IMPORT,
                "Unidade (chave legada): " + unitLegacyKey,
                "Matricula: " + matricula,
                "",
                "Competencia  Consumo (m3)  Valor (R$)  Vencimento");

        long totalCents = 0L;
        long semValor = 0L;
        List<String> billLines = new ArrayList<>();
        for (LegacyHistoryDataPort.ImportedBill bill : bills) {
            // Correção A9: valor nulo NÃO é zero — é ausência de valor no
            // histórico; a linha mostra "-" e o valor fica fora do total.
            String valor;
            if (bill.billedAmountCents() == null) {
                semValor++;
                valor = "-";
            } else {
                totalCents += bill.billedAmountCents();
                valor = formatCents(bill.billedAmountCents());
            }
            billLines.add(bill.competence() + "  "
                    + (bill.consumptionM3() == null ? "-" : bill.consumptionM3().toPlainString()) + "  "
                    + valor + "  "
                    + (bill.dueDate() == null ? "-" : bill.dueDate().format(DATE_BR)));
        }

        List<String> footer = new ArrayList<>();
        footer.add("");
        footer.add("Total do historico: R$ " + formatCents(totalCents));
        footer.add("Contas importadas: " + bills.size());
        if (semValor > 0) {
            footer.add("Contas sem valor no historico importado: " + semValor
                    + " (exibidas como -, fora do total)");
        }
        footer.add("");
        footer.add(HISTORICO_IMPORTADO_NOTICE + " - nao e o documento fiscal original;");
        footer.add("o XML autorizado fica no armazenamento imutavel (P2.18).");

        // Correção A3: paginar — cabeçalho (com a indicação) em toda
        // página, rodapé só na última; nenhuma conta fica fora da página.
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < billLines.size(); i += CONTAS_POR_PAGINA) {
            List<String> page = new ArrayList<>(header);
            page.addAll(billLines.subList(i, Math.min(i + CONTAS_POR_PAGINA, billLines.size())));
            pages.add(page);
        }
        pages.get(pages.size() - 1).addAll(footer);
        return MinimalPdf.writePages(pages);
    }

    /** Centavos → "1.234,56" sem depender de Locale da JVM: milhares com '.', decimal com ','. */
    static String formatCents(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        String reais = Long.toString(abs / 100);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < reais.length(); i++) {
            if (i > 0 && (reais.length() - i) % 3 == 0) {
                grouped.append('.');
            }
            grouped.append(reais.charAt(i));
        }
        return sign + grouped + "," + String.format("%02d", abs % 100);
    }

    /**
     * Escritor de PDF mínimo e válido (PDF 1.4): catálogo, N páginas A4
     * (correção A3 — antes era página única e o histórico longo perdia
     * linhas fora da página), fonte Helvetica (base 14) compartilhada e
     * um fluxo de texto por página com uma linha por operação {@code Tj}.
     * Texto restrito a ASCII (acentos fora — o conteúdo é normalizado por
     * {@link #ascii(String)}), parênteses e barra invertida escapados,
     * xref calculado sobre os bytes exatos.
     *
     * <p>Numeração dos objetos: 1 = catálogo, 2 = árvore de páginas,
     * 3 = fonte; cada página i (base 0) usa o objeto {@code 4+2i} (página)
     * e {@code 5+2i} (conteúdo).
     */
    static final class MinimalPdf {

        private MinimalPdf() {
        }

        static byte[] write(List<String> lines) {
            return writePages(List.of(lines));
        }

        static byte[] writePages(List<List<String>> pages) {
            if (pages == null || pages.isEmpty()) {
                throw new IllegalArgumentException("PDF sem nenhuma página.");
            }
            List<byte[]> objects = new ArrayList<>();
            objects.add("<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.US_ASCII));
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                if (i > 0) {
                    kids.append(' ');
                }
                kids.append(4 + 2 * i).append(" 0 R");
            }
            objects.add(("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>")
                    .getBytes(StandardCharsets.US_ASCII));
            objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
                    .getBytes(StandardCharsets.US_ASCII));
            for (int i = 0; i < pages.size(); i++) {
                int contentObject = 5 + 2 * i;
                objects.add(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + contentObject + " 0 R >>")
                        .getBytes(StandardCharsets.US_ASCII));
                objects.add(streamObjectDe(pages.get(i)));
            }

            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            pdf.writeBytes("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
            long[] offsets = new long[objects.size()];
            for (int i = 0; i < objects.size(); i++) {
                offsets[i] = pdf.size();
                pdf.writeBytes(((i + 1) + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
                pdf.writeBytes(objects.get(i));
                pdf.writeBytes("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
            }
            long xrefAt = pdf.size();
            StringBuilder xref = new StringBuilder();
            xref.append("xref\n0 ").append(objects.size() + 1).append('\n');
            xref.append("0000000000 65535 f \n");
            for (long offset : offsets) {
                xref.append(String.format("%010d 00000 n \n", offset));
            }
            xref.append("trailer\n<< /Size ").append(objects.size() + 1)
                    .append(" /Root 1 0 R >>\nstartxref\n").append(xrefAt).append("\n%%EOF");
            pdf.writeBytes(xref.toString().getBytes(StandardCharsets.US_ASCII));
            return pdf.toByteArray();
        }

        private static byte[] streamObjectDe(List<String> lines) {
            StringBuilder content = new StringBuilder();
            content.append("BT /F1 11 Tf 50 800 Td 14 TL\n");
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    content.append("T* ");
                }
                content.append('(').append(escape(ascii(lines.get(i)))).append(") Tj\n");
            }
            content.append("ET");
            byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream streamObject = new ByteArrayOutputStream();
            streamObject.writeBytes(("<< /Length " + stream.length + " >>\nstream\n")
                    .getBytes(StandardCharsets.US_ASCII));
            streamObject.writeBytes(stream);
            streamObject.writeBytes("\nendstream".getBytes(StandardCharsets.US_ASCII));
            return streamObject.toByteArray();
        }

        private static String escape(String text) {
            return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }

        private static String ascii(String text) {
            StringBuilder out = new StringBuilder(text.length());
            for (char c : text.toCharArray()) {
                out.append(c < 128 ? c : '?');
            }
            return out.toString();
        }
    }
}
