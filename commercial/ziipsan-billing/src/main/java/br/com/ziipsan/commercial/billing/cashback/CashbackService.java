package br.com.ziipsan.commercial.billing.cashback;

import br.com.ziipsan.commercial.billing.parameters.TaxProfile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Concessão do cashback de CBS/IBS como <b>crédito na fatura</b> (P2.34;
 * LC 214/2025, arts. 112 a 118; Decreto 12.955/2026, art. 511), por
 * domicílio e por período de aferição. Fluxo, nesta ordem:
 *
 * <ol>
 *   <li>{@code tax.profile.cashback_enabled} desligado → {@code NAO_HABILITADO}
 *       (decisão explícita do perfil JUR, nunca erro nem concessão);</li>
 *   <li>consulta à lista da RFB importada (V4_12) — <b>cada acesso é
 *       registrado</b> pela porta (padrão G2), conste o domicílio ou não;</li>
 *   <li>domicílio fora da lista no período → {@code NAO_ELEGIVEL};</li>
 *   <li>domicílio já devolvido no período → {@code JA_DEVOLVIDO} com a
 *       devolução existente: <b>uma única devolução por domicílio no
 *       período</b>, inclusive quando a família tem mais de uma unidade —
 *       a segunda unidade cai aqui, e a constraint
 *       {@code uq_cashback_return_domicile_period} da V4_12 é a garantia
 *       no banco;</li>
 *   <li>cálculo por {@link CashbackCalculator} (100% CBS / 20% IBS, por
 *       vigência); total zero na vigência → {@code SEM_DEVOLUCAO_NA_VIGENCIA},
 *       nada é gravado;</li>
 *   <li>{@code tax_event_date} resolvido pela porta
 *       {@link TaxEventDateResolver} <b>antes</b> de gravar: sem a regra
 *       conferida (LC 227/2026, art. 10, §3º), a implementação entregue
 *       recusa com erro explícito e <b>nada é concedido</b>;</li>
 *   <li>devolução gravada ({@code GRANTED}) ligada à conta do comando — é
 *       ela que a fiação da integração transforma em crédito na fatura
 *       (linha de crédito da conta, pelo mecanismo de créditos da P2.5).</li>
 * </ol>
 *
 * <p>Os valores de CBS/IBS do comando são os resolvidos pelo chamador da
 * tabela com vigência da P2.4 / itens da conta: este serviço não conhece
 * alíquota. O reflexo na NFAg-e é pelo DTO condicional da P2.6
 * ({@code NfageCashbackReflection}, fiscal) e o efeito por competência,
 * para o pleito de reequilíbrio, por {@link CashbackCompetenceReport}.
 */
public final class CashbackService {

    /** Desfecho da concessão. */
    public enum Status {
        CONCEDIDO,
        JA_DEVOLVIDO,
        NAO_ELEGIVEL,
        NAO_HABILITADO,
        SEM_DEVOLUCAO_NA_VIGENCIA
    }

    /**
     * @param tenantId         tenant
     * @param domicileKeyHash  índice cego do domicílio (hash, como na V4_12 — nunca documento em claro)
     * @param assessmentMonth  período de aferição
     * @param unitId           unidade da conta que recebe o crédito (a devolução é do domicílio, não dela)
     * @param billId           conta que recebe o crédito
     * @param billReferenceMonth competência da conta (primeiro dia do mês)
     * @param periodoDe        primeiro dia do período de fornecimento (para o tax_event_date)
     * @param periodoAte       último dia do período de fornecimento (para o tax_event_date)
     * @param cbsSobreConsumo  CBS sobre o consumo do período, resolvida da P2.4
     * @param ibsSobreConsumo  IBS sobre o consumo do período, resolvido da P2.4
     * @param accessedBy       quem consulta a lista (vai para o registro de acesso, G2)
     */
    public record ComandoCashback(UUID tenantId, String domicileKeyHash, YearMonth assessmentMonth,
                                  UUID unitId, UUID billId, LocalDate billReferenceMonth,
                                  LocalDate periodoDe, LocalDate periodoAte,
                                  BigDecimal cbsSobreConsumo, BigDecimal ibsSobreConsumo,
                                  UUID accessedBy) {
    }

    /** @param devolucao a devolução concedida ou a já existente; {@code null} nos demais desfechos */
    public record Resultado(Status status, CashbackStore.DevolucaoRegistrada devolucao,
                            CashbackCalculator.Calculo calculo) {
    }

    private final Clock clock;
    private final CashbackStore store;
    private final TaxEventDateResolver taxEventDateResolver;

