package br.com.ziipsan.commercial.fiscal.relief;

import br.com.ziipsan.commercial.fiscal.port.CertificateVault;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Planeador do processo diario de alerta de certificado (DESENHO-PA02 secao
 * 7.3): classifica cada certificado de referencia e produz os alertas a
 * emitir, com idempotencia por (certificado, limiar, dia) — correr duas
 * vezes no dia nao duplica alerta. O registo dessa chave e a tabela
 * {@code fiscal_certificate_alert_log} (M3): o registo de notificacoes
 * existente deduplica por {@code source_message_id} da outbox, nao por esta
 * chave, pelo que nao garante a idempotencia da secao 3.8 (decisao de
 * execucao registada no relatorio de producao).
 *
 * <p>Falha do job nao bloqueia a emissao: quem bloqueia e a guarda sincrona
 * ({@link CertificateEmissionGuard}). O job e aviso; a guarda e a trava.
 */
public final class CertificateAlertPlanner {

    /** Chave de idempotencia de um alerta. */
    public record AlertKey(UUID certificateId, String threshold, LocalDate alertDate) {
    }

    /** Alerta a emitir/regista. */
    public record PlannedAlert(UUID tenantId, UUID certificateId, String threshold,
                               CertificateAlertClassifier.Classification classification,
                               LocalDate alertDate) {
    }

    /** Certificado de referencia de um tenant/emitente, para o job. */
    public record TenantCertificate(UUID tenantId, CertificateVault.CertificateRef reference) {
    }

    /**
     * @param alreadyAlerted chaves ja registadas (do log M3) — nunca se repete
     */
    public List<PlannedAlert> plan(List<TenantCertificate> certificates, OffsetDateTime now,
                                   Set<AlertKey> alreadyAlerted) {
        List<PlannedAlert> planned = new ArrayList<>();
        LocalDate today = now.toLocalDate();
        for (TenantCertificate entry : certificates) {
            if (entry.reference() == null) {
                continue; // sem certificado de referencia: nada a alertar (a guarda trata a ausencia)
            }
            var classification = CertificateAlertClassifier.classify(entry.reference(), now);
            if (classification == CertificateAlertClassifier.Classification.OK) {
                continue;
            }
            String threshold = CertificateAlertClassifier.thresholdOf(classification);
            AlertKey key = new AlertKey(entry.reference().certificateId(), threshold, today);
            if (alreadyAlerted.contains(key)) {
                continue;
            }
            planned.add(new PlannedAlert(entry.tenantId(), entry.reference().certificateId(),
                    threshold, classification, today));
        }
        return List.copyOf(planned);
    }
}
