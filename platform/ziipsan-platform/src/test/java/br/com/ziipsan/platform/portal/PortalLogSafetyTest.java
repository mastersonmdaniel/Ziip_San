package br.com.ziipsan.platform.portal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.3 - RNF-52: documento fora de URL, login_hint e logs (teste de log). */
class PortalLogSafetyTest {

    @Test
    void detectaDocumentoFormatadoESemFormatacao() {
        assertThat(PortalLogSafety.containsDocument("login de 529.982.247-25")).isTrue();
        assertThat(PortalLogSafety.containsDocument("login de 52998224725")).isTrue();
        assertThat(PortalLogSafety.containsDocument("protocolo 2026-000123")).isFalse();
        assertThat(PortalLogSafety.containsDocument(null)).isFalse();
    }

    @Test
    void urlComDocumentoOuLoginHintComDocumentoERecusada() {
        assertThatThrownBy(() -> PortalLogSafety.assertUrlHasNoDocument(
                "https://portal.example/login?login_hint=52998224725"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortalLogSafety.assertUrlHasNoDocument(
                "https://portal.example/529.982.247-25"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void documentoComDigitoErradoECnpjAlfanumericoTambemSaoDetectados() {
        // CPF com DV errado continua sendo o documento digitado no login (RNF-52).
        assertThat(PortalLogSafety.containsDocument("tentativa com 52998224726")).isTrue();
        assertThat(PortalLogSafety.redact("tentativa com 52998224726")).doesNotContain("52998224726");
        // CNPJ alfanumerico (IN RFB 2.229/2024), pontuado e corrido.
        assertThat(PortalLogSafety.containsDocument("empresa 12.ABC.345/01DE-35")).isTrue();
        assertThat(PortalLogSafety.containsDocument("empresa 12ABC34501DE35")).isTrue();
        assertThat(PortalLogSafety.redact("empresa 12ABC34501DE35")).doesNotContain("12ABC34501DE35");
        assertThatThrownBy(() -> PortalLogSafety.assertUrlHasNoDocument(
                "https://portal.example/login?login_hint=52998224726"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void redacaoRemoveDocumentoDaLinhaDeLog() {
        String redacted = PortalLogSafety.redact("tentativa de 529.982.247-25 e de 52998224725");
        assertThat(redacted).doesNotContain("529");
        assertThat(redacted).contains("[documento redigido]");
    }
}
