package br.com.ziipsan.intelligence.gis.importer.web;

/**
 * Autorização da importação GIS pelo catálogo de permissões
 * {@code recurso:ação} (P0.13, ADR-0040), no mesmo desenho do
 * {@code GisChangeSetRoles} e do {@code GisValidationRoles}.
 *
 * <p>Como as demais permissões GIS novas, nenhum perfil de fábrica as
 * inclui ainda (perfis por função são da P1.37) — hoje são concessão
 * direta. Rotas do frontend (§10.3): {@code /gis/imports}.
 */
final class GisImportRoles {

    static final String READ = "hasAuthority('gis:import-read')";
    static final String EDIT = "hasAuthority('gis:import-edit')";

    static final String BASE = "/api/v1/gis/imports";

    private GisImportRoles() {
    }
}
