package br.com.ziipsan.operations.metering.hydrometry;

import java.math.BigDecimal;

/** Validação do modelo aprovado do catálogo (ED-14): sem texto livre, sem vazão inventada. */
public final class MeterModelRule {
    private MeterModelRule() {}

    public record ModelFacts(String approvalOrdinance, BigDecimal nominalDiameterMm, BigDecimal q3,
                             int r, int accuracyClass, HydrometryEnums.InstallationPosition position,
                             HydrometryEnums.Regime regime) {}

    public static FlowRates validate(ModelFacts f) {
        if (f == null) throw new IllegalArgumentException("Modelo aprovado é obrigatório");
        if (f.approvalOrdinance() == null || f.approvalOrdinance().isBlank())
            throw new IllegalArgumentException("Portaria de aprovação do modelo é obrigatória (Portaria 155/2022)");
        if (f.nominalDiameterMm() == null || f.nominalDiameterMm().signum() <= 0)
            throw new IllegalArgumentException("DN do modelo é obrigatório e positivo");
        if (f.accuracyClass() != 1 && f.accuracyClass() != 2)
            throw new IllegalArgumentException("Classe de exatidão deve ser 1 ou 2 (Portaria 155/2022): " + f.accuracyClass());
        if (f.position() == null) throw new IllegalArgumentException("Posição do modelo é obrigatória");
        if (f.regime() == null) throw new IllegalArgumentException("Regime do modelo é obrigatório");
        return FlowRates.derive(f.q3(), f.r());
    }
}
