package br.com.ziipsan.platform.central;

import java.util.List;

/**
 * Abas da Central da Ziip (P1.17 + P2.11, ADR-0018). A P1.17 entregou
 * Visão geral, Plataforma, Segurança e Alertas; a P2.11 acrescenta
 * {@link #FATURAMENTO} e {@link #FISCAL} — e só elas são desta tarefa.
 *
 * <p>Fronteira com a P2.33 (registrada no plano): a aba {@link #FISCAL}
 * é a visão da OPERADORA Ziip sobre a fila fiscal agregada (contagens e
 * idades por tenant, sem dado pessoal — isolamento da V9_1). A fila de
 * documentos fiscais OPERADA pelo tenant (reenvio, inutilização,
 * cancelamento pendente) é a tela da P2.33, em outro módulo e com outras
 * permissões; nenhuma ação dessa fila existe nesta aba.
 */
public enum CentralTab {
    VISAO_GERAL("visao-geral", "Visão geral", "/central/visao-geral", "P1.17"),
    PLATAFORMA("plataforma", "Plataforma", "/central/plataforma", "P1.17"),
    SEGURANCA("seguranca", "Segurança", "/central/seguranca", "P1.17"),
    ALERTAS("alertas", "Alertas", "/central/alertas", "P1.17"),
    FATURAMENTO("faturamento", "Faturamento", "/central/faturamento", "P2.11"),
    FISCAL("fiscal", "Fiscal", "/central/fiscal", "P2.11");

    private final String key;
    private final String title;
    private final String route;
    private final String originTask;

    CentralTab(String key, String title, String route, String originTask) {
        this.key = key;
        this.title = title;
        this.route = route;
        this.originTask = originTask;
    }

    public String key() { return key; }
    public String title() { return title; }
    public String route() { return route; }
    public String originTask() { return originTask; }

    /** Abas entregues pela P2.11, na ordem em que aparecem na Central. */
    public static List<CentralTab> deliveredByP211() {
        return List.of(FATURAMENTO, FISCAL);
    }
}
