package br.com.ziipsan.core.effluent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Automonitoramento do efluente da ETE sobre o plano, o laboratorio
 * e a cadeia de custodia da ED-50:
 * <ul>
 *   <li>uma analise so e registrada sobre amostra cuja custodia a
 *       ED-50 prova RECEBIDA pelo laboratorio — sem recebimento,
 *       recusa nomeada (o portao JOR-37 da ED-50 aplicado aqui);</li>
 *   <li>o veredito e o do {@link EffluentConformity} contra o
 *       catalogo de padrao DESTA tarefa (o mesmo que a ED-83 usa);</li>
 *   <li>a cobertura do plano compara planejado x coletado por linha,
 *       sem percentual presumido: linha sem coleta conta como zero
 *       coletado, declarado.</li>
 * </ul>
 */
public final class EffluentMonitoring {

    /** Amostra de efluente coletada num ponto do plano, identificada pela amostra da ED-50. */
    public record CollectedSample(UUID sampleId, String samplingPointCode, String parameterKey) {
        public CollectedSample {
            if (sampleId == null) throw new IllegalArgumentException("sampleId (ED-50) e obrigatorio");
            if (samplingPointCode == null || samplingPointCode.isBlank())
                throw new IllegalArgumentException("samplingPointCode e obrigatorio");
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parameterKey e obrigatorio");
        }
    }

    /** Cobertura de uma linha do plano: planejado, coletado e o saldo nomeado. */
    public record Coverage(EffluentMonitoringPlan.PlanLine line, int collected) {
        public int missing() { return Math.max(0, line.plannedCount() - collected); }
        public boolean fulfilled() { return collected >= line.plannedCount(); }
    }

    private final EffluentPorts.EffluentLabPort lab;

    public EffluentMonitoring(EffluentPorts.EffluentLabPort lab) {
        if (lab == null) throw new IllegalArgumentException(
                "porta do laboratorio/custodia da ED-50 e obrigatoria");
        this.lab = lab;
    }

    /**
     * Registra a analise de uma amostra coletada e devolve o veredito
     * contra o catalogo. Recusa se a ED-50 nao provar o recebimento
     * da amostra pelo laboratorio.
     */
    public EffluentConformity.Assessment registerAnalysis(
            UUID tenantId, CollectedSample sample, BigDecimal numericValue, String qualitativeValue,
            EffluentCatalog catalog, EffluentStandard.Jurisdiction jurisdiction,
            String stateCode, LocalDate referenceDate) {
        if (tenantId == null || sample == null)
            throw new IllegalArgumentException("tenant e amostra coletada sao obrigatorios");
        EffluentPorts.LabReceipt receipt = lab.receiptOf(tenantId, sample.sampleId());
        if (receipt == null || !receipt.sampleId().equals(sample.sampleId()) || !receipt.receivedByLab())
            throw new IllegalStateException("amostra " + sample.sampleId()
                    + " sem recebimento registrado pelo laboratorio na cadeia de custodia"
                    + " da ED-50: analise de automonitoramento nao se registra sem custodia");
        return EffluentConformity.evaluate(sample.parameterKey(), numericValue, qualitativeValue,
                catalog, jurisdiction, stateCode, referenceDate);
    }

    /** Cobertura do plano pelas amostras coletadas (contagem por linha, sem presumir coleta). */
    public static List<Coverage> coverage(EffluentMonitoringPlan plan, List<CollectedSample> collected) {
        if (plan == null) throw new IllegalArgumentException("plano e obrigatorio");
        List<CollectedSample> safe = collected == null ? List.of() : collected;
        return plan.lines().stream()
                .map(line -> new Coverage(line, (int) safe.stream()
                        .filter(s -> s.samplingPointCode().equals(line.samplingPointCode())
                                && s.parameterKey().equals(line.parameterKey()))
                        .count()))
                .toList();
    }
}
