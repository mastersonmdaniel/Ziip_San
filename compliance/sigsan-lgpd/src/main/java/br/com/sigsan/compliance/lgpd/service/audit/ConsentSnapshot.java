package br.com.sigsan.compliance.lgpd.service.audit;

import br.com.sigsan.compliance.lgpd.domain.ConsentRecord;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * O que a trilha de auditoria guarda de um consentimento.
 *
 * <p>Antes a entidade inteira ia para {@code audit_log} via {@code ObjectMapper}: IP, user agent,
 * geolocalização, o texto do consentimento e o id do titular, cópia de dado pessoal numa tabela
 * que não tem prazo de retenção nem é alcançada pela anonimização (Art. 16 e Art. 18 IV). Ficam só
 * campos que descrevem o ato, não a pessoa. O hash do texto basta para provar qual versão foi
 * aceita sem copiar o texto.
 *
 * <p>Ser um record com campos escalares também evita serializar proxies JPA preguiçosos.
 */
public record ConsentSnapshot(
        UUID id,
        String subjectType,
        String purpose,
        String purposeCategory,
        ConsentRecord.LegalBasis legalBasis,
        ConsentRecord.ConsentStatus status,
        String consentHash,
        OffsetDateTime grantedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime revokedAt,
        String withdrawalMethod
) {

    public static ConsentSnapshot of(ConsentRecord c) {
        return new ConsentSnapshot(c.getId(), c.getSubjectType(), c.getPurpose(), c.getPurposeCategory(),
                c.getLegalBasis(), c.getStatus(), c.getConsentHash(), c.getGrantedAt(), c.getExpiresAt(),
                c.getRevokedAt(), c.getWithdrawalMethod());
    }
}
