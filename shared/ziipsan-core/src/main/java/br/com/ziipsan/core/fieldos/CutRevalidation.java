package br.com.ziipsan.core.fieldos;

import java.util.ArrayList;
import java.util.List;

/**
 * Revalidacao da OS de corte (ED-20; v2.15) — a MESMA regra roda:
 * <ul>
 *   <li>no aparelho, antes de executar, inclusive SEM REDE: o tecnico
 *       registra no aparelho os fatos que observa/confirma localmente
 *       (ex.: quitacao apresentada pelo usuario) e a regra confere contra
 *       o retrato do pacote;</li>
 *   <li>no servidor, na sincronizacao: a regra confere contra os fatos
 *       ATUAIS ({@link FieldOsPorts.CutFactsPort}).</li>
 * </ul>
 * Falha = a OS NAO pode ser executada: o desfecho e o CANCELAMENTO da OS
 * SEM EFEITO NA LIGACAO (classe a do aceite) — nenhum corte acontece.
 *
 * <p>Motivos nomeados: debito quitado (inclusive quitacao registrada
 * DEPOIS da emissao do pacote — o caso do teste offline do aceite),
 * debito inexistente, contestacao aberta, aviso nao dado e vedacao legal.
 */
public final class CutRevalidation {

    public enum Reason {
        DEBIT_PAID_AFTER_ISSUE,
        DEBIT_PAID_AT_ISSUE,
        NO_OPEN_DEBIT,
        CONTESTATION_OPEN,
        NOTICE_NOT_GIVEN,
        LEGAL_PROHIBITION
    }

    public record Decision(boolean allowed, List<Reason> reasons) {
        public Decision { reasons = reasons == null ? List.of() : List.copyOf(reasons); }
        public boolean cancelled() { return !allowed; }
    }

    private CutRevalidation() {}

    /**
     * @param atIssue  fatos do retrato emitido ao aparelho
     * @param current  fatos conferidos agora (aparelho offline ou servidor)
     */
    public static Decision evaluate(CutPackage.Facts atIssue, CutPackage.Facts current) {
        java.util.Objects.requireNonNull(atIssue, "fatos da emissao sao obrigatorios");
        java.util.Objects.requireNonNull(current, "fatos atuais sao obrigatorios");
        List<Reason> reasons = new ArrayList<>();
        if (current.debitState() == CutPackage.DebitState.PAID) {
            reasons.add(atIssue.debitState() == CutPackage.DebitState.OPEN
                    ? Reason.DEBIT_PAID_AFTER_ISSUE : Reason.DEBIT_PAID_AT_ISSUE);
        }
        if (current.debitState() == CutPackage.DebitState.NONE) reasons.add(Reason.NO_OPEN_DEBIT);
        if (current.prohibitions().contains(CutPackage.Prohibition.CONTESTATION_OPEN)) reasons.add(Reason.CONTESTATION_OPEN);
        if (current.prohibitions().contains(CutPackage.Prohibition.NOTICE_NOT_GIVEN)) reasons.add(Reason.NOTICE_NOT_GIVEN);
        if (current.prohibitions().contains(CutPackage.Prohibition.LEGAL_PROHIBITION)) reasons.add(Reason.LEGAL_PROHIBITION);
        return new Decision(reasons.isEmpty(), reasons);
    }
}
