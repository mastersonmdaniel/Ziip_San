package br.com.ziipsan.reporting.panels;

/**
 * ED-49: os SETE paineis padrao do aceite, na ordem literal do acceptance
 * ("paineis de arrecadacao, atendimento, cobranca, faturamento, medicao,
 * operacional e indicadores estrategicos"). O titulo e o texto do aceite;
 * nenhum outro painel existe nesta entrega.
 *
 * <p>POC: o roteiro VISAN (alinea t) nomeia POC propria para cinco deles
 * (POC-77 arrecadacao, POC-78 atendimento, POC-79 cobranca, POC-80
 * faturamento, POC-81 indicadores estrategicos). MEDICAO e OPERACIONAL
 * NAO tem POC propria nomeada no roteiro — {@code pocRef == null} e a
 * ausencia declarada, nao um numero presumido: os dois sao cobertos pelo
 * acceptance da ED-49 e pela faixa comum de requisitos do roteiro
 * (TR-909 a TR-1007), como os demais.
 */
public enum PanelKind {

    ARRECADACAO("Arrecadação", "POC-77"),
    ATENDIMENTO("Atendimento", "POC-78"),
    COBRANCA("Cobrança", "POC-79"),
    FATURAMENTO("Faturamento", "POC-80"),
    MEDICAO("Medição", null),
    OPERACIONAL("Operacional", null),
    ESTRATEGICO("Indicadores estratégicos", "POC-81");

    private final String title;
    private final String pocRef;

    PanelKind(String title, String pocRef) {
        this.title = title;
        this.pocRef = pocRef;
    }

    public String title() {
        return title;
    }

    /** POC do roteiro VISAN, ou {@code null} quando o roteiro nao nomeia uma (MEDICAO/OPERACIONAL). */
    public String pocRef() {
        return pocRef;
    }
}
