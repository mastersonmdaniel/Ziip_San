package br.com.ziipsan.platform.tenant;

import com.zaxxer.hikari.HikariDataSource;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * P4.6, item 4 do aceite: roteamento de pools por CLUSTER e por PAPEL.
 *
 * <p>A P2.2 entregou os pools por papel ({@link RoleDataSourceFactory}: API,
 * lote e ingestao, cada um com credencial e teto proprios) sobre UMA url. Num
 * modelo multi-cluster (P0.21: {@code tenant_catalog.cluster_id}) isso nao
 * basta: um tenant em banco dedicado (DEDICATED) ou num segundo cluster nao
 * pode cair no pool do cluster {@code default}. Este roteador segura, por
 * cluster, o conjunto de pools por papel desse cluster, e resolve
 * {@code (resolucao do tenant, papel) -> pool}:
 *
 * <pre>
 *   TenantCatalog.findResolution(tenantId)   // devolve cluster + modo (item 4)
 *        -> router.route(resolution, PoolRole.BATCH)
 * </pre>
 *
 * <p>Regras deliberadas:
 * <ul>
 *   <li>Cluster desconhecido FALHA com mensagem nomeando o cluster: cair em
 *       silencio no cluster default mandaria o tenant dedicado para o banco
 *       compartilhado — o erro tem de ser ruidoso, nunca um desvio silencioso.</li>
 *   <li>As credenciais de TODOS os clusters sao validadas antes de criar
 *       qualquer pool (a {@code RoleDataSourceFactory.createAll} ja valida as
 *       de um cluster; aqui a lista de endpoints e validada inteira primeiro),
 *       para um endpoint incompleto nao deixar pools abertos dos anteriores.</li>
 *   <li>Quem cria fecha: o roteador e {@link AutoCloseable} e fecha todos os
 *       pools de todos os clusters.</li>
 *   <li>Criar o roteador nao abre conexao nenhuma (os pools nascem com
 *       {@code minimumIdle = 0} na fabrica da P2.2): a autenticacao so acontece
 *       no primeiro uso de cada pool, com a credencial do papel NAQUELE cluster.</li>
 * </ul>
 *
 * <p>Estado de integração (declaração da correcção R-P46-08): componente
 * de biblioteca testado, AINDA SEM chamador nem registo Spring em
 * produção — os endpoints por cluster (URL + credenciais por papel)
 * nascem de configuração operacional que não existe antes do primeiro
 * cluster real além do 'default'. O item 4 está entregue ao nível de
 * componente; a ligação à aplicação é pendência nomeada no relatório da
 * correcção da P4.6.
 */
public final class ClusterPoolRouter implements AutoCloseable {

    /** Endpoint de um cluster: a sua url JDBC e a credencial de cada papel nesse cluster. */
    public record ClusterEndpoint(String clusterId, String jdbcUrl,
                                  Map<PoolRole, RoleDataSourceFactory.RoleCredentials> credentials) {
        public ClusterEndpoint {
            if (clusterId == null || clusterId.isBlank()) {
                throw new IllegalArgumentException("clusterId do endpoint e obrigatorio");
            }
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                throw new IllegalArgumentException("URL JDBC do cluster " + clusterId + " e obrigatoria");
            }
            Objects.requireNonNull(credentials, "credenciais do cluster " + clusterId + " sao obrigatorias");
            for (PoolRole role : PoolRole.values()) {
                if (credentials.get(role) == null) {
                    throw new IllegalArgumentException(
                            "Falta a credencial do papel " + role + " no cluster " + clusterId);
                }
            }
            credentials = Map.copyOf(credentials);
        }
    }

    private final Map<String, Map<PoolRole, HikariDataSource>> poolsByCluster;

    private ClusterPoolRouter(Map<String, Map<PoolRole, HikariDataSource>> poolsByCluster) {
        this.poolsByCluster = poolsByCluster;
    }

    /** Constroi o roteador a partir dos endpoints; valida tudo antes de criar o primeiro pool. */
    public static ClusterPoolRouter of(List<ClusterEndpoint> endpoints) {
        Objects.requireNonNull(endpoints, "endpoints dos clusters sao obrigatorios");
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("Pelo menos um cluster e obrigatorio");
        }
        Map<String, ClusterEndpoint> unique = new LinkedHashMap<>();
        for (ClusterEndpoint endpoint : endpoints) {
            Objects.requireNonNull(endpoint, "endpoint de cluster nao pode ser nulo");
            if (unique.putIfAbsent(endpoint.clusterId(), endpoint) != null) {
                throw new IllegalArgumentException("Cluster repetido: " + endpoint.clusterId());
            }
        }
        Map<String, Map<PoolRole, HikariDataSource>> pools = new LinkedHashMap<>();
        try {
            for (ClusterEndpoint endpoint : unique.values()) {
                pools.put(endpoint.clusterId(),
                        RoleDataSourceFactory.createAll(endpoint.jdbcUrl(), endpoint.credentials()));
            }
        } catch (RuntimeException e) {
            pools.values().forEach(byRole -> byRole.values().forEach(HikariDataSource::close));
            throw e;
        }
        return new ClusterPoolRouter(pools);
    }

    /** Pool do papel num cluster nomeado. Cluster desconhecido falha — nunca cai no default. */
    public HikariDataSource pool(String clusterId, PoolRole role) {
        Objects.requireNonNull(role, "papel do pool e obrigatorio");
        Map<PoolRole, HikariDataSource> byRole = poolsByCluster.get(clusterId);
        if (byRole == null) {
            throw new IllegalArgumentException(
                    "Cluster desconhecido no roteador: '" + clusterId + "' (conhecidos: "
                            + poolsByCluster.keySet() + "). Recusado o desvio silencioso para outro cluster.");
        }
        return byRole.get(role);
    }

    /** Rota da resolucao de um tenant (item 4): o cluster vem do catalogo, nunca do cliente. */
    public HikariDataSource route(TenantResolution resolution, PoolRole role) {
        Objects.requireNonNull(resolution, "resolucao do tenant e obrigatoria");
        if (!resolution.active()) {
            throw new IllegalStateException(
                    "Tenant " + resolution.id() + " nao esta ACTIVE (status=" + resolution.status()
                            + "); nenhum pool e roteado para tenant inactivo.");
        }
        return pool(resolution.clusterId(), role);
    }

    /** Clusters conhecidos pelo roteador. */
    public Set<String> clusterIds() {
        return Set.copyOf(poolsByCluster.keySet());
    }

    @Override
    public void close() {
        poolsByCluster.values().forEach(byRole -> byRole.values().forEach(HikariDataSource::close));
    }
}
