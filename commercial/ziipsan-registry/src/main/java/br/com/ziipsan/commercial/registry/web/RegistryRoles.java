package br.com.ziipsan.commercial.registry.web;

/**
 * Autorização do cadastro pelo catálogo de permissões {@code recurso:ação} (P0.13, ADR-0040),
 * no lugar dos papéis fixos por {@code hasAnyRole}.
 *
 * <p>Leitura para qualquer perfil de fábrica; escrita para operador, gestor e administrador.
 * Revelar o documento em claro exige a permissão direta {@code registry:reveal-document}, que
 * nenhum perfil de fábrica inclui: quem cadastra não precisa, em regra, ver o CPF depois de
 * gravado. Os perfis do token são expandidos para permissões pelo conversor JWT da aplicação.
 */
final class RegistryRoles {

    static final String READ = "hasAuthority('registry:read')";
    static final String WRITE = "hasAuthority('registry:write')";
    static final String REVEAL_DOCUMENT = "hasAuthority('registry:reveal-document')";

    // P0.10: rotas de negócio sob /api/v1.
    static final String BASE = "/api/v1/registry";

    private RegistryRoles() {
    }
}
