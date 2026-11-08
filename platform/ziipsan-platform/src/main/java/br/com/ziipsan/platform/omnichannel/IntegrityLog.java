package br.com.ziipsan.platform.omnichannel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Log com integridade do atendimento omnichannel (aceite da ED-42: "logs
 * com integridade (SG-655 a SG-661)"): cadeia de hashes SHA-256
 * append-only — cada lançamento carrega o hash do anterior e o seu,
 * calculado sobre a forma canônica do lançamento. Adulterar, remover ou
 * reordenar um lançamento quebra a cadeia na conferência
 * ({@link #verifyChain}); lançamento sem antecessor usa o hash-gênese
 * declarado ({@link #GENESIS_HASH}).
 *
 * <p><b>Marcador honesto:</b> os textos individuais dos itens SG-655 a
 * SG-661 do roteiro do SAMAE NÃO estão nas fontes desta VM — nenhum
 * significado por código SG é inventado aqui. O que esta entrega prova é
 * o MECANISMO de integridade (cadeia verificável, eventos de webhook,
 * conversa, opt-in, atribuição e credencial referenciada) ao qual os
 * itens apontam; a atribuição código-a-código vai na cobertura
 * ({@code OmnichannelCoverage}) como pendência de conferência.
 *
 * <p>Credencial NUNCA entra no log: o evento {@code CREDENTIAL_ACCESSED}
 * registra a REFERÊNCIA da credencial no cofre e o canal, jamais o valor.
 */
public final class IntegrityLog {

    public static final String GENESIS_HASH = "GENESIS";

    public enum EventType {
        WEBHOOK_VERIFIED, WEBHOOK_REJECTED, CONVERSATION_OPENED, MESSAGE_RECORDED,
        OPT_IN_CHECKED, ASSIGNMENT_CHANGED, CONVERSATION_CLOSED, CREDENTIAL_ACCESSED,
        REFUSAL_RECORDED
    }

    public record Entry(long sequence, UUID tenantId, EventType eventType, String reference,
                        String actor, Instant occurredAt, String previousHash, String hash) {
        public Entry {
            if (sequence < 1 || tenantId == null || eventType == null || occurredAt == null
                    || previousHash == null || previousHash.isBlank()
                    || hash == null || hash.isBlank()) {
                throw new IllegalArgumentException(
                        "lançamento íntegro exige sequência, tenant, evento, instante e os dois hashes");
            }
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("lançamento íntegro exige a referência (conversa/evento/credencial)");
            }
        }
    }

    private IntegrityLog() {
    }

    public static String computeHash(long sequence, UUID tenantId, EventType eventType, String reference,
                                     String actor, Instant occurredAt, String previousHash) {
        String canonical = sequence + "|" + tenantId + "|" + eventType + "|" + reference + "|"
                + (actor == null ? "" : actor) + "|" + occurredAt + "|" + previousHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    /** Monta o próximo lançamento da cadeia a partir do hash vigente (ou do gênese, no primeiro). */
    public static Entry append(long sequence, UUID tenantId, EventType eventType, String reference,
                               String actor, Instant occurredAt, String previousHash) {
        String prev = previousHash == null ? GENESIS_HASH : previousHash;
        String hash = computeHash(sequence, tenantId, eventType, reference, actor, occurredAt, prev);
        return new Entry(sequence, tenantId, eventType, reference, actor, occurredAt, prev, hash);
    }

    /**
     * Confere a cadeia na ordem dada: sequência 1..n sem buraco, cada
     * {@code previousHash} igual ao hash do lançamento anterior e cada
     * hash recalculado igual ao gravado.
     *
     * @return índice do primeiro lançamento quebrado, ou {@code -1} se a cadeia está íntegra
     */
    public static int firstBrokenIndex(List<Entry> chain) {
        if (chain == null) {
            throw new IllegalArgumentException("a cadeia é obrigatória (pode ser vazia, não nula)");
        }
        String expectedPrevious = GENESIS_HASH;
        for (int i = 0; i < chain.size(); i++) {
            Entry entry = chain.get(i);
            if (entry.sequence() != i + 1L) {
                return i;
            }
            if (!entry.previousHash().equals(expectedPrevious)) {
                return i;
            }
            String recomputed = computeHash(entry.sequence(), entry.tenantId(), entry.eventType(),
                    entry.reference(), entry.actor(), entry.occurredAt(), entry.previousHash());
            if (!recomputed.equals(entry.hash())) {
                return i;
            }
            expectedPrevious = entry.hash();
        }
        return -1;
    }

    public static boolean verifyChain(List<Entry> chain) {
        return firstBrokenIndex(chain) == -1;
    }
}
