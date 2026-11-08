package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

/**
 * Registro do leiaute de fatura de uma distribuidora (aceite da ED-90:
 * "importacao da fatura de energia por arquivo, com o leiaute de cada
 * distribuidora conferido na implantacao").
 *
 * <p>FRONTEIRA DURA — nada inventado: esta entrega NAO conhece o
 * leiaute de nenhuma distribuidora (nenhum campo, delimitador ou
 * posicao consta das fontes). O registro nasce {@code verified=false}
 * e so vira verificado na implantacao, quando o leiaute real for
 * conferido contra faturas da distribuidora e a versao for declarada
 * por quem conferiu. Enquanto nao verificado, a importacao recusa
 * ({@link EnergyBillImportService}) — marcador [SEM FONTE] ate la.
 */
public record DistributorLayout(String distributorKey, String layoutVersion,
                                boolean verified, String verificationNote) {

    public DistributorLayout {
        if (distributorKey == null || distributorKey.isBlank()) {
            throw new IndicatorValidationException("ENERGY_DISTRIBUTOR_KEY_REQUIRED",
                    "registro de leiaute sem a chave da distribuidora");
        }
        if (verified) {
            if (layoutVersion == null || layoutVersion.isBlank()) {
                throw new IndicatorValidationException("ENERGY_LAYOUT_VERSION_REQUIRED",
                        "leiaute verificado de " + distributorKey + " exige a versao "
                                + "declarada por quem conferiu na implantacao");
            }
            if (verificationNote == null || verificationNote.isBlank()) {
                throw new IndicatorValidationException("ENERGY_LAYOUT_VERIFICATION_NOTE_REQUIRED",
                        "leiaute verificado de " + distributorKey + " exige a nota de "
                                + "conferencia (quem conferiu, contra quais faturas)");
            }
        }
    }

    /** Registro ainda nao conferido: a importacao desta distribuidora esta fechada. */
    public static DistributorLayout unverified(String distributorKey) {
        return new DistributorLayout(distributorKey, null, false,
                "[SEM FONTE] leiaute nao conferido na implantacao");
    }

    /** Registro conferido na implantacao, com a versao do leiaute e a nota de conferencia. */
    public static DistributorLayout verified(String distributorKey, String layoutVersion,
                                             String verificationNote) {
        return new DistributorLayout(distributorKey, layoutVersion, true, verificationNote);
    }
}
