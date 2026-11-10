package br.com.ziipsan.commercial.collection.cession;

import br.com.ziipsan.commercial.collection.cession.CessionRegistry.Registration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-103/E5 (decisao 62) — reconciliacao das baixas esperadas dos
 * recebiveis CEDIDOS com os movimentos da conta vinculada. Produtor
 * (execucao ED-103, decisao 62): sessao
 * 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p>Casamento: referencia do recebivel + valor. A omissao e valor
 * EXACTO; qualquer tolerancia e parametro declarado de quem reconcilia
 * ({@code tolerance}, nunca presumida). Divergencias TIPIFICADAS:
 * <ul>
 *   <li>{@code VALOR_DIVERGENTE} — mesma referencia, valor fora da
 *       tolerancia declarada;</li>
 *   <li>{@code SEM_BAIXA_ESPERADA} — movimento sem cessao esperada
 *       correspondente;</li>
 *   <li>{@code SEM_MOVIMENTO} — baixa esperada sem movimento (reportada
 *       no conjunto dos PENDENTES).</li>
 * </ul>
 *
 * <p><b>Nenhum ajuste silencioso:</b> a reconciliacao e um RELATORIO —
 * nao liquida registos, nao altera valores, nao cria movimentos. O que
 * fazer com cada divergencia e acto declarado de quem opera (a
 * liquidacao faz-se pela transicao explicita do {@link CessionRegistry}).
 * O relatorio tem os tres conjuntos: casados, divergentes e pendentes.
 */
public final class Reconciliation {

    public enum DivergenceKind { SEM_MOVIMENTO, SEM_BAIXA_ESPERADA, VALOR_DIVERGENTE }

    /** Par casado: baixa esperada x movimento, com o valor conferido. */
    public record Matched(String receivableId, BigDecimal expectedAmount, BigDecimal movementAmount) {}

    /** Divergencia tipificada; campos nao aplicaveis vao a null, nunca inventados. */
    public record Divergence(DivergenceKind kind, String receivableId,
                             BigDecimal expectedAmount, BigDecimal movementAmount) {}

    /** Relatorio: casados, divergentes (tipificados) e pendentes (SEM_MOVIMENTO). */
    public record Report(List<Matched> matched, List<Divergence> divergent, List<Divergence> pending) {
        public Report {
            matched = List.copyOf(matched);
            divergent = List.copyOf(divergent);
            pending = List.copyOf(pending);
        }
    }

    private Reconciliation() {}

    /**
     * Reconcilia as baixas esperadas (registos CEDIDO) com os movimentos.
     *
     * @param tolerance tolerancia de valor DECLARADA; {@code null} = zero (exacto)
     */
    public static Report reconcile(List<Registration> expectedSettlements,
                                   List<LinkedAccountMovement> movements, BigDecimal tolerance) {
        Objects.requireNonNull(expectedSettlements, "baixas esperadas sao obrigatorias");
        Objects.requireNonNull(movements, "movimentos sao obrigatorios");
        BigDecimal tol = tolerance == null ? BigDecimal.ZERO : tolerance;
        if (tol.signum() < 0)
            throw new ReceivablesCessionException("RECONCILIATION_TOLERANCE_NEGATIVE",
                    "tolerancia de reconciliacao nao pode ser negativa");

        Map<String, LinkedAccountMovement> movementByRef = new LinkedHashMap<>();
        List<Divergence> divergent = new ArrayList<>();
        for (LinkedAccountMovement m : movements) {
            if (movementByRef.putIfAbsent(m.reference(), m) != null)
                divergent.add(new Divergence(DivergenceKind.SEM_BAIXA_ESPERADA, m.reference(), null, m.amount()));
        }

        List<Matched> matched = new ArrayList<>();
        List<Divergence> pending = new ArrayList<>();
        for (Registration expected : expectedSettlements) {
            LinkedAccountMovement m = movementByRef.remove(expected.receivableId());
            if (m == null) {
                pending.add(new Divergence(DivergenceKind.SEM_MOVIMENTO, expected.receivableId(),
                        expected.amount(), null));
            } else if (m.amount().subtract(expected.amount()).abs().compareTo(tol) <= 0) {
                matched.add(new Matched(expected.receivableId(), expected.amount(), m.amount()));
            } else {
                divergent.add(new Divergence(DivergenceKind.VALOR_DIVERGENTE, expected.receivableId(),
                        expected.amount(), m.amount()));
            }
        }
        for (LinkedAccountMovement leftover : movementByRef.values())
            divergent.add(new Divergence(DivergenceKind.SEM_BAIXA_ESPERADA, leftover.reference(),
                    null, leftover.amount()));

        return new Report(matched, divergent, pending);
    }

    /** Atalho sobre o registo: baixas esperadas = CEDIDO do tenant. */
    public static Report reconcile(CessionRegistry registry, UUID tenantId,
                                   List<LinkedAccountMovement> movements, BigDecimal tolerance) {
        return reconcile(registry.expectedSettlements(tenantId), movements, tolerance);
    }
}