    public CashbackService(Clock clock, CashbackStore store, TaxEventDateResolver taxEventDateResolver) {
        this.clock = Objects.requireNonNull(clock, "Clock é obrigatório (relógio injetável)");
        this.store = Objects.requireNonNull(store, "Porta de persistência do cashback é obrigatória");
        this.taxEventDateResolver = Objects.requireNonNull(taxEventDateResolver,
                "Resolvedor do tax_event_date é obrigatório (ativação condicionada à conferência da LC 227/2026)");
    }

    public Resultado conceder(TaxProfile profile, ComandoCashback cmd) {
        Objects.requireNonNull(profile, "tax.profile é obrigatório: ausência é erro, nunca perfil presumido");
        validar(cmd);

        if (!profile.cashbackEnabled()) {
            return new Resultado(Status.NAO_HABILITADO, null, null);
        }

        Optional<CashbackStore.EntradaLista> entrada =
                store.buscarEntrada(cmd.tenantId(), cmd.domicileKeyHash(), cmd.assessmentMonth());
        // G2: CADA acesso à lista é registrado — elegível ou não.
        store.registrarAcesso(cmd.tenantId(), entrada.map(CashbackStore.EntradaLista::importId).orElse(null),
                cmd.domicileKeyHash(), cmd.accessedBy(), "ELIGIBILITY_CHECK",
                "concessão de cashback CBS/IBS do período " + cmd.assessmentMonth(), clock.instant());

        if (entrada.isEmpty()) {
            return new Resultado(Status.NAO_ELEGIVEL, null, null);
        }

        Optional<CashbackStore.DevolucaoRegistrada> existente =
                store.buscarDevolucao(cmd.tenantId(), cmd.domicileKeyHash(), cmd.assessmentMonth());
        if (existente.isPresent()) {
            return new Resultado(Status.JA_DEVOLVIDO, existente.get(), null);
        }

        CashbackCalculator.Calculo calculo = CashbackCalculator.calcular(
                cmd.assessmentMonth(), cmd.cbsSobreConsumo(), cmd.ibsSobreConsumo());
        if (!calculo.temDevolucao()) {
            return new Resultado(Status.SEM_DEVOLUCAO_NA_VIGENCIA, null, calculo);
        }

        // Antes de gravar: sem tax_event_date resolvido não há concessão.
        LocalDate taxEventDate = taxEventDateResolver.resolver(
                profile.taxEventDateRule(), cmd.periodoDe(), cmd.periodoAte());

        CashbackStore.EntradaLista entradaElegivel = entrada.orElseThrow();
        CashbackStore.DevolucaoRegistrada salva = store.salvarDevolucao(
                new CashbackStore.DevolucaoRegistrada(null, cmd.tenantId(), cmd.domicileKeyHash(),
                        cmd.assessmentMonth(), cmd.billId(), cmd.billReferenceMonth(),
                        entradaElegivel.importId(), entradaElegivel.entryId(),
                        cmd.cbsSobreConsumo(), cmd.ibsSobreConsumo(),
                        calculo.cbsDevolvida(), calculo.ibsDevolvida(), calculo.totalDevolvido(),
                        taxEventDate));
        return new Resultado(Status.CONCEDIDO, salva, calculo);
    }

    private static void validar(ComandoCashback cmd) {
        Objects.requireNonNull(cmd, "Comando de cashback é obrigatório");
        if (cmd.tenantId() == null || cmd.billId() == null || cmd.unitId() == null || cmd.accessedBy() == null) {
            throw new IllegalArgumentException("Comando de cashback exige tenant, unidade, conta e solicitante do acesso");
        }
        if (cmd.domicileKeyHash() == null || cmd.domicileKeyHash().isBlank()) {
            throw new IllegalArgumentException("Domicílio é identificado pelo índice cego (hash): ausência é erro");
        }
        Objects.requireNonNull(cmd.assessmentMonth(), "Período de aferição é obrigatório");
        Objects.requireNonNull(cmd.billReferenceMonth(), "Competência da conta é obrigatória");
        Objects.requireNonNull(cmd.periodoDe(), "Início do período de fornecimento é obrigatório");
        Objects.requireNonNull(cmd.periodoAte(), "Fim do período de fornecimento é obrigatório");
        if (cmd.periodoAte().isBefore(cmd.periodoDe())) {
            throw new IllegalArgumentException("Período de fornecimento invertido: " + cmd.periodoDe() + ".." + cmd.periodoAte());
        }
    }
}
