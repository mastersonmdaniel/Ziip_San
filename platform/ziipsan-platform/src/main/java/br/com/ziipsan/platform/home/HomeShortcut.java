package br.com.ziipsan.platform.home;

import java.util.List;

/**
 * Atalho da pagina inicial (P3.39): um destino do mapa do produto com a
 * permissao que o portao exige ({@code recurso:acao} do catalogo da P0.13).
 * A composicao por perfil e DERIVADA, nunca digitada por perfil: o atalho
 * entra na pagina de um perfil de fabrica se e somente se a permissao dele
 * esta no conjunto do perfil (PermissionCatalog da P1.37) — perfil novo ou
 * permissao nova mudam a pagina sem tocar nesta classe.
 *
 * <p>{@code requiredPermission} nula = destino UNIVERSAL (a caixa, a central
 * e a agenda do PROPRIO usuario: o servidor ja filtra tudo pelo dono, como
 * nas APIs /api/v1/me/* das P2.28/P3.36). As rotas abaixo sao as das telas
 * donas de cada area, como entregues pelas tarefas delas; atalho cuja area
 * ainda nao tem tela nao entra neste catalogo (nada de destino inventado).
 */
public record HomeShortcut(String code, String label, String route, String requiredPermission) {

    public HomeShortcut {
        if (code == null || code.isBlank() || label == null || label.isBlank()
                || route == null || route.isBlank()) {
            throw new IllegalArgumentException("atalho exige codigo, rotulo e rota");
        }
    }

    /** Universal: aparece para todo perfil (inclusive os sem permissao catalogada). */
    public boolean isUniversal() {
        return requiredPermission == null;
    }

    /** Catalogo de fabrica dos atalhos, na ordem de exibicao. */
    public static final List<HomeShortcut> FACTORY = List.of(
            new HomeShortcut("minhas-tarefas", "Minhas tarefas", "/minhas-tarefas", null),
            new HomeShortcut("notificacoes", "Notificacoes", "/notificacoes", null),
            new HomeShortcut("agenda", "Agenda", "/agenda", null),
            new HomeShortcut("cadastro", "Cadastro", "/cadastro", "registry:read"),
            new HomeShortcut("usuarios", "Usuarios do tenant", "/tenant/usuarios", "user:read"),
            new HomeShortcut("protocolos", "Protocolos de atendimento", "/atendimento/protocolos", "protocol:read"),
            new HomeShortcut("suporte", "Suporte", "/suporte", "support:read"),
            new HomeShortcut("cadastro-tecnico", "Cadastro tecnico (GIS)", "/gis/catalogo", "gis:catalog-read"),
            new HomeShortcut("certificacoes", "Certificacoes", "/certificacoes", "cert:read"),
            new HomeShortcut("modelos", "Modelos de documento", "/documentos/modelos", "template:read"),
            new HomeShortcut("meu-contrato", "Meu contrato", "/tenant/meu-contrato", "contract:read-own"),
            new HomeShortcut("parametros", "Parametros do tenant", "/tenant/parametros", "parameters:manage"),
            new HomeShortcut("marca", "Marca do tenant", "/tenant/marca", "brand:manage"),
            new HomeShortcut("integracoes", "Integracoes", "/tenant/integracoes", "integration:manage"));
}
