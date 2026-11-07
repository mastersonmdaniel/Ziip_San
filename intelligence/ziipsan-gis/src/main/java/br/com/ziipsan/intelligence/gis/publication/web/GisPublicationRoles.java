package br.com.ziipsan.intelligence.gis.publication.web;

/**
 * Autorização da publicação GIS pelo catálogo de permissões
 * {@code recurso:ação} (P0.13, ADR-0040), no mesmo desenho do
 * {@code GisCatalogRoles}. Correspondência com o §6.6:
 * {@code gis.view} → {@code gis:view}; {@code gis.view-personal} →
 * {@code gis:view-personal}; {@code gis.style.manage} →
 * {@code gis:style-manage}. A guarda da camada pessoal NÃO é só o
 * {@code @PreAuthorize} da classe: ela é por camada e mora no
 * serviço de tiles/estilo (403 para camada pessoal sem a permissão,
 * com {@code gis:view} válido para as demais).
 */
final class GisPublicationRoles {

    static final String VIEW = "hasAuthority('gis:view')";
    static final String VIEW_PERSONAL = "hasAuthority('gis:view-personal')";
    static final String STYLE_MANAGE = "hasAuthority('gis:style-manage')";

    static final String TILES_BASE = "/api/v1/gis/tiles";
    static final String MAP_BASE = "/api/v1/gis/map";

    private GisPublicationRoles() {
    }
}
