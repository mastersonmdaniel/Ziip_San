package br.com.ziipsan.commercial.billing.parameters;

import br.com.ziipsan.commercial.billing.tariff.ReadjustFormula;
import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Leitura dos parâmetros do reajuste (P2.29): objeto ampliado
 * {@code bill.tariff_readjust} e as chaves novas, com as travas de leitura.
 * Os valores de índice/ato nestes testes são fictícios, só para exercitar a
 * leitura — índice e ato reais são da agência de cada prestador.
 */
class TariffReadjustParametersTest {

    private static final String READJUST_JSON = """
            {"baseDate": "2025-06-01", "indexCode": "IPCA", "indexPct": "6.25",
             "factorXPct": "1.25", "formula": "INDEX_MINUS_FACTOR_X",
             "regulatoryAct": "Res. ARESC 001/2026", "publishedOn": "2026-01-05",
             "applicationDate": "2026-02-10", "tacitApprovalAllowed": true}
            """;

    private static String withPublicationDays(int days) {
        return READJUST_JSON.replace("\"tacitApprovalAllowed\": true}",
                "\"tacitApprovalAllowed\": true, \"publicationMinDays\": " + days + "}");
    }

    @Test
    @DisplayName("bill.tariff_readjust ampliado: fórmula, índice, fator X, homologação tácita e prazo de publicação")
    void readjustObjectIsFullyRead() {
        TariffReadjustParameters params = TariffReadjustParameters.fromValues(
                Map.of(TariffReadjustParameters.TARIFF_READJUST, withPublicationDays(45)));

        TariffReadjustParameters.ReadjustSettings s = params.readjustSettings();

        assertThat(s.baseDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(s.indexCode()).isEqualTo("IPCA");
        assertThat(s.indexPct()).isEqualByComparingTo("6.25");
        assertThat(s.factorXPct()).isEqualByComparingTo("1.25");
        assertThat(s.formula()).isEqualTo(ReadjustFormula.INDEX_MINUS_FACTOR_X);
        assertThat(s.regulatoryAct()).isEqualTo("Res. ARESC 001/2026");
        assertThat(s.publishedOn()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(s.applicationDate()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(s.tacitApprovalAllowed()).isTrue();
        assertThat(s.publicationMinDays()).isEqualTo(45);
        // e a fórmula sobre os fatos lidos: 6,25 − 1,25 = 5,00
        assertThat(s.formula().appliedPct(s.indexPct(), s.factorXPct())).isEqualByComparingTo("5.0000");
    }

    @Test
    @DisplayName("publicationMinDays abaixo de 30 no parâmetro é RECUSADO na leitura (piso travado, o parâmetro não reduz o mínimo)")
    void publicationDaysBelowTheFloorAreRefused() {
        TariffReadjustParameters params = TariffReadjustParameters.fromValues(
                Map.of(TariffReadjustParameters.TARIFF_READJUST, withPublicationDays(10)));

        assertThatThrownBy(params::readjustSettings)
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("30");
    }

    @Test
    @DisplayName("publicationMinDays ausente no objeto vale o piso travado de 30 (o piso é da norma)")
    void missingPublicationDaysFallsToTheLockedFloor() {
        TariffReadjustParameters.ReadjustSettings s = TariffReadjustParameters.fromValues(
                Map.of(TariffReadjustParameters.TARIFF_READJUST, READJUST_JSON)).readjustSettings();

        assertThat(s.publicationMinDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("objeto ausente é erro explícito, nunca reajuste assumido")
    void missingObjectIsAnExplicitError() {
        TariffReadjustParameters params = TariffReadjustParameters.fromValues(Map.of());

        assertThatThrownBy(params::readjustSettings).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(params::readjustFormula).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(params::historyYearsPublic).isInstanceOf(MissingParameterException.class);
    }

    @Test
    @DisplayName("tariff.readjust_formula: só os valores do catálogo; tariff.tacit_approval_days: ausente = não configurado, 0 = recusado")
    void formulaAndTacitDays() {
        TariffReadjustParameters params = TariffReadjustParameters.fromValues(Map.of(
                TariffReadjustParameters.READJUST_FORMULA, "\"INDEX_ONLY\"",
                TariffReadjustParameters.TACIT_APPROVAL_DAYS, "15"));

        assertThat(params.readjustFormula()).isEqualTo(ReadjustFormula.INDEX_ONLY);
        assertThat(params.tacitApprovalDays()).hasValue(15);

        assertThat(TariffReadjustParameters.fromValues(Map.of()).tacitApprovalDays()).isEmpty();
        assertThatThrownBy(() -> TariffReadjustParameters.fromValues(Map.of(
                TariffReadjustParameters.TACIT_APPROVAL_DAYS, "0")).tacitApprovalDays())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TariffReadjustParameters.fromValues(Map.of(
                TariffReadjustParameters.READJUST_FORMULA, "\"CUSTOM_FORMULA\"")).readjustFormula())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catálogo");
    }

    @Test
    @DisplayName("tariff.history_years_public: 5 e acima valem; 4 é recusado (piso travado da NR 13)")
    void historyYearsFloor() {
        assertThat(TariffReadjustParameters.fromValues(Map.of(
                TariffReadjustParameters.HISTORY_YEARS_PUBLIC, "5")).historyYearsPublic()).isEqualTo(5);
        assertThat(TariffReadjustParameters.fromValues(Map.of(
                TariffReadjustParameters.HISTORY_YEARS_PUBLIC, "7")).historyYearsPublic()).isEqualTo(7);
        assertThatThrownBy(() -> TariffReadjustParameters.fromValues(Map.of(
                TariffReadjustParameters.HISTORY_YEARS_PUBLIC, "4")).historyYearsPublic())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5");
    }
}
