package br.com.ziipsan.platform.security;

/**
 * Contador de requisições por chave e janela, compartilhado entre réplicas (P0.16).
 *
 * <p>A janela é fixa e identificada pelo seu início em segundos de época, calculado
 * por quem chama ({@code agora / janela * janela}). O store só precisa incrementar
 * (ou zerar, se a janela guardada for mais antiga) e devolver a contagem nova — a
 * semântica de janela vive numa única instrução atômica no JDBC, sem limpeza.
 */
public interface RateLimitStore {

    /**
     * Incrementa a contagem da chave na janela e devolve a contagem nova.
     *
     * @param key chave do balde (ex.: {@code tenant:<uuid>})
     * @param windowStartEpochSecond início da janela, em segundos de época
     * @return contagem da chave na janela após este incremento (sempre &ge; 1)
     */
    int increment(String key, long windowStartEpochSecond);
}
