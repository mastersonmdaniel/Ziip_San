package br.com.ziipsan.intelligence.gis.publication;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.DirtyStatusView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Tiles vetoriais da GIS-06 (§10.1): gera o MVT por
 * {@code ST_AsMVT} sobre {@code geom_web}, com cache cuja chave é
 * {@link GisTileCacheKey} — (tenant, camada, z, x, y, versão).
 *
 * <p>Isolamento (aceite): o tenant da chave e de todos os filtros
 * vem do contexto da requisição; camada pessoal sem
 * {@code gis.view-personal} recebe 403 ANTES de qualquer consulta
 * ou leitura de cache — nem o tile em cache de outro usuário do
 * mesmo tenant é servido a quem não tem a permissão.
 *
 * <p>Invalidação (aceite: até 1 min após aprovação de pacote): a
 * V13_12 incrementa {@code layer_version} na transação da
 * aprovação; como a versão faz parte da chave, o próximo pedido já
 * monta outra chave e regenera — a janela efetiva é zero após o
 * commit. {@link #processDirtyTiles()} é o worker de evicção
 * física das linhas de {@code gis_tile_dirty} (chamado pelo
 * agendador da app em intervalo curto; a pendência mais antiga é
 * exposta em {@link #dirtyStatus()} para a operação conferir a
 * janela).
 *
 * <p>O fornecedor de permissão é injetável para o teste unitário
 * exercitar a guarda sem montar o SecurityContext.
 */
@Service
@Transactional(readOnly = true)
public class GisTileService {

    private final JdbcTemplate jdbc;
    private final GisTileCache cache;
    private final BooleanSupplier canViewPersonal;

    public GisTileService(JdbcTemplate jdbc, GisTileCache cache) {
        this(jdbc, cache, GisPublicationContext::canViewPersonal);
    }

    GisTileService(JdbcTemplate jdbc, GisTileCache cache, BooleanSupplier canViewPersonal) {
        this.jdbc = jdbc;
        this.cache = cache;
        this.canViewPersonal = canViewPersonal;
    }

    public record TileResult(byte[] body, String etag, boolean empty) {
    }

    /**
     * Tile da camada. Camada desconhecida ou inativa → 404; z fora
     * da grade → a própria chave recusa (400 via validação do
     * controller); abaixo do zoom mínimo da camada → tile vazio
     * (o MapLibre trata como "sem feições", sem erro).
     */
    public TileResult tile(String layerKey, int z, int x, int y) {
        UUID tenantId = GisPublicationContext.tenantId();
        GisTileLayers layer = GisTileLayers.byKey(layerKey)
                .orElseThrow(() -> new ApiException.NotFound("Camada não encontrada."));
        if (layer.personal() && !canViewPersonal.getAsBoolean()) {
            throw new ApiException.Forbidden(
                    "Camada pessoal: exige a permissão gis.view-personal.");
        }
        long version = layerVersion(tenantId, layerKey);
        GisTileCacheKey key = new GisTileCacheKey(tenantId, layerKey, version, z, x, y);
        if (!layer.servesAt(z)) {
            return new TileResult(new byte[0], key.etag(), true);
        }
        return cache.get(key)
                .map(body -> new TileResult(body, key.etag(), body.length == 0))
                .orElseGet(() -> {
                    byte[] body = jdbc.queryForObject(GisTileSql.tileSql(layer, z),
                            byte[].class, GisTileSql.tileArgs(layer, tenantId, z, x, y));
                    byte[] safe = body == null ? new byte[0] : body;
                    cache.put(key, safe);
                    return new TileResult(safe, key.etag(), safe.length == 0);
                });
    }

    /** Versão vigente da camada no tenant (parte da chave de cache). */
    public long layerVersion(UUID tenantId, String layerKey) {
        List<Long> rows = jdbc.queryForList(
                "SELECT layer_version FROM gis_layer WHERE tenant_id = ? AND key = ? AND active",
                Long.class, tenantId, layerKey);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Camada não encontrada ou inativa.");
        }
        return rows.get(0);
    }

    /**
     * Worker de evicção: consome as linhas pendentes de
     * {@code gis_tile_dirty} (marcadas na aprovação, V13_12),
     * despeja as entradas físicas correspondentes — inclusive as de
     * versões anteriores da camada, que a troca de versão já tornou
     * inalcançáveis — e carimba {@code processed_at}. Devolve quantas
     * linhas foram processadas. Deve ser chamado em intervalo curto
     * (≤ 30 s) pelo agendador da app; a garantia de frescor NÃO
     * depende dele (é a versão na chave).
     */
    @Transactional
    public int processDirtyTiles(int batchSize) {
        UUID tenantId = GisPublicationContext.tenantId();
        List<DirtyRow> rows = jdbc.query("""
                SELECT id, layer_key, z, x, y FROM gis_tile_dirty
                WHERE tenant_id = ? AND processed_at IS NULL
                ORDER BY created_at LIMIT ?
                """, (rs, i) -> new DirtyRow(rs.getObject("id", UUID.class),
                rs.getString("layer_key"), rs.getInt("z"), rs.getInt("x"), rs.getInt("y")),
                tenantId, Math.max(1, Math.min(batchSize, 10_000)));
        for (DirtyRow row : rows) {
            cache.evictLayer(tenantId, row.layerKey());
            jdbc.update("UPDATE gis_tile_dirty SET processed_at = clock_timestamp() WHERE id = ?",
                    row.id());
        }
        return rows.size();
    }

    private record DirtyRow(UUID id, String layerKey, int z, int x, int y) {
    }

    /** Saúde da invalidação para a operação (janela do aceite). */
    public DirtyStatusView dirtyStatus() {
        UUID tenantId = GisPublicationContext.tenantId();
        return jdbc.queryForObject("""
                SELECT count(*) AS pending, min(created_at) AS oldest
                FROM gis_tile_dirty WHERE tenant_id = ? AND processed_at IS NULL
                """, (rs, i) -> new DirtyStatusView(rs.getLong("pending"),
                instant(rs.getTimestamp("oldest"))), tenantId);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
