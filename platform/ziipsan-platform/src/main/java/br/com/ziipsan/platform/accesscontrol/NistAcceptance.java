package br.com.ziipsan.platform.accesscontrol;

import java.time.Instant;
import java.util.UUID;

/**
 * Aceite registrado do administrador do tenant para uma opção de política
 * CONTRÁRIA à recomendação do NIST SP 800-63B-4 (ED-06, nota de alinhamento
 * do ADR-0040): expiração periódica de senha ou regra de composição exigidas
 * por edital ficam DISPONÍVEIS, mas só passam a valer com este registro —
 * quem aceitou, quando e para qual opção. Sem aceite vigente, a opção é
 * ignorada pelo {@link PasswordPolicyRule} (a política efetiva volta ao
 * alinhado), nunca aplicada em silêncio.
 */
public record NistAcceptance(UUID id, UUID tenantId, UUID acceptedBy, String optionKey,
                             Instant acceptedAt, Instant revokedAt) {

    /** Opção: expiração periódica ({@code sec.password_max_age_days}). */
    public static final String OPTION_PERIODIC_EXPIRY = "sec.password_max_age_days";
    /** Opção: regra de composição ({@code sec.password_composition_required}). */
    public static final String OPTION_COMPOSITION = "sec.password_composition_required";

    public NistAcceptance {
        if (id == null || tenantId == null || acceptedBy == null || acceptedAt == null) {
            throw new IllegalArgumentException("aceite NIST exige id, tenant, administrador e instante");
        }
        if (!OPTION_PERIODIC_EXPIRY.equals(optionKey) && !OPTION_COMPOSITION.equals(optionKey)) {
            throw new IllegalArgumentException("opção fora do conjunto contrário ao NIST: " + optionKey);
        }
    }

    public boolean activeAt(Instant now) {
        return !acceptedAt.isAfter(now) && (revokedAt == null || revokedAt.isAfter(now));
    }
}
