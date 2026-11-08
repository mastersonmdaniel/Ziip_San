package br.com.ziipsan.intelligence.gis.thematic;

/**
 * Permissões da GIS-08 no catálogo {@code recurso:ação} (P0.13,
 * ADR-0040). A leitura usa as da publicação (GIS-06):
 * {@code gis:view} e {@code gis:view-personal} de
 * {@code GisPermissions}. A gestão de temáticos ganha permissão
 * própria, {@code gis:thematic-manage}, para não emprestar a de
 * estilo ({@code gis:style-manage} é da fonte única de simbologia,
 * outro objeto). Como as demais permissões GIS novas, nenhum perfil
 * de fábrica a inclui ainda (perfis por função são da P1.37) — hoje
 * é concessão direta.
 */
public final class GisThematicPermissions {

    public static final String THEMATIC_MANAGE = "gis:thematic-manage";

    private GisThematicPermissions() {
    }
}
