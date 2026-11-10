package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ziipsan.platform.infra.crypto.TenantKeyStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P4.6, item 6: exportacao, restauracao e eliminacao por tenant_id no schema
 * compartilhado, com crypto-shredding — sobre duplos em memoria que guardam
 * linhas de DOIS tenants, para provar que so as do alvo sao tocadas.
 */
class SharedTenantLifecycleTest {

    private static final String SCHEMA = "tenant_shared";
    private static final UUID ALVO = UUID.randomUUID();
    private static final UUID VIZINHO = UUID.randomUUID();

    /** Store em memoria: tabela -> linhas (cada linha tem tenant_id). Sem 'final' desde a correcção R-P46-04: o teste de compensação do erase especializa deleteByTenant para simular a falha a meio. */
    static class InMemoryRows implements SharedTenantLifecycle.TenantRowStore {
        final Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();

        void seed(String table, UUID tenant, int count) {
            tables.computeIfAbsent(table, t -> new ArrayList<>());
            for (int i = 0; i < count; i++) {
                tables.get(table).add(new LinkedHashMap<>(
                        Map.of("tenant_id", tenant, "note", table + "-" + i)));
            }
        }

        @Override
        public List<Map<String, Object>> exportByTenant(String schema, String table, UUID tenantId) {
            return tables.getOrDefault(table, List.of()).stream()
                    .filter(r -> tenantId.toString().equals(r.get("tenant_id").toString()))
                    .toList();
        }

        @Override
        public int deleteByTenant(String schema, String table, UUID tenantId) {
            List<Map<String, Object>> lines = tables.getOrDefault(table, List.of());
            int before = lines.size();
            lines.removeIf(r -> tenantId.toString().equals(r.get("tenant_id").toString()));
            return before - lines.size();
        }

        @Override
        public void insertRow(String schema, String table, Map<String, Object> row) {
            tables.computeIfAbsent(table, t -> new ArrayList<>()).add(new LinkedHashMap<>(row));
        }
    }

    static class InMemoryKeys implements TenantKeyStore {
        boolean destroyed;

        @Override
        public Optional<KeyMaterial> loadActive(UUID tenantId) {
            return destroyed ? Optional.empty()
                    : Optional.of(new KeyMaterial(1, new byte[]{1}, new byte[]{2}, "ACTIVE"));
        }

        @Override
        public int createVersion(UUID tenantId, byte[] w, byte[] b) {
            return 1;
        }

        @Override
        public void destroyAll(UUID tenantId) {
            destroyed = true;
        }

        @Override
        public boolean destroyed(UUID tenantId) {
            return destroyed;
        }
    }

    @Test
    @DisplayName("erase: exporta, apaga SO as linhas do alvo e destroi a chave; o vizinho fica intacto")
    void eraseOnlyTargetTenantAndShredsKey() {
        var rows = new InMemoryRows();
        rows.seed("consumer_unit", ALVO, 3);
        rows.seed("consumer_unit", VIZINHO, 2);
        rows.seed("invoice", ALVO, 1);
        var keys = new InMemoryKeys();
        var lifecycle = new SharedTenantLifecycle(rows, keys);

        var report = lifecycle.eraseTenant(SCHEMA, List.of("consumer_unit", "invoice"), ALVO);

        assertThat(report.exportedRows()).containsEntry("consumer_unit", 3).containsEntry("invoice", 1);
        assertThat(report.deletedRows()).containsEntry("consumer_unit", 3).containsEntry("invoice", 1);
        assertThat(report.keyDestroyed()).isTrue();
        assertThat(rows.tables.get("consumer_unit")).hasSize(2)
                .allSatisfy(r -> assertThat(r.get("tenant_id")).isEqualTo(VIZINHO));
        assertThat(rows.tables.get("invoice")).isEmpty();
    }

