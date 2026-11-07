package br.com.ziipsan.commercial.billing.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Nome impresso da conta (decisao do dono de 28/10/2026, item 6): as duas opcoes
 * do parametro bill.holder_name_display, com USER_HOLDER_ONLY de padrao de fabrica.
 */
class HolderNameDisplayTest {

    @Test
    @DisplayName("padrao de fabrica: parametro ausente ou em branco vale USER_HOLDER_ONLY")
    void factoryDefaultIsUserHolderOnly() {
        assertThat(HolderNameDisplay.FACTORY_DEFAULT).isEqualTo(HolderNameDisplay.USER_HOLDER_ONLY);
        assertThat(HolderNameDisplay.fromParameterValue(null)).isEqualTo(HolderNameDisplay.USER_HOLDER_ONLY);
        assertThat(HolderNameDisplay.fromParameterValue("  ")).isEqualTo(HolderNameDisplay.USER_HOLDER_ONLY);
        assertThat(HolderNameDisplay.fromParameterValue("USER_HOLDER_ONLY"))
                .isEqualTo(HolderNameDisplay.USER_HOLDER_ONLY);
        assertThat(HolderNameDisplay.fromParameterValue("USER_HOLDER_AND_OWNER"))
                .isEqualTo(HolderNameDisplay.USER_HOLDER_AND_OWNER);
    }

    @Test
    @DisplayName("valor vigente como JSON (entre aspas, como circula em parameter_versions) e interpretado")
    void jsonQuotedValueIsInterpreted() {
        assertThat(HolderNameDisplay.fromParameterValue("\"USER_HOLDER_AND_OWNER\""))
                .isEqualTo(HolderNameDisplay.USER_HOLDER_AND_OWNER);
        assertThat(HolderNameDisplay.fromParameterValue("\"USER_HOLDER_ONLY\""))
                .isEqualTo(HolderNameDisplay.USER_HOLDER_ONLY);
        assertThatThrownBy(() -> HolderNameDisplay.fromParameterValue("\"OWNER_ONLY\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.holder_name_display");
    }

    @Test
    @DisplayName("valor desconhecido do parametro e recusado, nunca interpretado por aproximacao")
    void unknownValueIsRefused() {
        assertThatThrownBy(() -> HolderNameDisplay.fromParameterValue("OWNER_ONLY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.holder_name_display");
    }

    @Test
    @DisplayName("USER_HOLDER_ONLY: so o usuario titular, mesmo havendo proprietario")
    void userHolderOnlyPrintsOnlyTheHolder() {
        assertThat(HolderNameDisplay.USER_HOLDER_ONLY.printedName("Maria da Silva", "Joao Souza"))
                .isEqualTo("Maria da Silva");
    }

    @Test
    @DisplayName("USER_HOLDER_AND_OWNER: titular e proprietario")
    void userHolderAndOwnerPrintsBoth() {
        assertThat(HolderNameDisplay.USER_HOLDER_AND_OWNER.printedName("Maria da Silva", "Joao Souza"))
                .isEqualTo("Maria da Silva / Joao Souza");
    }

    @Test
    @DisplayName("USER_HOLDER_AND_OWNER sem proprietario (ou igual ao titular): so o titular, sem separador pendurado")
    void userHolderAndOwnerWithoutOwnerPrintsOnlyTheHolder() {
        assertThat(HolderNameDisplay.USER_HOLDER_AND_OWNER.printedName("Maria da Silva", null))
                .isEqualTo("Maria da Silva");
        assertThat(HolderNameDisplay.USER_HOLDER_AND_OWNER.printedName("Maria da Silva", "  "))
                .isEqualTo("Maria da Silva");
        assertThat(HolderNameDisplay.USER_HOLDER_AND_OWNER.printedName("Maria da Silva", "Maria da Silva"))
                .isEqualTo("Maria da Silva");
    }

    @Test
    @DisplayName("sem nome de titular nao ha nome impresso em nenhuma opcao")
    void missingHolderNameIsRefused() {
        assertThatThrownBy(() -> HolderNameDisplay.USER_HOLDER_ONLY.printedName(null, "Joao Souza"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HolderNameDisplay.USER_HOLDER_AND_OWNER.printedName(" ", "Joao Souza"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
