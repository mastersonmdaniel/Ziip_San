package br.com.ziipsan.reporting.panels;

import java.util.List;
import java.util.Optional;

/**
 * ED-49: o catalogo dos sete paineis padrao. Slugs PROPOSTOS (prefixo
 * {@code ziipsan-painel-}); a ordem e a do acceptance. O catalogo NAO
 * amarra indicador nenhum: qual indicador aparece em qual painel e a
 * ligacao DECLARADA do tenant (tabela {@code panel_indicator_bindings}
 * do SQL pendente da ED-49), validada contra o registro da P3.41 na
 * montagem — nenhum codigo de indicador e presumido aqui, porque nenhuma
 * fonte lista os indicadores de cada painel.
 */
public final class StandardPanelCatalog {

    private static final List<PanelDefinition> ALL = List.of(
            new PanelDefinition(PanelKind.ARRECADACAO, "ziipsan-painel-arrecadacao", false),
            new PanelDefinition(PanelKind.ATENDIMENTO, "ziipsan-painel-atendimento", false),
            new PanelDefinition(PanelKind.COBRANCA, "ziipsan-painel-cobranca", false),
            new PanelDefinition(PanelKind.FATURAMENTO, "ziipsan-painel-faturamento", false),
            new PanelDefinition(PanelKind.MEDICAO, "ziipsan-painel-medicao", false),
            new PanelDefinition(PanelKind.OPERACIONAL, "ziipsan-painel-operacional", false),
            new PanelDefinition(PanelKind.ESTRATEGICO, "ziipsan-painel-estrategico", false));

    private StandardPanelCatalog() {
    }

    public static List<PanelDefinition> all() {
        return ALL;
    }

    public static Optional<PanelDefinition> byKind(PanelKind kind) {
        return ALL.stream().filter(d -> d.kind() == kind).findFirst();
    }

    /** Paineis sem nenhuma celula pedida: lacuna de fiacao nomeada, nunca painel inventado. */
    public static List<PanelKind> unboundPanels(List<PanelCellRequest> requests) {
        return ALL.stream()
                .map(PanelDefinition::kind)
                .filter(kind -> requests.stream().noneMatch(r -> r.panel() == kind))
                .toList();
    }
}
