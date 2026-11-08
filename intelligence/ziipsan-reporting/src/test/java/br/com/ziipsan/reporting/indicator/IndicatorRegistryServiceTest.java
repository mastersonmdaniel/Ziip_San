package br.com.ziipsan.reporting.indicator;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.ParameterVersionRule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndicatorRegistryServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID EDITOR_ACTOR = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final LocalDate JAN_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_END = LocalDate.of(2026, 1, 31);

    private IndicatorRegistryService service;

    @BeforeEach
    void setUp() {
        service = new IndicatorRegistryService(new InMemoryIndicatorStore(),
                Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC));
        service.define(IndicatorAccess.EDITOR, perdaDefinition(), "(produzido - distribuido) / produzido * 100",
                new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                EDITOR_ACTOR);
    }

    private static IndicatorDefinition perdaDefinition() {
        return new IndicatorDefinition(TENANT, "IND-PERDA", "Perdas na distribuicao",
                "Percentual de agua produzida nao distribuida", "%",
                IndicatorPeriodicity.MONTHLY, ConfidenceGrade.HIGH,
                "Rede de distribuicao urbana", "ISO_24510", Set.of("OPERACAO", "DIRETORIA"),
                List.of(IndicatorVariable.fromReportView("produzido", "rpt_producao"),
                        IndicatorVariable.manual("distribuido", RESPONSIBLE)));
    }

    private static Map<String, BigDecimal> values(String produzido, String distribuido) {
        return Map.of("produzido", new BigDecimal(produzido), "distribuido", new BigDecimal(distribuido));
    }

    private static final ParameterVersionRule.Validity VALIDITY_2026 =
            new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    private static IndicatorDefinition abDefinition(String code) {
        return new IndicatorDefinition(TENANT, code, "Indicador AB", "def", "un",
                IndicatorPeriodicity.ANNUAL, ConfidenceGrade.LOW, "ctx", null, Set.of(),
                List.of(IndicatorVariable.manual("a", RESPONSIBLE),
                        IndicatorVariable.manual("b", RESPONSIBLE)));
    }

    private IndicatorTarget target(String area, String value, String tolerance,
                                   IndicatorTarget.Direction direction,
                                   LocalDate start, LocalDate end) {
        return new IndicatorTarget("IND-PERDA", start, end, area, new BigDecimal(value),
                new BigDecimal(tolerance), direction);
    }

    @Test
    void computeUsesEffectiveFormulaAndDeclaredVariables() {
        var result = service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null, values("1000", "900"));
        assertThat(result.value()).isEqualByComparingTo("10");
        assertThat(result.formulaVersion()).isEqualTo(1);
        assertThat(result.frozen()).isFalse();
        assertThat(result.semaphore()).isEqualTo(Semaphore.NO_TARGET);
    }

    @Test
    void formulaChangeCreatesNewVersionAndKeepsTheOldOne() {
        var v2 = service.changeFormula(IndicatorAccess.EDITOR, TENANT, "IND-PERDA",
                "(produzido - distribuido) / distribuido * 100",
                new ParameterVersionRule.Validity(LocalDate.of(2027, 1, 1), null), EDITOR_ACTOR);
        assertThat(v2.version()).isEqualTo(2);
        var versions = service.formulaVersions(TENANT, "IND-PERDA");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).expression())
                .isEqualTo("(produzido - distribuido) / produzido * 100");
        // Periodo de 2026 continua apurado pela v1 (historico nao reescrito):
        var result = service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null, values("1000", "900"));
        assertThat(result.formulaVersion()).isEqualTo(1);
        assertThat(result.value()).isEqualByComparingTo("10");
    }

    @Test
    void overlappingFormulaValidityIsRefusedByTheP019Rule() {
        assertThatThrownBy(() -> service.changeFormula(IndicatorAccess.EDITOR, TENANT, "IND-PERDA",
                "produzido - distribuido",
                new ParameterVersionRule.Validity(LocalDate.of(2026, 6, 1), null), EDITOR_ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobreposta");
    }

    @Test
    void formulaUsingUndeclaredVariableIsRefusedAtDefineTime() {
        var other = new IndicatorDefinition(TENANT, "IND-X", "X", "def", "un",
                IndicatorPeriodicity.ANNUAL, ConfidenceGrade.LOW, "ctx", null, Set.of(),
                List.of(IndicatorVariable.manual("a", RESPONSIBLE)));
        assertThatThrownBy(() -> service.define(IndicatorAccess.EDITOR, other, "a + fantasma",
                new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), null), EDITOR_ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("FORMULA_USES_UNDECLARED_VARIABLE");
    }

    @Test
    void recalculationDoesNotAlterAClosedPeriod() {
        IndicatorClosing closing = service.close(IndicatorAccess.EDITOR, TENANT, "IND-PERDA",
                JAN_START, JAN_END, null, values("1000", "900"), EDITOR_ACTOR);
        assertThat(closing.value()).isEqualByComparingTo("10");
        // O retrato congelado e o JSON das variaveis (Json do core), decimais em texto:
        assertThat(Json.readMap(closing.variableValuesJson()))
                .containsEntry("produzido", "1000")
                .containsEntry("distribuido", "900");
        // Recalculo com valores DIFERENTES devolve o congelado, sem recalcular:
        var again = service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null, values("1000", "100"));
        assertThat(again.frozen()).isTrue();
        assertThat(again.value()).isEqualByComparingTo("10");
        assertThat(again.closingId()).isEqualTo(closing.id());
        // E fechar de novo sem reabrir e recusa nomeada:
        assertThatThrownBy(() -> service.close(IndicatorAccess.EDITOR, TENANT, "IND-PERDA",
                JAN_START, JAN_END, null, values("1000", "900"), EDITOR_ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("PERIOD_ALREADY_CLOSED");
    }

    @Test
    void reopenRequiresReasonAndLeavesAuditTrail() {
        service.close(IndicatorAccess.EDITOR, TENANT, "IND-PERDA", JAN_START, JAN_END, null,
                values("1000", "900"), EDITOR_ACTOR);
        assertThatThrownBy(() -> service.reopen(IndicatorAccess.EDITOR, TENANT, "IND-PERDA",
                JAN_START, null, " ", EDITOR_ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("REOPEN_REASON_REQUIRED");
        IndicatorClosing reopened = service.reopen(IndicatorAccess.EDITOR, TENANT, "IND-PERDA",
                JAN_START, null, "leitura corrigida pela equipe de campo", EDITOR_ACTOR);
        assertThat(reopened.status()).isEqualTo(IndicatorClosing.Status.REOPENED);
        assertThat(reopened.reopenReason()).isEqualTo("leitura corrigida pela equipe de campo");
        assertThat(service.auditTrail(TENANT, "IND-PERDA"))
                .anySatisfy(e -> {
                    assertThat(e.action()).isEqualTo("REOPEN");
                    assertThat(e.reason()).isEqualTo("leitura corrigida pela equipe de campo");
                });
        // Depois da reabertura, o recalculo volta a valer e o novo fechamento congela o novo valor:
        var recomputed = service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null, values("1000", "800"));
        assertThat(recomputed.frozen()).isFalse();
        assertThat(recomputed.value()).isEqualByComparingTo("20");
        IndicatorClosing reclosed = service.close(IndicatorAccess.EDITOR, TENANT, "IND-PERDA",
                JAN_START, JAN_END, null, values("1000", "800"), EDITOR_ACTOR);
        assertThat(reclosed.value()).isEqualByComparingTo("20");
        assertThat(reclosed.id()).isNotEqualTo(reopened.id());
    }

    @Test
    void semaphoreByDeclaredBandForBothDirections() {
        service.setTarget(IndicatorAccess.EDITOR, TENANT,
                target(null, "25", "20", IndicatorTarget.Direction.LOWER_IS_BETTER, JAN_START, JAN_END),
                EDITOR_ACTOR);
        // Meta 25 (menor melhor), tolerancia 20% = 5: verde <= 25, amarelo <= 30, vermelho > 30.
        assertThat(service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null, values("1000", "800"))
                .semaphore()).isEqualTo(Semaphore.GREEN);
        assertThat(service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null, values("1000", "720"))
                .semaphore()).isEqualTo(Semaphore.YELLOW);
        assertThat(service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null, values("1000", "500"))
                .semaphore()).isEqualTo(Semaphore.RED);
        // HIGHER_IS_BETTER (a outra direcao do nome do teste): cobertura,
        // meta 90 com tolerancia de 10% = faixa de 9: verde >= 90,
        // amarelo >= 81, vermelho < 81.
        service.define(IndicatorAccess.EDITOR, new IndicatorDefinition(TENANT, "IND-COBERTURA",
                "Cobertura de coleta", "Percentual da demanda atendida", "%",
                IndicatorPeriodicity.MONTHLY, ConfidenceGrade.HIGH,
                "Coleta urbana", null, Set.of("OPERACAO"),
                List.of(IndicatorVariable.manual("atendido", RESPONSIBLE),
                        IndicatorVariable.manual("demanda", RESPONSIBLE))),
                "atendido / demanda * 100", VALIDITY_2026, EDITOR_ACTOR);
        service.setTarget(IndicatorAccess.EDITOR, TENANT,
                new IndicatorTarget("IND-COBERTURA", JAN_START, JAN_END, null,
                        new BigDecimal("90"), new BigDecimal("10"),
                        IndicatorTarget.Direction.HIGHER_IS_BETTER),
                EDITOR_ACTOR);
        assertThat(service.compute(TENANT, "IND-COBERTURA", JAN_START, JAN_END, null,
                Map.of("atendido", new BigDecimal("950"), "demanda", new BigDecimal("1000")))
                .semaphore()).isEqualTo(Semaphore.GREEN);
        assertThat(service.compute(TENANT, "IND-COBERTURA", JAN_START, JAN_END, null,
                Map.of("atendido", new BigDecimal("850"), "demanda", new BigDecimal("1000")))
                .semaphore()).isEqualTo(Semaphore.YELLOW);
        assertThat(service.compute(TENANT, "IND-COBERTURA", JAN_START, JAN_END, null,
                Map.of("atendido", new BigDecimal("800"), "demanda", new BigDecimal("1000")))
                .semaphore()).isEqualTo(Semaphore.RED);
    }

    @Test
    void areaTargetOverridesTheGeneralOne() {
        service.setTarget(IndicatorAccess.EDITOR, TENANT,
                target(null, "25", "20", IndicatorTarget.Direction.LOWER_IS_BETTER, JAN_START, JAN_END),
                EDITOR_ACTOR);
        service.setTarget(IndicatorAccess.EDITOR, TENANT,
                target("NORTE", "10", "0", IndicatorTarget.Direction.LOWER_IS_BETTER, JAN_START, JAN_END),
                EDITOR_ACTOR);
        // Valor 10: verde na meta da area NORTE (<=10); na geral tambem seria verde,
        // entao o desempate se prova com valor 20: NORTE=vermelho, geral=verde.
        assertThat(service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, "NORTE", values("1000", "800"))
                .semaphore()).isEqualTo(Semaphore.RED);
        assertThat(service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, "SUL", values("1000", "800"))
                .semaphore()).isEqualTo(Semaphore.GREEN);
    }

    @Test
    void overlappingTargetForSameAreaIsRefused() {
        service.setTarget(IndicatorAccess.EDITOR, TENANT,
                target(null, "25", "20", IndicatorTarget.Direction.LOWER_IS_BETTER, JAN_START, JAN_END),
                EDITOR_ACTOR);
        assertThatThrownBy(() -> service.setTarget(IndicatorAccess.EDITOR, TENANT,
                target(null, "30", "10", IndicatorTarget.Direction.LOWER_IS_BETTER,
                        LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15)), EDITOR_ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobreposta");
    }

    @Test
    void missingOrUndeclaredValuesAreNamedRefusals() {
        assertThatThrownBy(() -> service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null,
                Map.of("produzido", new BigDecimal("1000"))))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("MISSING_VARIABLE_VALUE");
        assertThatThrownBy(() -> service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null,
                Map.of("produzido", BigDecimal.ONE, "distribuido", BigDecimal.ONE,
                        "extra", BigDecimal.ONE)))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("UNDECLARED_VARIABLE_VALUE");
    }

    @Test
    void periodWithoutEffectiveFormulaIsNamedRefusal() {
        assertThatThrownBy(() -> service.compute(TENANT, "IND-PERDA",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), null, values("1000", "900")))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("FORMULA_NOT_EFFECTIVE");
    }

    @Test
    void readersReadButCannotWriteDefinitions() {
        // Leitura do leitor funciona:
        assertThat(service.definition(TENANT, "IND-PERDA").code()).isEqualTo("IND-PERDA");
        assertThat(service.listForProfile(TENANT, "DIRETORIA"))
                .extracting(IndicatorDefinition::code).containsExactly("IND-PERDA");
        assertThat(service.listForProfile(TENANT, "CAMPO")).isEmpty();
        // Escrita do leitor e recusada, sem efeito:
        assertThatThrownBy(() -> service.changeFormula(IndicatorAccess.READER, TENANT, "IND-PERDA",
                "produzido - distribuido",
                new ParameterVersionRule.Validity(LocalDate.of(2030, 1, 1), null), EDITOR_ACTOR))
                .isInstanceOf(IndicatorAccessDeniedException.class)
                .hasMessageContaining("READER_CANNOT_WRITE");
        assertThatThrownBy(() -> service.setTarget(IndicatorAccess.READER, TENANT,
                target(null, "1", "0", IndicatorTarget.Direction.LOWER_IS_BETTER, JAN_START, JAN_END),
                EDITOR_ACTOR))
                .isInstanceOf(IndicatorAccessDeniedException.class);
        assertThatThrownBy(() -> service.close(IndicatorAccess.READER, TENANT, "IND-PERDA",
                JAN_START, JAN_END, null, values("1000", "900"), EDITOR_ACTOR))
                .isInstanceOf(IndicatorAccessDeniedException.class);
        assertThat(service.formulaVersions(TENANT, "IND-PERDA")).hasSize(1);
        assertThat(service.targets(TENANT, "IND-PERDA")).isEmpty();
        assertThat(service.closing(TENANT, "IND-PERDA", JAN_START, null)).isEmpty();
    }

    @Test
    void readersCannotDefineNorReopen() {
        // define com READER (a 4a escrita; changeFormula/setTarget/close
        // estao no teste acima): recusado e sem efeito.
        var leitorDefinition = new IndicatorDefinition(TENANT, "IND-LEITOR", "Do leitor", "def", "un",
                IndicatorPeriodicity.ANNUAL, ConfidenceGrade.LOW, "ctx", null, Set.of(),
                List.of(IndicatorVariable.manual("a", RESPONSIBLE)));
        assertThatThrownBy(() -> service.define(IndicatorAccess.READER, leitorDefinition, "a",
                VALIDITY_2026, EDITOR_ACTOR))
                .isInstanceOf(IndicatorAccessDeniedException.class)
                .hasMessageContaining("READER_CANNOT_WRITE");
        assertThatThrownBy(() -> service.definition(TENANT, "IND-LEITOR"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("INDICATOR_NOT_FOUND");
        // reopen com READER (a 5a escrita): recusado e o periodo continua
        // fechado, com o valor congelado.
        service.close(IndicatorAccess.EDITOR, TENANT, "IND-PERDA", JAN_START, JAN_END, null,
                values("1000", "900"), EDITOR_ACTOR);
        assertThatThrownBy(() -> service.reopen(IndicatorAccess.READER, TENANT, "IND-PERDA",
                JAN_START, null, "tentativa do leitor", EDITOR_ACTOR))
                .isInstanceOf(IndicatorAccessDeniedException.class);
        assertThat(service.closing(TENANT, "IND-PERDA", JAN_START, null)
                .map(IndicatorClosing::status)).contains(IndicatorClosing.Status.CLOSED);
        var stillFrozen = service.compute(TENANT, "IND-PERDA", JAN_START, JAN_END, null,
                values("1000", "100"));
        assertThat(stillFrozen.frozen()).isTrue();
        assertThat(stillFrozen.value()).isEqualByComparingTo("10");
    }

    @Test
    void undeclaredVariableAfterDataDependentZeroDenominatorIsRefusedAtDefineTime() {
        // D2: o denominador (a - b) zera no ensaio com 1s; a variavel nao
        // declarada DEPOIS dele tem de recusar a definicao mesmo assim.
        assertThatThrownBy(() -> service.define(IndicatorAccess.EDITOR, abDefinition("IND-D2A"),
                "a / (a - b) + fantasma", VALIDITY_2026, EDITOR_ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("FORMULA_USES_UNDECLARED_VARIABLE")
                .hasMessageContaining("fantasma");
        assertThatThrownBy(() -> service.definition(TENANT, "IND-D2A"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("INDICATOR_NOT_FOUND");
    }

    @Test
    void syntaxErrorAfterDataDependentZeroDenominatorIsRefusedAtDefineTime() {
        // D2: idem para erro de sintaxe depois do denominador que zera.
        assertThatThrownBy(() -> service.define(IndicatorAccess.EDITOR, abDefinition("IND-D2B"),
                "a / (a - b) + ^", VALIDITY_2026, EDITOR_ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("FORMULA_SYNTAX");
        assertThatThrownBy(() -> service.definition(TENANT, "IND-D2B"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("INDICATOR_NOT_FOUND");
    }

    @Test
    void constantDivisionByZeroIsRefusedAtDefineTime() {
        // D2: denominador constante zero nao depende de dado — recusa na
        // definicao, literal ou subexpressao constante.
        assertThatThrownBy(() -> service.define(IndicatorAccess.EDITOR, abDefinition("IND-D2C"),
                "a / 0", VALIDITY_2026, EDITOR_ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("DIVISION_BY_ZERO");
        assertThatThrownBy(() -> service.define(IndicatorAccess.EDITOR, abDefinition("IND-D2C"),
                "a / (2 - 2) + b", VALIDITY_2026, EDITOR_ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("DIVISION_BY_ZERO");
        assertThatThrownBy(() -> service.definition(TENANT, "IND-D2C"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("INDICATOR_NOT_FOUND");
    }

    @Test
    void dataDependentZeroDenominatorIsAcceptedAtDefineTimeAndRefusedAtComputeTime() {
        // Controle positivo de D2: a / (a - b) NAO e erro de definicao
        // (o denominador so zera com certos dados); a recusa e no calculo.
        service.define(IndicatorAccess.EDITOR, abDefinition("IND-D2D"), "a / (a - b)",
                VALIDITY_2026, EDITOR_ACTOR);
        var ok = service.compute(TENANT, "IND-D2D", JAN_START, JAN_END, null,
                Map.of("a", new BigDecimal("10"), "b", new BigDecimal("5")));
        assertThat(ok.value()).isEqualByComparingTo("2");
        assertThatThrownBy(() -> service.compute(TENANT, "IND-D2D", JAN_START, JAN_END, null,
                Map.of("a", new BigDecimal("5"), "b", new BigDecimal("5"))))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("DIVISION_BY_ZERO");
    }

    @Test
    void closeWithInvertedPeriodIsRefused() {
        // D3: a V15_2 tem CHECK (period_end >= period_start); o servico
        // recusa em memoria, sem gravar fechamento.
        assertThatThrownBy(() -> service.close(IndicatorAccess.EDITOR, TENANT, "IND-PERDA",
                JAN_END, JAN_START, null, values("1000", "900"), EDITOR_ACTOR))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_PERIOD_INVALID");
        assertThat(service.closing(TENANT, "IND-PERDA", JAN_END, null)).isEmpty();
    }

    @Test
    void targetWithNullPeriodEndIsRefusedLikeTheNotNullColumn() {
        // D5 confirmado: a Validity da P0.19 admite fim nulo (vigencia
        // aberta das formulas), mas indicator_targets.period_end e NOT
        // NULL na V15_2 — a meta aberta e recusada no dominio.
        assertThatThrownBy(() -> new IndicatorTarget("IND-PERDA", JAN_START, null, null,
                new BigDecimal("25"), new BigDecimal("20"),
                IndicatorTarget.Direction.LOWER_IS_BETTER))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("TARGET_PERIOD_END_REQUIRED");
    }

    @Test
    void tenantsAreIsolated() {
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> service.definition(other, "IND-PERDA"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("INDICATOR_NOT_FOUND");
        assertThat(service.listForProfile(other, "DIRETORIA")).isEmpty();
    }
}
