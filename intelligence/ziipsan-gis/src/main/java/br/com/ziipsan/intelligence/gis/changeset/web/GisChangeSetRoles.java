package br.com.ziipsan.intelligence.gis.changeset.web;

/**
 * Autorização dos pacotes de edição GIS pelo catálogo de permissões
 * {@code recurso:ação} (P0.13, ADR-0040), no mesmo desenho do
 * {@code GisCatalogRoles} e do {@code GisValidationRoles}.
 *
 * <p>Correspondência com as permissões do §6.6 do GIS-CADASTRO-TECNICO:
 * {@code gis.edit} → {@code gis:change-set-edit}; {@code gis.review} →
 * {@code gis:change-set-review}; leitura → {@code gis:change-set-read}.
 * Como as demais permissões GIS novas, nenhum perfil de fábrica as
 * inclui ainda (perfis por função são da P1.37) — hoje são concessão
 * direta.
 */
final class GisChangeSetRoles {

    static final String READ = "hasAuthority('gis:change-set-read')";
    static final String EDIT = "hasAuthority('gis:change-set-edit')";
    static final String REVIEW = "hasAuthority('gis:change-set-review')";

    static final String BASE = "/api/v1/gis/change-sets";
    static final String NETWORK_AT_BASE = "/api/v1/gis/network-at";

    private GisChangeSetRoles() {
    }
}
