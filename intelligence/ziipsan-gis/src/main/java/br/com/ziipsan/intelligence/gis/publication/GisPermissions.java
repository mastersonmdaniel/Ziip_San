package br.com.ziipsan.intelligence.gis.publication;

/**
 * Permissões da publicação GIS no catálogo {@code recurso:ação}
 * (P0.13, ADR-0040), correspondentes às permissões do §6.6 do
 * GIS-CADASTRO-TECNICO: {@code gis.view} → {@code gis:view};
 * {@code gis.view-personal} → {@code gis:view-personal} (camadas e
 * atributos ligados a imóvel e ligação); {@code gis.style.manage} →
 * {@code gis:style-manage}. Como as demais permissões GIS novas,
 * nenhum perfil de fábrica as inclui ainda (perfis por função são
 * da P1.37) — hoje são concessão direta.
 */
public final class GisPermissions {

    public static final String VIEW = "gis:view";
    public static final String VIEW_PERSONAL = "gis:view-personal";
    public static final String STYLE_MANAGE = "gis:style-manage";

    private GisPermissions() {
    }
}
