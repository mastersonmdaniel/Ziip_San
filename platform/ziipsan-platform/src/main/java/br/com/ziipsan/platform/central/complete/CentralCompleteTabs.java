package br.com.ziipsan.platform.central.complete;

import java.util.List;

/**
 * P3.15 (ADR-0018, ADR-0025): registro COMPLETO das abas da Central Ziip.
 *
 * <p>A P1.17 entregou Visão geral, Plataforma, Segurança e Alertas; a
 * P2.11, Faturamento e Fiscal (registro próprio em
 * {@code platform.central.CentralTab}, arquivo daquela tarefa — esta
 * classe NÃO o altera). A P3.15 acrescenta as sete abas próprias do seu
 * aceite e declara as três remissões como fronteira, sem implementar o
 * escopo alheio:
 * <ul>
 *   <li>próprias: ARRECADACAO, PIX, LGPD, REGULATORIO, INFRAESTRUTURA,
 *       RADAR, SUPORTE;</li>
 *   <li>remissão {@link Boundary#REFERRAL}: INTEGRACOES é a aba que a
 *       P3.37 preenche (o hub depende DESTA tarefa); ESTOQUE é da ED-58;
 *       a agenda de obrigações do tenant é da ED-98 e NÃO é aba da
 *       Central — é tela do tenant, registrada aqui só como remissão
 *       declarada ({@link #OBRIGACOES_TENANT}, sem rota de Central).</li>
 * </ul>
 *
 * <p>Como na P2.11, a Central só lê AGREGADOS (ADR-0018): nenhuma aba
 * desta tarefa carrega dado pessoal nem linha de tenant.
 */
public enum CentralCompleteTabs {

    ARRECADACAO("arrecadacao", "Arrecadação", "/central/arrecadacao", "P3.15", Boundary.OWN),
    PIX("pix", "Pix", "/central/pix", "P3.15", Boundary.OWN),
    LGPD("lgpd", "LGPD", "/central/lgpd", "P3.15", Boundary.OWN),
    REGULATORIO("regulatorio", "Regulatório", "/central/regulatorio", "P3.15", Boundary.OWN),
    INFRAESTRUTURA("infraestrutura", "Infraestrutura", "/central/infraestrutura", "P3.15", Boundary.OWN),
    RADAR("radar", "Radar", "/central/radar", "P3.15", Boundary.OWN),
    SUPORTE("suporte", "Suporte", "/central/suporte", "P3.15", Boundary.OWN),
    INTEGRACOES("integracoes", "Integrações", "/central/integracoes", "P3.37", Boundary.REFERRAL),
    ESTOQUE("estoque", "Estoque", "/central/estoque", "ED-58", Boundary.REFERRAL),
    /** Remissão do aceite: agenda de obrigações do tenant (ED-98). Não é aba da Central. */
    OBRIGACOES_TENANT("obrigacoes-tenant", "Agenda de obrigações do tenant", null, "ED-98", Boundary.REFERRAL);

    /** OWN = aba entregue pela P3.15; REFERRAL = só a remissão declarada. */
    public enum Boundary { OWN, REFERRAL }

    private final String key;
    private final String title;
    private final String route;
    private final String originTask;
    private final Boundary boundary;

    CentralCompleteTabs(String key, String title, String route, String originTask, Boundary boundary) {
        this.key = key;
        this.title = title;
        this.route = route;
        this.originTask = originTask;
        this.boundary = boundary;
    }

    public String key() { return key; }
    public String title() { return title; }
    /** Rota na Central; {@code null} apenas na remissão que não é aba (ED-98). */
    public String route() { return route; }
    public String originTask() { return originTask; }
    public Boundary boundary() { return boundary; }

    /** As sete abas próprias da P3.15, na ordem do aceite. */
    public static List<CentralCompleteTabs> deliveredByP315() {
        return List.of(ARRECADACAO, PIX, LGPD, REGULATORIO, INFRAESTRUTURA, RADAR, SUPORTE);
    }

    /** As remissões do aceite (P3.37, ED-58, ED-98) — fronteira, não escopo. */
    public static List<CentralCompleteTabs> referrals() {
        return List.of(INTEGRACOES, ESTOQUE, OBRIGACOES_TENANT);
    }
}