    @Test
    @DisplayName("export/restore: a exportacao do alvo restaura as mesmas linhas; linha de outro tenant aborta")
    void exportAndRestoreRoundTrip() {
        var rows = new InMemoryRows();
        rows.seed("consumer_unit", ALVO, 2);
        var lifecycle = new SharedTenantLifecycle(rows, new InMemoryKeys());

        var export = lifecycle.exportTenant(SCHEMA, List.of("consumer_unit"), ALVO);
        rows.deleteByTenant(SCHEMA, "consumer_unit", ALVO);
        var restored = lifecycle.restoreTenant(SCHEMA, export, ALVO);

        assertThat(restored).containsEntry("consumer_unit", 2);
        assertThat(rows.tables.get("consumer_unit")).hasSize(2);

        var foreign = Map.of("consumer_unit",
                List.of(Map.<String, Object>of("tenant_id", VIZINHO, "note", "x")));
        assertThatThrownBy(() -> lifecycle.restoreTenant(SCHEMA, foreign, ALVO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao pertence ao tenant");
    }

    @Test
    @DisplayName("restore: tenant com chave destruida e recusado (campos cifrados seriam indecifraveis)")
    void restoreAfterShreddingIsRefused() {
        var keys = new InMemoryKeys();
        keys.destroyed = true;
        var lifecycle = new SharedTenantLifecycle(new InMemoryRows(), keys);
        var export = Map.of("consumer_unit",
                List.of(Map.<String, Object>of("tenant_id", ALVO, "note", "x")));
        assertThatThrownBy(() -> lifecycle.restoreTenant(SCHEMA, export, ALVO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chave destruida");
    }

    @Test
    @DisplayName("erase: sem confirmacao do crypto-shredding, a eliminacao falha em voz alta")
    void eraseFailsWhenShreddingUnconfirmed() {
        var rows = new InMemoryRows();
        rows.seed("consumer_unit", ALVO, 1);
        TenantKeyStore silentKeys = new InMemoryKeys() {
            @Override
            public void destroyAll(UUID tenantId) {
                // nao destroi: simula a falha silenciosa que o item 6 nao admite
            }
        };
        var lifecycle = new SharedTenantLifecycle(rows, silentKeys);
        assertThatThrownBy(() -> lifecycle.eraseTenant(SCHEMA, List.of("consumer_unit"), ALVO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Crypto-shredding nao confirmado");
    }

    @Test
    @DisplayName("erase (R-P46-04): o relatorio transporta o CONTEUDO exportado, nao so contagens")
    void eraseReportCarriesExportedContent() {
        var rows = new InMemoryRows();
        rows.seed("consumer_unit", ALVO, 2);
        rows.seed("consumer_unit", VIZINHO, 1);
        var lifecycle = new SharedTenantLifecycle(rows, new InMemoryKeys());

        var report = lifecycle.eraseTenant(SCHEMA, List.of("consumer_unit"), ALVO);

        assertThat(report.exportedContent()).containsKey("consumer_unit");
        assertThat(report.exportedContent().get("consumer_unit")).hasSize(2)
                .allSatisfy(r -> assertThat(r.get("tenant_id")).isEqualTo(ALVO));
        // E o conteudo arquivado basta para restaurar o tenant apagado.
        var keys2 = new InMemoryKeys();
        var lifecycle2 = new SharedTenantLifecycle(rows, keys2);
        var restored = lifecycle2.restoreTenant(SCHEMA, report.exportedContent(), ALVO);
        assertThat(restored).containsEntry("consumer_unit", 2);
    }

    @Test
    @DisplayName("restore (R-P46-04): linha alheia na 2a tabela aborta SEM inserir nada da 1a")
    void restoreValidatesEverythingBeforeInserting() {
        var rows = new InMemoryRows();
        var lifecycle = new SharedTenantLifecycle(rows, new InMemoryKeys());
        var poisoned = new LinkedHashMap<String, List<Map<String, Object>>>();
        poisoned.put("consumer_unit",
                List.of(Map.<String, Object>of("tenant_id", ALVO, "note", "ok")));
        poisoned.put("invoice",
                List.of(Map.<String, Object>of("tenant_id", VIZINHO, "note", "alheia")));

        assertThatThrownBy(() -> lifecycle.restoreTenant(SCHEMA, poisoned, ALVO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao pertence ao tenant");
        assertThat(rows.tables.getOrDefault("consumer_unit", List.of())).isEmpty();
        assertThat(rows.tables.getOrDefault("invoice", List.of())).isEmpty();
    }

    @Test
    @DisplayName("erase (R-P46-04): falha a apagar a 2a tabela repoe a 1a e NAO destroi a chave")
    void eraseFailureCompensatesAndKeepsKey() {
        var rows = new InMemoryRows() {
            @Override
            public int deleteByTenant(String schema, String table, UUID tenantId) {
                if ("invoice".equals(table)) {
                    throw new IllegalStateException("falha simulada a apagar invoice");
                }
                return super.deleteByTenant(schema, table, tenantId);
            }
        };
        rows.seed("consumer_unit", ALVO, 2);
        rows.seed("invoice", ALVO, 1);
        var keys = new InMemoryKeys();
        var lifecycle = new SharedTenantLifecycle(rows, keys);

        assertThatThrownBy(() -> lifecycle.eraseTenant(
                SCHEMA, List.of("consumer_unit", "invoice"), ALVO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compensada");
        assertThat(rows.tables.get("consumer_unit")).hasSize(2);
        assertThat(rows.tables.get("invoice")).hasSize(1);
        assertThat(keys.destroyed).isFalse();
    }

    @Test
    @DisplayName("identificadores fora do padrao sao recusados (nunca interpolados no SQL)")
    void invalidIdentifiersAreRefused() {
        var lifecycle = new SharedTenantLifecycle(new InMemoryRows(), new InMemoryKeys());
        assertThatThrownBy(() -> lifecycle.exportTenant("shared; DROP TABLE x",
                List.of("consumer_unit"), ALVO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lifecycle.exportTenant("public",
                List.of("consumer_unit"), ALVO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
