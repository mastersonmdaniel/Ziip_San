package br.com.ziipsan.commercial.collection.ampliada;

import java.util.List;

/**
 * Caixa proprio do prestador como item CONDICIONAL (ED-26, JOR-13/PD-07), ligado
 * por {@code pay.own_cashier_enabled} (desligado por padrao).
 *
 * <p>Desligado: menu, permissoes e rotas do caixa ficam INDISPONIVEIS - listas
 * vazias aqui, e {@link #requireEnabled(boolean)} recusa qualquer operacao de
 * caixa. Ligado: o conjunto completo abaixo e o que a fiacao (menu, catalogo
 * {@code recurso:acao} e roteador) deve expor; nada alem dele.
 */
public final class CashierAccess {

    public static final List<String> MENU_WHEN_ENABLED = List.of("Caixa do prestador");
    public static final List<String> PERMISSIONS_WHEN_ENABLED = List.of(
            "cashier:open", "cashier:receive", "cashier:bleed", "cashier:close", "cashier:check");
    public static final List<String> ROUTES_WHEN_ENABLED = List.of(
            "/caixa/abertura", "/caixa/recebimento", "/caixa/sangria",
            "/caixa/fechamento", "/caixa/conferencia");

    private CashierAccess() {
    }

    public static List<String> menu(boolean enabled) {
        return enabled ? MENU_WHEN_ENABLED : List.of();
    }

    public static List<String> permissions(boolean enabled) {
        return enabled ? PERMISSIONS_WHEN_ENABLED : List.of();
    }

    public static List<String> routes(boolean enabled) {
        return enabled ? ROUTES_WHEN_ENABLED : List.of();
    }

    public static void requireEnabled(boolean enabled) {
        if (!enabled) {
            throw new IllegalStateException("caixa proprio do prestador indisponivel:"
                    + " pay.own_cashier_enabled esta desligado (PD-07)");
        }
    }
}
