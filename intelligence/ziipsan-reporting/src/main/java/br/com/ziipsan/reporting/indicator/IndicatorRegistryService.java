package br.com.ziipsan.reporting.indicator;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.ParameterVersionRule;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Registro unico de indicadores e metas por tenant (P3.41, JOR-19).
 *
 * <p>Escritas exigem {@link IndicatorAccess#EDITOR}; leitores (objetivos da
 * P3.31, pagina inicial da P3.39, catalogo SINISA/ED-48, fichas da ED-77,
 * paineis da ED-49, sala de situacao da ED-93) usam os metodos de leitura,
 * que nao pedem papel porque nao gravam nada — e qualquer tentativa de
 * escrita com papel de leitor e recusada nomeada, sem efeito.
 *
 * <p>Invariantes do aceite, todas com teste:
 * <ul>
 *   <li>variavel sem fonte declarada: recusada (construcao de
 *       {@link IndicatorVariable});</li>
 *   <li>mudanca de formula = nova versao com vigencia (motor da P0.19),
 *       sem reescrever versoes anteriores e sem vigencias sobrepostas;</li>
 *   <li>meta por periodo e por area, com vigencia/sobreposicao pela regra
 *       da P0.19 e semaforo por faixa declarada na meta;</li>
 *   <li>fechamento congela valor + versao da formula + valores das
 *       variaveis: {@link #compute} sobre periodo fechado devolve o valor
 *       congelado, sem recalcular;</li>
 *   <li>reabertura so com motivo, registrada no fechamento e na trilha;</li>
 *   <li>calculo com variavel sem valor, valor nao declarado ou formula sem
 *       versao vigente no periodo: recusa nomeada — nunca zero presumido,
 *       nunca valor silenciosamente descartado.</li>
 * </ul>
 */
public final class IndicatorRegistryService {

    private final IndicatorStore store;
    private final Clock clock;

    public IndicatorRegistryService(IndicatorStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Resultado de uma apuracao: {@code frozen == true} quando veio de um fechamento. */
    public record Computation(BigDecimal value, int formulaVersion, Semaphore semaphore,
                              boolean frozen, UUID closingId) {
    }

    // ------------------------------------------------------------------
    // Escritas (EDITOR)
    // ------------------------------------------------------------------

    public IndicatorDefinition define(IndicatorAccess access, IndicatorDefinition definition,
                                      String initialFormulaExpression,
                                      ParameterVersionRule.Validity formulaValidity, UUID actor) {
        requireEditor(access, "define", definition.code());
        if (store.findDefinition(definition.tenantId(), definition.code()).isPresent()) {
            throw new IndicatorValidationException("INDICATOR_ALREADY_DEFINED",
                    "indicador " + definition.code() + " ja definido neste tenant; "
                            + "mudanca de formula e nova versao, nao nova definicao");
        }
        // Valida a formula contra as variaveis declaradas ANTES de gravar:
        // variavel usada e nao declarada, ou declarada e nao usada, e recusa
        // nomeada — a formula nao pode operar sobre fonte nao declarada.
        validateFormulaAgainstVariables(initialFormulaExpression, definition.variables());
        store.saveDefinition(definition);
        store.saveFormulaVersion(definition.tenantId(), definition.code(),
                new IndicatorFormulaVersion(1, initialFormulaExpression, formulaValidity));
        audit(definition.tenantId(), definition.code(), "DEFINE",
                "definicao criada com formula v1", null, actor);
        return definition;
    }

    public IndicatorFormulaVersion changeFormula(IndicatorAccess access, UUID tenantId, String code,
                                                 String newExpression,
                                                 ParameterVersionRule.Validity validity, UUID actor) {
        requireEditor(access, "changeFormula", code);
        IndicatorDefinition definition = definitionOrThrow(tenantId, code);
        validateFormulaAgainstVariables(newExpression, definition.variables());
        List<IndicatorFormulaVersion> existing = store.listFormulaVersions(tenantId, code);
        ParameterVersionRule.validateNoOverlap(validity,
                existing.stream().map(IndicatorFormulaVersion::validity).toList());
        int next = existing.stream().mapToInt(IndicatorFormulaVersion::version).max().orElse(0) + 1;
        IndicatorFormulaVersion version = new IndicatorFormulaVersion(next, newExpression, validity);
        store.saveFormulaVersion(tenantId, code, version);
        audit(tenantId, code, "FORMULA_VERSION", "nova versao de formula v" + next, null, actor);
        return version;
    }

    public IndicatorTarget setTarget(IndicatorAccess access, UUID tenantId, IndicatorTarget target,
                                     UUID actor) {
        requireEditor(access, "setTarget", target.indicatorCode());
        definitionOrThrow(tenantId, target.indicatorCode());
        List<ParameterVersionRule.Validity> sameArea = store.listTargets(tenantId, target.indicatorCode())
                .stream()
                .filter(t -> java.util.Objects.equals(t.areaCode(), target.areaCode()))
                .map(IndicatorTarget::validity)
                .toList();
        ParameterVersionRule.validateNoOverlap(target.validity(), sameArea);
        store.saveTarget(target, tenantId);
        audit(tenantId, target.indicatorCode(), "TARGET",
                "meta " + target.targetValue().toPlainString() + " area="
                        + (target.areaCode() == null ? "(geral)" : target.areaCode()),
                null, actor);
        return target;
    }

    public IndicatorClosing close(IndicatorAccess access, UUID tenantId, String code,
                                  LocalDate periodStart, LocalDate periodEnd, String areaCode,
                                  Map<String, BigDecimal> variableValues, UUID actor) {
        requireEditor(access, "close", code);
        // Periodo do fechamento validado como na V15_2 (NOT NULL +
        // period_end >= period_start), antes de qualquer apuracao —
        // correcao D3 da revisao independente.
        if (periodStart == null || periodEnd == null) {
            throw new IndicatorValidationException("CLOSING_PERIOD_REQUIRED",
                    "periodo do fechamento (inicio e fim) e obrigatorio para " + code);
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IndicatorValidationException("CLOSING_PERIOD_INVALID",
                    "fim do periodo " + periodEnd + " anterior ao inicio " + periodStart
                            + " no fechamento de " + code
                            + " (CHECK period_end >= period_start da V15_2)");
        }
        Optional<IndicatorClosing> current = store.findClosing(tenantId, code, periodStart, areaCode);
        if (current.isPresent() && current.get().status() == IndicatorClosing.Status.CLOSED) {
            throw new IndicatorValidationException("PERIOD_ALREADY_CLOSED",
                    "periodo " + periodStart + " do indicador " + code + " ja fechado; "
                            + "reabra com motivo antes de fechar de novo");
        }
        Computation computation = computeFresh(tenantId, code, periodStart, periodEnd, areaCode,
                variableValues);
        IndicatorClosing closing = new IndicatorClosing(UUID.randomUUID(), tenantId, code,
                periodStart, periodEnd, areaCode, computation.value(), computation.formulaVersion(),
                snapshotJson(variableValues), computation.semaphore(),
                IndicatorClosing.Status.CLOSED, actor, Instant.now(clock), null, null, null);
        store.saveClosing(closing);
        audit(tenantId, code, "CLOSE",
                "fechamento " + periodStart + " valor=" + computation.value().toPlainString()
                        + " formula v" + computation.formulaVersion(),
                null, actor);
        return closing;
    }

    public IndicatorClosing reopen(IndicatorAccess access, UUID tenantId, String code,
                                   LocalDate periodStart, String areaCode, String reason, UUID actor) {
        requireEditor(access, "reopen", code);
        if (reason == null || reason.isBlank()) {
            throw new IndicatorValidationException("REOPEN_REASON_REQUIRED",
                    "reabertura de periodo fechado exige motivo registrado (indicador " + code + ")");
        }
        IndicatorClosing current = store.findClosing(tenantId, code, periodStart, areaCode)
                .orElseThrow(() -> new IndicatorValidationException("CLOSING_NOT_FOUND",
                        "nenhum fechamento de " + code + " em " + periodStart));
        if (current.status() != IndicatorClosing.Status.CLOSED) {
            throw new IndicatorValidationException("PERIOD_NOT_CLOSED",
                    "periodo " + periodStart + " do indicador " + code + " nao esta fechado");
        }
        IndicatorClosing reopened = current.reopened(actor, Instant.now(clock), reason);
        store.saveClosing(reopened);
        audit(tenantId, code, "REOPEN", "reabertura de " + periodStart, reason, actor);
        return reopened;
    }

    // ------------------------------------------------------------------
    // Apuracao
    // ------------------------------------------------------------------

    /**
     * Apura o periodo. Se houver fechamento CLOSED para a chave, devolve o
     * valor CONGELADO (com {@code frozen == true}), ignorando os valores
     * passados — recalculo nao altera periodo fechado (teste do aceite).
     */
    public Computation compute(UUID tenantId, String code, LocalDate periodStart, LocalDate periodEnd,
                               String areaCode, Map<String, BigDecimal> variableValues) {
        Optional<IndicatorClosing> closing = store.findClosing(tenantId, code, periodStart, areaCode);
        if (closing.isPresent() && closing.get().status() == IndicatorClosing.Status.CLOSED) {
            IndicatorClosing frozen = closing.get();
            return new Computation(frozen.value(), frozen.formulaVersion(), frozen.semaphore(),
                    true, frozen.id());
        }
        return computeFresh(tenantId, code, periodStart, periodEnd, areaCode, variableValues);
    }

    private Computation computeFresh(UUID tenantId, String code, LocalDate periodStart,
                                     LocalDate periodEnd, String areaCode,
                                     Map<String, BigDecimal> variableValues) {
        IndicatorDefinition definition = definitionOrThrow(tenantId, code);
        Map<String, BigDecimal> values = variableValues == null ? Map.of() : variableValues;
        Set<String> declared = definition.variables().stream()
                .map(IndicatorVariable::name).collect(java.util.stream.Collectors.toSet());
        for (String provided : values.keySet()) {
            if (!declared.contains(provided)) {
                throw new IndicatorValidationException("UNDECLARED_VARIABLE_VALUE",
                        "valor informado para variavel nao declarada " + provided
                                + " no indicador " + code + " (nada e descartado em silencio)");
            }
            if (values.get(provided) == null) {
                throw new IndicatorValidationException("MISSING_VARIABLE_VALUE",
                        "variavel " + provided + " com valor nulo no indicador " + code);
            }
        }
        IndicatorFormulaVersion formula = store.listFormulaVersions(tenantId, code).stream()
                .filter(v -> v.validity().covers(periodStart))
                .findFirst()
                .orElseThrow(() -> new IndicatorValidationException("FORMULA_NOT_EFFECTIVE",
                        "nenhuma versao de formula vigente em " + periodStart
                                + " para o indicador " + code));
        BigDecimal value = FormulaEvaluator.evaluate(formula.expression(), values);
        Semaphore semaphore = Semaphore.evaluate(value,
                targetFor(tenantId, code, periodStart, areaCode));
        return new Computation(value, formula.version(), semaphore, false, null);
    }

    // ------------------------------------------------------------------
    // Leituras (qualquer papel, inclusive READER)
    // ------------------------------------------------------------------

    public IndicatorDefinition definition(UUID tenantId, String code) {
        return definitionOrThrow(tenantId, code);
    }

    public List<IndicatorFormulaVersion> formulaVersions(UUID tenantId, String code) {
        definitionOrThrow(tenantId, code);
        return store.listFormulaVersions(tenantId, code);
    }

    public List<IndicatorTarget> targets(UUID tenantId, String code) {
        definitionOrThrow(tenantId, code);
        return store.listTargets(tenantId, code);
    }

    public Optional<IndicatorClosing> closing(UUID tenantId, String code, LocalDate periodStart,
                                              String areaCode) {
        return store.findClosing(tenantId, code, periodStart, areaCode);
    }

    /** Marcacao por perfil: a leitura da pagina inicial da P3.39. */
    public List<IndicatorDefinition> listForProfile(UUID tenantId, String profileTag) {
        return store.listDefinitions(tenantId).stream()
                .filter(d -> d.profileTags().contains(profileTag))
                .toList();
    }

    public List<IndicatorAuditTrailEntry> auditTrail(UUID tenantId, String code) {
        return store.auditTrail(tenantId, code);
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private IndicatorTarget targetFor(UUID tenantId, String code, LocalDate periodStart,
                                      String areaCode) {
        IndicatorTarget general = null;
        for (IndicatorTarget target : store.listTargets(tenantId, code)) {
            if (!target.covers(periodStart)) {
                continue;
            }
            if (areaCode != null && areaCode.equals(target.areaCode())) {
                return target;
            }
            if (target.areaCode() == null) {
                general = target;
            }
        }
        return general;
    }

    private IndicatorDefinition definitionOrThrow(UUID tenantId, String code) {
        return store.findDefinition(tenantId, code)
                .orElseThrow(() -> new IndicatorValidationException("INDICATOR_NOT_FOUND",
                        "indicador " + code + " nao definido neste tenant"));
    }

    private void validateFormulaAgainstVariables(String expression,
                                                 List<IndicatorVariable> variables) {
        // (1) Gramatica inteira + denominador constante (correcao D2 da
        // revisao independente): a validacao sintatica percorre a expressao
        // TODA — na avaliacao com valores, uma divisao por zero dependente
        // de dado abortava o parse e mascarava erro de sintaxe e variavel
        // nao declarada DEPOIS dela. Aqui, FORMULA_SYNTAX em qualquer
        // posicao e denominador constante zero (a / 0) recusam a definicao.
        FormulaEvaluator.validateSyntax(expression);
        // (2) Identificadores usados x declarados, SINTATICAMENTE: variavel
        // usada e nao declarada e recusa de definicao em qualquer posicao
        // da formula, sem depender da ordem de avaliacao.
        java.util.Set<String> declaredNames = new java.util.HashSet<>();
        for (IndicatorVariable variable : variables) {
            declaredNames.add(variable.name());
        }
        java.util.Set<String> used = new java.util.HashSet<>();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(expression);
        while (matcher.find()) {
            used.add(matcher.group());
        }
        for (String name : used) {
            if (!declaredNames.contains(name)) {
                throw new IndicatorValidationException("FORMULA_USES_UNDECLARED_VARIABLE",
                        "a formula usa variavel nao declarada na definicao: " + name);
            }
        }
        // (3) Avalia com valores 1 SEM produzir resultado de negocio, como
        // rede adicional: variavel usada e nao declarada cairia em
        // MISSING_VARIABLE_VALUE (a checagem sintatica acima ja a recusa);
        // declarada e nao usada e recusada no passo (4), nomeada.
        Map<String, BigDecimal> ones = new LinkedHashMap<>();
        for (IndicatorVariable variable : variables) {
            ones.put(variable.name(), BigDecimal.ONE);
        }
        try {
            FormulaEvaluator.evaluate(expression, ones);
        } catch (IndicatorValidationException e) {
            if ("MISSING_VARIABLE_VALUE".equals(e.code())) {
                throw new IndicatorValidationException("FORMULA_USES_UNDECLARED_VARIABLE",
                        "a formula usa variavel nao declarada na definicao: " + e.getMessage());
            }
            if ("DIVISION_BY_ZERO".equals(e.code())) {
                // Dependente de dado, nao de sintaxe: com todos os valores 1,
                // um denominador como (a - b) zera sem a formula ser invalida.
                // A divisao por zero real e recusada no calculo, com os
                // valores do periodo — nao aqui.
            } else {
                throw e;
            }
        }
        // (4) Declarada e nao usada: fonte declarada sem uso e recusa.
        for (IndicatorVariable variable : variables) {
            if (!used.contains(variable.name())) {
                throw new IndicatorValidationException("VARIABLE_NOT_IN_FORMULA",
                        "variavel declarada " + variable.name()
                                + " nao aparece na formula (fonte declarada sem uso)");
            }
        }
    }

    private String snapshotJson(Map<String, BigDecimal> variableValues) {
        Map<String, String> snapshot = new TreeMap<>();
        variableValues.forEach((k, v) -> snapshot.put(k, v.toPlainString()));
        return Json.write(snapshot);
    }

    private void audit(UUID tenantId, String code, String action, String detail, String reason,
                       UUID actor) {
        store.appendAudit(new IndicatorAuditTrailEntry(UUID.randomUUID(), tenantId, code, action,
                detail, reason, actor, Instant.now(clock)));
    }

    private void requireEditor(IndicatorAccess access, String operation, String code) {
        if (access != IndicatorAccess.EDITOR) {
            throw new IndicatorAccessDeniedException(operation, code);
        }
    }
}
