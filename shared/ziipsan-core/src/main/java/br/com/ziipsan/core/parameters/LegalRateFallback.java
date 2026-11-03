package br.com.ziipsan.core.parameters;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/**
 * Taxa legal do art. 406 do Código Civil (P0.20, {@code fee.legal_rate_fallback}).
 *
 * <p>Quando ligada e nada foi contratado, a taxa é a divulgada mensalmente pelo BCB,
 * pela metodologia da Resolução CMN 5.171/2024 (Lei 14.905/2024). Esta classe não
 * conhece nenhuma taxa: o mapa mês → taxa é injetado (carga do BCB, fora do P0.20)
 * e mês sem taxa divulgada falha com {@link MissingParameterException} — nunca com
 * taxa inventada ou herdada de outro mês. A aplicação a tarifa está a confirmar
 * (PARAMETROS G5); ligar o fallback não muda nenhum cálculo existente por si só.
 */
public final class LegalRateFallback {

    /** Chave do parâmetro, para mensagens de erro explícitas. */
    public static final String KEY = "fee.legal_rate_fallback";

    /** Metodologia e fonte, como registradas no catálogo do core V3. */
    public static final String METHODOLOGY =
            "Resolução CMN 5.171/2024; taxa divulgada mensalmente pelo BCB";

    private LegalRateFallback() {
    }

    /**
     * Taxa legal do mês de referência.
     *
     * @param referenceMonth mês da taxa (competência do cálculo)
     * @param disclosedRates taxas divulgadas pelo BCB, por mês; mapa injetado, nunca fixo
     * @throws MissingParameterException se o mês não tiver taxa divulgada
     */
    public static BigDecimal rateFor(YearMonth referenceMonth, Map<YearMonth, BigDecimal> disclosedRates) {
        if (referenceMonth == null) {
            throw new IllegalArgumentException("O mês de referência é obrigatório");
        }
        if (disclosedRates == null) {
            throw new MissingParameterException(KEY,
                    "nenhuma taxa do BCB carregada para " + referenceMonth + " (" + METHODOLOGY + ")");
        }
        BigDecimal rate = disclosedRates.get(referenceMonth);
        if (rate == null) {
            throw new MissingParameterException(KEY,
                    "mês " + referenceMonth + " sem taxa divulgada pelo BCB (" + METHODOLOGY + ")");
        }
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("Taxa legal divulgada não pode ser negativa: " + rate);
        }
        return rate;
    }
}
