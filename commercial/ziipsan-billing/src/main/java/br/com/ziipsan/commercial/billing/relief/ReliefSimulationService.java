package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.ImpactSimulator;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Simulacao de impacto da desoneracao (DESENHO-PA02 secao 8, Passo 5):
 * proposta = versao desonerada gerada em ENSAIO, alimentando o
 * {@link ImpactSimulator} existente (P2.29) — nenhum simulador novo.
 *
 * <p><b>Agregacao por contrato (V-46 do PA-01 = AUSENTE):</b> a simulacao
 * IBS/CBS por contrato nao existe no codigo; a vista por contrato faz-se como
 * relatorio SOBRE o resultado da simulacao, agrupando as unidades da massa
 * pela entidade-contrato (contrato de grande consumidor/contrato do tenant),
 * nao como modo novo do simulador.
 */
public final class ReliefSimulationService {

    /** Agregacao do impacto por contrato. */
    public record ContractImpact(String contractRef, long units, BigDecimal currentNet,
                                 BigDecimal proposedNet, BigDecimal delta) {
    }

    private ReliefSimulationService() {
    }

    /** Corre a simulacao vigente x ensaio desonerado no simulador existente. */
    public static ImpactSimulator.ImpactReport simulate(TariffVersion current, TariffVersion trialRelieved,
                                                        List<ImpactSimulator.SimulationUnit> units,
                                                        LocalDate periodFrom, LocalDate periodTo,
                                                        List<ImpactSimulator.TypicalProfile> profiles) {
        Objects.requireNonNull(trialRelieved, "versao de ensaio desonerada e obrigatoria");
        return ImpactSimulator.simulate(current, trialRelieved, units, periodFrom, periodTo, profiles);
    }

    /**
     * Agrupa o resultado por contrato (V-46).
     *
     * @param unitContract contrato de cada unidade da massa (unidade sem
     *        contrato fica fora da vista por contrato, nunca inventada)
     */
    public static List<ContractImpact> aggregateByContract(ImpactSimulator.ImpactReport report,
                                                           Map<UUID, String> unitContract) {
        Objects.requireNonNull(report, "relatorio de simulacao e obrigatorio");
        Objects.requireNonNull(unitContract, "mapa unidade->contrato e obrigatorio");
        Map<String, Object[]> acc = new LinkedHashMap<>();
        for (ImpactSimulator.UnitResult unit : report.units()) {
            String contract = unitContract.get(unit.unitId());
            if (contract == null) {
                continue;
            }
            Object[] a = acc.computeIfAbsent(contract,
                    k -> new Object[]{0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = (Long) a[0] + 1;
            a[1] = ((BigDecimal) a[1]).add(unit.currentNet());
            a[2] = ((BigDecimal) a[2]).add(unit.proposedNet());
            a[3] = ((BigDecimal) a[3]).add(unit.delta());
        }
        return acc.entrySet().stream()
                .map(e -> new ContractImpact(e.getKey(), (Long) e.getValue()[0],
                        (BigDecimal) e.getValue()[1], (BigDecimal) e.getValue()[2],
                        (BigDecimal) e.getValue()[3]))
                .toList();
    }
}
