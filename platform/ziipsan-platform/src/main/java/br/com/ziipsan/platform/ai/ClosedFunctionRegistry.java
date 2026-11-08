package br.com.ziipsan.platform.ai;

import java.util.Set;

/** Agencia excessiva (OWASP LLM06): o modelo SO chama funcoes FECHADAS do portal, com as permissoes
 * do consumidor. Funcao fora da lista, ou sem a permissao do consumidor, e RECUSADA (teste do aceite).
 * Nenhuma funcao escreve decisao: as de leitura/dados sao deterministicas; a unica "acao" e pedir humano. */
public final class ClosedFunctionRegistry {
    public record ClosedFunction(String name, String requiredPermission) {}
    public static final Set<ClosedFunction> FUNCTIONS = Set.of(
        new ClosedFunction("EXPLAIN_BILL", "portal.bill.read"),
        new ClosedFunction("BILL_SECOND_COPY", "portal.bill.read"),
        new ClosedFunction("PROTOCOL_STATUS", "portal.protocol.read"),
        new ClosedFunction("HELP_SEARCH", "portal.help.read"),
        new ClosedFunction("REQUEST_HUMAN", "portal.chat.use"),
        new ClosedFunction("OPEN_SERVICE_REQUEST_DRAFT", "portal.protocol.create"));
    private ClosedFunctionRegistry() {}
    public static boolean isClosed(String name) { return FUNCTIONS.stream().anyMatch(f -> f.name().equals(name)); }
    public static boolean allowed(String name, Set<String> consumerPermissions) {
        return FUNCTIONS.stream().anyMatch(f -> f.name().equals(name)
            && consumerPermissions != null && consumerPermissions.contains(f.requiredPermission()));
    }
}
