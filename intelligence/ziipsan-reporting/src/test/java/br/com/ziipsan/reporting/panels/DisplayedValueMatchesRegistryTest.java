package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.IndicatorAccess;
import br.com.ziipsan.reporting.indicator.IndicatorDefinition;
import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.IndicatorTarget;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;
import br.com.ziipsan.reporting.indicator.Semaphore;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O TESTE CENTRAL DO ACEITE da ED-49: para cada um dos sete paineis, o
 * valor EXIBIDO e comparado com o valor APURADO diretamente no registro
 * da P3.41 (JOR-19) — valor, semaforo, versao da formula, nome, unidade
 * e meta.
 *
 * <p><b>Correcao D2 da revisao (2026-11-08, REVISAO-ED-49 §6-D2).</b>
 * A redacao original afirmava "igualdade exata por compareTo" —
 * afirmacao ERRADA de semantica: {@code isEqualByComparingTo} e
 * igualdade apenas NUMERICA, cega a escala ({@code 42.5} x
 * {@code 42.50} passava nas assercoes deste teste). Estado real
 * medido pelo revisor sobre o disco atual (REVISAO §2, mutacao M2):
 * com {@code setScale(2)} injetado na montagem, 11 testes falham —
 * mas todos pela EXCECAO da guarda de producao D1a
 * ({@code PANEL_DISPLAYED_VALUE_DIVERGES_FROM_REGISTRY}), nao pelas
 * assercoes deste teste; sem a guarda, a divergencia de escala so
 * seria pega indiretamente pela string do JSON exportado. A
 * correcao torna o TESTE CENTRAL sensivel a escala por si so:
 * identidade ESTRITA de representacao (valor E escala —
 * {@code BigDecimal.equals} via {@code isEqualTo}, {@code scale()} e
 * {@code PanelRowView.matchesRegistryValueExactly}) para o valor
 * exibido e para a META exibida, sem remover assercao nenhuma (a
 * igualdade numerica permanece onde ela e a nocao certa). Prova de
 * mutacao desta correcao no RELATORIO-CORRECAO-ED-49.md: com a
 * guarda neutralizada no mutante, o {@code setScale(2)} passa a ser
 * mordido pela assercao do teste central. A producao nao continha
 * {@code setScale}: o defeito era de precisao do teste, nao do
 * produto. (Nota de auditoria do CORR-2: uma redacao anterior deste
 * javadoc, do corretor-1, afirmava "25/26 passavam" — numero da
 * revisao SUPERADA, que a REVISAO atual declara nao se reproduzir
 * no disco; substituida por esta, fiel a revisao vigente.)
 *
 * <p><b>Correcao D3 da revisao (selagem).</b> {@code exportsRefuseRowsNotBuiltFromTheRegistry}
 * reproduz a sonda do revisor (linha fabricada com valor 999 e
 * semaforo GREEN inventados): a exportacao agora recusa nomeada
 * ({@code PANEL_ROW_NOT_FROM_REGISTRY}); linhas montadas pelo
 * servico seguem exportaveis ({@code exportsCarryTheDisplayedRegistryValues}).
 */
class DisplayedValueMatchesRegistryTest {

    @Test
    void displayedValueEqualsRegistryValueForAllSevenPanels() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        StandardPanelService panels = new StandardPanelService(registry);
        List<PanelCellRequest> requests = PanelFixtures.oneRequestPerPanel("42.5");

