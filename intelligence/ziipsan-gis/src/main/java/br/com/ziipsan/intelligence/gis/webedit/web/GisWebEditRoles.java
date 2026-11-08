package br.com.ziipsan.intelligence.gis.webedit.web;

/**
 * Authorities da GIS-09 (ED-53), no vocabulário gis:* existente
 * (GIS-06/ED-55): leitura com gis:view e escrita com
 * gis:import-edit, a autoridade de escrita GIS vigente. A
 * granularidade fina de SG-864/SG-865 (por camada e operação)
 * NÃO é authority nova inventada: é a tabela
 * gis_layer_permission (SQL em db/pendente), conferida no
 * serviço a cada operação. Uma authority própria de edição web
 * no catálogo (P0.13/P1.37) é pendência declarada no relatório.
 */
public final class GisWebEditRoles {

    static final String READ = "hasAuthority('gis:view')";
    static final String EDIT = "hasAuthority('gis:import-edit')";

    private GisWebEditRoles() {
    }
}
