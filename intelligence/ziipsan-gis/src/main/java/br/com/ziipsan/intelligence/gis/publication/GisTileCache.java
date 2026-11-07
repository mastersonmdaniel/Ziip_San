package br.com.ziipsan.intelligence.gis.publication;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache em memória dos tiles MVT na própria {@code api} (§10.1: cache
 * na api ou em armazenamento de objetos; nunca em CDN pública).
 *
 * <p>Isolamento: a chave ({@link GisTileCacheKey}) carrega tenant e
 * versão da camada, portanto (a) um tile de um tenant nunca é servido
 * a outro — a chave de quem pede é outra; e (b) após a aprovação de
 * pacote (que incrementa a versão na V13_12), as entradas velhas
 * ficam inalcançáveis de imediato, dentro da janela de 1 min do
 * aceite com folga (a janela efetiva é a da transação de aprovação).
 * {@link #evictDirty} faz a evicção física das linhas de
 * {@code gis_tile_dirty} — higiene de memória do worker, não a
 * garantia de frescor.
 *
 * <p>Limite de entradas com evicção simples do mais antigo por
 * inserção aproximada (remove 10% ao estourar): tile é binário
 * pequeno e regenerável; um LRU completo não se paga aqui.
 */
@Component
public class GisTileCache {

    static final int MAX_ENTRIES = 4096;

    private final Map<GisTileCacheKey, byte[]> entries = new ConcurrentHashMap<>();

    public Optional<byte[]> get(GisTileCacheKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    public void put(GisTileCacheKey key, byte[] tile) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.keySet().stream().limit(MAX_ENTRIES / 10).toList()
                    .forEach(entries::remove);
        }
        entries.put(key, tile);
    }

    /** Evicção física de um tile sujo (linha de gis_tile_dirty). */
    public void evictDirty(UUID tenantId, String layerKey, long layerVersion,
                           int z, int x, int y) {
        entries.remove(new GisTileCacheKey(tenantId, layerKey, layerVersion, z, x, y));
    }

    /** Descarta tudo de um tenant+camada (uso administrativo/teste). */
    public void evictLayer(UUID tenantId, String layerKey) {
        entries.keySet().removeIf(k -> k.tenantId().equals(tenantId)
                && k.layerKey().equals(layerKey));
    }

    public int size() {
        return entries.size();
    }
}
