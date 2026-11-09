package br.com.ziipsan.core.regulatory.export;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Reconciliacao mensal com a lista positiva publicada pela ANA
 * (ED-97, CAD-92; NR 13/ANA, art. 45): por municipio e regulador, os
 * codigos de economia beneficiada locais sao comparados com os da lista
 * positiva do mes. A lista real da ANA e prova externa (pendencia
 * P-ED97-03); aqui a reconciliacao e a REGRA, exercitada com massa
 * sintetica. Codigos sao identificadores de economia (nao nome/CPF).
 */
public record AnaReconciliation(YearMonth month, String municipalityIbge, String regulator,
                                int localCount, int anaCount, int matched,
                                int onlyLocal, int onlyAna) {

    public AnaReconciliation {
        Objects.requireNonNull(month); Objects.requireNonNull(municipalityIbge);
        Objects.requireNonNull(regulator);
        if (municipalityIbge.isBlank() || regulator.isBlank())
            throw new IllegalArgumentException("municipio/regulador obrigatorios na reconciliacao ANA");
        // Correcao D3 (REVISAO-ED-97): consistencia das contagens POR
        // CONSTRUCAO, no construtor canonico — nao so em reconcile():
        // nenhuma contagem negativa e as somas fecham com os totais.
        if (localCount < 0 || anaCount < 0 || matched < 0 || onlyLocal < 0 || onlyAna < 0)
            throw new IllegalArgumentException("contagens negativas na reconciliacao ANA");
        if (matched + onlyLocal != localCount)
            throw new IllegalArgumentException("matched + onlyLocal != localCount na reconciliacao ANA: "
                    + matched + " + " + onlyLocal + " != " + localCount);
        if (matched + onlyAna != anaCount)
            throw new IllegalArgumentException("matched + onlyAna != anaCount na reconciliacao ANA: "
                    + matched + " + " + onlyAna + " != " + anaCount);
    }

    public boolean diverged() { return onlyLocal > 0 || onlyAna > 0; }

    public static AnaReconciliation reconcile(YearMonth month, String municipalityIbge, String regulator,
                                              Set<String> localBenefitedEconomyCodes,
                                              Set<String> anaPositiveListEconomyCodes) {
        Objects.requireNonNull(localBenefitedEconomyCodes);
        Objects.requireNonNull(anaPositiveListEconomyCodes);
        Set<String> inter = new HashSet<>(localBenefitedEconomyCodes);
        inter.retainAll(anaPositiveListEconomyCodes);
        return new AnaReconciliation(month, municipalityIbge, regulator,
                localBenefitedEconomyCodes.size(), anaPositiveListEconomyCodes.size(), inter.size(),
                localBenefitedEconomyCodes.size() - inter.size(),
                anaPositiveListEconomyCodes.size() - inter.size());
    }
}
