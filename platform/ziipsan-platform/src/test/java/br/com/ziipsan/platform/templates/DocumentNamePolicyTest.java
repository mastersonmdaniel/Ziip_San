package br.com.ziipsan.platform.templates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Nome social × civil nos documentos (P2.17, CAD-67/P1.41): a regra é a
 * do BillHolderResolver do cadastro estendida aos contextos de documento
 * — social em tudo, civil só onde a lei exige (fiscal e termos).
 */
class DocumentNamePolicyTest {

    private static final DocumentNamePolicy.Mode DEFAULT =
            DocumentNamePolicy.Mode.SOCIAL_EVERYWHERE_EXCEPT_FISCAL;

    @Test
    @DisplayName("PF com nome social: documento comum imprime o nome social")
    void socialNameOnOrdinaryDocument() {
        assertThat(DocumentNamePolicy.printedName("PF", "Joao Civil", "Joao Social",
                DEFAULT, DocumentNamePolicy.Context.ORDINARY)).isEqualTo("Joao Social");
    }

    @Test
    @DisplayName("PF com nome social: documento fiscal e termo imprimem o nome CIVIL (só onde a lei exige)")
    void civilNameOnlyWhereLawRequires() {
        assertThat(DocumentNamePolicy.printedName("PF", "Joao Civil", "Joao Social",
                DEFAULT, DocumentNamePolicy.Context.FISCAL_DOCUMENT)).isEqualTo("Joao Civil");
        assertThat(DocumentNamePolicy.printedName("PF", "Joao Civil", "Joao Social",
                DEFAULT, DocumentNamePolicy.Context.TERM)).isEqualTo("Joao Civil");
        assertThat(DocumentNamePolicy.civilNameLegallyRequired(
                DocumentNamePolicy.Context.FISCAL_DOCUMENT)).isTrue();
        assertThat(DocumentNamePolicy.civilNameLegallyRequired(
                DocumentNamePolicy.Context.TERM)).isTrue();
        assertThat(DocumentNamePolicy.civilNameLegallyRequired(
                DocumentNamePolicy.Context.ORDINARY)).isFalse();
    }

    @Test
    @DisplayName("modo CIVIL_NAME_ONLY do tenant: civil em todo documento, inclusive o comum")
    void civilNameOnlyMode() {
        assertThat(DocumentNamePolicy.printedName("PF", "Joao Civil", "Joao Social",
                DocumentNamePolicy.Mode.CIVIL_NAME_ONLY,
                DocumentNamePolicy.Context.ORDINARY)).isEqualTo("Joao Civil");
    }

    @Test
    @DisplayName("PJ não tem nome social nesta regra e PF sem social cadastrado imprime o civil")
    void noSocialNameFallsBackToCivil() {
        assertThat(DocumentNamePolicy.printedName("PJ", "Prestador SA", "Nome Social Ignorado",
                DEFAULT, DocumentNamePolicy.Context.ORDINARY)).isEqualTo("Prestador SA");
        assertThat(DocumentNamePolicy.printedName("PF", "Joao Civil", null,
                DEFAULT, DocumentNamePolicy.Context.ORDINARY)).isEqualTo("Joao Civil");
        assertThat(DocumentNamePolicy.printedName("PF", "Joao Civil", "  ",
                DEFAULT, DocumentNamePolicy.Context.ORDINARY)).isEqualTo("Joao Civil");
    }

    @Test
    @DisplayName("parâmetro: ausente/em branco = padrão de fábrica; JSON entre aspas é lido; desconhecido recusa")
    void parameterParsing() {
        assertThat(DocumentNamePolicy.modeFromParameterValue(null))
                .isEqualTo(DocumentNamePolicy.Mode.FACTORY_DEFAULT);
        assertThat(DocumentNamePolicy.modeFromParameterValue("  "))
                .isEqualTo(DocumentNamePolicy.Mode.FACTORY_DEFAULT);
        assertThat(DocumentNamePolicy.Mode.FACTORY_DEFAULT)
                .isEqualTo(DocumentNamePolicy.Mode.SOCIAL_EVERYWHERE_EXCEPT_FISCAL);
        assertThat(DocumentNamePolicy.modeFromParameterValue("\"CIVIL_NAME_ONLY\""))
                .isEqualTo(DocumentNamePolicy.Mode.CIVIL_NAME_ONLY);
        assertThat(DocumentNamePolicy.modeFromParameterValue("SOCIAL_EVERYWHERE_EXCEPT_FISCAL"))
                .isEqualTo(DocumentNamePolicy.Mode.SOCIAL_EVERYWHERE_EXCEPT_FISCAL);
        assertThatThrownBy(() -> DocumentNamePolicy.modeFromParameterValue("MODO_INVENTADO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registry.social_name_display");
    }

    @Test
    @DisplayName("sem nome civil cadastrado não há documento: erro explícito")
    void missingCivilNameIsRefused() {
        assertThatThrownBy(() -> DocumentNamePolicy.printedName("PF", null, "Social",
                DEFAULT, DocumentNamePolicy.Context.ORDINARY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
