package br.com.ziipsan.platform.infra.mail;

import java.util.List;

/**
 * P0.45: envio de e-mail. Interface pura (sem SDK de provedor de e-mail). A
 * implementacao local e {@link RecordingMailSender}: grava a mensagem num diretorio
 * local e NAO envia nada para fora — o ambiente local nunca dispara e-mail externo.
 * O envio real (SES ou equivalente) e da LC.1 e deve passar na mesma suite de contrato.
 */
public interface MailSender {

    /** Mensagem a enviar. Corpo em texto simples; anexos ficam para a LC.1. */
    record MailMessage(String to, String subject, String bodyText) {
        public MailMessage {
            if (to == null || to.isBlank() || !to.contains("@")) {
                throw new IllegalArgumentException("destinatario de e-mail invalido");
            }
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("assunto de e-mail obrigatorio");
            }
            if (bodyText == null) {
                throw new IllegalArgumentException("corpo de e-mail obrigatorio");
            }
        }
    }

    void send(MailMessage message);

    /** Destinatarios em copia, quando houver; vazio por padrao. */
    default void send(MailMessage message, List<String> cc) {
        send(message);
    }
}
