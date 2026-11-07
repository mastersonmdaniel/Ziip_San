package br.com.ziipsan.platform.portal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.3 - VLibras so depois da consulta formal aos termos; declaracao publicada. */
class PortalAccessibilityTest {

    private static PortalParameters params(boolean vlibras) {
        return PortalParameters.fromValues(Map.of(
                PortalParameters.VLIBRAS_ENABLED, Boolean.toString(vlibras),
                PortalParameters.ACCESSIBILITY_STATEMENT,
                "{\"contactChannel\":\"canal@tenant.example\",\"evaluationDate\":\"2026-09-15\",\"text\":\"Texto\"}"));
    }

    @Test
    void vlibrasSemConsultaFormalERecusado() {
        assertThatThrownBy(() -> PortalAccessibility.assertVlibrasAllowed(params(true), false))
                .isInstanceOf(br.com.ziipsan.core.api.ApiException.Forbidden.class);
        PortalAccessibility.assertVlibrasAllowed(params(true), true);
        assertThat(PortalAccessibility.vlibrasAllowed(params(true), true)).isTrue();
        assertThat(PortalAccessibility.vlibrasAllowed(params(false), true)).isFalse();
    }

    @Test
    void declaracaoPublicadaTemContatoEData() {
        var st = PortalAccessibility.publishedStatement(params(false));
        assertThat(st.contactChannel()).isEqualTo("canal@tenant.example");
        assertThat(st.evaluationDate()).isEqualTo(java.time.LocalDate.of(2026, 9, 15));
    }
}
