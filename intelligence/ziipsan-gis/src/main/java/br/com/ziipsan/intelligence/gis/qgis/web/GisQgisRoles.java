package br.com.ziipsan.intelligence.gis.qgis.web;

/**
 * Authorities da GIS-10 (mesmo vocabulario gis:* das GIS-06/07/08).
 * Pedir role QGIS usa gis:import-edit como autoridade de escrita GIS
 * existente enquanto o catalogo de permissoes (P0.13/P1.37) nao ganha
 * uma permissao propria de administracao QGIS — pendencia declarada
 * no relatorio da ED-55; nenhuma authority nova foi inventada aqui.
 */
public final class GisQgisRoles {

    static final String READ = "hasAuthority('gis:view')";
    static final String ADMIN = "hasAuthority('gis:import-edit')";

    private GisQgisRoles() {
    }
}
