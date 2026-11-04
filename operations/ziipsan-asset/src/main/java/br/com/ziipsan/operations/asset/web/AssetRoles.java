package br.com.ziipsan.operations.asset.web;

/**
 * Autorizacao do cadastro de ativos pelo catalogo de permissoes
 * {@code recurso:acao} (P0.13, ADR-0040), no mesmo desenho do GisCatalogRoles
 * (P1.27). Leitura para quem tem {@code asset:read}; inclusao e alteracao
 * (sempre por OS interna, ver AssetService) exigem {@code asset:manage}.
 * Nenhum perfil de fabrica inclui estas permissoes ainda (perfis por funcao
 * sao da P1.37): hoje elas sao concessao direta.
 */
final class AssetRoles {

    static final String READ = "hasAuthority('asset:read')";
    static final String MANAGE = "hasAuthority('asset:manage')";

    static final String BASE = "/api/v1/assets";

    private AssetRoles() {
    }
}
