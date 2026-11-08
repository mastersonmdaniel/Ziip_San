package br.com.ziipsan.commercial.collection.debit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Relatorios de debito em conta (ED-27): visao unica dos dois canais -
 * Pix Automatico (instrucoes da P4.5) e legado FEBRABAN 150 (desfechos da
 * P3.24 pela porta) - por periodo de arrecadacao, com o progresso da
 * campanha de conversao e o desligamento que se aproxima.
 *
 * <p>As entradas sao retratos (snapshots) que a fiacao monta das instrucoes
 * da P4.5 e dos retornos legados; nenhum dado e buscado aqui dentro e
 * nenhum valor e inventado - periodo sem lancamentos devolve relatorio
 * zerado, nao erro.
 */
public final class DebitAccountReport {

    public enum Channel { PIX_AUTOMATICO, FEBRABAN_150_LEGADO }

    public enum Outcome { LIQUIDADO, FALHOU, CANCELADO, AGENDADO }

    /** Lancamento de debito em conta (retrato de um ciclo). */
    public record DebitEntry(Channel channel, Outcome outcome, BigDecimal amount,
                             LocalDate settlementDate, LocalDate collectionMonth) {
        public DebitEntry {
            Objects.requireNonNull(channel, "canal e obrigatorio");
            Objects.requireNonNull(outcome, "desfecho e obrigatorio");
            Objects.requireNonNull(amount, "valor e obrigatorio");
            Objects.requireNonNull(settlementDate, "data de liquidacao e obrigatoria");
            Objects.requireNonNull(collectionMonth, "mes de arrecadacao e obrigatorio");
            if (collectionMonth.getDayOfMonth() != 1) {
                throw new IllegalArgumentException(
                        "collection_month e sempre dia 1 (referencia de arrecadacao, DAD-19): " + collectionMonth);
            }
        }
    }

    /** Totais de um canal num recorte. */
    public record ChannelTotals(int count, BigDecimal settledTotal, int settledCount,
                                int failedCount, int cancelledCount, int scheduledCount) {
    }

    /** Relatorio do periodo. */
    public record Report(LocalDate from, LocalDate to,
                         Map<Channel, ChannelTotals> byChannel,
                         int legacyAuthorizations, int convertedToPix, int pendingConversion,
                         LocalDate nextShutdown) {
        /** Taxa de conversao da base legada (0 quando nao ha base legada). */
        public BigDecimal conversionRate() {
            if (legacyAuthorizations == 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(convertedToPix)
                    .divide(BigDecimal.valueOf(legacyAuthorizations), 4, java.math.RoundingMode.HALF_UP);
        }
    }

    private DebitAccountReport() {
    }

    /**
     * Monta o relatorio do periodo [from, to] (inclusive) sobre os retratos
     * dados, com o progresso de conversao e o proximo desligamento do
     * enquadramento informado.
     */
    public static Report build(LocalDate from, LocalDate to, List<DebitEntry> entries,
                               int legacyAuthorizations, int convertedToPix,
                               DebitRegulatoryCalendar.AgreementKind kind) {
        Objects.requireNonNull(from, "inicio do periodo e obrigatorio");
        Objects.requireNonNull(to, "fim do periodo e obrigatorio");
        Objects.requireNonNull(entries, "lancamentos sao obrigatorios");
        Objects.requireNonNull(kind, "enquadramento do convenio e obrigatorio");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("periodo invalido: fim antes do inicio");
        }
        if (legacyAuthorizations < 0 || convertedToPix < 0 || convertedToPix > legacyAuthorizations) {
            throw new IllegalArgumentException("progresso de conversao invalido: convertidos "
                    + convertedToPix + " de " + legacyAuthorizations);
        }
        Map<Channel, ChannelTotals> byChannel = new EnumMap<>(Channel.class);
        for (Channel channel : Channel.values()) {
            int count = 0;
            int settled = 0;
            int failed = 0;
            int cancelled = 0;
            int scheduled = 0;
            BigDecimal settledTotal = BigDecimal.ZERO;
            for (DebitEntry e : entries) {
                if (e.channel() != channel || e.settlementDate().isBefore(from)
                        || e.settlementDate().isAfter(to)) {
                    continue;
                }
                count++;
                switch (e.outcome()) {
                    case LIQUIDADO -> {
                        settled++;
                        settledTotal = settledTotal.add(e.amount());
                    }
                    case FALHOU -> failed++;
                    case CANCELADO -> cancelled++;
                    case AGENDADO -> scheduled++;
                }
            }
            byChannel.put(channel, new ChannelTotals(count, settledTotal, settled, failed, cancelled, scheduled));
        }
        return new Report(from, to, Map.copyOf(byChannel), legacyAuthorizations, convertedToPix,
                legacyAuthorizations - convertedToPix,
                DebitRegulatoryCalendar.legacyShutdownDate(kind));
    }
}
