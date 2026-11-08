package br.com.ziipsan.intelligence.gis.maneuver.web;

/** Authorities da GIS-11: as do §6.6 do GIS-CADASTRO-TECNICO (gis.view / gis.edit). */
public final class GisManeuverRoles {

    static final String READ = "hasAuthority('gis:view')";
    static final String EDIT = "hasAuthority('gis:edit')";

    private GisManeuverRoles() {
    }
}
