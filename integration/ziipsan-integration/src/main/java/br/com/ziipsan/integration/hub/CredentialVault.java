package br.com.ziipsan.integration.hub;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P3.37: porta do cofre de credenciais da P0.45. O hub guarda e circula
 * SOMENTE a REFERÊNCIA ({@code integration.<name>.credential_ref},
 * classe d); o segredo vive no cofre e é resolvido pelo ADAPTADOR do
 * conector no momento da chamada, nunca pelo painel, pelo log ou pela
 * fila de falhas. Esta porta expõe metadados e operações do cofre —
 * nenhum método devolve segredo para o hub.
 *
 * <p>A implementação é o cofre da P0.45 (local nesta fase; AWS na LC.1,
 * pela suíte de contrato daquela tarefa). Nome da interface do cofre no
 * core não foi relido nesta tarefa (HTTP 429 no espelho — ver relatório):
 * a fiação liga esta porta ao armazenamento de segredos da P0.45 sem
 * mudar a regra daqui.
 */
public interface CredentialVault {

    /** Metadados da credencial referenciada (sem segredo): validade e última rotação. */
    Optional<CredentialMetadata> metadata(UUID tenantId, String credentialRef);

    /**
     * Rotaciona a credencial do tenant: o cofre gera a nova versão e
     * devolve a NOVA referência. A referência antiga continua válida no
     * cofre durante a sobreposição que a fiação configurar — o hub só
     * passa a usar a nova quando o parâmetro do tenant for atualizado
     * e aceito (P1.6); rotacionar não troca parâmetro em silêncio.
     */
    RotationResult rotate(UUID tenantId, String credentialRef, Instant at);

    /** Metadados de uma credencial no cofre. {@code expiresAt} nulo = credencial sem expiração declarada. */
    record CredentialMetadata(String credentialRef, Instant expiresAt, Instant lastRotatedAt) {
        public CredentialMetadata {
            if (credentialRef == null || credentialRef.isBlank()) {
                throw new IllegalArgumentException("referência da credencial é obrigatória nos metadados");
            }
        }
    }

    /** Resultado da rotação: referência antiga, referência nova e instante. */
    record RotationResult(String previousRef, String newRef, Instant rotatedAt) {
        public RotationResult {
            if (previousRef == null || previousRef.isBlank() || newRef == null || newRef.isBlank()) {
                throw new IllegalArgumentException("rotação exige referência antiga e nova");
            }
            if (previousRef.equals(newRef)) {
                throw new IllegalArgumentException("rotação deve produzir referência nova, diferente da antiga");
            }
            Objects.requireNonNull(rotatedAt, "instante da rotação é obrigatório");
        }
    }
}
