package br.com.ziipsan.platform.infra.secrets;

import java.util.Optional;

/**
 * P0.45: cofre de segredos. Interface pura: chaves sao caminhos logicos
 * ({@code kek/current}, {@code tenants/<uuid>/dek}) e valores sao bytes opacos.
 * A implementacao local e {@link LocalFileSecretStore} (diretorio fora do repositorio,
 * arquivos com permissao restrita); o cofre gerenciado (Vault/KMS) e da LC.4 e deve
 * passar na mesma suite de contrato. Nenhum segredo e logado por nenhuma implementacao.
 */
public interface SecretStore {

    void put(String key, byte[] value);

    Optional<byte[]> get(String key);

    boolean exists(String key);

    /** Idempotente: apagar uma chave inexistente nao falha. */
    void delete(String key);
}
