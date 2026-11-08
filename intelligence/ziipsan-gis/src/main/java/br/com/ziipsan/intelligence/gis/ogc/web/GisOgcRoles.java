package br.com.ziipsan.intelligence.gis.ogc.web;

/** Autorização OGC/exportação: gis:view para tudo (sem anônimo); pessoal é por camada, no serviço. */
final class GisOgcRoles {
    static final String VIEW = "hasAuthority('gis:view')";
    static final String OGC_BASE = "/api/v1/gis/ogc";
    static final String WFS_BASE = "/api/v1/gis/wfs";
    static final String EXPORT_BASE = "/api/v1/gis/exports";
    private GisOgcRoles(){}
}
