package br.com.ziipsan.commercial.fiscal.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta do cofre de certificado (FISCAL-PORTE-JAVA §3.2; ADR-0033 §3.2).
 *
 * <p>Esta porta só devolve <b>referências</b>: thumbprint, referência ao cofre e validade.
 * O material secreto (PFX, senha, chave privada) nunca atravessa esta porta nem é gravado
 * em tabela do produto: no ambiente local ele fica no cofre local, fora do banco, e só o
 * cliente de transporte (P2) o abre, em memória, no momento da assinatura/mTLS. Cada acesso
 * é registrado em {@code fiscal_certificate_access_log} (quem, quando, para quê).
 */
public interface CertificateVault {

    /** Certificado de referência do emitente: o não revogado com maior validade, se houver. */
    record CertificateRef(UUID certificateId, UUID issuerId, String thumbprint, String vaultRef,
                          String keyType, OffsetDateTime notAfter) {
    }

    Optional<CertificateRef> certificateFor(UUID tenantId, UUID issuerId);

    /** Registra um acesso ao certificado. Sem acesso registrado, não há uso do certificado. */
    void recordAccess(UUID tenantId, UUID certificateId, UUID accessedBy, String purpose);
}
