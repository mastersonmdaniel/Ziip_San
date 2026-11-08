package br.com.ziipsan.intelligence.gis.asset.web;

/** Authorities da GIS-19: as do GIS-CADASTRO-TECNICO (gis.view / gis.edit). */
public final class GisAssetRoles {

    static final String READ = "hasAuthority('gis:view')";
    static final String EDIT = "hasAuthority('gis:edit')";

    private GisAssetRoles() {
    }
}
