package br.com.ziipsan.core.quality;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Fechamento mensal da qualidade por sistema (SG-816 do levantamento
 * 18g): agrega, por parametro, o planejado (do plano), o coletado, o
 * analisado e os vereditos do {@link ConformityEvaluator}. So fecha
 * com responsavel tecnico identificado; fechado, e imutavel neste
 * nucleo (reabertura e ato administrativo da integracao, com trilha).
 *
 * <p>Percentuais sao calculados SO sobre o denominador nomeado:
 * conformidade sobre resultados avaliados com veredito determinado;
 * resultado INDETERMINADO conta a parte — nunca entra como conforme.
 * Cobertura sobre o planejado do plano no mes.
 */
public final class MonthlyClosing {

    public record ParameterLine(
            String parameterKey,
            int planned,
            int collected,
            int analyzed,
            int conforming,
            int nonConforming,
            int indeterminate) {

        /** % de conformidade sobre os vereditos determinados; null quando nao ha veredito determinado. */
        public Double conformityPercent() {
            int determined = conforming + nonConforming;
            if (determined == 0) return null;
            return 100.0 * conforming / determined;
        }

        /** % de cobertura do plano (coletado/planejado); null quando a linha nao esta no plano. */
        public Double coveragePercent() {
            if (planned == 0) return null;
            return 100.0 * collected / planned;
        }
    }

    public record Entry(Sample sample, AnalysisResult result, ConformityEvaluator.Assessment assessment) {
        public Entry {
            if (sample == null || result == null || assessment == null)
                throw new IllegalArgumentException("entrada do fechamento exige amostra, resultado e avaliacao");
            if (!result.sampleId().equals(sample.id()))
                throw new IllegalArgumentException("resultado nao pertence a amostra da entrada");
            if (!assessment.parameterKey().equals(result.parameterKey()))
                throw new IllegalArgumentException("avaliacao nao corresponde ao parametro do resultado");
            if (!result.validated())
                throw new IllegalArgumentException("fechamento so recebe resultado validado ("
                        + result.parameterKey() + ")");
        }
    }

    private final UUID id;
    private final String supplySystem;
    private final YearMonth month;
    private final List<ParameterLine> lines;
    private boolean closed;
    private String closedByRef;
    private Instant closedAt;

    private MonthlyClosing(UUID id, String supplySystem, YearMonth month, List<ParameterLine> lines) {
        this.id = id;
        this.supplySystem = supplySystem;
        this.month = month;
        this.lines = lines;
    }

