package br.com.ziipsan.platform.templates;

import br.com.ziipsan.platform.templates.TemplateBlock.Kind;
import br.com.ziipsan.platform.templates.TemplateBlock.Origin;
import br.com.ziipsan.platform.templates.TemplateEngine.DeclaredVariable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.30: o motor puro — variável inexistente ou de tipo incompatível recusa
 * o modelo; bloco travado não muda nem some na versão seguinte; geração
 * exige valor de tipo compatível para toda referência; documento gerado não
 * depende de re-renderização (o instantâneo é testado na IT do serviço).
 */
class TemplateEngineTest {

    private static final TemplateDocumentType CUT = TemplateDocumentType.CUT_NOTICE;

    private static TemplateBlock free(String id, String text) {
        return new TemplateBlock(id, Kind.FREE, Origin.TENANT, text);
    }

    private static TemplateBlock locked(String id, String text) {
        return new TemplateBlock(id, Kind.LOCKED, Origin.TENANT, text);
    }

    @Test
    @DisplayName("P2.30: variável inexistente no corpo recusa o modelo na validação")
    void unknownVariableInBodyIsRejected() {
        List<String> problems = TemplateEngine.validationProblems(CUT,
                List.of(free("b1", "Valor: {{amount}} e {{inexistente}}.")),
                List.of(new DeclaredVariable("amount", TemplateVariableType.MONEY)));
        assertThat(problems).anyMatch(p -> p.contains("inexistente"));
    }

    @Test
    @DisplayName("P2.30: variável declarada com tipo diferente do catálogo recusa o modelo")
    void declaredVariableWithWrongTypeIsRejected() {
        List<String> problems = TemplateEngine.validationProblems(CUT,
                List.of(free("b1", "Valor: {{amount}}.")),
                List.of(new DeclaredVariable("amount", TemplateVariableType.DATE)));
        assertThat(problems).anyMatch(p -> p.contains("amount") && p.contains("MONEY"));
    }

    @Test
    @DisplayName("P2.30: variável declarada fora do catálogo recusa o modelo")
    void declaredVariableOutsideCatalogIsRejected() {
        List<String> problems = TemplateEngine.validationProblems(CUT,
                List.of(free("b1", "Sem variáveis.")),
                List.of(new DeclaredVariable("fantasia", TemplateVariableType.TEXT)));
        assertThat(problems).anyMatch(p -> p.contains("fantasia"));
    }

    @Test
    @DisplayName("P2.30: modelo válido (variáveis do catálogo, tipos certos) não tem problema")
    void validModelHasNoProblems() {
        List<String> problems = TemplateEngine.validationProblems(CUT,
                List.of(free("b1", "Titular {{customer_name}}, valor R$ {{amount}}.")),
                List.of(new DeclaredVariable("customer_name", TemplateVariableType.TEXT),
                        new DeclaredVariable("amount", TemplateVariableType.MONEY)));
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("P2.30: bloco com origem SYSTEM é recusado (conteúdo legal é da P2.32)")
    void systemBlockIsRejected() {
        List<String> problems = TemplateEngine.validationProblems(CUT,
                List.of(new TemplateBlock("legal", Kind.LOCKED, Origin.SYSTEM, "Texto legal futuro.")),
                List.of());
        assertThat(problems).anyMatch(p -> p.contains("SYSTEM"));
    }

    @Test
    @DisplayName("P2.30: bloco travado alterado na versão nova é recusado")
    void lockedBlockChangedIsRejected() {
        List<String> problems = TemplateEngine.lockedBlockProblems(
                List.of(locked("legal", "Texto travado."), free("livre", "Antes.")),
                List.of(locked("legal", "Texto ALTERADO."), free("livre", "Depois.")));
        assertThat(problems).anyMatch(p -> p.contains("legal") && p.contains("alterado"));
    }

    @Test
    @DisplayName("P2.30: bloco travado removido na versão nova é recusado")
    void lockedBlockRemovedIsRejected() {
        List<String> problems = TemplateEngine.lockedBlockProblems(
                List.of(locked("legal", "Texto travado.")),
                List.of(free("livre", "Só o livre.")));
        assertThat(problems).anyMatch(p -> p.contains("legal") && p.contains("removido"));
    }

    @Test
    @DisplayName("P2.30: bloco travado preservado byte a byte passa, com livre alterado")
    void lockedBlockPreservedPasses() {
        List<String> problems = TemplateEngine.lockedBlockProblems(
                List.of(locked("legal", "Texto travado."), free("livre", "Antes.")),
                List.of(locked("legal", "Texto travado."), free("livre", "Depois.")));
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("P2.30: geração formata MONEY com 2 casas e recusa valor incompatível")
    void renderFormatsMoneyAndRejectsIncompatibleValue() {
        List<TemplateBlock> blocks = List.of(free("b1", "Valor R$ {{amount}} em {{due_date}}."));
        String rendered = TemplateEngine.render(CUT, blocks,
                Map.of("amount", "123.4", "due_date", "2026-12-10"), null, UUID.randomUUID());
        assertThat(rendered).isEqualTo("Valor R$ 123.40 em 2026-12-10.");
        assertThatThrownBy(() -> TemplateEngine.render(CUT, blocks,
                Map.of("amount", "não-numérico", "due_date", "2026-12-10"), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("P2.30: geração sem valor para referência é recusada, não renderiza buraco")
    void renderWithoutValueIsRejected() {
        assertThatThrownBy(() -> TemplateEngine.render(CUT,
                List.of(free("b1", "Titular {{customer_name}}.")),
                Map.of(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customer_name");
    }

    @Test
    @DisplayName("P2.30: prévia usa massa sintética e nunca dado real")
    void previewUsesSyntheticData() {
        String preview = TemplateEngine.preview(CUT,
                List.of(free("b1", "Titular {{customer_name}}, valor R$ {{amount}}.")));
        assertThat(preview).contains("NOME EXEMPLO").contains("123.45");
    }

    @Test
    @DisplayName("P2.30: marca via gancho resolve {{brand.*}}; sem gancho, é recusada")
    void brandHookResolvesBrandReferences() {
        List<TemplateBlock> blocks = List.of(free("b1", "De {{brand.name}} para {{customer_name}}."));
        TemplateBrandProvider brand = tenantId -> Map.of("brand.name", "Concessionária Exemplo");
        String rendered = TemplateEngine.render(CUT, blocks,
                Map.of("customer_name", "NOME EXEMPLO"), brand, UUID.randomUUID());
        assertThat(rendered).isEqualTo("De Concessionária Exemplo para NOME EXEMPLO.");
        assertThatThrownBy(() -> TemplateEngine.render(CUT, blocks,
                Map.of("customer_name", "NOME EXEMPLO"), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brand.name");
    }
}
