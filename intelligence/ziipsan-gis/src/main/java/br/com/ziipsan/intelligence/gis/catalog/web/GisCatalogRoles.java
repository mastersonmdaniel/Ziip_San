package br.com.ziipsan.intelligence.gis.catalog.web;

/**
 * Autorização do catálogo GIS pelo catálogo de permissões {@code recurso:ação}
 * (P0.13, ADR-0040), no mesmo desenho do RegistryRoles do cadastro.
 *
 * <p>Leitura para quem tem {@code gis:catalog-read}; criar, aprovar, recusar,
 * ativar e desativar exigem {@code gis:catalog-manage}. Nenhum perfil de
 * fábrica inclui estas permissões ainda (perfis por função são da P1.37):
 * hoje elas são concessão direta, como {@code registry:reveal-document}.
 */
final class GisCatalogRoles {

    static final String READ = "hasAuthority('gis:catalog-read')";
    static final String MANAGE = "hasAuthority('gis:catalog-manage')";

    static final String BASE = "/api/v1/gis/catalog";

    private GisCatalogRoles() {
    }
}
