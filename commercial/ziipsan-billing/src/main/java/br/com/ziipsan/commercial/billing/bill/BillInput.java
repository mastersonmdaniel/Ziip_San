package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.math.Money;
import br.com.ziipsan.commercial.billing.tariff.TariffGroup;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import br.com.ziipsan.commercial.billing.tariff.TariffSchedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tudo que o cálculo da conta precisa, já resolvido pelo chamador (que lê o banco). O calculador não
 * lê nada e não assume nada: ausência de um serviço é {@code null}, explícito.
 *
 * @param schedule         tarifa do imóvel (todas as versões); obrigatória se há água ou esgoto
 * @param periodFrom       data da leitura anterior (início do período tarifado), inclusive
 * @param periodTo         data da leitura atual, inclusive
 * @param groups           economias por categoria/subcategoria
 * @param waterConsumption consumo faturado de água em m³; {@code null} = água não faturada
 * @param sewer            esgoto; {@code null} = esgoto não faturado
 * @param debits           débitos a cobrar selecionados para este mês
 * @param credits          créditos a realizar, na ordem de abatimento
 * @param taxPolicy        retenção de impostos; {@code null} = a conta não retém (cliente sem retenção)
 * @param fixedPartValue   parcela fixa avulsa (TFDI, Res. ARESC 105/2018) já resolvida pelo
 *                         chamador (regra {@code FixedPartCharge}); {@code null} = sem TFDI,
 *                         vale {@link Money#ZERO} -- contas sem parcela fixa não mudam
 */
public record BillInput(TariffSchedule schedule, LocalDate periodFrom, LocalDate periodTo,
                        List<TariffGroup> groups, Integer waterConsumption, SewerBilling sewer,
                        List<DebitReceivable> debits, List<CreditReceivable> credits,
                        TaxWithholdingPolicy taxPolicy, BigDecimal fixedPartValue) {

    /**
     * @param baseVolume volume de água (ou do poço) de que o esgoto parte; a coleta o reduz
     */
    public record SewerBilling(int baseVolume, SewerParameters parameters) {
    }

    /** Sem parcela fixa (TFDI): o construtor de antes da P2.5, para os chamadores sem TFDI. */
    public BillInput(TariffSchedule schedule, LocalDate periodFrom, LocalDate periodTo,
                     List<TariffGroup> groups, Integer waterConsumption, SewerBilling sewer,
                     List<DebitReceivable> debits, List<CreditReceivable> credits,
                     TaxWithholdingPolicy taxPolicy) {
        this(schedule, periodFrom, periodTo, groups, waterConsumption, sewer, debits, credits,
                taxPolicy, null);
    }

    public BillInput {
        debits = debits == null ? List.of() : List.copyOf(debits);
        credits = credits == null ? List.of() : List.copyOf(credits);
        fixedPartValue = fixedPartValue == null ? Money.ZERO : fixedPartValue;
        if (fixedPartValue.signum() < 0) {
            throw new IllegalArgumentException("Parcela fixa (TFDI) negativa: " + fixedPartValue);
        }
        boolean usesTariff = waterConsumption != null || sewer != null;
        if (usesTariff && (schedule == null || periodFrom == null || periodTo == null || groups == null || groups.isEmpty())) {
            throw new IllegalArgumentException(
                    "Água ou esgoto faturados exigem tarifa, período e economias");
        }
        if (waterConsumption != null && waterConsumption < 0) {
            throw new IllegalArgumentException("Consumo de água negativo: " + waterConsumption);
        }
    }
}
