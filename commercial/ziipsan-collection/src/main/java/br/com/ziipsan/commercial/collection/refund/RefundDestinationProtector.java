package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.security.crypto.FieldCipher;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Protecao do dado de destino da devolucao (P3.29): o destino so existe
 * CIFRADO fora desta classe. O texto claro e montado como JSON SOMENTE pela
 * classe {@link Json} do core e cifrado pelo {@link FieldCipher}
 * (AES-256-GCM, chave do tenant, contexto {@value #CONTEXT}); o que se grava
 * em {@code refund_requests.destination_ciphertext} e o texto cifrado, e a
 * retencao e definida por {@code destination_retention_until}, resolvida
 * pela fiacao a partir da agenda de retencao do tenant - sem prazo, nao ha
 * gravacao (erro explicito, nunca retencao presumida).
 *
 * <p>Nenhuma mensagem de erro, log ou {@code toString} desta classe contem o
 * dado de destino.
 */
public final class RefundDestinationProtector {

    public static final String CONTEXT = "refunds.destination";

    private final FieldCipher cipher;

    public RefundDestinationProtector(FieldCipher cipher) {
        this.cipher = Objects.requireNonNull(cipher, "FieldCipher e obrigatorio");
    }

    /** @param retentionUntil fim da guarda do dado cifrado (agenda do tenant) */
    public record ProtectedDestination(String ciphertext, LocalDate retentionUntil) {
    }

    /**
     * Cifra o destino ja validado por {@link RefundDestinationValidator}.
     * {@code fields} sao os pares do destino (ex.: pixKey; ou bank/branch/
     * account/holderDocument) - vao para dentro da cifra, nunca para fora.
     */
    public ProtectedDestination protect(UUID tenantId, Map<String, String> fields, LocalDate retentionUntil) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("dado de destino e obrigatorio");
        }
        if (retentionUntil == null) {
            throw new IllegalArgumentException("retencao do dado de destino e obrigatoria: "
                    + "sem prazo definido pela agenda do tenant o destino nao e gravado");
        }
        String plaintext = Json.write(new LinkedHashMap<>(fields));
        return new ProtectedDestination(cipher.encrypt(tenantId, CONTEXT, plaintext), retentionUntil);
    }

    /** Decifra para o executor da devolucao bancaria, no momento do envio. */
    public Map<String, Object> reveal(UUID tenantId, String ciphertext) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("destino cifrado e obrigatorio para executar a devolucao");
        }
        return Json.readMap(cipher.decrypt(tenantId, CONTEXT, ciphertext));
    }

    /**
     * Decifra RESPEITANDO a retencao (correcao pos-revisao b1): dado com a
     * retencao vencida ({@code asOf} depois de {@code retentionUntil}) NAO
     * decifra - a revelacao e recusada antes de tocar na cifra, porque o
     * prazo de guarda definido pela agenda do tenant ja expirou. A fiacao
     * chama esta sobrecarga com {@code asOf = LocalDate.now()} no momento
     * do envio; sem data de retencao gravada, tambem nao revela.
     */
    public Map<String, Object> reveal(UUID tenantId, String ciphertext, LocalDate retentionUntil,
                                      LocalDate asOf) {
        Objects.requireNonNull(asOf, "data da revelacao e obrigatoria");
        if (retentionUntil == null) {
            throw new IllegalArgumentException("retencao do dado de destino e obrigatoria "
                    + "para revelar: sem prazo gravado o destino nao e revelado");
        }
        if (asOf.isAfter(retentionUntil)) {
            throw new IllegalStateException("retencao do dado de destino vencida em "
                    + retentionUntil + ": dado vencido nao decifra");
        }
        return reveal(tenantId, ciphertext);
    }
}