        Map<PanelKind, List<PanelRowView>> all =
                panels.buildAll(PanelFixtures.TENANT, requests);
        assertThat(all).hasSize(7);
        for (PanelKind kind : PanelKind.values()) {
            List<PanelRowView> rows = all.get(kind);
            assertThat(rows).as("painel %s", kind).hasSize(1);
            PanelRowView row = rows.get(0);
            // A referencia: a apuracao direta no registro, com as mesmas entradas.
            var direct = registry.compute(PanelFixtures.TENANT, PanelFixtures.codeFor(kind),
                    PanelFixtures.JAN_START, PanelFixtures.JAN_END, null,
                    Map.of("valor", new BigDecimal("42.5")));
            assertThat(row.value()).as("valor exibido x registro em %s", kind)
                    .isEqualByComparingTo(direct.value());
            // Correcao D2: identidade ESTRITA (valor E escala) — a
            // igualdade numerica acima, sozinha, nao distingue 42.5 de
            // 42.50 e deixava a mutacao setScale(2) passar (M2 do revisor).
            assertThat(row.value()).as("identidade estrita exibido x registro em %s", kind)
                    .isEqualTo(direct.value());
            assertThat(row.value().scale()).as("escala exibida x registro em %s", kind)
                    .isEqualTo(direct.value().scale());
            assertThat(row.matchesRegistryValueExactly(direct.value()))
                    .as("matchesRegistryValueExactly em %s", kind).isTrue();
            assertThat(row.semaphore()).isEqualTo(direct.semaphore());
            assertThat(row.formulaVersion()).isEqualTo(direct.formulaVersion());
            assertThat(row.frozen()).isEqualTo(direct.frozen());
            var definition = registry.definition(PanelFixtures.TENANT, PanelFixtures.codeFor(kind));
            assertThat(row.indicatorName()).isEqualTo(definition.name());
            assertThat(row.unit()).isEqualTo(definition.unit());
        }
    }

    @Test
    void displayedValueIdentityIsStrictInValueAndScale() {
        // Correcao D2: a semantica que o teste central afirma, provada
        // isolada. 42.50 e o MESMO numero que 42.5 (igualdade numerica
        // verdadeira), mas NAO e a mesma representacao do registro:
        // quem afirma "o exibido e o valor do registro" usa a estrita.
        PanelRowView rescaled = new PanelRowView(PanelKind.MEDICAO, "ED49-TESTE-ESCALA",
                "Escala sintetica", "un", PanelFixtures.JAN_START, PanelFixtures.JAN_END, null,
                new BigDecimal("42.50"), null, null, Semaphore.NO_TARGET, false, 1);
        assertThat(rescaled.matchesRegistryValueNumerically(new BigDecimal("42.5"))).isTrue();
        assertThat(rescaled.matchesRegistryValueExactly(new BigDecimal("42.5"))).isFalse();
        assertThat(rescaled.matchesRegistryValueExactly(new BigDecimal("42.50"))).isTrue();
        // Divergencia de VALOR nao passa em nenhuma das duas nocoes.
        assertThat(rescaled.matchesRegistryValueNumerically(new BigDecimal("42.6"))).isFalse();
        assertThat(rescaled.matchesRegistryValueExactly(new BigDecimal("42.6"))).isFalse();
        // E a linha montada pelo servico, com o valor do registro, e
        // estritamente identica a apuracao direta (sem setScale algum).
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        StandardPanelService panels = new StandardPanelService(registry);
        PanelRowView row = panels.buildPanel(PanelFixtures.TENANT, PanelKind.MEDICAO,
                List.of(PanelFixtures.request(PanelKind.MEDICAO, "42.5"))).get(0);
        var direct = registry.compute(PanelFixtures.TENANT, PanelFixtures.codeFor(PanelKind.MEDICAO),
                PanelFixtures.JAN_START, PanelFixtures.JAN_END, null,
                Map.of("valor", new BigDecimal("42.5")));
        assertThat(row.value()).isEqualTo(direct.value());
        assertThat(row.matchesRegistryValueExactly(direct.value())).isTrue();
    }

    @Test
    void panelTransportsComputedResultOfARegistryFormulaWithoutRepeatingIt() {
        // Indicador com formula de verdade no registro: (a - b) / a * 100.
        IndicatorRegistryService registry = PanelFixtures.newRegistry();
        registry.define(IndicatorAccess.EDITOR,
                new IndicatorDefinition(PanelFixtures.TENANT, "ED49-TESTE-TAXA", "Taxa sintetica",
                        "def", "%", IndicatorPeriodicity.MONTHLY, ConfidenceGrade.HIGH, "ctx",
                        null, Set.of(),
                        List.of(IndicatorVariable.manual("a", PanelFixtures.RESPONSIBLE),
                                IndicatorVariable.manual("b", PanelFixtures.RESPONSIBLE))),
                "(a - b) / a * 100", PanelFixtures.VALIDITY_2026, PanelFixtures.EDITOR);
        StandardPanelService panels = new StandardPanelService(registry);
        PanelCellRequest request = new PanelCellRequest(PanelKind.FATURAMENTO, "ED49-TESTE-TAXA",
                PanelFixtures.JAN_START, PanelFixtures.JAN_END, null,
                Map.of("a", new BigDecimal("1000"), "b", new BigDecimal("750")));
        PanelRowView row = panels.buildPanel(PanelFixtures.TENANT, PanelKind.FATURAMENTO,
                List.of(request)).get(0);
        var direct = registry.compute(PanelFixtures.TENANT, "ED49-TESTE-TAXA",
                PanelFixtures.JAN_START, PanelFixtures.JAN_END, null,
                Map.of("a", new BigDecimal("1000"), "b", new BigDecimal("750")));
        assertThat(direct.value()).isEqualByComparingTo("25");
        assertThat(row.value()).isEqualByComparingTo(direct.value());
    }

    @Test
    void formulaChangeInRegistryFlowsToPanelWithoutAnyPanelChange() {
        IndicatorRegistryService registry = PanelFixtures.newRegistry();
        registry.define(IndicatorAccess.EDITOR,
                new IndicatorDefinition(PanelFixtures.TENANT, "ED49-TESTE-VERSAO", "Versionado",
                        "def", "un", IndicatorPeriodicity.MONTHLY, ConfidenceGrade.HIGH, "ctx",
                        null, Set.of(),
                        List.of(IndicatorVariable.manual("a", PanelFixtures.RESPONSIBLE),
                                IndicatorVariable.manual("b", PanelFixtures.RESPONSIBLE))),
                "a - b", PanelFixtures.VALIDITY_2026, PanelFixtures.EDITOR);
        registry.changeFormula(IndicatorAccess.EDITOR, PanelFixtures.TENANT, "ED49-TESTE-VERSAO",
                "a + b", new ParameterVersionRule.Validity(LocalDate.of(2027, 1, 1), null),
                PanelFixtures.EDITOR);
        StandardPanelService panels = new StandardPanelService(registry);
        PanelCellRequest request2027 = new PanelCellRequest(PanelKind.MEDICAO, "ED49-TESTE-VERSAO",
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), null,
                Map.of("a", new BigDecimal("10"), "b", new BigDecimal("3")));
        PanelRowView row = panels.buildPanel(PanelFixtures.TENANT, PanelKind.MEDICAO,
                List.of(request2027)).get(0);
        // v2 do registro (a + b = 13, versao 2): o painel segue o registro sozinho.
        assertThat(row.value()).isEqualByComparingTo("13");
        assertThat(row.formulaVersion()).isEqualTo(2);
    }

    @Test
    void closedPeriodShowsTheFrozenRegistryValueNotARecalculation() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        registry.close(IndicatorAccess.EDITOR, PanelFixtures.TENANT,
                PanelFixtures.codeFor(PanelKind.ARRECADACAO),
                PanelFixtures.JAN_START, PanelFixtures.JAN_END, null,
                Map.of("valor", new BigDecimal("100")), PanelFixtures.EDITOR);
        StandardPanelService panels = new StandardPanelService(registry);
        // O painel pede com OUTRO valor de variavel: exibe o congelado (100).
        PanelRowView row = panels.buildPanel(PanelFixtures.TENANT, PanelKind.ARRECADACAO,
                List.of(PanelFixtures.request(PanelKind.ARRECADACAO, "999"))).get(0);
        assertThat(row.frozen()).isTrue();
        assertThat(row.value()).isEqualByComparingTo("100");
    }

    @Test
    void targetAndSemaphoreDisplayedAreTheRegistryOnesIncludingNoTarget() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        String code = PanelFixtures.codeFor(PanelKind.ATENDIMENTO);
        registry.setTarget(IndicatorAccess.EDITOR, PanelFixtures.TENANT,
                new IndicatorTarget(code, PanelFixtures.JAN_START, PanelFixtures.JAN_END, null,
                        new BigDecimal("50"), new BigDecimal("10"),
                        IndicatorTarget.Direction.HIGHER_IS_BETTER),
                PanelFixtures.EDITOR);
        StandardPanelService panels = new StandardPanelService(registry);
        PanelRowView withTarget = panels.buildPanel(PanelFixtures.TENANT, PanelKind.ATENDIMENTO,
                List.of(PanelFixtures.request(PanelKind.ATENDIMENTO, "40"))).get(0);
        var direct = registry.compute(PanelFixtures.TENANT, code, PanelFixtures.JAN_START,
                PanelFixtures.JAN_END, null, Map.of("valor", new BigDecimal("40")));
        // Meta 50, tolerancia 10% = 5: 40 < 45 => RED do registro, exibido verbatim.
        assertThat(direct.semaphore()).isEqualTo(Semaphore.RED);
        assertThat(withTarget.semaphore()).isEqualTo(direct.semaphore());
        assertThat(withTarget.targetValue()).isEqualByComparingTo("50");
        // Correcao D2: a META exibida tambem com identidade estrita
        // (valor E escala) — 50 (escala 0) nao e 50.0 (escala 1).
        assertThat(withTarget.targetValue()).isEqualTo(new BigDecimal("50"));
        assertThat(withTarget.targetValue().scale()).isEqualTo(new BigDecimal("50").scale());
        assertThat(withTarget.targetDirection()).isEqualTo("HIGHER_IS_BETTER");
        // Sem meta (outro painel): NO_TARGET do registro, meta exibida nula — nunca verde.
        PanelRowView noTarget = panels.buildPanel(PanelFixtures.TENANT, PanelKind.COBRANCA,
                List.of(PanelFixtures.request(PanelKind.COBRANCA, "40"))).get(0);
        assertThat(noTarget.semaphore()).isEqualTo(Semaphore.NO_TARGET);
        assertThat(noTarget.targetValue()).isNull();
    }

    @Test
    void areaTargetPrevailsOverGeneralInTheDisplayedTarget() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        String code = PanelFixtures.codeFor(PanelKind.OPERACIONAL);
        registry.setTarget(IndicatorAccess.EDITOR, PanelFixtures.TENANT,
                new IndicatorTarget(code, PanelFixtures.JAN_START, PanelFixtures.JAN_END, null,
                        new BigDecimal("50"), new BigDecimal("10"),
                        IndicatorTarget.Direction.HIGHER_IS_BETTER), PanelFixtures.EDITOR);
        registry.setTarget(IndicatorAccess.EDITOR, PanelFixtures.TENANT,
                new IndicatorTarget(code, PanelFixtures.JAN_START, PanelFixtures.JAN_END, "NORTE",
                        new BigDecimal("70"), new BigDecimal("10"),
                        IndicatorTarget.Direction.HIGHER_IS_BETTER), PanelFixtures.EDITOR);
        StandardPanelService panels = new StandardPanelService(registry);
        PanelCellRequest north = new PanelCellRequest(PanelKind.OPERACIONAL, code,
                PanelFixtures.JAN_START, PanelFixtures.JAN_END, "NORTE",
                Map.of("valor", new BigDecimal("65")));
        PanelRowView row = panels.buildPanel(PanelFixtures.TENANT, PanelKind.OPERACIONAL,
                List.of(north)).get(0);
        assertThat(row.targetValue()).isEqualByComparingTo("70");
        // Correcao D2: meta de area tambem com identidade estrita.
        assertThat(row.targetValue()).isEqualTo(new BigDecimal("70"));
        var direct = registry.compute(PanelFixtures.TENANT, code, PanelFixtures.JAN_START,
                PanelFixtures.JAN_END, "NORTE", Map.of("valor", new BigDecimal("65")));
        assertThat(row.value()).isEqualByComparingTo(direct.value());
        assertThat(row.semaphore()).isEqualTo(direct.semaphore());
    }

    @Test
    void unknownIndicatorAndOtherTenantIndicatorAreRefusedByTheRegistry() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        StandardPanelService panels = new StandardPanelService(registry);
        PanelCellRequest ghost = new PanelCellRequest(PanelKind.MEDICAO, "NAO-EXISTE",
                PanelFixtures.JAN_START, PanelFixtures.JAN_END, null, Map.of());
        assertThatThrownBy(() -> panels.buildPanel(PanelFixtures.TENANT, PanelKind.MEDICAO,
                List.of(ghost)))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("INDICATOR_NOT_FOUND");
        // Isolamento: o indicador existe, mas em OUTRO tenant nao.
        assertThatThrownBy(() -> panels.buildPanel(PanelFixtures.OTHER_TENANT, PanelKind.MEDICAO,
                List.of(PanelFixtures.request(PanelKind.MEDICAO, "1"))))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("INDICATOR_NOT_FOUND");
    }

    @Test
    void exportsRefuseRowsNotBuiltFromTheRegistry() {
        // Correcao D3: a sonda do revisor, agora recusada. A linha e
        // fabricada NESTE pacote de teste pelo construtor package-private
        // (fora do pacote nem isso compila) com valor 999 e semaforo
        // GREEN inventados — sem selo do servico, a exportacao recusa
        // nomeada em vez de emitir "999"/"GREEN" como se fossem do
        // registro. Ver PanelRowView (selagem) e StandardPanelService
        // (requireRegistrySealed).
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        StandardPanelService panels = new StandardPanelService(registry);
        PanelRowView forged = new PanelRowView(PanelKind.ARRECADACAO, "SONDA-D3",
                "Sonda sintetica", "un", PanelFixtures.JAN_START, PanelFixtures.JAN_END, null,
                new BigDecimal("999"), null, null, Semaphore.GREEN, false, 1);
        assertThatThrownBy(() -> panels.exportJson(List.of(forged)))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("PANEL_ROW_NOT_FROM_REGISTRY");
        assertThatThrownBy(() -> panels.exportCsv(List.of(forged)))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("PANEL_ROW_NOT_FROM_REGISTRY");
        // E a linha legitima, montada pelo servico, continua exportavel.
        List<PanelRowView> legit = panels.buildPanel(PanelFixtures.TENANT, PanelKind.ARRECADACAO,
                List.of(PanelFixtures.request(PanelKind.ARRECADACAO, "42.5")));
        assertThat(panels.exportJson(legit)).contains("\"valor\":\"42.5\"");
    }

    @Test
    void exportsCarryTheDisplayedRegistryValues() {
        IndicatorRegistryService registry = PanelFixtures.registryWithOneIndicatorPerPanel();
        StandardPanelService panels = new StandardPanelService(registry);
        List<PanelRowView> rows = panels.buildPanel(PanelFixtures.TENANT, PanelKind.ARRECADACAO,
                List.of(PanelFixtures.request(PanelKind.ARRECADACAO, "42.5")));
        assertThat(panels.exportCsv(rows)).contains("ARRECADACAO").contains("42.5");
        assertThat(panels.exportJson(rows)).contains("\"valor\":\"42.5\"")
                .contains("\"semaforo\":\"NO_TARGET\"");
    }
}
