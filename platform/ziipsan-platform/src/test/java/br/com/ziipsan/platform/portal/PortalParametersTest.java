package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.3 - parametros portal.*: ausencia = erro; classes b/c/d conferidas. */
class PortalParametersTest {

    private static final PortalParameters.AccountThrottle FACTORY =
            new PortalParameters.AccountThrottle(3, 5, 20, 30, 10, 15);

    private static Map<String, String> full() {
        return Map.of(
                PortalParameters.GUEST_PAYMENT, "\"CODE_ONLY\"",
                PortalParameters.IDP_GOVBR_ENABLED, "false",
                PortalParameters.MIN_ASSURANCE_BY_OPERATION,
                "{\"HOLDER_CHANGE\":\"GOLD\",\"CONTACT_CHANGE\":\"SILVER\"}",
                PortalParameters.BOT_DEFENSE, "\"CHALLENGE\"",
                PortalParameters.ACCOUNT_THROTTLE,
                "{\"maxAccountsPerEmail\":2,\"maxLoginFailuresPerIdentifier\":5,\"maxLoginFailuresPerIp\":10,"
                        + "\"maxGuestQueriesPerOrigin\":20,\"maxGuestQueriesPerCode\":5,\"windowMinutes\":15}",
                PortalParameters.STATIC_FALLBACK_ENABLED, "true",
                PortalParameters.VLIBRAS_ENABLED, "false",
                PortalParameters.ACCESSIBILITY_STATEMENT,
                "{\"contactChannel\":\"acessibilidade@tenant.example\",\"evaluationDate\":\"2026-10-01\",\"text\":\"Declaracao do tenant\"}");
    }

    @Test
    void leTodosOsParametros() {
        var p = PortalParameters.fromValues(full());
        assertThat(p.guestPaymentMode()).isEqualTo(PortalParameters.GuestPaymentMode.CODE_ONLY);
        assertThat(p.idpGovbrEnabled()).isFalse();
        assertThat(p.botDefense()).isEqualTo(PortalParameters.BotDefenseMode.CHALLENGE);
        assertThat(p.staticFallbackEnabled()).isTrue();
        assertThat(p.vlibrasEnabled()).isFalse();
    }

    @Test
    void ausenciaDeParametroEErroExplicito() {
        var p = PortalParameters.fromValues(Map.of());
        assertThatThrownBy(p::guestPaymentMode).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(p::botDefense).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(p::accessibilityStatement).isInstanceOf(MissingParameterException.class);
    }

    @Test
    void nivelMinimoUsaValorDoTenantEFabricaPrataNasSensiveis() {
        var p = PortalParameters.fromValues(full());
        assertThat(p.minAssurance(PortalParameters.SensitiveOperation.HOLDER_CHANGE))
                .isEqualTo(PortalParameters.AssuranceLevel.GOLD);
        assertThat(p.minAssurance(PortalParameters.SensitiveOperation.SOCIAL_TARIFF))
                .isEqualTo(PortalParameters.AssuranceLevel.SILVER);
        assertThat(p.minAssurance(PortalParameters.SensitiveOperation.DEBT_NEGOTIATION))
                .isEqualTo(PortalParameters.AssuranceLevel.SILVER);
        assertThat(p.minAssurance(PortalParameters.SensitiveOperation.SECOND_COPY))
                .isEqualTo(PortalParameters.AssuranceLevel.BRONZE);
    }

    @Test
    void throttleClasseCSoReduz() {
        var p = PortalParameters.fromValues(full());
        var throttle = p.accountThrottle(FACTORY);
        assertThat(throttle.maxAccountsPerEmail()).isEqualTo(2);
        assertThat(throttle.maxLoginFailuresPerIp()).isEqualTo(10);
    }

    @Test
    void nivelMinimoDasSensiveisNaoDesceAbaixoDePrata() {
        var values = new java.util.HashMap<>(full());
        values.put(PortalParameters.MIN_ASSURANCE_BY_OPERATION, "{\"HOLDER_CHANGE\":\"BRONZE\"}");
        var p = PortalParameters.fromValues(values);
        assertThat(p.minAssurance(PortalParameters.SensitiveOperation.HOLDER_CHANGE))
                .isEqualTo(PortalParameters.AssuranceLevel.SILVER);
    }

    @Test
    void throttleJanelaMenorQueFabricaERecusadoEJanelaMaiorPassa() {
        var smaller = new java.util.HashMap<>(full());
        smaller.put(PortalParameters.ACCOUNT_THROTTLE,
                "{\"maxAccountsPerEmail\":2,\"maxLoginFailuresPerIdentifier\":5,\"maxLoginFailuresPerIp\":10,"
                        + "\"maxGuestQueriesPerOrigin\":20,\"maxGuestQueriesPerCode\":5,\"windowMinutes\":5}");
        assertThatThrownBy(() -> PortalParameters.fromValues(smaller).accountThrottle(FACTORY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("so reduz");
        var bigger = new java.util.HashMap<>(full());
        bigger.put(PortalParameters.ACCOUNT_THROTTLE,
                "{\"maxAccountsPerEmail\":2,\"maxLoginFailuresPerIdentifier\":5,\"maxLoginFailuresPerIp\":10,"
                        + "\"maxGuestQueriesPerOrigin\":20,\"maxGuestQueriesPerCode\":5,\"windowMinutes\":30}");
        assertThat(PortalParameters.fromValues(bigger).accountThrottle(FACTORY).windowMinutes()).isEqualTo(30);
    }

    @Test
    void throttleAcimaDaFabricaERecusado() {
        var values = new java.util.HashMap<>(full());
        values.put(PortalParameters.ACCOUNT_THROTTLE,
                "{\"maxAccountsPerEmail\":99,\"maxLoginFailuresPerIdentifier\":5,\"maxLoginFailuresPerIp\":10,"
                        + "\"maxGuestQueriesPerOrigin\":20,\"maxGuestQueriesPerCode\":5,\"windowMinutes\":15}");
        var p = PortalParameters.fromValues(values);
        assertThatThrownBy(() -> p.accountThrottle(FACTORY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("so reduz");
    }

    @Test
    void declaracaoDeAcessibilidadeExigeContatoEData() {
        var p = PortalParameters.fromValues(full());
        var st = p.accessibilityStatement();
        assertThat(st.contactChannel()).isEqualTo("acessibilidade@tenant.example");
        assertThat(st.evaluationDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 1));
        assertThatThrownBy(() -> new PortalParameters.AccessibilityStatement(" ", null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modoDeConvidadoDesconhecidoERecusado() {
        var p = PortalParameters.fromValues(Map.of(PortalParameters.GUEST_PAYMENT, "\"MATRICULA\""));
        assertThatThrownBy(p::guestPaymentMode).isInstanceOf(IllegalStateException.class);
    }
}
