package br.com.ziipsan.operations.metering.telemetry;

/**
 * Escopos da credencial de integração usados pela ED-17, no catálogo
 * {@code recurso:ação} do ADR-0040 que a P2.26 implementa (por exemplo
 * {@code properties:read}, {@code bills:read}, {@code service-orders:write}).
 * A credencial é por tenant e o cliente é o da API pública da P2.26
 * ({@code V7_1}); este módulo só exige o escopo pela porta
 * {@link IntegrationCredentialChecker} — não conhece segredo nem token.
 *
 * <p><b>Registro no catálogo:</b> os três escopos são novos no catálogo
 * {@code recurso:ação} e o aceite da ED-17 não reserva onde registrá-los —
 * o registro no catálogo da P2.26 é pendência declarada no relatório da
 * entrega, junto da dos parâmetros de {@link TelemetryParameters}; sem ele,
 * a credencial real não concede estes escopos na implantação.
 */
public final class TelemetryScopes {

    /** Ingestão de leituras AMR e de amostras horárias de telemetria. */
    public static final String TELEMETRY_WRITE = "telemetry:write";

    /** Leitura da curva horária e das leituras de telemetria. */
    public static final String TELEMETRY_READ = "telemetry:read";

    /** Ingestão de amostras do supervisório (somente leitura na origem). */
    public static final String SUPERVISORY_WRITE = "supervisory:write";

    private TelemetryScopes() {
    }
}
