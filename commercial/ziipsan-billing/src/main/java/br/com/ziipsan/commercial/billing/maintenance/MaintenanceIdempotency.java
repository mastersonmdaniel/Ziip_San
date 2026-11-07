package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.core.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Idempotencia das operacoes de manutencao pela {@code Idempotency-Key}
 * (aceite da P2.20): repetir a chamada com a mesma chave e o mesmo pedido
 * devolve o resultado ja gravado em {@code bill_maintenance_operations},
 * sem duplicar versao, OS nem evento; a mesma chave com pedido diferente e
 * conflito explicito. O hash do pedido e calculado sobre JSON montado pela
 * classe {@code Json} do core (nunca {@code toString()} de mapa).
 */
public final class MaintenanceIdempotency {

    public enum Decision {
        EXECUTE,
        REPLAY,
        CONFLICT
    }

    private MaintenanceIdempotency() {
    }

    /** Hash estavel do pedido: SHA-256 do JSON canonico (chaves na ordem de insercao do mapa). */
    public static String requestHash(Map<String, Object> requestFields) {
        Objects.requireNonNull(requestFields, "requestFields");
        String json = Json.write(new LinkedHashMap<>(requestFields));
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    /**
     * @param storedHash hash gravado para a chave (null = chave nunca vista)
     * @param requestHash hash do pedido atual
     */
    public static Decision decide(String storedHash, String requestHash) {
        Objects.requireNonNull(requestHash, "requestHash");
        if (storedHash == null) {
            return Decision.EXECUTE;
        }
        return storedHash.equals(requestHash) ? Decision.REPLAY : Decision.CONFLICT;
    }
}
