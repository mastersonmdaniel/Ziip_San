package br.com.ziipsan.platform.infra.mail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * P0.45: implementacao local de {@link MailSender} que GRAVA e nao envia. Cada
 * mensagem vira um arquivo {@code <instante>-<hash>.eml} no diretorio local, em
 * formato texto simples (cabecalhos To/Subject + corpo). Nenhuma conexao SMTP e
 * aberta: o ambiente local nunca dispara e-mail externo, e nenhum dado real pode
 * vazar por aqui — em homologacao local so circulam dados sinteticos.
 */
public final class RecordingMailSender implements MailSender {

    private final Path outboxDir;

    public RecordingMailSender(Path outboxDir) {
        if (outboxDir == null) {
            throw new IllegalArgumentException("diretorio de gravacao de e-mail e obrigatorio");
        }
        this.outboxDir = outboxDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.outboxDir);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao criar o diretorio de e-mail gravado", e);
        }
    }

    @Override
    public void send(MailMessage message) {
        String safeBody = message.bodyText().replace("\r", "");
        String content = "To: " + message.to() + "\n"
                + "Subject: " + message.subject().replace("\n", " ") + "\n"
                + "Recorded-At: " + Instant.now() + "\n"
                + "\n" + safeBody + "\n";
        String name = Instant.now().toEpochMilli() + "-"
                + java.util.UUID.randomUUID()
                + ".eml";
        try {
            Files.writeString(outboxDir.resolve(name), content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new UncheckedIOException("falha ao gravar e-mail local", new IOException(e));
        }
    }
}
