package br.com.ziipsan.intelligence.gis.validation.web;

/**
 * Autorização do painel de qualidade GIS pelo catálogo de permissões
 * {@code recurso:ação} (P0.13, ADR-0040), no mesmo desenho do
 * {@code GisCatalogRoles} do catálogo.
 *
 * <p>Leitura para quem tem {@code gis:quality-read}; aceitar problema
 * exige {@code gis:quality-manage}. São permissões novas desta frente:
 * como as do catálogo, nenhum perfil de fábrica as inclui ainda (perfis
 * por função são da P1.37) — hoje são concessão direta.
 */
final class GisValidationRoles {

    static final String READ = "hasAuthority('gis:quality-read')";
    static final String MANAGE = "hasAuthority('gis:quality-manage')";

    static final String BASE = "/api/v1/gis/quality";

    private GisValidationRoles() {
    }
}
