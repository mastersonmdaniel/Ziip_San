package br.com.ziipsan.intelligence.gis.expandedimport.web;

/** Authorities da GIS-18: as mesmas da importacao GIS-07. */
public final class GisExpandedImportRoles {

    static final String READ = "hasAuthority('gis:import-read')";
    static final String EDIT = "hasAuthority('gis:import-edit')";

    private GisExpandedImportRoles() {
    }
}
