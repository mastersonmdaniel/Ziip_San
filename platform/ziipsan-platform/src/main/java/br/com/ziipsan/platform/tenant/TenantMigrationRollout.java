package br.com.ziipsan.platform.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;

/**
 * Rollout de migrations por tenant (P1.14; ondas explícitas na P3.12; 17:D1; ADR-0005).
 *
 * <p>Regras, na ordem:
 * <ol>
 *   <li><b>Canário.</b> O primeiro tenant da lista (o {@code DatabaseMigrator} ordena por
 *   {@code schema_name}) migra sozinho, em sequência — é a onda zero. Se ele falha, a onda
 *   é abortada: os demais tenants nem são tentados — o canário existe justamente para que
 *   uma migration quebrada não atinja todo mundo. O canário falho vai para a quarentena
 *   como qualquer outro tenant falho.</li>
 *   <li><b>Ondas com paralelismo limitado (P3.12).</b> Passando o canário, os demais
 *   tenants migram em <b>ondas</b>: lotes de no máximo {@code parallelism} grupos de
 *   schema, executados em paralelo dentro da onda e em sequência entre ondas — a onda
 *   seguinte só começa quando a anterior termina e tem a situação gravada. O padrão do
 *   paralelismo é 2, igual ao pool do DataSource de migração (ver
 *   {@code DataSourceConfig}): cada migração segura uma conexão durante toda a execução,
 *   então um paralelismo maior que o pool só faria threads esperarem conexão — nunca
 *   configure acima do pool do migrador. A falha de um tenant numa onda <b>não</b> aborta
 *   as ondas seguintes: ele vai para a quarentena e os demais continuam (regra 3).</li>
 *   <li><b>Quarentena sem parar os demais.</b> A falha de um tenant não-canário (exceção do
 *   Flyway ou de conexão) é capturada: o tenant é marcado {@code QUARANTINED} no catálogo
 *   com a mensagem da falha e os demais continuam. O job só falha no fim (o
 *   {@code DatabaseMigrator} lança exceção se houve quarentena ou aborto) — sem registrar
 *   a implantação como concluída —, mas o que já migrou fica migrado.</li>
 *   <li><b>Um schema, uma migração.</b> Tenants em modo SHARED (ADR-0005) dividem o schema:
 *   o grupo migra uma única vez e o resultado vale para todos os tenants do grupo, com a
 *   situação registrada por tenant (uma linha por tenant, mesmo resultado).</li>
 *   <li><b>Situação registrada por tenant.</b> Ao fim de cada tentativa, o catálogo recebe
 *   {@code migration_status}, {@code migration_error} e {@code migration_attempted_at}
 *   (core V14_1). A escrita é feita só pela thread principal, depois que as workers
 *   terminam: assim nenhuma thread segura conexão de migração e de escrita ao mesmo
 *   tempo, o que com pool 2 poderia travar as duas esperando conexão uma da outra.</li>
 * </ol>
 *
 * <p>Quarentena é situação da última tentativa, não bloqueio permanente: a próxima
 * execução do job tenta o tenant de novo (a recuperação de uma migration quebrada é
 * corrigir a migration — ou reparar o histórico via Flyway repair — e rodar o job;
 * um tenant com falha registrada no histórico do Flyway volta a falhar até o reparo,
 * o que é o comportamento padrão do Flyway, não uma decisão desta classe).
 *
 * <p>A migração em si é injetada como função (schema, tenantId) para que esta classe
 * seja testável sem banco: em produção o {@code DatabaseMigrator} passa
 * {@code TenantMigrator::migrate} adaptado. O retorno da função é o número de
 * migrations executadas e a versão-alvo, que o chamador usa para atualizar
 * {@code tenant_schema_version} — esta classe não escreve essa coluna.
 */
