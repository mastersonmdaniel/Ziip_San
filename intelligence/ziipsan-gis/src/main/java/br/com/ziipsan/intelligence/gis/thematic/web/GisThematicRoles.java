package br.com.ziipsan.intelligence.gis.thematic.web;

/**
 * Autorização da GIS-08 pelo catálogo {@code recurso:ação}
 * (P0.13, ADR-0040), no mesmo desenho do {@code GisPublicationRoles}
 * (GIS-06). A guarda fina — camada pessoal por fonte, imutabilidade
 * da definição de fábrica e a regra do canal público — mora nos
 * serviços, não nestas constantes: o {@code @PreAuthorize} aqui é a
 * porta, não a regra inteira.
 */
final class GisThematicRoles {

    static final String VIEW = "hasAuthority('gis:view')";
    static final String MANAGE = "hasAuthority('gis:thematic-manage')";
    static final String EXPORT = "hasAuthority('gis:view')";

    static final String THEMATICS_BASE = "/api/v1/gis/thematics";
    static final String PUBLIC_BASE = "/api/v1/gis/public/thematics";
    static final String ORDERS_BASE = "/api/v1/gis/service-orders";
    static final String SELECTION_BASE = "/api/v1/gis/selection";
    static final String REPORTS_BASE = "/api/v1/gis/reports";

    private GisThematicRoles() {
    }
}
