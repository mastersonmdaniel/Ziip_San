package br.com.ziipsan.commercial.billing.servicecatalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredDocumentAndSignatureTest {

    @Test @DisplayName("firma reconhecida e copia autenticada sao recusadas (Lei 13.726/2018)")
    void notarizedForbidden() {
        assertThatThrownBy(() -> new RequiredDocument("DOC", "Doc", null, true, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequiredDocument("DOC", "Doc", null, false, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("validade e estado de verificacao do documento apresentado")
    void presentedValidity() {
        var req = new RequiredDocument("CERT", "Certidao", 12, false, false);
        var ok = new RequiredDocument.Presented(req, RequiredDocument.VerificationState.VERIFIED, LocalDate.of(2026, 1, 10));
        assertThat(ok.validOn(LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(ok.validOn(LocalDate.of(2027, 2, 1))).isFalse();
        assertThat(ok.validOn(LocalDate.of(2025, 6, 1))).isFalse(); // antes da emissao (D1)
        assertThat(ok.validOn(LocalDate.of(2026, 1, 10))).isTrue(); // no dia da emissao
        var pending = new RequiredDocument.Presented(req, RequiredDocument.VerificationState.PENDING, null);
        assertThat(pending.validOn(LocalDate.of(2026, 6, 1))).isFalse();
    }

    @Test @DisplayName("politica de assinatura (Lei 14.063) e codigo verificador conferivel/adulteracao detectada")
    void signaturePolicyAndCode() {
        var p = new SignaturePolicy(Map.of("CERTIDAO_QUITACAO", SignaturePolicy.Level.ADVANCED,
                "DECLARACAO", SignaturePolicy.Level.SIMPLE));
        assertThat(p.levelFor("CERTIDAO_QUITACAO")).isEqualTo(SignaturePolicy.Level.ADVANCED);
        assertThatThrownBy(() -> p.levelFor("DESCONHECIDO")).isInstanceOf(IllegalStateException.class);
        String code = SignaturePolicy.verificationCode("CERTIDAO_QUITACAO", "2026-0001", "2026-03-10");
        assertThat(SignaturePolicy.verifies("CERTIDAO_QUITACAO", "2026-0001", "2026-03-10", code)).isTrue();
        assertThat(SignaturePolicy.verifies("CERTIDAO_QUITACAO", "2026-0002", "2026-03-10", code)).isFalse();
    }
}
