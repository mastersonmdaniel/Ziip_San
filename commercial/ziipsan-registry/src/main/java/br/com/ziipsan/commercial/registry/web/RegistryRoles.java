package br.com.ziipsan.commercial.registry.web;

/**
 * Autorização mínima do cadastro (papéis do realm "ziipsan", que a aplicação converte em ROLE_*).
 *
 * <p>Leitura para qualquer papel do ZIIPSAN; escrita para operador, gestor e administrador. Revelar o
 * documento em claro exige um papel à parte ({@code registry.reveal-document}), que nenhum dos outros
 * inclui: quem cadastra não precisa, em regra, ver o CPF depois de gravado.
 */
final class RegistryRoles {

    static final String READ = "hasAnyRole('VIEWER','OPERATOR','MANAGER','ADMIN')";
    static final String WRITE = "hasAnyRole('OPERATOR','MANAGER','ADMIN')";
    static final String REVEAL_DOCUMENT = "hasRole('registry.reveal-document')";

    // P0.10: rotas de negócio sob /api/v1.
    static final String BASE = "/api/v1/registry";

    private RegistryRoles() {
    }
}
