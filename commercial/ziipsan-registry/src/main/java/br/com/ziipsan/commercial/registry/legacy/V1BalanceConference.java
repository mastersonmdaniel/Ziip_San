package br.com.ziipsan.commercial.registry.legacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P2.10: conferência do saldo por matrícula, com as prestações incluídas —
 * o mecanismo que prova o "saldo por matrícula igual ao do legado" do
 * aceite. Regra pura, sem banco: recebe as unidades e as prestações
 * encenadas pela P1.2 e decompõe o saldo declarado do legado, por
 * matrícula:
 *
 * <pre>
 *   saldo do legado (balance_cents do staging da unidade)
 *     = Σ prestações a vencer da matrícula (viram débitos de parcelamento)
 *     + resíduo (o restante do saldo: contas vencidas e demais débitos —
 *       vira UM débito de origem LEGACY_IMPORT na aplicação)
 * </pre>
 *
 * <p>A conferência FALHA para a matrícula quando: o saldo declarado é
 * nulo (nada a conferir — a unidade não entra na aplicação de saldo), o
 * saldo é negativo, ou as prestações somam MAIS que o saldo declarado
 * (resíduo negativo: ou o legado declarou saldo sem as prestações, ou a
 * carga de prestações está errada — nos dois casos é divergência a
 * resolver no legado, nunca um número a "ajustar" aqui). Matrícula em
 * falha não gera débito nenhum na aplicação; as demais seguem.
 *
 * <p>Nenhum número é inventado: o saldo esperado é o declarado na carga
 * (nos testes, massa sintética declarada; na produção do lançamento, o
 * extrato do legado) e o saldo migrado é a soma do que a aplicação
 * efetivamente cria.
 */
public final class V1BalanceConference {

    private V1BalanceConference() {
    }

    public record MatriculaConference(
            String legacyKey,
            String matricula,
            Long legacyBalanceCents,
            long installmentsCents,
            int installmentsCount,
            long residualCents,
            boolean matches,
            String failureCode) {

        /** O saldo que a aplicação criará em débitos para esta matrícula. */
        public long migratedBalanceCents() {
            return installmentsCents + Math.max(residualCents, 0L);
        }
    }

    public record Report(List<MatriculaConference> rows) {

        public boolean allMatch() {
            return rows.stream().allMatch(MatriculaConference::matches);
        }

        public List<MatriculaConference> failures() {
            return rows.stream().filter(r -> !r.matches()).toList();
        }

        public long legacyTotalCents() {
            return rows.stream().mapToLong(r -> r.legacyBalanceCents() == null ? 0L : r.legacyBalanceCents()).sum();
        }

        public long migratedTotalCents() {
            return rows.stream().filter(MatriculaConference::matches).mapToLong(MatriculaConference::migratedBalanceCents).sum();
        }
    }

    public static Report confer(List<StagedUnit> units, List<StagedInstallment> installments) {
        Map<String, List<StagedInstallment>> byUnit = new LinkedHashMap<>();
        for (StagedInstallment installment : installments) {
            byUnit.computeIfAbsent(installment.unitLegacyKey(), k -> new ArrayList<>()).add(installment);
        }
        List<MatriculaConference> rows = new ArrayList<>();
        for (StagedUnit unit : units) {
            List<StagedInstallment> own = byUnit.getOrDefault(unit.legacyKey(), List.of());
            long sum = own.stream().mapToLong(StagedInstallment::amountCents).sum();
            Long balance = unit.balanceCents();
            if (balance == null) {
                rows.add(new MatriculaConference(unit.legacyKey(), unit.matricula(), null,
                        sum, own.size(), 0L, false, "BALANCE_MISSING"));
                continue;
            }
            if (balance < 0) {
                rows.add(new MatriculaConference(unit.legacyKey(), unit.matricula(), balance,
                        sum, own.size(), balance - sum, false, "BALANCE_NEGATIVE"));
                continue;
            }
            long residual = balance - sum;
            if (residual < 0) {
                rows.add(new MatriculaConference(unit.legacyKey(), unit.matricula(), balance,
                        sum, own.size(), residual, false, "INSTALLMENTS_EXCEED_BALANCE"));
                continue;
            }
            rows.add(new MatriculaConference(unit.legacyKey(), unit.matricula(), balance,
                    sum, own.size(), residual, true, null));
        }
        return new Report(List.copyOf(rows));
    }
}
