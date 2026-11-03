package br.com.ziipsan.security.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Medição do autenticador próprio do portal, versão reduzida (P0.23).
 *
 * <p>O plano pede a localização da conta pelo índice cego com base sintética de 1 milhão
 * de usuários no realm do portal. Esta VM não comporta 1 milhão de linhas com o resto do
 * build ao mesmo tempo, e o realm do portal ainda não existe como base carregada: a
 * medição aqui é em escala reduzida (100 mil documentos sintéticos, em memória, sem
 * PostgreSQL) e serve para caracterizar o custo do HMAC + lookup, não para aprovar a
 * escala cheia. A escala cheia (1 milhão, no banco, no realm do portal) fica pendente
 * com o harness pronto. Os números saem no stdout do teste (surefire) e são transcritos
 * para scripts/load/README.md — sem arredondamento inventado, sem asserção de tempo
 * (tempo em CI compartilhada não é medida estável).
 */
class BlindIndexBenchmarkTest {

    private static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final int SCALE = 100_000;

    private static TenantKeyProvider provider() {
        return new TenantKeyProvider() {
            @Override
            public byte[] encryptionKey(UUID tenantId) {
                return key(tenantId, (byte) 0x11);
            }

            @Override
            public byte[] blindIndexKey(UUID tenantId) {
                return key(tenantId, (byte) 0x22);
            }

            private byte[] key(UUID tenantId, byte salt) {
                byte[] k = new byte[32];
                byte[] id = tenantId.toString().getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i < 32; i++) {
                    k[i] = (byte) (id[i % id.length] ^ salt ^ i);
                }
                return k;
            }
        };
    }

    @Test
    void blindIndexLookupAtReducedScale() {
        BlindIndex index = new BlindIndex(provider());
        // Documentos sintéticos: CPFs sequenciais normalizados (só dígitos, 11 posições).
        List<String> documents = new ArrayList<>(SCALE);
        for (int i = 0; i < SCALE; i++) {
            documents.add(String.format("%011d", 100_000_000 + i));
        }
        long buildStart = System.nanoTime();
        Map<String, String> byBlindIndex = new HashMap<>(SCALE * 2);
        for (String document : documents) {
            byBlindIndex.put(index.of(TENANT, "customer.document", document), document);
        }
        long buildMs = (System.nanoTime() - buildStart) / 1_000_000;
        assertThat(byBlindIndex).hasSize(SCALE);

        // Lookup: 10 mil consultas em documentos existentes, com percentis medidos.
        List<Long> lookupNanos = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            String document = documents.get((i * 7) % SCALE);
            long start = System.nanoTime();
            String found = byBlindIndex.get(index.of(TENANT, "customer.document", document));
            lookupNanos.add(System.nanoTime() - start);
            assertThat(found).isEqualTo(document);
        }
        lookupNanos.sort(Long::compareTo);
        long p50 = lookupNanos.get(lookupNanos.size() / 2);
        long p95 = lookupNanos.get((int) (lookupNanos.size() * 0.95));
        long p99 = lookupNanos.get((int) (lookupNanos.size() * 0.99));
        double buildThroughput = SCALE * 1000.0 / Math.max(buildMs, 1);
        System.out.printf("P0.23 blind-index: escala=%d (reduzida; cheia=1000000 pendente) "
                        + "construcao_ms=%d throughput_construcao/s=%.0f "
                        + "lookup_us_p50=%d lookup_us_p95=%d lookup_us_p99=%d "
                        + "(em memoria, sem PostgreSQL; HMAC-SHA-256 + HashMap)%n",
                SCALE, buildMs, buildThroughput, p50 / 1000, p95 / 1000, p99 / 1000);
    }
}