    /**
     * Monta o fechamento (ainda aberto) a partir das entradas do mes e
     * do plano. Amostra coletada sem resultado validado conta em
     * {@code collected} pela lista de amostras do mes entregue pelo
     * chamador — o nucleo nao presume coleta nao apresentada.
     *
     * <p><b>Correcao M-2 (revisao da ED-50):</b> antes, {@code collected}
     * por parametro so contava amostras que ja tinham ENTRADA (resultado
     * validado) — a sonda D1 (4 planejados, 1 amostra coletada+recebida
     * sem entrada) saia {@code collected=0}, contra o planejado x
     * coletado x analisado do aceite. Agora: (1) amostra com entrada
     * conta no parametro da entrada; (2) amostra coletada SEM nenhuma
     * entrada (sem resultado, ou com resultado ainda nao validado) conta
     * nos parametros que o plano preve para o PONTO dela — a coleta no
     * ponto serve as linhas do plano daquele ponto; (3) amostra apenas
     * PLANNED ou CANCELLED nao conta. {@code coveragePercent} herda a
     * contagem corrigida.
     */
    public static MonthlyClosing assemble(UUID id, String supplySystem, YearMonth month,
                                          SamplingPlan plan, List<Sample> samplesOfMonth,
                                          List<Entry> entries) {
        if (id == null) throw new IllegalArgumentException("id do fechamento e obrigatorio");
        if (supplySystem == null || supplySystem.isBlank())
            throw new IllegalArgumentException("supplySystem do fechamento e obrigatorio");
        if (month == null) throw new IllegalArgumentException("mes do fechamento e obrigatorio");
        if (plan == null) throw new IllegalArgumentException("plano de amostragem e obrigatorio");
        if (!plan.supplySystem().equals(supplySystem))
            throw new IllegalArgumentException("plano e de outro sistema: " + plan.supplySystem());
        if (!plan.covers(month))
            throw new IllegalArgumentException("plano nao cobre o mes " + month);
        if (samplesOfMonth == null || entries == null)
            throw new IllegalArgumentException("amostras e entradas do mes sao obrigatorias (podem ser vazias)");

        java.util.Set<String> keys = new java.util.TreeSet<>();
        plan.lines().forEach(l -> keys.add(l.parameterKey()));
        entries.forEach(e -> keys.add(e.result().parameterKey()));

        // Amostras apresentadas fora do estado coletado em diante nao contam como coletadas.
        java.util.Set<UUID> collectedIds = samplesOfMonth.stream()
                .filter(s -> s.status() != Sample.Status.PLANNED && s.status() != Sample.Status.CANCELLED)
                .map(Sample::id)
                .collect(java.util.stream.Collectors.toSet());
        // M-2: amostras coletadas SEM nenhuma entrada (sem resultado
        // validado) — atribuidas pelos parametros do plano no ponto delas.
        java.util.Set<UUID> entrySampleIds = entries.stream()
                .map(e -> e.sample().id())
                .collect(java.util.stream.Collectors.toSet());
        List<Sample> collectedWithoutEntry = samplesOfMonth.stream()
                .filter(s -> collectedIds.contains(s.id()) && !entrySampleIds.contains(s.id()))
                .collect(java.util.stream.Collectors.toMap(Sample::id, s -> s, (a, b) -> a))
                .values().stream().toList();
        List<ParameterLine> lines = new java.util.ArrayList<>();
        for (String key : keys) {
            int planned = plan.lines().stream()
                    .filter(l -> l.parameterKey().equals(key))
                    .mapToInt(SamplingPlan.PlanLine::plannedCount)
                    .sum();
            List<Entry> ofKey = entries.stream().filter(e -> e.result().parameterKey().equals(key)).toList();
            int analyzed = ofKey.size();
            // Coletado do parametro = (a) amostras distintas das entradas
            // coletadas no mes + (b) amostras coletadas sem entrada cujo
            // ponto tem este parametro no plano (correcao M-2).
            int collectedFromEntries = (int) ofKey.stream().map(e -> e.sample().id()).distinct()
                    .filter(collectedIds::contains).count();
            int collectedWithoutResult = (int) collectedWithoutEntry.stream()
                    .filter(s -> plan.plannedFor(s.pointId(), key) > 0)
                    .count();
            int collected = collectedFromEntries + collectedWithoutResult;
            int conf = (int) ofKey.stream()
                    .filter(e -> e.assessment().verdict() == ConformityEvaluator.Verdict.CONFORMING).count();
            int nonConf = (int) ofKey.stream()
                    .filter(e -> e.assessment().verdict() == ConformityEvaluator.Verdict.NON_CONFORMING).count();
            int indet = analyzed - conf - nonConf;
            lines.add(new ParameterLine(key, planned, collected, analyzed, conf, nonConf, indet));
        }
        return new MonthlyClosing(id, supplySystem, month, List.copyOf(lines));
    }

    public void close(String technicalResponsibleRef, Instant at) {
        if (closed) throw new IllegalStateException("fechamento de " + month + " ja encerrado");
        if (technicalResponsibleRef == null || technicalResponsibleRef.isBlank())
            throw new IllegalArgumentException("fechamento mensal exige o responsavel tecnico");
        if (at == null) throw new IllegalArgumentException("instante do fechamento e obrigatorio");
        this.closed = true;
        this.closedByRef = technicalResponsibleRef;
        this.closedAt = at;
    }

    public UUID id() { return id; }
    public String supplySystem() { return supplySystem; }
    public YearMonth month() { return month; }
    public List<ParameterLine> lines() { return lines; }
    public boolean isClosed() { return closed; }
    public String closedByRef() { return closedByRef; }
    public Instant closedAt() { return closedAt; }
}
