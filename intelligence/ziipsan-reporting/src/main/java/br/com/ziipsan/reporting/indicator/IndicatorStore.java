package br.com.ziipsan.reporting.indicator;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistencia do registro de indicadores (tabelas da V15_2).
 * A implementacao JDBC sobre a V15_2 e fiacao de integracao (precedente das
 * frentes P2.10/P3.22); esta entrega traz a implementacao em memoria usada
 * pelos testes do motor, com exatamente esta superficie.
 */
public interface IndicatorStore {

    void saveDefinition(IndicatorDefinition definition);

    Optional<IndicatorDefinition> findDefinition(UUID tenantId, String code);

    List<IndicatorDefinition> listDefinitions(UUID tenantId);

    void saveFormulaVersion(UUID tenantId, String indicatorCode, IndicatorFormulaVersion version);

    List<IndicatorFormulaVersion> listFormulaVersions(UUID tenantId, String indicatorCode);

    void saveTarget(IndicatorTarget target, UUID tenantId);

    List<IndicatorTarget> listTargets(UUID tenantId, String indicatorCode);

    void saveClosing(IndicatorClosing closing);

    /** Fechamento vigente (o mais recente) da chave, qualquer status. */
    Optional<IndicatorClosing> findClosing(UUID tenantId, String indicatorCode,
                                           LocalDate periodStart, String areaCode);

    void appendAudit(IndicatorAuditTrailEntry entry);

    List<IndicatorAuditTrailEntry> auditTrail(UUID tenantId, String indicatorCode);
}
