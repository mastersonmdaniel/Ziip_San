package br.com.ziipsan.compliance.lgpd.privacy;

import br.com.ziipsan.core.json.Json;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Relatório do encarregado (aceite da P3.40): pedidos por tipo, situação e prazo;
 * consentimentos por finalidade; operações de tratamento; RIPD; pendências de cobertura
 * (coluna classificada sem operação — {@link ProcessingCoverage}); e alertas de acesso
 * anômalo a dado pessoal previstos no ADR-0008 e no RNF-41.
 *
 * <p>Os alertas de anomalia são ENTRADA, não cálculo: a detecção é da P0.11 sobre o
 * {@code personal_data_access_log} (e a P3.10 a liga ao incidente) — critério próprio
 * aqui duplicaria a régua do ADR-0008, que o aceite da P0.11 proíbe ("sem critério
 * próprio"). O relatório agrega e expõe; quem detecta é a P0.11.
 *
 * <p>Tudo é agregado em memória a partir das leituras da integração e serializado só
 * pela classe {@code Json} do core.
 */
public final class DpoReport {

    private DpoReport() {
    }

    /** Pedido resumido para o relatório (tipo = direito do art. 18; situação = status da V2_5). */
    public record RequestSummary(DataSubjectRight right, String status, LocalDate dueDate,
                                 boolean completed) {

        public RequestSummary {
            Objects.requireNonNull(right, "right é obrigatório");
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("status do pedido é obrigatório");
            }
            Objects.requireNonNull(dueDate, "dueDate do pedido é obrigatória");
        }
    }

    /**
     * Monta o relatório.
     *
     * @param requests               pedidos do período (todos os estados)
     * @param today                  data do relatório (vencimento é calculado contra ela)
     * @param consentsByPurpose      contagem de consentimentos ATIVOS por código de finalidade
     * @param processingRecordCodes  códigos dos registros de operações ativos
     * @param dpiaByStatus           contagem de RIPD ({@code lgpd_dpia}) por status
     * @param uncoveredColumns       pendências de cobertura ({@link ProcessingCoverage#uncovered})
     * @param anomalyAlerts          alertas de acesso anômalo detectados pela P0.11 (ADR-0008/RNF-41)
     * @param factoryRecordsLoaded   se a pré-carga de fábrica do ROPA foi carregada para o tenant
     */
    public static String buildJson(List<RequestSummary> requests, LocalDate today,
                                   Map<String, Long> consentsByPurpose,
                                   List<String> processingRecordCodes,
                                   Map<String, Long> dpiaByStatus,
                                   List<ProcessingCoverage.ClassifiedColumn> uncoveredColumns,
                                   List<String> anomalyAlerts,
                                   boolean factoryRecordsLoaded) {
        Objects.requireNonNull(requests, "requests é obrigatório");
        Objects.requireNonNull(today, "today é obrigatório");
        Objects.requireNonNull(consentsByPurpose, "consentsByPurpose é obrigatório");
        Objects.requireNonNull(processingRecordCodes, "processingRecordCodes é obrigatório");
        Objects.requireNonNull(dpiaByStatus, "dpiaByStatus é obrigatório");
        Objects.requireNonNull(uncoveredColumns, "uncoveredColumns é obrigatório");
        Objects.requireNonNull(anomalyAlerts, "anomalyAlerts é obrigatório");

        Map<String, Long> byRight = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long overdue = 0;
        for (RequestSummary r : requests) {
            byRight.merge(r.right().name(), 1L, Long::sum);
            byStatus.merge(r.status(), 1L, Long::sum);
            if (!r.completed() && today.isAfter(r.dueDate())) {
                overdue++;
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedOn", today.toString());
        report.put("requestsTotal", requests.size());
        report.put("requestsByRight", byRight);
        report.put("requestsByStatus", byStatus);
        report.put("requestsOverdue", overdue);
        report.put("consentsByPurpose", consentsByPurpose);
        report.put("processingRecords", processingRecordCodes);
        report.put("processingFactoryRecordsLoaded", factoryRecordsLoaded);
        report.put("dpiaByStatus", dpiaByStatus);
        report.put("uncoveredPersonalDataColumns", uncoveredColumns.stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("column", c.key());
                    m.put("dataClass", c.dataClass());
                    return m;
                })
                .toList());
        report.put("anomalousAccessAlerts", anomalyAlerts);
        return Json.write(report);
    }
}
