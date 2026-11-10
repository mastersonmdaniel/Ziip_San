package br.com.ziipsan.integration.works;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-102 (C4): monta os DADOS estruturados da prestacao de contas de
 * um convenio para o Transferegov — instrumento, ligacoes ao GIS,
 * medicoes com acumulados e confronto com o cronograma.
 *
 * <p>[SEM FONTE] A submissao real e o leiaute oficial do Transferegov
 * nao fazem parte desta entrega: o pacote produzido e estruturado e
 * generico (campos canonicos proprios), traduzido apenas por um
 * {@link TransferegovLayoutMapping} DECLARADO, e carrega
 * {@code officialLayout=false}. Submissao real (credenciais, convenio
 * real) e pendencia P-ED102-02; leiaute oficial, P-ED102-01.
 */
public final class AccountabilityService {

    /** Pacote de prestacao de contas: linhas ordenadas campo=valor ja traduzidas. */
    public record AccountabilityPack(
            UUID tenantId,
            UUID agreementId,
            String layoutVersion,
            boolean officialLayout,
            List<Map.Entry<String, String>> header,
            List<List<Map.Entry<String, String>>> measurementLines) {
        public AccountabilityPack {
            header = List.copyOf(header);
            measurementLines = List.copyOf(measurementLines);
        }
    }

    private final WorksAgreementService agreements;

    public AccountabilityService(WorksAgreementService agreements) {
        this.agreements = Objects.requireNonNull(agreements, "servico de convenios e obrigatorio");
    }

    public AccountabilityPack buildPack(UUID tenantId, UUID agreementId, TransferegovLayoutMapping mapping) {
        Objects.requireNonNull(mapping, "mapeamento declarado e obrigatorio ([SEM FONTE] sem ele nada e emitido)");
        mapping.requireComplete();
        FundingAgreement a = agreements.requireAgreement(tenantId, agreementId);
        List<Measurement> measurements = agreements.measurementsOf(tenantId, agreementId);
        if (measurements.isEmpty())
            throw new IllegalStateException("prestacao de contas exige ao menos uma medicao registrada");
        Schedule schedule = agreements.scheduleOf(tenantId, agreementId);

        Map<String, String> head = new LinkedHashMap<>();
        head.put("convenio_ref", a.externalRef());
        head.put("esfera", a.kind().name().startsWith("FEDERAL") ? "FEDERAL" : "ESTADUAL");
        head.put("tipo_instrumento", a.kind().name());
        head.put("concedente", a.concedenteName());
        head.put("valor_total", a.totalValue().toPlainString());
        head.put("valor_concedente", a.grantorValue().toPlainString());
        head.put("valor_contrapartida", a.counterpartValue().toPlainString());
        head.put("vigencia_inicio", a.startDate().toString());
        head.put("vigencia_fim", a.endDate().toString());
        head.put("projeto_gis_ref", a.gisLinks().iterator().next().projectRef());
        head.put("cronograma_percentual_previsto",
                schedule == null ? "0" : schedule.plannedPercentThrough(a.endDate()).toPlainString());
        head.put("cronograma_valor_previsto",
                schedule == null ? "0" : schedule.plannedAmountThrough(a.endDate()).toPlainString());

        List<List<Map.Entry<String, String>>> lines = new ArrayList<>();
        BigDecimal accPct = BigDecimal.ZERO, accAmt = BigDecimal.ZERO;
        for (Measurement m : measurements) {
            accPct = accPct.add(m.physicalPercentPeriod());
            accAmt = accAmt.add(m.amount());
            Map<String, String> line = new LinkedHashMap<>();
            line.put("convenio_ref", a.externalRef());
            line.put("medicao_sequencia", Integer.toString(m.sequence()));
            line.put("medicao_periodo_inicio", m.periodStart().toString());
            line.put("medicao_periodo_fim", m.periodEnd().toString());
            line.put("medicao_percentual_periodo", m.physicalPercentPeriod().toPlainString());
            line.put("medicao_percentual_acumulado", accPct.toPlainString());
            line.put("medicao_valor_periodo", m.amount().toPlainString());
            line.put("medicao_valor_acumulado", accAmt.toPlainString());
            lines.add(translate(line, mapping));
        }
        return new AccountabilityPack(tenantId, agreementId, mapping.layoutVersion(),
                mapping.officialLayout(), translate(head, mapping), lines);
    }

    private static List<Map.Entry<String, String>> translate(Map<String, String> canonical,
                                                             TransferegovLayoutMapping mapping) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        canonical.forEach((k, v) -> out.add(Map.entry(mapping.fieldFor(k), v)));
        return List.copyOf(out);
    }
}
