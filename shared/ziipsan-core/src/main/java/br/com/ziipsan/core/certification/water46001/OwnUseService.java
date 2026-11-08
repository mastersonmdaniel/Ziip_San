package br.com.ziipsan.core.certification.water46001;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Servico do uso proprio (ED-81): amarra as fronteiras das deps por
 * PORTAS, fail-closed — a fiacao (adapters JDBC/Spring sobre P1.3,
 * ED-14, ED-85 e P3.31) e pendencia nomeada de integracao.
 * <ul>
 *   <li>{@link ConsumerUnitPort} — a unidade existe no cadastro da
 *       P1.3 ({@code consumer_units});</li>
 *   <li>{@link HydrometryPort} — o hidrometro esta INSTALADO na
 *       unidade pela hidrometria da ED-14 (medicao real do uso);</li>
 *   <li>{@link BalanceSourcePort} — a fonte do uso proprio da ED-85
 *       existe para o DMC/periodo, ja DECOMPOSTA pelo adapter
 *       conforme o contrato declarado na porta (a fonte e LIDA,
 *       nunca recalculada);</li>
 *   <li>{@link ObjectivePort} — o objetivo existe no registro da
 *       P3.31 ({@code cert_objectives}).</li>
 * </ul>
 * Qualquer porta negativa recusa a operacao nomeando a fronteira.
 */
public final class OwnUseService {

    public interface ConsumerUnitPort { boolean exists(UUID tenantId, UUID consumerUnitId); }
    public interface HydrometryPort { boolean meterInstalledOnUnit(UUID tenantId, UUID meterId, UUID consumerUnitId); }
    /**
     * Porta da fonte do uso proprio (ED-85). CONTRATO DO ADAPTER
     * (correcao D1 da revisao; pendencia P-ED81-01): o snapshot
     * {@code loss_monthly_balances} da ED-85 NAO tem split
     * operacional/emergencial — so {@code exported_m3} e o agregado
     * {@code authorized_unbilled_m3} (que inclui o social). O
     * adapter DEVE devolver a {@link OwnUseSource} ja decomposta:
     * operacional = soma das categorias {@code NETWORK_FLUSHING} e
     * {@code RESERVOIR_CLEANING} de
     * {@code loss_authorized_unbilled_volumes} por DMC/mes de
     * {@code occurred_on}; emergencial = soma de
     * {@code FIREFIGHTING} pelo mesmo agrupamento; a categoria
     * social (carro-pipa social) excluida; exportada = {@code exported_m3} do
     * snapshot. Decompor por categoria nao e recalcular o balanco
     * (nada de entradas/perdas/ILI e refeito).
     */
    public interface BalanceSourcePort { Optional<OwnUseSource> source(UUID tenantId, UUID balanceRef); }
    public interface ObjectivePort { boolean exists(UUID tenantId, UUID objectiveId); }

    private final ConsumerUnitPort units;
    private final HydrometryPort hydrometry;
    private final BalanceSourcePort balances;
    private final ObjectivePort objectives;

    public OwnUseService(ConsumerUnitPort units, HydrometryPort hydrometry,
                         BalanceSourcePort balances, ObjectivePort objectives) {
        this.units = Objects.requireNonNull(units, "porta do cadastro da P1.3 obrigatoria");
        this.hydrometry = Objects.requireNonNull(hydrometry, "porta da hidrometria da ED-14 obrigatoria");
        this.balances = Objects.requireNonNull(balances, "porta do balanco da ED-85 obrigatoria");
        this.objectives = Objects.requireNonNull(objectives, "porta dos objetivos da P3.31 obrigatoria");
    }

    /** Cadastra unidade de uso operacional sobre a P1.3, medida pela ED-14. */
    public OperationalUseUnit registerUnit(UUID tenantId, OperationalUseUnit unit) {
        Objects.requireNonNull(tenantId, "tenant obrigatorio");
        Objects.requireNonNull(unit, "unidade de uso operacional obrigatoria");
        unit.activation().requireEnabled();
        if (!units.exists(tenantId, unit.consumerUnitId()))
            throw new IllegalArgumentException("unidade consumidora inexistente no cadastro "
                    + "da P1.3 para o tenant: " + unit.consumerUnitId());
        if (!hydrometry.meterInstalledOnUnit(tenantId, unit.meterId(), unit.consumerUnitId()))
            throw new IllegalArgumentException("hidrometro " + unit.meterId()
                    + " nao esta instalado na unidade " + unit.consumerUnitId()
                    + " pela hidrometria da ED-14: uso proprio sem medicao nao se cadastra");
        return unit;
    }

    /** Le a fonte do uso proprio da ED-85 (decomposta pelo adapter da porta) — leitura, sem recalculo. */
    public OwnUseSource requireSource(UUID tenantId, UUID balanceRef) {
        Objects.requireNonNull(tenantId, "tenant obrigatorio");
        Objects.requireNonNull(balanceRef, "referencia do balanco da ED-85 obrigatoria");
        return balances.source(tenantId, balanceRef).orElseThrow(() ->
                new IllegalArgumentException("snapshot do balanco da ED-85 inexistente "
                        + "para o tenant: " + balanceRef + " (sem fonte, nao ha uso proprio)"));
    }

    /** Valida o plano de acao contra o objetivo da P3.31. */
    public WaterActionPlan registerPlan(UUID tenantId, WaterActionPlan plan) {
        Objects.requireNonNull(tenantId, "tenant obrigatorio");
        Objects.requireNonNull(plan, "plano de acao obrigatorio");
        plan.activation().requireEnabled();
        if (!objectives.exists(tenantId, plan.objectiveId()))
            throw new IllegalArgumentException("objetivo inexistente no registro da P3.31 "
                    + "para o tenant: " + plan.objectiveId()
                    + " (plano de acao da ISO 46001 e ligado a objetivo existente)");
        return plan;
    }
}
