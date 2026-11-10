package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Componente fixo por disponibilidade de infraestrutura (TFDI,
 * Res. ARESC 105/2018; Lei 11.445/2007, art. 45) cobrado de forma avulsa
 * (P2.5/RAS-22). O modelo vem de {@code tariff.fixed_part_model} e o
 * interruptor da unidade desligada vem de {@code bill.cut_connection_charge}:
 *
 * <pre>
 *   unidade conectada                 → sem cobrança avulsa (a parte fixa já está
 *                                       na tarifa normal, linha MINIMUM do TariffEngine)
 *   não conectada, modelo NONE        → zero
 *   não conectada, cut = false        → zero (o tenant não cobra TFDI de unidade cortada)
 *   não conectada, cut = true         → parcela fixa da categoria × economias, e SÓ isso:
 *                                       sem consumo, sem faixas, sem excedente
 * </pre>
 *
 * @param amount  valor da TFDI avulsa (zero quando não cobrada)
 * @param charged se houve cobrança avulsa nesta chamada
 */
public record FixedPartCharge(BigDecimal amount, boolean charged) {

    /**
     * @param connected          a unidade está conectada à rede
     * @param model              {@code tariff.fixed_part_model} vigente
     * @param cutConnectionCharge {@code bill.cut_connection_charge} vigente:
     *                           o tenant cobra a TFDI de unidade com ligação cortada/não conectada
     * @param rule               regra tarifária da categoria (fonte da parcela fixa)
     * @param economies          economias da unidade (ao menos 1)
     */
    public static FixedPartCharge calculate(boolean connected, FixedPartModel model,
                                            boolean cutConnectionCharge, CategoryRule rule, int economies) {
        if (model == null) {
            throw new TariffConfigurationException("tariff.fixed_part_model é obrigatório");
        }
        if (economies < 1) {
            throw new IllegalArgumentException("A unidade não tem economias");
        }
        if (connected || model == FixedPartModel.NONE || !cutConnectionCharge) {
            return new FixedPartCharge(Money.ZERO, false);
        }
        if (rule == null) {
            throw new TariffConfigurationException(
                    "TFDI por MINIMUM_CHARGE_PER_ECONOMY exige a regra da categoria (parcela fixa)");
        }
        BigDecimal amount = rule.minCharge().multiply(BigDecimal.valueOf(economies))
                .setScale(Money.SCALE, RoundingMode.HALF_UP);
        return new FixedPartCharge(amount, true);
    }
}
