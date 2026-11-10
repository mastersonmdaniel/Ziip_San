package br.com.ziipsan.commercial.collection.cession;

import java.util.List;

/**
 * ED-103/E2 (decisao 62) — faixas de aging PARAMETRIZAVEIS, por dias de
 * atraso face a data de referencia (atraso = referencia - vencimento;
 * negativo = a vencer). Produtor (execucao ED-103, decisao 62): sessao
 * 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p>As faixas sao entrada declarada de quem pede o relatorio — nenhuma
 * faixa e norma do sistema. {@link #proofDefaults()} e uma CONSTRUCAO DE
 * PROVA declarada como tal (faixas usuais de relatorio de carteira), nao
 * um leiaute oficial nem uma regra juridica: quem consumir em producao
 * declara as suas.
 *
 * <p>Invariantes: ao menos uma faixa; rotulos unicos e nao vazios; faixas
 * ordenadas, contiguas e sem sobreposicao (cada inteiro de atraso cai em
 * exactamente uma faixa quando a primeira comeca em -infinito logico e a
 * ultima e aberta); limites abertos representam-se por {@code null}.
 */
public record AgingBands(List<Band> bands) {

    public AgingBands {
        if (bands == null || bands.isEmpty())
            throw new ReceivablesCessionException("AGING_BANDS_EMPTY", "aging exige ao menos uma faixa");
        bands = List.copyOf(bands);
        for (int i = 0; i < bands.size(); i++) {
            Band b = bands.get(i);
            if (b.label() == null || b.label().isBlank())
                throw new ReceivablesCessionException("AGING_BAND_LABEL_REQUIRED", "faixa de aging exige rotulo");
            if (bands.stream().filter(x -> x.label().equals(b.label())).count() > 1)
                throw new ReceivablesCessionException("AGING_BAND_LABEL_DUPLICATED", "rotulo de faixa duplicado: " + b.label());
            if (b.minDays() != null && b.maxDays() != null && b.maxDays() < b.minDays())
                throw new ReceivablesCessionException("AGING_BAND_INVERTED", "faixa invertida: " + b.label());
            if (i > 0) {
                Band prev = bands.get(i - 1);
                if (prev.maxDays() == null || b.minDays() == null || b.minDays() != prev.maxDays() + 1)
                    throw new ReceivablesCessionException("AGING_BANDS_NOT_CONTIGUOUS",
                            "faixas de aging devem ser contiguas e ordenadas: " + prev.label() + " -> " + b.label());
            }
        }
        if (bands.get(0).minDays() != null)
            throw new ReceivablesCessionException("AGING_BANDS_MUST_START_OPEN",
                    "a primeira faixa de aging deve ser aberta no inicio (minDays null)");
        if (bands.get(bands.size() - 1).maxDays() != null)
            throw new ReceivablesCessionException("AGING_BANDS_MUST_END_OPEN",
                    "a ultima faixa de aging deve ser aberta no fim (maxDays null)");
    }

    /** Faixa: rotulo + limites inclusivos de dias de atraso (null = aberto). */
    public record Band(String label, Integer minDays, Integer maxDays) {
        public boolean contains(long daysOverdue) {
            return (minDays == null || daysOverdue >= minDays) && (maxDays == null || daysOverdue <= maxDays);
        }
    }

    /** Rotulo da faixa de um atraso, pelas faixas declaradas. */
    public String labelFor(long daysOverdue) {
        for (Band b : bands)
            if (b.contains(daysOverdue)) return b.label();
        throw new IllegalStateException("nenhuma faixa cobre o atraso " + daysOverdue);
    }

    /**
     * Faixas da CONSTRUCAO DE PROVA (declaradas, nao norma): a vencer,
     * 0-30 (vencido hoje ate 30 dias), 31-60, 61-90, 91-180, 181-360 e
     * acima de 360 dias de atraso.
     */
    public static AgingBands proofDefaults() {
        return new AgingBands(List.of(
                new Band("A_VENCER", null, -1),
                new Band("0-30", 0, 30),
                new Band("31-60", 31, 60),
                new Band("61-90", 61, 90),
                new Band("91-180", 91, 180),
                new Band("181-360", 181, 360),
                new Band("ACIMA_360", 361, null)));
    }
}
