package br.com.ziipsan.core.audit;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerador de UUID versão 7 (RFC 9562), usado pela P1.7 no {@link AuditLog}.
 *
 * <p>Layout: 48 bits de Unix millis | 4 bits de versão (0111) | 12 bits
 * aleatórios | 2 bits de variante (10) | 62 bits aleatórios. O id é, portanto,
 * ordenável por tempo de criação dentro da partição mensal do audit_log, sem
 * depender de sequência do banco — e sem o custo de um UUIDv4 aleatório
 * espalhado pelo índice da partição.
 *
 * <p>O caminho do trigger usa o default {@code uuidv7()} do PostgreSQL 17
 * (core V14); este gerador cobre o caminho JPA, onde o id nasce na JVM
 * (BaseEntity.onCreate). A monotonicidade estrita dentro do mesmo milissegundo
 * NÃO é prometida (os 12 + 62 bits são aleatórios); o que se garante é a
 * versão, a variante e o timestamp embutido.
 */
public final class UuidV7 {

    private UuidV7() {
    }

    public static UUID generate() {
        long millis = System.currentTimeMillis();
        long msb = (millis << 16)
                | 0x7000L
                | (ThreadLocalRandom.current().nextLong() & 0x0FFFL);
        long lsb = 0x8000000000000000L
                | (ThreadLocalRandom.current().nextLong() & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(msb, lsb);
    }

    public static boolean isVersion7(UUID id) {
        return id != null && id.version() == 7;
    }

    /** Millis Unix embutidos nos 48 bits altos; -1 quando o id não é v7. */
    public static long timestampMillis(UUID id) {
        if (!isVersion7(id)) {
            return -1L;
        }
        return id.getMostSignificantBits() >>> 16;
    }
}
