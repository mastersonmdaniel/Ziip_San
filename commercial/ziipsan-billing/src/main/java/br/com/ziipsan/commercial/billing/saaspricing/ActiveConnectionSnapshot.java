package br.com.ziipsan.commercial.billing.saaspricing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Fotografia mensal das ligacoes ativas por tenant (P2.12/PLT-17): a
 * BASE COMPROVAVEL da cobranca proporcional a ligacoes que a ED-10
 * consome. O hash SHA-256 cobre (tenant, competencia, contagem): foto
 * alterada depois do hash e DETECTADA por {@link #verifyHash()}, no
 * mesmo desenho do teste exigido pelo aceite da P2.12. Esta entrega
 * NAO reimplementa o usage_ledger da P2.12 - consome a fotografia por
 * este contrato.
 */
public record ActiveConnectionSnapshot(UUID tenantId, YearMonth competence, long activeConnections, String hash) {

    public ActiveConnectionSnapshot {
        if (tenantId == null || competence == null) throw new IllegalArgumentException("tenant e competencia sao obrigatorios na fotografia");
        if (activeConnections < 0) throw new IllegalArgumentException("ligacoes ativas invalidas: " + activeConnections);
        if (hash == null || hash.isBlank()) throw new IllegalArgumentException("hash da fotografia e obrigatorio (base comprovavel, PLT-17)");
    }

    public static String computeHash(UUID tenantId, YearMonth competence, long activeConnections) {
        String canonical = tenantId + "|" + competence + "|" + activeConnections;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 indisponivel", e); }
    }

    public static ActiveConnectionSnapshot sealed(UUID tenantId, YearMonth competence, long activeConnections) {
        return new ActiveConnectionSnapshot(tenantId, competence, activeConnections,
                computeHash(tenantId, competence, activeConnections));
    }

    /** true somente se a contagem gravada corresponde ao hash gravado. */
    public boolean verifyHash() {
        return hash.equals(computeHash(tenantId, competence, activeConnections));
    }

    /** Fotografia adulterada nao serve de base de cobranca: recusa nomeada. */
    public long verifiedConnections() {
        if (!verifyHash()) throw new IllegalStateException(
                "fotografia de ligacoes ativas adulterada (hash nao confere) - PLT-17: cobranca proporcional recusada");
        return activeConnections;
    }
}
