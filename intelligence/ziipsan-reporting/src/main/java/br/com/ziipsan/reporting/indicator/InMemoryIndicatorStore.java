package br.com.ziipsan.reporting.indicator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Implementacao em memoria da porta, para os testes do motor (padrao das frentes anteriores). */
public final class InMemoryIndicatorStore implements IndicatorStore {

    private final Map<String, IndicatorDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, List<IndicatorFormulaVersion>> formulas = new LinkedHashMap<>();
    private final Map<String, List<IndicatorTarget>> targets = new LinkedHashMap<>();
    private final List<IndicatorClosing> closings = new ArrayList<>();
    private final List<IndicatorAuditTrailEntry> audit = new ArrayList<>();

    private static String key(UUID tenantId, String code) {
        return tenantId + "|" + code;
    }

    private static String closingKey(UUID tenantId, String code, LocalDate periodStart, String area) {
        return key(tenantId, code) + "|" + periodStart + "|" + (area == null ? "" : area);
    }

    @Override
    public void saveDefinition(IndicatorDefinition definition) {
        definitions.put(key(definition.tenantId(), definition.code()), definition);
    }

    @Override
    public Optional<IndicatorDefinition> findDefinition(UUID tenantId, String code) {
        return Optional.ofNullable(definitions.get(key(tenantId, code)));
    }

    @Override
    public List<IndicatorDefinition> listDefinitions(UUID tenantId) {
        return definitions.values().stream().filter(d -> d.tenantId().equals(tenantId)).toList();
    }

    @Override
    public void saveFormulaVersion(UUID tenantId, String indicatorCode, IndicatorFormulaVersion version) {
        formulas.computeIfAbsent(key(tenantId, indicatorCode), k -> new ArrayList<>()).add(version);
    }

    @Override
    public List<IndicatorFormulaVersion> listFormulaVersions(UUID tenantId, String indicatorCode) {
        return List.copyOf(formulas.getOrDefault(key(tenantId, indicatorCode), List.of()));
    }

    @Override
    public void saveTarget(IndicatorTarget target, UUID tenantId) {
        targets.computeIfAbsent(key(tenantId, target.indicatorCode()), k -> new ArrayList<>())
                .add(target);
    }

    @Override
    public List<IndicatorTarget> listTargets(UUID tenantId, String indicatorCode) {
        return List.copyOf(targets.getOrDefault(key(tenantId, indicatorCode), List.of()));
    }

    @Override
    public void saveClosing(IndicatorClosing closing) {
        // Historico preservado: reabertura e novo fechamento sao registros
        // novos na lista; findClosing devolve o mais recente da chave.
        closings.add(closing);
    }

    @Override
    public Optional<IndicatorClosing> findClosing(UUID tenantId, String indicatorCode,
                                                  LocalDate periodStart, String areaCode) {
        IndicatorClosing latest = null;
        for (IndicatorClosing closing : closings) {
            if (closingKey(closing.tenantId(), closing.indicatorCode(), closing.periodStart(),
                    closing.areaCode())
                    .equals(closingKey(tenantId, indicatorCode, periodStart, areaCode))) {
                latest = closing;
            }
        }
        return Optional.ofNullable(latest);
    }

    @Override
    public void appendAudit(IndicatorAuditTrailEntry entry) {
        audit.add(entry);
    }

    @Override
    public List<IndicatorAuditTrailEntry> auditTrail(UUID tenantId, String indicatorCode) {
        return audit.stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.indicatorCode().equals(indicatorCode))
                .toList();
    }
}
