package br.com.ziipsan.core.nondomestic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cobranca complementar do esgoto nao domestico (aceite da ED-83,
 * classe d, parametro {@code sewer.nondomestic_charge_model}): o
 * calculo devolve o COMPONENTE que entra na conta pelo motor da
 * P2.5 — este nucleo nao emite conta, nao cria debito e nao liga
 * cobranca automatica alguma; ele calcula o componente nomeado
 * {@link #COMPONENT_CODE} com a base auditavel do modelo usado, e a
 * P2.5 o compoe na fatura como os demais componentes.
 *
 * <p>Modelos (todos os valores sao dados entregues pelo chamador —
 * percentual, tarifa unitaria, preco por kg e tabela de taxas sao do
 * tenant/prestador/agencia; nenhum nasce aqui):
 * <ul>
 *   <li>VOLUME_PERCENT: volume faturavel = volume de agua x
 *       percentual/100; valor = volume faturavel x tarifa unitaria
 *       de esgoto vigente;</li>
 *   <li>POLLUTANT_LOAD: valor = carga total (kg, {@link PollutantLoad})
 *       x preco por kg. GUARDA DO ACEITE: so calcula quando a agencia
 *       previr a cobranca por carga ({@code agencyForeseesLoadBilling});
 *       sem a previsao da agencia, RECUSA — mesmo com o modelo
 *       configurado;</li>
 *   <li>FIXED_BY_CLASS: valor = taxa fixa da classe do gerador na
 *       tabela do prestador; classe sem taxa declarada RECUSA.</li>
 * </ul>
 * Arredondamento declarado: valores monetarios com 2 casas, HALF_UP;
 * volumes com 3 casas; carga com 4 (ver {@link PollutantLoad}).
 */
public final class NonDomesticChargeCalculator {

    /** Codigo do componente que a P2.5 compoe na conta. */
    public static final String COMPONENT_CODE = "NONDOMESTIC_EFFLUENT";

    /**
     * Componente de calculo para a conta (motor da P2.5): valor e
     * base nomeados por modelo, para o snapshot da conta auditar o
     * que foi cobrado e por que.
     */
    public record ChargeComponent(
            UUID dischargerId,
            YearMonth competence,
            NonDomesticChargeModel model,
            String componentCode,
            BigDecimal amount,
            BigDecimal waterVolumeM3,
            BigDecimal billableVolumeM3,
            BigDecimal totalLoadKg,
            String chargeClassCode) {
        public ChargeComponent {
            if (dischargerId == null) throw new IllegalArgumentException("gerador do componente e obrigatorio");
            if (competence == null) throw new IllegalArgumentException("competencia do componente e obrigatoria");
            if (model == null) throw new IllegalArgumentException("modelo do componente e obrigatorio");
            if (!COMPONENT_CODE.equals(componentCode))
                throw new IllegalArgumentException("componente da ED-83 tem codigo fixo " + COMPONENT_CODE);
            if (amount == null || amount.signum() < 0)
                throw new IllegalArgumentException("valor do componente invalido: " + amount);
        }
    }

    private NonDomesticChargeCalculator() {}

    /** VOLUME_PERCENT: percentual do volume de agua x tarifa unitaria de esgoto vigente. */
    public static ChargeComponent byVolumePercent(DischargerRegistration discharger, YearMonth competence,
                                                  BigDecimal waterVolumeM3, BigDecimal percent,
                                                  BigDecimal sewerUnitRate) {
        requireDischarger(discharger, competence);
        if (waterVolumeM3 == null || waterVolumeM3.signum() < 0)
            throw new IllegalArgumentException("volume de agua da competencia e obrigatorio e nao negativo");
        if (percent == null || percent.signum() <= 0 || percent.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("percentual do volume (classe d) fora de (0, 100]: "
                    + percent + " — o percentual e dado do tenant/prestador, nunca de fabrica");
        if (sewerUnitRate == null || sewerUnitRate.signum() < 0)
            throw new IllegalArgumentException("tarifa unitaria de esgoto vigente (P2.5) e obrigatoria");
        BigDecimal billable = waterVolumeM3.multiply(percent)
                .divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
        BigDecimal amount = billable.multiply(sewerUnitRate).setScale(2, RoundingMode.HALF_UP);
        return new ChargeComponent(discharger.id(), competence, NonDomesticChargeModel.VOLUME_PERCENT,
                COMPONENT_CODE, amount, waterVolumeM3, billable, null, null);
    }

    /**
     * POLLUTANT_LOAD: carga total x preco por kg. So com a previsao
     * da agencia ({@code agencyForeseesLoadBilling}) — sem ela, o
     * aceite manda recusar, nao cobrar por outro modelo em silencio.
     */
    public static ChargeComponent byPollutantLoad(DischargerRegistration discharger, YearMonth competence,
                                                  List<PollutantLoad.LoadMeasurement> measurements,
                                                  BigDecimal pricePerKg, boolean agencyForeseesLoadBilling) {
        requireDischarger(discharger, competence);
        if (!agencyForeseesLoadBilling)
            throw new IllegalStateException("cobranca por carga poluidora so quando a agencia a previr"
                    + " (aceite da ED-83): previsao da agencia nao declarada — cobranca recusada");
        if (pricePerKg == null || pricePerKg.signum() < 0)
            throw new IllegalArgumentException("preco por kg de carga e dado da agencia/prestador,"
                    + " obrigatorio e nao negativo [SEM FONTE para valor de fabrica]");
        BigDecimal totalLoad = PollutantLoad.totalLoadKg(measurements);
        BigDecimal amount = totalLoad.multiply(pricePerKg).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volume = measurements.stream().map(PollutantLoad.LoadMeasurement::volumeM3)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ChargeComponent(discharger.id(), competence, NonDomesticChargeModel.POLLUTANT_LOAD,
                COMPONENT_CODE, amount, null, volume, totalLoad, null);
    }

    /** FIXED_BY_CLASS: taxa fixa da classe do gerador na tabela do prestador entregue. */
    public static ChargeComponent byFixedClass(DischargerRegistration discharger, YearMonth competence,
                                               Map<String, BigDecimal> fixedFeeByClass) {
        requireDischarger(discharger, competence);
        if (fixedFeeByClass == null || fixedFeeByClass.isEmpty())
            throw new IllegalArgumentException("tabela de taxas fixas por classe do prestador"
                    + " nao entregue: nenhuma taxa nasce aqui [SEM FONTE]");
        String classCode = discharger.chargeClassCode();
        BigDecimal fee = fixedFeeByClass.get(classCode);
        if (fee == null)
            throw new IllegalArgumentException("classe " + classCode + " sem taxa fixa declarada"
                    + " na tabela do prestador: cobranca recusada, taxa nao inventada");
        if (fee.signum() < 0)
            throw new IllegalArgumentException("taxa fixa da classe " + classCode + " negativa: " + fee);
        return new ChargeComponent(discharger.id(), competence, NonDomesticChargeModel.FIXED_BY_CLASS,
                COMPONENT_CODE, fee.setScale(2, RoundingMode.HALF_UP), null, null, null, classCode);
    }

    private static void requireDischarger(DischargerRegistration discharger, YearMonth competence) {
        if (discharger == null) throw new IllegalArgumentException("gerador e obrigatorio");
        if (competence == null) throw new IllegalArgumentException("competencia e obrigatoria");
    }
}
