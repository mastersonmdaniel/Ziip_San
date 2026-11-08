package br.com.ziipsan.platform.tenantalert;

/**
 * ED-07: recusa explícita — evento fora do catálogo da P1.38, em versão não
 * declarada, ou regra duplicada. É a mesma semântica da recusa da P3.36
 * ({@code event-not-in-catalog}): nada é gravado e nenhum alerta nasce.
 */
public final class AlertRejectedException extends RuntimeException {

    private final String code;

    public AlertRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AlertRejectedException eventNotInCatalog(String eventName, int eventVersion) {
        return new AlertRejectedException("event-not-in-catalog",
                "Evento fora do catálogo de eventos da P1.38 ou em versão não declarada: "
                        + eventName + " v" + eventVersion);
    }
}
