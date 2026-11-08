package br.com.ziipsan.core.fieldos;

import java.util.Objects;
import java.util.UUID;

/**
 * Aceite do usuario na tela do aparelho (ED-20), por assinatura ou codigo
 * conforme o tipo de OS ({@code so.require_customer_acceptance_by_type}):
 * o mapa tipo -> modo e do tenant (ver {@link FieldOsParameters}); tipo
 * fora do mapa NAO ganha um modo presumido — a conclusao e recusada ate a
 * definicao existir ([A DEFINIR] nomeado no relatorio).
 */
public final class CustomerAcceptance {

    public enum Mode { SIGNATURE, CODE, NONE }

    public record Evidence(Mode mode, UUID signatureDocumentId, String presentedCode, String expectedCode) {}

    private CustomerAcceptance() {}

    public static void requireSatisfied(Mode required, Evidence evidence) {
        Objects.requireNonNull(required, "modo de aceite exigido pelo tipo e obrigatorio");
        if (required == Mode.NONE) return;
        if (evidence == null) throw new IllegalStateException(
                "aceite do usuario e obrigatorio para o tipo (modo " + required + ") e nao foi apresentado");
        if (evidence.mode() != required) throw new IllegalStateException(
                "aceite por " + evidence.mode() + " nao satisfaz o modo exigido pelo tipo: " + required);
        switch (required) {
            case SIGNATURE -> {
                if (evidence.signatureDocumentId() == null) throw new IllegalStateException(
                        "aceite por assinatura exige o documento da assinatura capturada no aparelho");
            }
            case CODE -> {
                if (evidence.presentedCode() == null || evidence.expectedCode() == null
                        || !evidence.presentedCode().equals(evidence.expectedCode())) throw new IllegalStateException(
                        "codigo de aceite apresentado nao confere com o codigo da OS");
            }
            case NONE -> { }
        }
    }
}
