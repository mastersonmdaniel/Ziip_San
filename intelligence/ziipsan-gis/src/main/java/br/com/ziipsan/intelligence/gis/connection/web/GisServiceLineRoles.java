package br.com.ziipsan.intelligence.gis.connection.web;

/**
 * Autorização dos ramais automáticos pelo catálogo recurso:ação
 * (P0.13, ADR-0040). Gerar ramal é editar pacote (§6.6: {@code gis.edit}
 * → {@code gis:change-set-edit} da GIS-04); a aprovação do pacote
 * gerado usa o fluxo e a permissão de revisão da GIS-04, sem caminho
 * próprio — quem gera não aprova o próprio pacote.
 */
final class GisServiceLineRoles {

    static final String GENERATE = "hasAuthority('gis:change-set-edit')";
    static final String READ = "hasAuthority('gis:change-set-read')";

    static final String BASE = "/api/v1/gis/service-lines";

    private GisServiceLineRoles() {
    }
}