public final class TenantMigrationRollout {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRollout.class);

    /** Teto do texto de erro gravado no catálogo: mensagem de exceção, nunca stack trace. */
    static final int MAX_ERROR_LENGTH = 4000;

    public record Tenant(UUID id, String schemaName) {
    }

    public record MigrationOutcome(int migrationsExecuted, String targetSchemaVersion) {
    }

    public record TenantResult(Tenant tenant, boolean canary, boolean succeeded,
                               int migrationsExecuted, String targetSchemaVersion, String error) {
    }

    public record RolloutResult(List<TenantResult> results, boolean abortedAfterCanary) {

        public List<TenantResult> succeeded() {
            return results.stream().filter(TenantResult::succeeded).toList();
        }

        public List<TenantResult> quarantined() {
            return results.stream().filter(r -> !r.succeeded()).toList();
        }
    }

    private final JdbcTemplate jdbc;
    private final BiFunction<String, UUID, MigrationOutcome> migrateOne;
    private final int parallelism;

    public TenantMigrationRollout(JdbcTemplate jdbc,
                                  BiFunction<String, UUID, MigrationOutcome> migrateOne,
                                  int parallelism) {
        this.jdbc = jdbc;
        this.migrateOne = migrateOne;
        this.parallelism = Math.max(1, parallelism);
    }

    public RolloutResult run(List<Tenant> tenants) {
        if (tenants.isEmpty()) {
            return new RolloutResult(List.of(), false);
        }
        // Agrupa por schema preservando a ordem: o primeiro grupo é o canário.
        Map<String, List<Tenant>> bySchema = new LinkedHashMap<>();
        for (Tenant tenant : tenants) {
            bySchema.computeIfAbsent(tenant.schemaName(), k -> new ArrayList<>()).add(tenant);
        }
        List<Map.Entry<String, List<Tenant>>> groups = new ArrayList<>(bySchema.entrySet());

        List<TenantResult> results = new ArrayList<>();
        Map.Entry<String, List<Tenant>> canaryGroup = groups.get(0);
        List<TenantResult> canaryResults = migrateGroup(canaryGroup, true);
        results.addAll(canaryResults);
        recordStatuses(canaryResults);
        if (canaryResults.stream().anyMatch(r -> !r.succeeded())) {
            log.warn("Canário {} falhou: onda abortada, {} grupo(s) restante(s) nem tentados",
                    canaryGroup.getKey(), groups.size() - 1);
            return new RolloutResult(results, true);
        }

        List<Map.Entry<String, List<Tenant>>> rest = groups.subList(1, groups.size());
        // P3.12: ondas explícitas de no máximo `parallelism` grupos. Cada onda
        // roda em paralelo por dentro, termina por completo (com a situação
        // gravada) e só então a próxima começa — diferente de submeter todos os
        // grupos de uma vez a um pool, em que o "lote" era só o limite de
        // threads e nenhuma fronteira de onda era observável no registro.
        for (int waveStart = 0, wave = 1; waveStart < rest.size(); waveStart += parallelism, wave++) {
            List<Map.Entry<String, List<Tenant>>> waveGroups =
                    rest.subList(waveStart, Math.min(waveStart + parallelism, rest.size()));
            List<TenantResult> waveResults = runWave(waveGroups, wave);
            results.addAll(waveResults);
            recordStatuses(waveResults);
        }
        return new RolloutResult(results, false);
    }

    private List<TenantResult> runWave(List<Map.Entry<String, List<Tenant>>> waveGroups, int wave) {
        log.info("Onda {} do rollout: {} grupo(s) de schema", wave, waveGroups.size());
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<List<TenantResult>>> futures = new ArrayList<>();
            for (Map.Entry<String, List<Tenant>> group : waveGroups) {
                futures.add(pool.submit(() -> migrateGroup(group, false)));
            }
            // Ordem determinística no registro: segue a ordem dos grupos, não a de
            // término das threads.
            List<TenantResult> waveResults = new ArrayList<>();
            for (Future<List<TenantResult>> future : futures) {
                try {
                    waveResults.addAll(future.get());
                } catch (ExecutionException e) {
                    // migrateGroup já captura qualquer exceção por tenant; chegar aqui
                    // seria um erro do próprio executor, não de um tenant.
                    throw new IllegalStateException("Falha do executor do rollout", e.getCause());
                }
            }
            return waveResults;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rollout interrompido", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private List<TenantResult> migrateGroup(Map.Entry<String, List<Tenant>> group, boolean canary) {
        String schema = group.getKey();
        try {
            // O resultado vale para o grupo inteiro; a versão-alvo vem do primeiro
            // tenant (o Flyway roda uma vez por schema, não por tenant).
            MigrationOutcome outcome = migrateOne.apply(schema, group.getValue().get(0).id());
            log.info("Tenant{} {}: {} migration(s) aplicada(s), versão {}",
                    canary ? " (canário)" : "", schema,
                    outcome.migrationsExecuted(), outcome.targetSchemaVersion());
            return group.getValue().stream()
                    .map(t -> new TenantResult(t, canary, true,
                            outcome.migrationsExecuted(), outcome.targetSchemaVersion(), null))
                    .toList();
        } catch (RuntimeException e) {
            String error = truncate(e.getMessage() != null ? e.getMessage() : e.toString());
            log.warn("Tenant {} em quarentena (canário={}): {}", schema, canary, error);
            return group.getValue().stream()
                    .map(t -> new TenantResult(t, canary, false, 0, null, error))
                    .toList();
        }
    }

    private void recordStatuses(List<TenantResult> results) {
        Instant now = Instant.now();
        for (TenantResult result : results) {
            jdbc.update(
                    "UPDATE public.tenant_catalog SET migration_status = ?, migration_error = ?, "
                            + "migration_attempted_at = ? WHERE id = ?",
                    result.succeeded() ? "SUCCEEDED" : "QUARANTINED",
                    result.error(),
                    java.sql.Timestamp.from(now),
                    result.tenant().id());
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
