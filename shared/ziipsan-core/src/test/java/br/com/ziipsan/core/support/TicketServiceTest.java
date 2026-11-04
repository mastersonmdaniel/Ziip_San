package br.com.ziipsan.core.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes puros do chamado (P1.23): sanitização, detecção e máscara de dado
 * pessoal. Fluxos com banco vivem em SupportTicketIT.
 */
class TicketServiceTest {

    @Test
    @DisplayName("corpo sanitizado: tags HTML somem, texto fica")
    void sanitizeStripsTags() {
        assertThat(TicketService.sanitize("<p>Portal <b>fora</b> do ar</p>"))
                .isEqualTo("Portal fora do ar");
        assertThat(TicketService.sanitize("  texto  ")).isEqualTo("texto");
        assertThat(TicketService.sanitize(null)).isNull();
    }

    @Test
    @DisplayName("dado pessoal detectado: CPF, CNPJ, e-mail e telefone marcam o chamado")
    void personalDataDetected() {
        assertThat(TicketService.containsPersonalData("meu CPF é 123.456.789-09")).isTrue();
        assertThat(TicketService.containsPersonalData("CNPJ 12.345.678/0001-99")).isTrue();
        assertThat(TicketService.containsPersonalData("fale com fulano@teste.com.br")).isTrue();
        assertThat(TicketService.containsPersonalData("ligar para (11) 99999-9999")).isTrue();
        assertThat(TicketService.containsPersonalData("o portal não abre")).isFalse();
        assertThat(TicketService.containsPersonalData(null)).isFalse();
    }

    @Test
    @DisplayName("máscara de lista: dado pessoal some, resto do texto fica")
    void maskHidesPersonalData() {
        assertThat(TicketService.mask("falha para fulano@teste.com.br na ligação"))
                .isEqualTo("falha para ***@*** na ligação");
        assertThat(TicketService.mask("CPF 123.456.789-09")).isEqualTo("CPF ***.***.***-**");
        assertThat(TicketService.mask("sem dado")).isEqualTo("sem dado");
    }

    @Test
    @DisplayName("vocabulário da v0: categorias, severidades e estados da P1.23")
    void vocabulary() {
        assertThat(TicketService.CATEGORIES)
                .containsExactlyInAnyOrder("QUESTION", "INCIDENT", "REQUEST", "IMPROVEMENT", "SECURITY", "PRIVACY");
        assertThat(TicketService.SEVERITIES).containsExactlyInAnyOrder("S1", "S2", "S3", "S4", "S5");
        assertThat(TicketService.STATUSES).containsExactlyInAnyOrder(
                "NEW", "IN_PROGRESS", "WAITING_CUSTOMER", "RESOLVED", "CLOSED", "CANCELLED");
        assertThat(TicketService.ALLOWED_MIMES)
                .containsExactlyInAnyOrder("image/png", "image/jpeg", "image/webp", "application/pdf",
                        "text/plain", "text/csv", "application/xml");
        assertThat(TicketService.MAX_ATTACHMENTS_PER_MESSAGE).isEqualTo(5);
        assertThat(TicketService.MAX_ATTACHMENT_BYTES).isEqualTo(10L * 1024 * 1024);
    }
}
