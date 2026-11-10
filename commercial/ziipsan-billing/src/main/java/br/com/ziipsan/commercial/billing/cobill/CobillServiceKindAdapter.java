package br.com.ziipsan.commercial.billing.cobill;

import java.util.Objects;

/**
 * De-para BIJETIVO entre o tipo de servico generalizado
 * ({@link CobillServiceKind}, novo, Camada 3) e o tipo de servico do
 * contrato vigente ({@link CobillContract.ServiceKind}, ED-25) —
 * desenho da Camada 0, secao 3.1 item 2; INV-C3-03.
 *
 * <p>Par unico vigente: {@code SOLID_WASTE <-> SOLID_WASTE}. Valor sem
 * par do outro lado = FALHA FECHADA, nunca mapeamento presumido: os
 * {@code switch} sao exaustivos sobre os enums actuais, por isso um
 * valor novo acrescentado a um dos lados sem par no outro deixa de
 * COMPILAR aqui ate o par ser declarado — a bijeccao e guardada pelo
 * compilador, nao por convencao. Nulo e recusado nos dois sentidos.
 */
public final class CobillServiceKindAdapter {

    private CobillServiceKindAdapter() {
    }

    /** Vigente (contrato da ED-25) -> generalizado (Camada 3). */
    public static CobillServiceKind toKind(CobillContract.ServiceKind serviceKind) {
        Objects.requireNonNull(serviceKind, "tipo de servico do contrato (ED-25) e obrigatorio");
        return switch (serviceKind) {
            case SOLID_WASTE -> CobillServiceKind.SOLID_WASTE;
        };
    }

    /** Generalizado (Camada 3) -> vigente (contrato da ED-25). */
    public static CobillContract.ServiceKind toServiceKind(CobillServiceKind kind) {
        Objects.requireNonNull(kind, "tipo de servico generalizado e obrigatorio");
        return switch (kind) {
            case SOLID_WASTE -> CobillContract.ServiceKind.SOLID_WASTE;
        };
    }
}
