package br.com.ziipsan.commercial.billing.parameters;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Leitura do {@code tax.profile} (P2.34, classe b, aprovação JUR): objeto
 * com {@code cashback_enabled}, {@code tax_event_date_rule} e
 * {@code split_payment_mode} — ausência ou valor fora do catálogo é erro
 * explícito, nunca perfil presumido. O catálogo de split é o da P3.1.
 */
class TaxProfileTest {

    @Test
    @DisplayName("perfil completo é lido com os três subcampos")
    void completeProfile() {
        TaxProfile p = TaxProfile.fromValues(Map.of("tax.profile",
                "{\"cashback_enabled\": true, \"tax_event_date_rule\": \"CONTINUOUS_OPERATION\", "
                        + "\"split_payment_mode\": \"SEGREGATED_AT_SETTLEMENT\"}"));
        assertThat(p.cashbackEnabled()).isTrue();
        assertThat(p.taxEventDateRule()).isEqualTo(TaxProfile.TaxEventDateRule.CONTINUOUS_OPERATION);
        assertThat(p.splitPaymentMode()).isEqualTo(TaxProfile.SplitPaymentMode.SEGREGATED_AT_SETTLEMENT);
    }

    @Test
    @DisplayName("os três modos de split da P3.1 são aceitos; a conciliação continua sendo dela")
    void splitModesCatalog() {
        for (String modo : new String[]{"SEGREGATED_AT_SETTLEMENT", "SIMPLIFIED_SEGREGATION", "NO_SEGREGATION"}) {
            TaxProfile p = TaxProfile.fromValues(Map.of("tax.profile",
                    "{\"cashback_enabled\": false, \"tax_event_date_rule\": \"CONTINUOUS_OPERATION\", "
                            + "\"split_payment_mode\": \"" + modo + "\"}"));
            assertThat(p.splitPaymentMode().name()).isEqualTo(modo);
        }
    }

    @Test
    @DisplayName("perfil ausente é MissingParameterException (erro explícito)")
    void missingProfileIsAnError() {
        assertThatThrownBy(() -> TaxProfile.fromValues(Map.of()))
                .isInstanceOf(MissingParameterException.class);
    }

    @Test
    @DisplayName("subcampo ausente ou fora do catálogo é erro explícito")
    void missingOrUnknownSubfieldIsAnError() {
        assertThatThrownBy(() -> TaxProfile.fromValues(Map.of("tax.profile",
                "{\"cashback_enabled\": true, \"tax_event_date_rule\": \"CONTINUOUS_OPERATION\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaxProfile.fromValues(Map.of("tax.profile",
                "{\"cashback_enabled\": true, \"tax_event_date_rule\": \"CONTINUOUS_OPERATION\", "
                        + "\"split_payment_mode\": \"MODO_INVENTADO\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaxProfile.fromValues(Map.of("tax.profile",
                "{\"cashback_enabled\": true, \"tax_event_date_rule\": \"REGRA_INVENTADA\", "
                        + "\"split_payment_mode\": \"NO_SEGREGATION\"}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
