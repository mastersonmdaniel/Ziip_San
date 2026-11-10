package br.com.ziipsan.integration.works;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ED-102 (C5): ponto de integracao com o ERP DE OBRAS do cliente,
 * seguindo o padrao da ED-62 ({@code ErpPayloads} + {@code
 * ErpLayoutMapping} + porta de envio): cargas canonicas ordenadas
 * campo=valor, traduzidas SOMENTE por mapeamento de leiaute
 * parametrizavel DECLARADO por tenant, e enviadas por uma porta.
 * Nenhum ERP de obras concreto, endpoint ou campo de fornecedor e
 * presumido — o fornecedor concreto por tenant e a pendencia
 * P-ED102-03.
 */
public final class WorksErpIntegration {
    private WorksErpIntegration() {}

    /** Operacoes do ponto de integracao de obras (nomes estaveis). */
    public static final String OP_AGREEMENT = "works.erp.agreement.export";
    public static final String OP_MEASUREMENT = "works.erp.measurement.export";
    public static final String OP_SCHEDULE = "works.erp.schedule.export";

    /** Campos canonicos das cargas de obras. */
    public static final List<String> AGREEMENT_FIELDS = List.of(
            "convenio_ref", "tipo_instrumento", "concedente", "valor_total",
            "valor_contrapartida", "vigencia_inicio", "vigencia_fim", "projeto_gis_ref");
    public static final List<String> MEASUREMENT_FIELDS = List.of(
            "convenio_ref", "medicao_sequencia", "periodo_inicio", "periodo_fim",
            "percentual_periodo", "percentual_acumulado", "valor_periodo", "valor_acumulado");

    /** Mapeamento de leiaute parametrizavel declarado pelo tenant. */
    public record WorksErpLayoutMapping(String providerLabel, String layoutVersion,
                                        Map<String, String> fieldByCanonical) {
        public WorksErpLayoutMapping {
            if (providerLabel == null || providerLabel.isBlank())
                throw new IllegalArgumentException("rotulo do ERP de obras declarado e obrigatorio (nenhum fornecedor presumido)");
            if (layoutVersion == null || layoutVersion.isBlank())
                throw new IllegalArgumentException("versao do leiaute declarada e obrigatoria");
            Objects.requireNonNull(fieldByCanonical, "mapa de campos declarado e obrigatorio");
            fieldByCanonical = Map.copyOf(new LinkedHashMap<>(fieldByCanonical));
        }
        public String fieldFor(String canonical) {
            String mapped = fieldByCanonical.get(canonical);
            if (mapped == null || mapped.isBlank())
                throw new IllegalArgumentException("sem mapeamento declarado para o campo canonico: " + canonical);
            return mapped;
        }
        public void requireFields(List<String> fields) { fields.forEach(this::fieldFor); }
    }

    /** Porta de envio ao ERP de obras do cliente (adaptador por tenant). */
    public interface WorksErpPort {
        /** @return referencia externa do envio (protocolo do ERP), nunca vazia. */
        String send(String operation, List<Map.Entry<String, String>> payload);
    }

    /** Constroi a carga canonica do instrumento (sem traduzir). */
    public static List<Map.Entry<String, String>> agreementPayload(FundingAgreement a) {
        Objects.requireNonNull(a, "agreement e obrigatorio");
        Map<String, String> m = new LinkedHashMap<>();
        m.put("convenio_ref", a.externalRef());
        m.put("tipo_instrumento", a.kind().name());
        m.put("concedente", a.concedenteName());
        m.put("valor_total", a.totalValue().toPlainString());
        m.put("valor_contrapartida", a.counterpartValue().toPlainString());
        m.put("vigencia_inicio", a.startDate().toString());
        m.put("vigencia_fim", a.endDate().toString());
        m.put("projeto_gis_ref", a.gisLinks().iterator().next().projectRef());
        return entries(m);
    }

    /** Constroi a carga canonica de uma medicao com os acumulados. */
    public static List<Map.Entry<String, String>> measurementPayload(
            FundingAgreement a, Measurement m, java.math.BigDecimal accPct, java.math.BigDecimal accAmt) {
        Objects.requireNonNull(a, "agreement e obrigatorio");
        Objects.requireNonNull(m, "measurement e obrigatoria");
        Map<String, String> map = new LinkedHashMap<>();
        map.put("convenio_ref", a.externalRef());
        map.put("medicao_sequencia", Integer.toString(m.sequence()));
        map.put("periodo_inicio", m.periodStart().toString());
        map.put("periodo_fim", m.periodEnd().toString());
        map.put("percentual_periodo", m.physicalPercentPeriod().toPlainString());
        map.put("percentual_acumulado", accPct.toPlainString());
        map.put("valor_periodo", m.amount().toPlainString());
        map.put("valor_acumulado", accAmt.toPlainString());
        return entries(map);
    }

    private static List<Map.Entry<String, String>> entries(Map<String, String> m) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(Map.entry(k, v)));
        return List.copyOf(out);
    }

    /** Servico de exportacao: traduz pelo mapeamento declarado e envia pela porta. */
    public static final class WorksErpExportService {
        private final WorksErpPort port;
        public WorksErpExportService(WorksErpPort port) {
            this.port = Objects.requireNonNull(port, "porta do ERP de obras e obrigatoria");
        }
        public String exportAgreement(FundingAgreement a, WorksErpLayoutMapping mapping) {
            Objects.requireNonNull(mapping, "mapeamento declarado e obrigatorio");
            mapping.requireFields(AGREEMENT_FIELDS);
            return sendChecked(OP_AGREEMENT, translate(agreementPayload(a), mapping));
        }
        public String exportMeasurement(FundingAgreement a, Measurement m,
                                        java.math.BigDecimal accPct, java.math.BigDecimal accAmt,
                                        WorksErpLayoutMapping mapping) {
            Objects.requireNonNull(mapping, "mapeamento declarado e obrigatorio");
            mapping.requireFields(MEASUREMENT_FIELDS);
            return sendChecked(OP_MEASUREMENT, translate(measurementPayload(a, m, accPct, accAmt), mapping));
        }
        private String sendChecked(String op, List<Map.Entry<String, String>> payload) {
            String ref = port.send(op, payload);
            if (ref == null || ref.isBlank())
                throw new IllegalStateException("a porta do ERP de obras nao devolveu referencia de envio");
            return ref;
        }
        private static List<Map.Entry<String, String>> translate(List<Map.Entry<String, String>> canonical,
                                                                 WorksErpLayoutMapping mapping) {
            List<Map.Entry<String, String>> out = new ArrayList<>();
            canonical.forEach(e -> out.add(Map.entry(mapping.fieldFor(e.getKey()), e.getValue())));
            return List.copyOf(out);
        }
    }
}
