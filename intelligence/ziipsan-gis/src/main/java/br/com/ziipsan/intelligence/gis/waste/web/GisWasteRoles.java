package br.com.ziipsan.intelligence.gis.waste.web;

/**
 * Authorities da GIS-14: as do §6.6 do GIS-CADASTRO-TECNICO
 * (gis.view / gis.edit), no padrão das demais superfícies GIS.
 */
public final class GisWasteRoles {

    static final String READ = "hasAuthority('gis:view')";
    static final String EDIT = "hasAuthority('gis:edit')";

    private GisWasteRoles() {
    }
}
