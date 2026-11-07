package br.com.ziipsan.intelligence.gis.publication.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.intelligence.gis.publication.GisTileService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rota de tiles da GIS-06 (§10.1):
 * {@code GET /api/v1/gis/tiles/{layer}/{z}/{x}/{y}.mvt}.
 *
 * <p>Cache HTTP (correção pós-revisão, achado A1): a URL do tile
 * NÃO carrega tenant nem versão, então um {@code max-age} longo
 * faria o navegador servir tile velho após a aprovação de pacote
 * (quebrando a invalidação de até 1 min do aceite) e poderia
 * servir, num navegador compartilhado, o tile gerado para outro
 * usuário/tenant sem o servidor ser consultado. Por isso a
 * resposta é {@code private, no-cache, must-revalidate}: o cliente
 * PODE guardar o corpo, mas revalida SEMPRE com o {@code ETag}
 * (tenant + camada + versão + tile) antes de reusar — revalidação
 * igual devolve 304 barato, e ETag de outro tenant/versão nunca
 * casa com o recém-computado no servidor. A janela de frescor
 * end-to-end fica em zero (≤ 1 min do aceite) e o {@code private}
 * continua vedando CDN pública (§10.1).
 *
 * <p>Tile vazio (sem feições ou abaixo do zoom mínimo da camada)
 * devolve 204, como os servidores de tile fazem, para o MapLibre
 * não tratar ausência como erro — com o MESMO cache de
 * revalidação: um 204 em cache sem revalidar esconderia por
 * minutos a feição recém-aprovada.
 */
@RestController
@RequestMapping(GisPublicationRoles.TILES_BASE)
@PreAuthorize(GisPublicationRoles.VIEW)
public class GisTileController {

    public static final MediaType MVT_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.mapbox-vector-tile");

    private final GisTileService service;

    public GisTileController(GisTileService service) {
        this.service = service;
    }

    /**
     * O Cache-Control de TODAS as respostas de tile (200, 204 e
     * 304): privado, sem frescor implícito, revalidação obrigatória
     * por ETag. Visível para o teste de cabeçalhos.
     */
    static CacheControl tileCacheControl() {
        return CacheControl.noCache().cachePrivate().mustRevalidate();
    }

    @GetMapping(value = "/{layer}/{z}/{x}/{y}.mvt", produces = "application/vnd.mapbox-vector-tile")
    public ResponseEntity<byte[]> tile(@PathVariable String layer,
                                       @PathVariable int z,
                                       @PathVariable int x,
                                       @PathVariable int y,
                                       @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                               required = false) String ifNoneMatch) {
        GisTileService.TileResult result;
        try {
            result = service.tile(layer, z, x, y);
        } catch (IllegalArgumentException e) {
            // GisTileCacheKey recusou z/x/y fora da grade.
            throw new ApiException.BadRequest("tile-fora-da-grade", e.getMessage());
        }
        if (result.etag().equals(ifNoneMatch)) {
            return ResponseEntity.status(304)
                    .eTag(result.etag())
                    .cacheControl(tileCacheControl())
                    .build();
        }
        if (result.empty()) {
            return ResponseEntity.noContent()
                    .eTag(result.etag())
                    .cacheControl(tileCacheControl())
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MVT_MEDIA_TYPE)
                .eTag(result.etag())
                .cacheControl(tileCacheControl())
                .body(result.body());
    }
}
