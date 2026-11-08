package br.com.ziipsan.intelligence.gis.validation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Componentes conexos da rede de um tenant (GIS-03, §5.6 e §5.7).
 *
 * <p>O cálculo é Java ({@link GisGraph}), sobre a adjacência persistida
 * {@code node_1_id}/{@code node_2_id} de {@code gis_arc} — arcos sem as
 * duas pontas não ligam nada e ficam fora, como manda o modelo nó-arco.
 * A comparação com o lote do pgRouting usa
 * {@link GisComponentConsistencyChecker}: é a prova de que o traçado
 * próprio e o {@code pgr_connectedComponents} enxergam a mesma rede.
 *
 * <p>O tenant é sempre parâmetro explícito (nunca ThreadLocal): quem
 * chama é a API (com o tenant da requisição) ou o worker (com o tenant
 * da mensagem da outbox, regra da P1.9).
 */
@Service
@Transactional(readOnly = true)
public class GisComponentService {

    private final JdbcTemplate jdbc;

    public GisComponentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Componentes Java do tenant: nó → representante (menor UUID do componente). */
    public Map<UUID, UUID> javaComponents(UUID tenantId) {
        List<GisGraph.Edge> edges = loadEdges(tenantId);
        Set<UUID> nodes = new HashSet<>();
        edges.forEach(e -> {
            nodes.add(e.node1Id());
            nodes.add(e.node2Id());
        });
        return GisGraph.components(nodes, edges);
    }

    /**
     * Compara, em lote, os componentes Java com os do pgRouting.
     *
     * <p>O SQL de arestas do {@code pgr_connectedComponents} é texto por
     * desenho da extensão; o {@code tenantId} interpolado nele é um
     * {@link UUID} já validado pelo tipo (forma canônica, sem aspas nem
     * espaços), nunca texto livre de usuário.
     */
    public List<GisComponentConsistencyChecker.Divergence> checkAgainstPgRouting(UUID tenantId) {
        Map<UUID, Long> pgr = new HashMap<>();
        String edgesSql = "SELECT id, node_1_id AS source, node_2_id AS target, 1 AS cost "
                + "FROM gis_arc WHERE tenant_id = '" + tenantId + "' "
                + "AND node_1_id IS NOT NULL AND node_2_id IS NOT NULL";
        // Correção M3 (D1): o lambda linha a linha é um RowCallbackHandler
        // (preenche o mapa, sem valor agregado de retorno); sem o cast, a
        // chamada é ambígua com o overload de ResultSetExtractor e o
        // módulo não compila.
        jdbc.query("SELECT node, component FROM pgr_connectedComponents(?)",
                ps -> ps.setString(1, edgesSql),
                (RowCallbackHandler) rs -> pgr.put(
                        rs.getObject("node", UUID.class), rs.getLong("component")));
        return GisComponentConsistencyChecker.compare(javaComponents(tenantId), pgr);
    }

    private List<GisGraph.Edge> loadEdges(UUID tenantId) {
        return jdbc.query("""
                SELECT id, node_1_id, node_2_id FROM gis_arc
                WHERE tenant_id = ? AND node_1_id IS NOT NULL AND node_2_id IS NOT NULL
                """, (rs, row) -> new GisGraph.Edge(
                rs.getObject("id", UUID.class),
                rs.getObject("node_1_id", UUID.class),
                rs.getObject("node_2_id", UUID.class)), tenantId);
    }
}
