package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.3/ADR-0043 - gov.br: recusa para tenant privado e nivel por operacao. */
class GovBrPolicyTest {

    private static PortalParameters params(boolean enabled) {
        return PortalParameters.fromValues(Map.of(
                PortalParameters.IDP_GOVBR_ENABLED, Boolean.toString(enabled),
                PortalParameters.MIN_ASSURANCE_BY_OPERATION, "{}"));
    }

    @Test
    void tenantPrivadoComGovBrLigadoERecusado() {
        // Literais REAIS do dominio de reg.legal_nature (core V9): PRIVATE.
        assertThatThrownBy(() -> GovBrPolicy.assertGovBrConfigurationAllowed("PRIVATE", params(true)))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> GovBrPolicy.assertGovBrConfigurationAllowed(null, params(true)))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> GovBrPolicy.assertGovBrConfigurationAllowed("DESCONHECIDA", params(true)))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void tenantPublicoComGovBrLigadoPassaEPrivadoDesligadoPassa() {
        // Todo o dominio publico real (core V9 / P3.19): AUTARCHY, PUBLIC_COMPANY, SEM.
        for (String nature : new String[]{"AUTARCHY", "PUBLIC_COMPANY", "SEM"}) {
            assertThatCode(() -> GovBrPolicy.assertGovBrConfigurationAllowed(nature, params(true)))
                    .as(nature).doesNotThrowAnyException();
            assertThat(GovBrPolicy.isPublicLaw(nature)).as(nature).isTrue();
        }
        assertThat(GovBrPolicy.isPublicLaw("PRIVATE")).isFalse();
        assertThatCode(() -> GovBrPolicy.assertGovBrConfigurationAllowed("PRIVATE", params(false)))
                .doesNotThrowAnyException();
    }

    @Test
    void operacoesSensiveisExigemPrataOuOuroQuandoAEntradaEGovBr() {
        var p = params(true);
        for (var op : new PortalParameters.SensitiveOperation[]{
                PortalParameters.SensitiveOperation.HOLDER_CHANGE,
                PortalParameters.SensitiveOperation.SOCIAL_TARIFF,
                PortalParameters.SensitiveOperation.CONTACT_CHANGE,
                PortalParameters.SensitiveOperation.DEBT_NEGOTIATION}) {
            assertThatThrownBy(() -> GovBrPolicy.requireAssurance(op, GovBrPolicy.EntryChannel.GOVBR,
                    PortalParameters.AssuranceLevel.BRONZE, p))
                    .as(op.name()).isInstanceOf(ApiException.Forbidden.class);
            assertThatThrownBy(() -> GovBrPolicy.requireAssurance(op, GovBrPolicy.EntryChannel.GOVBR, null, p))
                    .as(op.name()).isInstanceOf(ApiException.Forbidden.class);
            assertThatCode(() -> GovBrPolicy.requireAssurance(op, GovBrPolicy.EntryChannel.GOVBR,
                    PortalParameters.AssuranceLevel.SILVER, p)).as(op.name()).doesNotThrowAnyException();
            assertThatCode(() -> GovBrPolicy.requireAssurance(op, GovBrPolicy.EntryChannel.GOVBR,
                    PortalParameters.AssuranceLevel.GOLD, p)).as(op.name()).doesNotThrowAnyException();
        }
    }

    @Test
    void entradaLocalNaoPassaPelaTravaDoGovBr() {
        assertThatCode(() -> GovBrPolicy.requireAssurance(
                PortalParameters.SensitiveOperation.HOLDER_CHANGE, GovBrPolicy.EntryChannel.LOCAL,
                PortalParameters.AssuranceLevel.BRONZE, params(true))).doesNotThrowAnyException();
    }

    @Test
    void nivelLidoDoClaim() {
        assertThat(GovBrPolicy.levelFromClaim("urn:govbr:gold")).isEqualTo(PortalParameters.AssuranceLevel.GOLD);
        assertThat(GovBrPolicy.levelFromClaim("prata")).isEqualTo(PortalParameters.AssuranceLevel.SILVER);
        assertThat(GovBrPolicy.levelFromClaim("bronze")).isEqualTo(PortalParameters.AssuranceLevel.BRONZE);
        assertThat(GovBrPolicy.levelFromClaim("desconhecido")).isNull();
    }
}
