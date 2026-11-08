package br.com.ziipsan.intelligence.losses.dmc;

import java.util.Objects;

/**
 * Fornecimento em bloco (ED-85; OPE-05): agua tratada exportada a
 * outro prestador ou municipio e EXCLUIDA do balanco do DMC SOMENTE
 * por contrato de fornecimento em bloco — com ponto de entrega,
 * macromedidor do ponto e volume contratado, todos declarados. O
 * volume excluido e o MEDIDO no macromedidor do contrato (dado real),
 * nunca o contratado; medido acima do contratado e exposto pela flag
 * {@code exceedsContracted} para a gestao do contrato, sem truncar a
 * exclusao fisica.
 */
public record BulkExportContract(String contractRef, String deliveryPoint,
                                 String macroMeterPointCode, double contractedVolumeM3,
                                 double measuredExportM3) {
    public BulkExportContract {
        if (contractRef == null || contractRef.isBlank())
            throw new IllegalArgumentException("referencia do contrato de fornecimento em bloco obrigatoria");
        if (deliveryPoint == null || deliveryPoint.isBlank())
            throw new IllegalArgumentException("ponto de entrega do contrato obrigatorio");
        if (macroMeterPointCode == null || macroMeterPointCode.isBlank())
            throw new IllegalArgumentException("macromedidor do ponto de entrega obrigatorio (codigo ED-17)");
        Objects.requireNonNull(contractRef);
        if (contractedVolumeM3 < 0 || Double.isNaN(contractedVolumeM3))
            throw new IllegalArgumentException("volume contratado negativo");
        if (measuredExportM3 < 0 || Double.isNaN(measuredExportM3))
            throw new IllegalArgumentException("volume exportado medido negativo");
    }

    /** Volume que sai do balanco: o medido no macromedidor do contrato. */
    public double excludedVolumeM3() { return measuredExportM3; }

    public boolean exceedsContracted() { return measuredExportM3 > contractedVolumeM3; }
}
