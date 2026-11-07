package br.com.ziipsan.integration.sinisa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Certificado de Adimplencia (P4.2): registro exige data e tipo de servico —
 * a certidao oficial e por tipo de servico e comprova a adimplencia que
 * condiciona o acesso a recursos federais (Lei 11.445/2007, art. 50;
 * Decreto 11.599/2023, art. 7o, V).
 */
class ComplianceCertificateTest {

    @Test
    @DisplayName("certificado com data e servico e registrado")
    void certificadoComData() {
        ComplianceCertificate cert = new ComplianceCertificate(
                2026, "AGUA", LocalDate.of(2026, 12, 20), "Certidao 123/2026");

        assertThat(cert.baseYear()).isEqualTo(2026);
        assertThat(cert.issuedOn()).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(cert.serviceKind()).isEqualTo("AGUA");
    }

    @Test
    @DisplayName("certificado sem data e recusado")
    void certificadoSemDataRecusado() {
        assertThatThrownBy(() -> new ComplianceCertificate(2026, "AGUA", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("data");
    }

    @Test
    @DisplayName("certificado sem tipo de servico e recusado (a certidao e por tipo de servico)")
    void certificadoSemServicoRecusado() {
        assertThatThrownBy(() -> new ComplianceCertificate(2026, " ", LocalDate.of(2026, 12, 20), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo de servico");
    }
}
