package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.parameters.BillingParameters;
import br.com.ziipsan.commercial.billing.tariff.FixedPartModel;

import java.util.Map;
import java.util.Objects;

/**
 * Leitura tipada dos parametros que o maestro CONSOME (desenho da
 * Camada 0, secao 2.1 item 15): <b>nenhuma chave nova inventada</b> —
 * esta classe apenas delega nas constantes e nos acessores vigentes de
 * {@link BillingParameters} (P2.5) para as chaves que tocam a
 * composicao:
 *
 * <ul>
 *   <li>{@code tariff.fixed_part_model} — modelo do componente fixo
 *       (TFDI), consumido pela resolucao da parcela fixa que o chamador
 *       faz na {@code FixedPartCharge} vigente ANTES de montar o
 *       contexto (o maestro transporta o valor resolvido; nao resolve
 *       a TFDI — regra de fronteira (iv));</li>
 *   <li>{@code bill.cut_connection_charge} — interruptor de cobranca da
 *       TFDI de unidade cortada/nao conectada, idem.</li>
 * </ul>
 *
 * <p><b>Chaves que faltam (pendencias nomeadas, sem inventar chave):</b>
 * <ul>
 *   <li>modo de arredondamento do PERCENT_OF_BASE por cobranca:
 *       {@code [A DEFINIR]} — nenhuma chave vigente o declara; por isso
 *       o modo e exigido do contexto, declarado pelo chamador por
 *       cobranca (regra de fronteira (iii); pendencia P-C2-01 do
 *       relatorio desta camada);</li>
 *   <li>qualquer outra chave de composicao que a integracao venha a
 *       precisar nasce como {@code [A DEFINIR]} na pendencia, nunca
 *       como constante nova nesta classe.</li>
 * </ul>
 *
 * Parametro ausente e erro explicito, pelo acessor vigente
 * ({@code MissingParameterException} do core) — nunca valor assumido.
 */
public final class CompositionParameters {

    /** Chave vigente (constante de {@link BillingParameters}, nao uma chave nova). */
    public static final String FIXED_PART_MODEL = BillingParameters.FIXED_PART_MODEL;

    /** Chave vigente (constante de {@link BillingParameters}, nao uma chave nova). */
    public static final String CUT_CONNECTION_CHARGE = BillingParameters.CUT_CONNECTION_CHARGE;

    private final BillingParameters delegate;

    private CompositionParameters(BillingParameters delegate) {
        this.delegate = delegate;
    }

    /** @param jsonByKey chave do parametro -&gt; JSON vigente e aceito (como em BillingParameters) */
    public static CompositionParameters fromValues(Map<String, String> jsonByKey) {
        Objects.requireNonNull(jsonByKey, "mapa de parametros e obrigatorio");
        return new CompositionParameters(BillingParameters.fromValues(jsonByKey));
    }

    /** Embrulha uma leitura vigente ja montada pelo chamador. */
    public static CompositionParameters of(BillingParameters parameters) {
        return new CompositionParameters(
                Objects.requireNonNull(parameters, "BillingParameters e obrigatorio"));
    }

    /** {@code tariff.fixed_part_model}: modelo do componente fixo (TFDI). */
    public FixedPartModel fixedPartModel() {
        return delegate.fixedPartModel();
    }

    /** {@code bill.cut_connection_charge}: cobrar a TFDI de unidade cortada/nao conectada. */
    public boolean cutConnectionCharge() {
        return delegate.cutConnectionCharge();
    }
}
