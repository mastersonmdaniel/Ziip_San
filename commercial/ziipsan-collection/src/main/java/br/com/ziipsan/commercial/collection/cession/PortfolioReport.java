package br.com.ziipsan.commercial.collection.cession;

import br.com.ziipsan.commercial.collection.cession.ReceivablesPortfolioSource.PortfolioLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-103/E2 (decisao 62) — relatorio da carteira: aging por faixas
 * parametrizaveis + adimplencia, com aritmetica exacta ({@code BigDecimal},
 * sem arredondamento intermédio). Produtor (execucao ED-103, decisao 62):
 * sessao 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p>Por faixa e no total: quantidade de recebiveis, valor de face, valor
 * pago e saldo devido. Adimplencia da faixa/carteira = pago/face e
 * devido/face derivam destes tres totais exactos — nenhum percentual e
 * guardado nem arredondado aqui; quem apresenta decide a casa decimal.
 *
 * <p>Recebiveis integralmente pagos entram na faixa do seu vencimento
 * (contam para a adimplencia com devido zero) — a carteira relata-se
 * inteira, nao so o saldo em aberto.
 */
public record PortfolioReport(UUID tenantId, LocalDate referenceDate, AgingBands bands,
                              List<BandRow> rows, BandRow total, List<PortfolioLine> lines) {

    public PortfolioReport {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(referenceDate, "data de referencia e obrigatoria");
        Objects.requireNonNull(bands, "faixas sao obrigatorias");
        rows = List.copyOf(rows);
        lines = List.copyOf(lines);
        Objects.requireNonNull(total, "total e obrigatorio");
    }

    /** Linha agregada de uma faixa (ou o total, com rotulo TOTAL). */
    public record BandRow(String label, long count, BigDecimal face, BigDecimal paid, BigDecimal due) {}

    /** Constroi o relatorio a partir das linhas da porta, na data de referencia. */
    public static PortfolioReport build(UUID tenantId, LocalDate referenceDate, AgingBands bands,
                                        List<PortfolioLine> lines) {
        Objects.requireNonNull(lines, "linhas da carteira sao obrigatorias");
        Map<String, Acc> byBand = new LinkedHashMap<>();
        for (AgingBands.Band b : bands.bands()) byBand.put(b.label(), new Acc());
        Acc total = new Acc();
        for (PortfolioLine l : lines) {
            long overdue = ChronoUnit.DAYS.between(l.dueDate(), referenceDate);
            Acc a = byBand.get(bands.labelFor(overdue));
            a.add(l);
            total.add(l);
        }
        List<BandRow> rows = new ArrayList<>();
        for (AgingBands.Band b : bands.bands()) rows.add(byBand.get(b.label()).row(b.label()));
        return new PortfolioReport(tenantId, referenceDate, bands, rows, total.row("TOTAL"), lines);
    }

    private static final class Acc {
        long count;
        BigDecimal face = BigDecimal.ZERO, paid = BigDecimal.ZERO, due = BigDecimal.ZERO;

        void add(PortfolioLine l) {
            count++;
            face = face.add(l.faceAmount());
            paid = paid.add(l.paidAmount());
            due = due.add(l.outstanding());
        }

        BandRow row(String label) {
            return new BandRow(label, count, face, paid, due);
        }
    }
}
