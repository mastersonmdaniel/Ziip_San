package br.com.ziipsan.intelligence.losses.nr15;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Servico da saida anual da NR 15 (ED-78): monta o balanco
 * padronizado do ano a partir da ED-85 + P4.2 e emite a declaracao
 * anual do diagnostico - somente para tenant aplicavel.
 *
 * <p>Portoes (todos fail-closed, nenhum contornavel por omissao):
 * <ol>
 *   <li>aplicabilidade pelo perfil regulatorio ({@link
 *       Nr15Applicability#requireApplicable});</li>
 *   <li>ano completo na ED-85 (12 fotos por DMC) e volume produzido
 *       presente na P4.2 ({@link Nr15BalanceAssembler});</li>
 *   <li>volume produzido da P4.2 RECONCILIADO com a entrada somada
 *       da ED-85: divergencia alem da tolerancia aritmetica recusa a
 *       declaracao nomeando os dois valores (a divergencia fica
 *       gravada no balanco para auditoria, mas nao vira declaracao);
 *       </li>
 *   <li>indicadores anuais derivados do MESMO conjunto de fotos do
 *       balanco (contagem conferida), com a cobertura de medicao do
 *       periodo anexada quando a porta da ED-17 a fornece.</li>
 * </ol>
 */
public final class Nr15DeclarationService {

    private final Nr15Applicability applicability;
    private final DmcBalancePort balances;
    private final ProducedVolumePort producedVolume;
    private final MeteringCoveragePort coverage;

    public Nr15DeclarationService(Nr15Applicability applicability, DmcBalancePort balances,
                                  ProducedVolumePort producedVolume, MeteringCoveragePort coverage) {
        this.applicability = Objects.requireNonNull(applicability, "aplicabilidade obrigatoria");
        this.balances = Objects.requireNonNull(balances, "porta de balancos da ED-85 obrigatoria");
        this.producedVolume = Objects.requireNonNull(producedVolume, "porta do volume produzido obrigatoria");
        this.coverage = coverage;
    }

    /** Balanco + indicadores do ano, sem emitir a declaracao (conferencia previa). */
    public record AnnualOutput(StandardizedWaterBalance balance, Nr15Indicators indicators,
                               Nr15Reconciler.ReconciliationResult reconciliation) {
    }

    public AnnualOutput buildAnnualOutput(UUID tenantId, int baseYear) {
        applicability.requireApplicable(tenantId);
        List<DmcBalanceSnapshot> snapshots = balances.balancesOfYear(tenantId, baseYear);
        StandardizedWaterBalance balance = Nr15BalanceAssembler.assemble(
                tenantId, baseYear, snapshots, producedVolume);
        Nr15Indicators indicators = Nr15Indicators.forPeriod(Integer.toString(baseYear), snapshots);
        if (coverage != null) {
            var annualCounts = coverage.annualCountsOf(tenantId, baseYear);
            if (annualCounts.isPresent()) {
                indicators = indicators.withCoverage(annualCounts.get());
            }
        }
        Nr15Reconciler.ReconciliationResult reconciliation =
                Nr15Reconciler.reconcile(balance, snapshots);
        return new AnnualOutput(balance, indicators, reconciliation);
    }

    /** Emite a declaracao anual do ano para o tenant aplicavel. */
    public AnnualDeclaration declare(UUID tenantId, int baseYear) {
        Nr15Applicability.Assessment assessment = applicability.requireApplicable(tenantId);
        AnnualOutput output = buildAnnualOutput(tenantId, baseYear);
        if (!output.reconciliation().reconciled()) {
            throw new IllegalStateException("NR15_BALANCE_NOT_RECONCILED: o balanco padronizado diverge"
                    + " das fotos da ED-85 em " + output.reconciliation().divergences());
        }
        if (!output.balance().producedReconciled()) {
            throw new IllegalStateException("NR15_PRODUCED_VOLUME_DIVERGES: volume produzido da P4.2 ("
                    + output.balance().producedVolumeP42M3() + " m3) diverge da entrada somada da ED-85 ("
                    + output.balance().systemInputM3() + " m3) em "
                    + output.balance().producedDivergenceM3()
                    + " m3; verificar a cobertura dos DMCs antes de declarar");
        }
        if (output.indicators().snapshotCount() != output.balance().snapshotCount()) {
            throw new IllegalStateException("indicadores e balanco de conjuntos distintos de fotos da ED-85");
        }
        return new AnnualDeclaration(UUID.randomUUID(), tenantId, assessment.agencyCodeOrNull(),
                output.balance(), output.indicators());
    }
}
