package br.com.ziipsan.commercial.billing.special;

import java.util.UUID;

/**
 * Vinculo consumo x situacao especial (ED-22): consumo de tipo
 * {@code SPECIAL_SITUATION_FIXED} <b>exige</b> {@code special_situation_id}
 * - a mesma regra que a migration pendente grava como FK + CHECK em
 * {@code consumption_records}. Consumo de outro tipo com
 * {@code special_situation_id} preenchido tambem e recusado: o vinculo so
 * tem sentido no consumo fixado pela situacao.
 *
 * <p>Os nomes dos tipos sao os do enum {@code ConsumptionType} do metering
 * (a ED-22 nao duplica o enum; recebe o nome para nao acoplar os modulos).
 */
public final class ConsumptionLinkRule {

    public static final String SPECIAL_SITUATION_FIXED = "SPECIAL_SITUATION_FIXED";

    private ConsumptionLinkRule() {
    }

    public static void check(String consumptionType, UUID specialSituationId) {
        if (consumptionType == null || consumptionType.isBlank()) {
            throw new IllegalArgumentException("tipo de consumo e obrigatorio");
        }
        boolean fixed = SPECIAL_SITUATION_FIXED.equals(consumptionType);
        if (fixed && specialSituationId == null) {
            throw new IllegalArgumentException("consumo SPECIAL_SITUATION_FIXED exige special_situation_id (FK)");
        }
        if (!fixed && specialSituationId != null) {
            throw new IllegalArgumentException(
                    "special_situation_id so e admitido em consumo SPECIAL_SITUATION_FIXED, nao em " + consumptionType);
        }
    }

    /** O volume que a situacao fixa para a competencia (agua), ou nulo se ela nao vale/fixa. */
    public static Integer fixedWaterVolume(SpecialSituationRecord record, java.time.YearMonth competence) {
        if (record == null || !record.appliesTo(competence) || !record.type().fixesWaterVolume()) {
            return null;
        }
        return record.fixedWaterVolume();
    }

    /** O volume que a situacao fixa para a competencia (esgoto), ou nulo se ela nao vale/fixa. */
    public static Integer fixedSewerVolume(SpecialSituationRecord record, java.time.YearMonth competence) {
        if (record == null || !record.appliesTo(competence) || !record.type().fixesSewerVolume()) {
            return null;
        }
        return record.fixedSewerVolume();
    }
}
