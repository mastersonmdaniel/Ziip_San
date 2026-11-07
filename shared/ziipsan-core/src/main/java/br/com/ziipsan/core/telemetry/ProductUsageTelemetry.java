package br.com.ziipsan.core.telemetry;

import java.util.List;
import java.util.Objects;

/**
 * P4.8 / PLT-42: serviço de ingestão da telemetria de produto.
 *
 * <p>Fluxo de cada evento bruto: (1) o modo do parâmetro
 * {@code telemetry.product_usage} decide se há coleta — {@code OFF}
 * recusa sem gravar nada; (2) o {@link TelemetryPrivacyGuard} valida o
 * JSON e recusa identificador de pessoa ou conteúdo (teste obrigatório
 * do aceite); (3) o evento tipado — que por construção não tem campo de
 * pessoa, sessão nem conteúdo — vai ao sumidouro agregado
 * ({@link TelemetrySink}), cuja implementação de produção persiste SÓ o
 * agregado por tenant e por papel (fiação de integração: não há
 * migration reservada à P4.8 no plano §3, logo nenhuma tabela é criada
 * nem presumida aqui).
 *
 * <p>Registro no DPA: o aceite remete o registro da telemetria ao DPA da
 * LC.9 ("telemetria registrada no DPA (ver LC.9)") — esta tarefa só
 * DECLARA o tratamento (ver {@code docs/telemetry/DPA-DECLARACAO-P4.8.md}
 * na entrega); o registro formal é da LC.9, não é fingido aqui.
 */
public final class ProductUsageTelemetry {

    /** Sumidouro dos eventos já validados (produção: agregado por tenant/papel). */
    public interface TelemetrySink {
        void accept(TelemetryEvent event);

        List<TelemetryEvent> events();
    }

    private final ProductUsageMode mode;
    private final TelemetrySink sink;

    public ProductUsageTelemetry(ProductUsageMode mode, TelemetrySink sink) {
        this.mode = Objects.requireNonNull(mode, "mode é obrigatório");
        this.sink = Objects.requireNonNull(sink, "sink é obrigatório");
    }

    public static ProductUsageTelemetry fromParameter(String rawParameter, TelemetrySink sink) {
        return new ProductUsageTelemetry(ProductUsageMode.fromParameter(rawParameter), sink);
    }

    public ProductUsageMode mode() {
        return mode;
    }

    /**
     * Ingere um evento bruto em JSON. Com o modo {@code OFF}, lança
     * {@link IllegalStateException} sem tocar no sumidouro. Com pessoa ou
     * conteúdo, o guard lança 422 e NADA é gravado.
     *
     * @return o evento validado que foi ao sumidouro
     */
    public TelemetryEvent ingest(String eventJson) {
        if (mode == ProductUsageMode.OFF) {
            throw new IllegalStateException(
                    "Telemetria de produto desligada (telemetry.product_usage=OFF): evento não coletado.");
        }
        TelemetryEvent event = TelemetryPrivacyGuard.parseAndValidate(eventJson);
        sink.accept(event);
        return event;
    }

    /** Resumos agregados correntes (aba Adoção da Central e painel do tenant). */
    public List<ProductUsageAggregator.TenantUsageSummary> summaries() {
        return ProductUsageAggregator.summaries(sink.events());
    }
}
