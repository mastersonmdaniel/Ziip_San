package br.com.ziipsan.commercial.billingrules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Comparacao conta impressa (campo) x conta recalculada (servidor) — ED-19.
 * O servidor recalcula com o MESMO modulo a partir da entrada remontada; a
 * divergencia tem duas naturezas, ambas nomeadas:
 *
 * <ul>
 *   <li><b>De base</b>: o retrato usado em campo nao e o que o servidor
 *       esperava (versao de tarifa, versao de contrato das regras ou versoes
 *       de parametro diferentes) — a conta nasceu de outra base, mesmo que o
 *       total coincida por acidente;</li>
 *   <li><b>De valor</b>: algum componente (consumo, agua, fixa, esgoto,
 *       debitos, creditos, total) difere no centavo.</li>
 * </ul>
 *
 * Qualquer uma das duas manda a conta para a fila de divergencia (o servico
 * de submissao decide o fluxo; aqui so se mede e se explica).
 */
public final class DivergenceEvaluator {

    /** Um campo divergente, com os dois valores em texto plano. */
    public record FieldDifference(String field, String fieldValue, String serverValue) {
    }

    /**
     * @param basisDivergent  base divergente (versoes)
     * @param differences     campos de valor divergentes (vazio = valores iguais)
     */
    public record Divergence(boolean basisDivergent, List<FieldDifference> differences) {

        public Divergence {
            differences = List.copyOf(differences);
        }

        public boolean divergent() {
            return basisDivergent || !differences.isEmpty();
        }
    }

    private DivergenceEvaluator() {
    }

    /**
     * @param fieldInput   entrada como o aparelho a recebeu (pacote da rota)
     * @param fieldResult  resultado impresso em campo
     * @param serverInput  entrada remontada pelo servidor (retrato esperado)
     * @param serverResult resultado recalculado pelo servidor
     */
    public static Divergence evaluate(FieldBillInput fieldInput, FieldBillResult fieldResult,
                                      FieldBillInput serverInput, FieldBillResult serverResult) {
        Objects.requireNonNull(fieldInput, "entrada de campo e obrigatoria");
        Objects.requireNonNull(fieldResult, "resultado de campo e obrigatorio");
        Objects.requireNonNull(serverInput, "entrada do servidor e obrigatoria");
        Objects.requireNonNull(serverResult, "resultado do servidor e obrigatorio");
        if (!fieldResult.taskId().equals(serverResult.taskId())) {
            throw new IllegalArgumentException("Divergencia so se avalia dentro da mesma task_id");
        }

        boolean basis = !fieldInput.snapshot().tariffVersionId()
                .equals(serverInput.snapshot().tariffVersionId())
                || !fieldInput.snapshot().parameterVersions()
                .equals(serverInput.snapshot().parameterVersions())
                || fieldInput.previousReading() != serverInput.previousReading()
                || fieldInput.economies() != serverInput.economies();

        List<FieldDifference> diffs = new ArrayList<>();
        compare(diffs, "consumptionM3",
                Integer.toString(fieldResult.consumptionM3()), Integer.toString(serverResult.consumptionM3()));
        compare(diffs, "billedM3",
                Integer.toString(fieldResult.billedM3()), Integer.toString(serverResult.billedM3()));
        compareMoney(diffs, "waterValue", fieldResult.waterValue(), serverResult.waterValue());
        compareMoney(diffs, "fixedValue", fieldResult.fixedValue(), serverResult.fixedValue());
        compareMoney(diffs, "sewerValue", fieldResult.sewerValue(), serverResult.sewerValue());
        compareMoney(diffs, "debitsValue", fieldResult.debitsValue(), serverResult.debitsValue());
        compareMoney(diffs, "creditsApplied", fieldResult.creditsApplied(), serverResult.creditsApplied());
        compareMoney(diffs, "total", fieldResult.total(), serverResult.total());
        return new Divergence(basis, diffs);
    }

    private static void compare(List<FieldDifference> diffs, String field, String a, String b) {
        if (!a.equals(b)) {
            diffs.add(new FieldDifference(field, a, b));
        }
    }

    private static void compareMoney(List<FieldDifference> diffs, String field, BigDecimal a, BigDecimal b) {
        if (a.compareTo(b) != 0) {
            diffs.add(new FieldDifference(field, a.toPlainString(), b.toPlainString()));
        }
    }
}
