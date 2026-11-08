package br.com.ziipsan.reporting.catalog;

import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As definicoes novas da ED-46 obedecem ao contrato da P3.22 (o proprio
 * record {@code ReportDefinition} valida codigo, view rpt_* e permissao
 * {@code <prefixo>.<codigo>:export} na construcao - estes testes provam
 * que as cinco passam e nao colidem com o catalogo de fabrica).
 */
class Ed46ReportDefinitionsTest {

    @Test
    void cincoDefinicoesSemColisaoComAFabricaDaP322() {
        List<ReportDefinition> defs = Ed46ReportDefinitions.all();
        assertThat(defs).hasSize(5);
        Set<String> codes = defs.stream().map(ReportDefinition::code).collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrder("RPT-101", "RPT-102", "RPT-103", "RPT-104", "RPT-105");
        Set<String> builtins = ReportRegistry.builtins().stream()
                .map(ReportDefinition::code).collect(Collectors.toSet());
        assertThat(codes).doesNotContainAnyElementsOf(builtins);
        Set<String> views = defs.stream().map(ReportDefinition::viewName).collect(Collectors.toSet());
        assertThat(views).hasSize(defs.size());
    }

    @Test
    void nenhumaColunaPessoalNasDefinicoesNovas() {
        // Os relatorios novos sao agregados (atendente, origem, tipo,
        // leiturista, usuario do sistema): nenhum expoe dado pessoal de
        // usuario final; documento nao aparece nem mascarado.
        assertThat(Ed46ReportDefinitions.all())
                .allSatisfy(d -> assertThat(d.columns())
                        .allSatisfy(c -> assertThat(c.personal()).isFalse()));
    }

    @Test
    void compensacoesExpoemTipoDeUsuarioTipoDeCompensacaoEFormula() {
        ReportDefinition def = Ed46ReportDefinitions.all().stream()
                .filter(d -> d.code().equals("RPT-103")).findFirst().orElseThrow();
        assertThat(def.columns()).extracting(c -> c.name())
                .containsExactly("tipo_usuario", "tipo_compensacao", "formula", "compensacoes", "valor_total");
        assertThat(def.viewName()).isEqualTo("rpt_compensacoes_usuario");
    }
}
