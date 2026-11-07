package br.com.ziipsan.platform.statuspage;

/**
 * P3.18: estado público de um componente (e, por agregação, da página).
 *
 * <p>Valores gravados/exibidos em inglês (regra de idioma do plano v3.0);
 * os textos exibidos ao visitante são traduzidos na página, não aqui.
 *
 * <p>{@link #UNKNOWN} existe de propósito: componente com sonda que nunca
 * mediu não é {@code OPERATIONAL} — afirmar "no ar" sem medição seria
 * exatamente o dado inventado que a página de status não pode publicar
 * (mesma regra da V9_1 para o watchdog: ausência é "nunca coletou").
 *
 * <p>A prioridade ({@link #severity()}) decide a agregação e os conflitos:
 * uma interrupção real durante uma janela de manutenção continua sendo
 * interrupção ({@code OUTAGE} vence {@code MAINTENANCE}), e degradação
 * real vence manutenção planejada.
 */
public enum ComponentStatus {
    OUTAGE(4),
    DEGRADED(3),
    MAINTENANCE(2),
    UNKNOWN(1),
    OPERATIONAL(0);

    private final int severity;

    ComponentStatus(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    /** O pior dos dois estados (maior severidade). */
    public static ComponentStatus worst(ComponentStatus a, ComponentStatus b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Estados são obrigatórios para agregar");
        }
        return a.severity >= b.severity ? a : b;
    }
}
