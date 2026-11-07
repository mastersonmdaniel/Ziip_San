package br.com.ziipsan.commercial.billing.maintenance;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Trava da alteracao de vencimento (aceite da P2.20):
 * {@code bill.due_change_max_per_year} (quantas alteracoes a conta pode ter
 * no ano civil do novo vencimento) e
 * {@code bill.due_change_min_days_before_due} (antecedencia minima entre
 * hoje e o vencimento vigente para ainda poder mudar).
 *
 * <p><b>Semantica da contagem (correcao A6 da revisao cruzada):</b> o teto
 * e <b>por conta</b> -- e "conta", aqui, e a cadeia de versoes da conta
 * (mesma unidade e mesma referencia), que e como a descricao do parametro
 * no catalogo (V4_16) e o contador {@code bills.due_change_count} da
 * propria conta (V4_1, acumulado ao longo da cadeia) contam. A contagem do
 * ano e fato de quem chama: o servico soma as alteracoes executadas no ano
 * civil sobre TODAS as versoes da cadeia (de
 * {@code bill_maintenance_operations}, pelos ids da cadeia) -- contar por
 * unidade somaria contas de meses diferentes da mesma unidade, que nao
 * sao a mesma conta. A regra so compara. Regra pura.
 */
public final class DueDateChangeRule {

    private DueDateChangeRule() {
    }

    /**
     * @param changesAlreadyInYear alteracoes ja feitas nesta conta (a cadeia inteira
     *                             de versoes) no ano civil da operacao
     * @param currentDueDate       vencimento vigente da conta
     * @param newDueDate           vencimento pretendido (obrigatorio, depois de hoje)
     * @param today                data da operacao
     * @throws IllegalArgumentException com a trava violada nomeada, sem aplicar nada
     */
    public static void check(MaintenanceParameters parameters, int changesAlreadyInYear,
                             LocalDate currentDueDate, LocalDate newDueDate, LocalDate today) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(today, "today");
        if (changesAlreadyInYear < 0) {
            throw new IllegalArgumentException("Contagem de alteracoes nao pode ser negativa");
        }
        if (currentDueDate == null) {
            throw new IllegalArgumentException("Conta sem vencimento vigente nao tem vencimento a alterar");
        }
        if (newDueDate == null) {
            throw new IllegalArgumentException("Novo vencimento e obrigatorio");
        }
        if (!newDueDate.isAfter(today)) {
            throw new IllegalArgumentException("Novo vencimento deve ser posterior a hoje: " + newDueDate);
        }
        if (newDueDate.equals(currentDueDate)) {
            throw new IllegalArgumentException("Novo vencimento igual ao vigente nao e alteracao");
        }
        int max = parameters.dueChangeMaxPerYear();
        if (changesAlreadyInYear >= max) {
            throw new IllegalArgumentException("bill.due_change_max_per_year: conta ja teve "
                    + changesAlreadyInYear + " alteracao(oes) de vencimento no ano; teto e " + max);
        }
        long daysBeforeDue = ChronoUnit.DAYS.between(today, currentDueDate);
        int minDays = parameters.dueChangeMinDaysBeforeDue();
        if (daysBeforeDue < minDays) {
            throw new IllegalArgumentException("bill.due_change_min_days_before_due: faltam " + daysBeforeDue
                    + " dia(s) para o vencimento vigente; o minimo para alterar e " + minDays);
        }
    }
}
