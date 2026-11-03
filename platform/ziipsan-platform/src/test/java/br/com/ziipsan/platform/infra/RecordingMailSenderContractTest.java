package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.mail.MailSender;
import br.com.ziipsan.platform.infra.mail.RecordingMailSender;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.45: contrato de MailSender contra a gravacao local (sem envio externo). */
class RecordingMailSenderContractTest extends MailContractTest {

    @TempDir
    Path outbox;

    @Override
    protected MailSender sender() {
        return new RecordingMailSender(outbox);
    }

    @Override
    protected void assertRecorded(String to, String subject) {
        try (var files = Files.list(outbox)) {
            assertThat(files
                    .filter(p -> p.getFileName().toString().endsWith(".eml"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .anyMatch(c -> c.contains("To: " + to) && c.contains("Subject: " + subject)))
                    .as("nenhum .eml gravado com To/Subject esperados em " + outbox)
                    .isTrue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
