package br.com.ziipsan.commercial.billing.cobill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Camada 3: de-para bijetivo de tipos de servico (INV-C3-03). */
class CobillServiceKindAdapterTest {

    @Test
    @DisplayName("par unico vigente: SOLID_WASTE -> SOLID_WASTE")
    void vigenteParaGeneralizado() {
        assertEquals(CobillServiceKind.SOLID_WASTE,
                CobillServiceKindAdapter.toKind(CobillContract.ServiceKind.SOLID_WASTE));
    }

    @Test
    @DisplayName("par unico vigente: SOLID_WASTE <- SOLID_WASTE")
    void generalizadoParaVigente() {
        assertEquals(CobillContract.ServiceKind.SOLID_WASTE,
                CobillServiceKindAdapter.toServiceKind(CobillServiceKind.SOLID_WASTE));
    }

    @Test
    @DisplayName("INV-C3-03: ida e volta devolvem o mesmo valor para TODO o valor vigente")
    void idaEVoltaVigente() {
        for (CobillContract.ServiceKind vigente : CobillContract.ServiceKind.values()) {
            assertEquals(vigente,
                    CobillServiceKindAdapter.toServiceKind(CobillServiceKindAdapter.toKind(vigente)));
        }
    }

    @Test
    @DisplayName("INV-C3-03: ida e volta devolvem o mesmo valor para TODO o valor generalizado; contagens iguais")
    void idaEVoltaGeneralizado() {
        for (CobillServiceKind kind : CobillServiceKind.values()) {
            assertEquals(kind,
                    CobillServiceKindAdapter.toKind(CobillServiceKindAdapter.toServiceKind(kind)));
        }
        assertEquals(CobillContract.ServiceKind.values().length, CobillServiceKind.values().length);
    }

    @Test
    @DisplayName("o enum generalizado nasce SO com SOLID_WASTE (nenhum tipo inventado)")
    void nasceSoComResiduosSolidos() {
        assertEquals(1, CobillServiceKind.values().length);
        assertEquals(CobillServiceKind.SOLID_WASTE, CobillServiceKind.values()[0]);
    }

    @Test
    @DisplayName("nulo falha fechado nos dois sentidos")
    void nuloFalhaFechado() {
        assertThrows(NullPointerException.class, () -> CobillServiceKindAdapter.toKind(null));
        assertThrows(NullPointerException.class, () -> CobillServiceKindAdapter.toServiceKind(null));
    }
}
