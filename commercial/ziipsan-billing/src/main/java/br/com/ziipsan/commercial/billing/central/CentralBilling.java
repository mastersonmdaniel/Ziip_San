package br.com.ziipsan.commercial.billing.central;

import br.com.ziipsan.commercial.billing.special.SpecialSituationParameters;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orgao centralizador e faturas agrupadas (ED-22): o vinculo das unidades
 * ao orgao nasce de OS {@code CENTRAL_BILLING_LINK}; a fatura agrupadora
 * de uma competencia tem <b>codigo unico</b> e soma as contas das unidades
 * vinculadas; o documento fiscal segue
 * {@code fiscal.central_billing_invoice_mode}.
 *
 * <p>POC-51 (roteiro VISAN): o usuario avulso/orgao centralizador <b>nao</b>
 * e usuario regular com matricula e fatura propria - ele recebe o
 * documento agrupador com codigo unico, nao uma fatura individual por
 * unidade neste fluxo.
 */
public final class CentralBilling {

    public static final String OS_LINK = "CENTRAL_BILLING_LINK";

    private CentralBilling() {
    }

    /** Vinculo unidade -> orgao centralizador, criado pela OS CENTRAL_BILLING_LINK. */
    public record CentralizerLink(UUID id, UUID centralizerCustomerId, UUID unitId,
                                  UUID linkOsId, YearMonth startMonth, YearMonth endMonth) {
        public CentralizerLink {
            if (id == null || centralizerCustomerId == null || unitId == null) {
                throw new IllegalArgumentException("vinculo centralizador exige id, orgao e unidade");
            }
            if (linkOsId == null) {
                throw new IllegalArgumentException("vinculo com o orgao centralizador so por OS " + OS_LINK);
            }
            if (startMonth == null) {
                throw new IllegalArgumentException("vinculo centralizador exige mes de inicio");
            }
            if (endMonth != null && endMonth.isBefore(startMonth)) {
                throw new IllegalArgumentException("mes de fim anterior ao de inicio do vinculo");
            }
        }

        public boolean activeIn(YearMonth competence) {
            return !competence.isBefore(startMonth) && (endMonth == null || !competence.isAfter(endMonth));
        }
    }

    /** Uma conta individual que entra na fatura agrupadora. */
    public record MemberBill(UUID billId, UUID unitId, BigDecimal total) {
        public MemberBill {
            if (billId == null || unitId == null || total == null || total.signum() < 0) {
                throw new IllegalArgumentException("conta do agrupador invalida");
            }
        }
    }

    /**
     * Fatura agrupadora/centralizadora. O {@code code} e o codigo unico do
     * documento: montado de (tenant, orgao, competencia, sequencia do
     * tenant) - a unicidade e garantida no banco (indice unico da
     * migration pendente) e conferida aqui contra codigos ja emitidos
     * quando o chamador os entrega.
     */
    public record GroupedBill(String code, UUID centralizerCustomerId, YearMonth reference,
                              List<MemberBill> members, BigDecimal total,
                              SpecialSituationParameters.CentralBillingInvoiceMode invoiceMode,
                              int fiscalDocumentCount) {
        public GroupedBill {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("fatura agrupadora exige codigo unico");
            }
            if (centralizerCustomerId == null || reference == null || members == null || members.isEmpty()) {
                throw new IllegalArgumentException("fatura agrupadora exige orgao, competencia e contas");
            }
            if (invoiceMode == null) {
                throw new IllegalArgumentException("fatura agrupadora exige fiscal.central_billing_invoice_mode");
            }
            // Correcao pos-revisao (D1): o record se defende sozinho - quem o
            // constroi fora de CentralBilling.group(...) nao grava agrupadora
            // inconsistente. Total nulo, total divergente da soma dos membros
            // e contagem fiscal incoerente com o modo/membros sao recusados.
            if (total == null) {
                throw new IllegalArgumentException("fatura agrupadora exige total (soma das contas agrupadas)");
            }
            members = List.copyOf(members);
            Set<UUID> units = new LinkedHashSet<>();
            for (MemberBill member : members) {
                if (!units.add(member.unitId())) {
                    throw new IllegalArgumentException("unidade repetida na fatura agrupadora: " + member.unitId());
                }
            }
            BigDecimal memberSum = members.stream().map(MemberBill::total)
                    .reduce(new BigDecimal("0.00"), BigDecimal::add);
            if (total.compareTo(memberSum) != 0) {
                throw new IllegalArgumentException("total da fatura agrupadora (" + total
                        + ") diverge da soma das contas agrupadas (" + memberSum + ")");
            }
            int expectedFiscalCount = switch (invoiceMode) {
                case SINGLE -> 1;
                case PER_UNIT -> members.size();
                case NONE -> 0;
            };
            if (fiscalDocumentCount != expectedFiscalCount) {
                throw new IllegalArgumentException("fiscalDocumentCount (" + fiscalDocumentCount
                        + ") incoerente com fiscal.central_billing_invoice_mode=" + invoiceMode
                        + " e " + members.size() + " conta(s) agrupada(s): esperado " + expectedFiscalCount);
            }
        }
    }

    /** Codigo unico do agrupador: AGR-<competencia>-<orgao curto>-<sequencia>. */
    public static String groupedCode(UUID centralizerCustomerId, YearMonth reference, long sequence) {
        if (centralizerCustomerId == null || reference == null || sequence < 1) {
            throw new IllegalArgumentException("codigo do agrupador exige orgao, competencia e sequencia >= 1");
        }
        String shortId = centralizerCustomerId.toString().replace("-", "").substring(0, 8).toUpperCase();
        return "AGR-" + reference + "-" + shortId + "-" + sequence;
    }

    /**
     * Monta a fatura agrupadora da competencia: so entram contas de
     * unidades com vinculo ativo na competencia; o total e a soma exata
     * das contas; a quantidade de documentos fiscais segue o modo do
     * parametro (SINGLE=1, PER_UNIT=n, NONE=0).
     */
    public static GroupedBill group(String code, UUID centralizerCustomerId, YearMonth reference,
                                    List<CentralizerLink> links, List<MemberBill> bills,
                                    SpecialSituationParameters parameters) {
        if (links == null || bills == null) {
            throw new IllegalArgumentException("vinculos e contas sao obrigatorios");
        }
        Set<UUID> linkedUnits = new LinkedHashSet<>();
        for (CentralizerLink link : links) {
            if (link.centralizerCustomerId().equals(centralizerCustomerId) && link.activeIn(reference)) {
                linkedUnits.add(link.unitId());
            }
        }
        List<MemberBill> members = bills.stream().filter(b -> linkedUnits.contains(b.unitId())).toList();
        if (members.isEmpty()) {
            throw new IllegalArgumentException("nenhuma conta de unidade vinculada para agrupar em " + reference);
        }
        BigDecimal total = members.stream().map(MemberBill::total).reduce(new BigDecimal("0.00"), BigDecimal::add);
        SpecialSituationParameters.CentralBillingInvoiceMode mode = parameters.centralBillingInvoiceMode();
        int fiscalCount = switch (mode) {
            case SINGLE -> 1;
            case PER_UNIT -> members.size();
            case NONE -> 0;
        };
        return new GroupedBill(code, centralizerCustomerId, reference, members, total, mode, fiscalCount);
    }
}
