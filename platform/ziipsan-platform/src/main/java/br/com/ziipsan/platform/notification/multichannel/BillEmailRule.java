package br.com.ziipsan.platform.notification.multichannel;

import java.util.Optional;

/**
 * Regra pura do PDF anexo da fatura por e-mail (aceite da ED-41; ADR-0028,
 * item l): o PDF só vai anexo em DUAS hipóteses —
 *
 * <ol>
 *   <li><b>opt-in registrado do morador</b> para receber o PDF anexo; ou</li>
 *   <li><b>padrão do tenant</b> ({@code msg.bill_pdf_default}) <b>com aceite
 *       formal registrado</b> ({@code msg.bill_pdf_formal_acceptance}).</li>
 * </ol>
 *
 * Fora delas, a fatura vai com aviso e LINK AUTENTICADO ao portal, sem
 * anexo. E o opt-out registrado do morador vence tudo, inclusive o padrão
 * do tenant com aceite formal: quem recusou o anexo não o recebe por
 * decisão do tenant. A base da decisão volta nomeada no resultado — a
 * trilha diz POR QUE o PDF foi ou não foi.
 *
 * <p>O opt-in/opt-out do morador é lido de uma porta própria
 * ({@link BillPdfOptInReader}) sobre o registro de preferências da P3.36 e
 * os consentimentos da P1.3: {@code Optional<Boolean>} — presente
 * {@code true} = opt-in, presente {@code false} = opt-out, vazio = nenhuma
 * manifestação registrada.
 */
public final class BillEmailRule {

    public enum Basis {
        RESIDENT_OPT_IN,
        TENANT_DEFAULT_WITH_FORMAL_ACCEPTANCE,
        LINK_ONLY_RESIDENT_OPT_OUT,
        LINK_ONLY_NO_FORMAL_ACCEPTANCE,
        LINK_ONLY_NO_OPT_IN
    }

    public record AttachmentDecision(boolean attachPdf, Basis basis) {
    }

    private BillEmailRule() {
    }

    public static AttachmentDecision decide(Optional<Boolean> residentOptIn,
                                            boolean tenantDefaultAttach,
                                            boolean formalAcceptanceRecorded) {
        if (residentOptIn == null) {
            throw new IllegalArgumentException("a manifestação do morador é obrigatória (pode ser vazia, não nula)");
        }
        if (residentOptIn.isPresent() && !residentOptIn.get()) {
            return new AttachmentDecision(false, Basis.LINK_ONLY_RESIDENT_OPT_OUT);
        }
        if (residentOptIn.isPresent()) {
            return new AttachmentDecision(true, Basis.RESIDENT_OPT_IN);
        }
        if (tenantDefaultAttach && formalAcceptanceRecorded) {
            return new AttachmentDecision(true, Basis.TENANT_DEFAULT_WITH_FORMAL_ACCEPTANCE);
        }
        if (tenantDefaultAttach) {
            return new AttachmentDecision(false, Basis.LINK_ONLY_NO_FORMAL_ACCEPTANCE);
        }
        return new AttachmentDecision(false, Basis.LINK_ONLY_NO_OPT_IN);
    }
}
