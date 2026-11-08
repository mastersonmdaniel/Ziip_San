package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Parcelamento de divida ativa (ED-31; POC-36): divide o total da inscricao
 * ATIVA em parcelas mensais iguais (ultima absorve o residuo de
 * arredondamento), respeitando {@code da.max_installments} e
 * {@code da.min_installment} do tenant.
 *
 * <p>PORTAO DE PRESCRICAO (correcao D4 da revisao): divida prescrita NAO
 * parcela. {@code plan} exige o {@link PrescriptionGate} e avalia a
 * prescricao ITEM A ITEM antes de gerar qualquer parcela — qualquer item
 * PRESCRIBED ou UNDETERMINED bloqueia o plano inteiro. Nao existe
 * assinatura sem portao: a versao anterior delegava a validacao a um
 * servico nomeado no javadoc que nao existia no pacote; a afirmacao era
 * falsa e foi removida junto com o caminho sem portao. Inscricao
 * cancelada/quitada tambem nao parcela.
 *
 * <p>FRONTEIRA DECLARADA: juros/multa do parcelamento de DA seguem a lei do
 * ente, que NAO esta nas fontes — este calculo NAO aplica encargos
 * inventados: parcela o total inscrito. Quando a lei do ente for entregue,
 * os encargos entram como parametro versionado, nao como constante.
 */
public final class ActiveDebtInstallment {

    public record Installment(int number, LocalDate dueDate, BigDecimal amount) {}

    public record Plan(UUID enrollmentId, BigDecimal total, List<Installment> installments) {
        public Plan { installments = List.copyOf(installments); }
    }

    private ActiveDebtInstallment() {}

    public static Plan plan(ActiveDebtEnrollment.Enrollment enrollment, int count,
                            LocalDate firstDueDate, ActiveDebtParameters params,
                            PrescriptionGate gate, Prescription.ProviderNature nature,
                            Prescription.ClaimType claim, boolean legalAnalysisRecorded) {
        Objects.requireNonNull(enrollment, "inscricao e obrigatoria");
        Objects.requireNonNull(firstDueDate, "primeiro vencimento e obrigatorio");
        Objects.requireNonNull(params, "parametros sao obrigatorios");
        Objects.requireNonNull(gate, "portao de prescricao (P4.1) e obrigatorio: divida prescrita nao parcela");
        Objects.requireNonNull(nature, "natureza do prestador e obrigatoria");
        Objects.requireNonNull(claim, "pretensao e obrigatoria");
        gate.assertAllowedForAll(PrescriptionGate.Operation.INSTALLMENT, nature, claim,
                enrollment.items(), legalAnalysisRecorded);
        if (enrollment.status() != ActiveDebtEnrollment.Status.ACTIVE
                && enrollment.status() != ActiveDebtEnrollment.Status.UNDER_INSTALLMENT)
            throw new IllegalStateException("parcelamento exige inscricao ativa; situacao atual: " + enrollment.status());
        int max = params.maxInstallments();
        if (count < 1 || count > max)
            throw new IllegalArgumentException("numero de parcelas fora do permitido (1.." + max + "): " + count);
        BigDecimal total = enrollment.total();
        BigDecimal each = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        if (each.compareTo(params.minInstallment()) < 0 && count > 1)
            throw new IllegalArgumentException("parcela de " + each + " abaixo do minimo do tenant (da.min_installment)");
        List<Installment> list = new ArrayList<>();
        BigDecimal acc = BigDecimal.ZERO;
        for (int i = 1; i <= count; i++) {
            BigDecimal amount = (i < count) ? each : total.subtract(acc);
            acc = acc.add(amount);
            list.add(new Installment(i, firstDueDate.plusMonths(i - 1L), amount));
        }
        return new Plan(enrollment.id(), total, list);
    }
}
