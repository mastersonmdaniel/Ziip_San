package br.com.ziipsan.intelligence.gis.clickos.web;

/**
 * Authorities da II-1: as mesmas da GIS-11 (§6.6 do
 * GIS-CADASTRO-TECNICO: gis.view / gis.edit), como no
 * {@code GisManeuverRoles} — propagar e pré-visualizar é leitura;
 * criar a OS é edição.
 */
final class GisClickOsRoles {

    static final String READ = "hasAuthority('gis:view')";
    static final String EDIT = "hasAuthority('gis:edit')";

    static final String BASE = "/api/v1/gis/click-os";

    private GisClickOsRoles() {
    }
}
