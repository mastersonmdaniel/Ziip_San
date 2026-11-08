package br.com.ziipsan.commercial.billingrules;

/**
 * Envio em lote a cada N imoveis (ED-19): parametro configuravel do tenant
 * {@code field.send_every_n}, resolvido no servidor e carimbado no pacote da
 * rota ({@link RoutePackage#sendEveryN()}). A politica e pura para valer o
 * mesmo no aparelho (decide a sincronizacao parcial) e em qualquer teste:
 * sincroniza quando o numero de contas retidas atinge um multiplo de N, e
 * sempre no fim da rota (o restante nunca fica para o dia seguinte).
 */
public final class BatchSendPolicy {

    /** Chave do parametro (classe de parametros do tenant). */
    public static final String SEND_EVERY_N_KEY = "field.send_every_n";

    private final int sendEveryN;

    public BatchSendPolicy(int sendEveryN) {
        if (sendEveryN < 1) {
            throw new IllegalArgumentException("field.send_every_n deve ser >= 1: " + sendEveryN);
        }
        this.sendEveryN = sendEveryN;
    }

    public int sendEveryN() {
        return sendEveryN;
    }

    /** Atingiu o multiplo de N? (0 retidas nunca dispara envio vazio.) */
    public boolean shouldFlush(int retainedCount) {
        if (retainedCount < 0) {
            throw new IllegalArgumentException("Contagem de retidas negativa: " + retainedCount);
        }
        return retainedCount > 0 && retainedCount % sendEveryN == 0;
    }

    /** Fim da rota: qualquer saldo retido e enviado, seja qual for o N. */
    public boolean shouldFlushAtRouteEnd(int retainedCount) {
        return retainedCount > 0;
    }
}
