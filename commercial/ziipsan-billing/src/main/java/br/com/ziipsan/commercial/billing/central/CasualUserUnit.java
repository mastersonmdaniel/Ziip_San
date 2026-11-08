package br.com.ziipsan.commercial.billing.central;

import java.util.UUID;

/**
 * Usuario avulso (ED-22; ADR-0039, itens 1 e 3): o avulso e uma
 * <b>unidade consumidora de tipo proprio no catalogo</b> - nao uma
 * excecao por cliente nem um desvio no cadastro. Ele nao e usuario
 * regular: nao tem matricula regular nem fatura individual do ciclo
 * (POC-51 do roteiro VISAN); e atendido por guia avulsa (ED-26) ou pelo
 * documento agrupador do orgao centralizador.
 */
public final class CasualUserUnit {

    /** Tipos de unidade do catalogo (extrato da ED-22; o catalogo completo e da P1.3/ED-12). */
    public enum UnitKind {
        /** Unidade regular do ciclo de faturamento. */
        REGULAR,
        /** Usuario avulso: tipo proprio, sem matricula/fatura regular (ADR-0039). */
        CASUAL,
        /** Unidade de orgao centralizador (agrupadora de outras unidades). */
        CENTRALIZER
    }

    private CasualUserUnit() {
    }

    /** Unidade avulsa minima: tipo CASUAL, sem matricula regular. */
    public record CasualUnit(UUID unitId, UnitKind kind, String payerName) {
        public CasualUnit {
            if (unitId == null) {
                throw new IllegalArgumentException("unidade avulsa exige id");
            }
            if (kind != UnitKind.CASUAL) {
                throw new IllegalArgumentException("unidade avulsa exige o tipo proprio CASUAL do catalogo");
            }
            if (payerName == null || payerName.isBlank()) {
                throw new IllegalArgumentException("unidade avulsa exige o pagador identificado");
            }
        }
    }

    /** O avulso pode receber fatura regular do ciclo? Nao (POC-51). */
    public static boolean canReceiveRegularBill(UnitKind kind) {
        return kind == UnitKind.REGULAR;
    }

    /** Recusa explicita da fatura regular para o avulso, com o caminho certo. */
    public static void requireRegularBillAllowed(UnitKind kind) {
        if (!canReceiveRegularBill(kind)) {
            throw new IllegalArgumentException("unidade do tipo " + kind
                    + " nao recebe fatura regular: avulso e atendido por guia avulsa ou agrupador (POC-51/ADR-0039)");
        }
    }
}
