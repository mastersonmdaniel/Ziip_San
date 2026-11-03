package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.mail.MailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.45: suite de contrato de {@link MailSender}. A implementacao local grava sem
 * enviar; a da LC.1 envia de verdade — ambas devem aceitar a mesma mensagem e
 * recusar destinatario/assunto invalidos.
 */
public abstract class MailContractTest {

    protected abstract MailSender sender();

    /** Gancho para a implementacao concreta afirmar que a mensagem ficou registrada. */
    protected abstract void assertRecorded(String to, String subject);

    @Test
    @DisplayName("mensagem valida e aceita e fica registrada")
    void validMessageIsRecorded() {
        sender().send(new MailSender.MailMessage("teste-sintetico@example.invalid", "Assunto", "Corpo"));
        assertRecorded("teste-sintetico@example.invalid", "Assunto");
    }

    @Test
    @DisplayName("destinatario sem @ e assunto vazio sao recusados na construcao")
    void invalidMessageRefused() {
        assertThatThrownBy(() -> new MailSender.MailMessage("sem-arroba", "Assunto", "Corpo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MailSender.MailMessage("a@example.invalid", "  ", "Corpo"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
