package br.com.ziipsan.commercial.fiscal.relief;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static br.com.ziipsan.commercial.fiscal.relief.SubstitutionLinkService.MotiveKind.BILL_ALTERATION;
import static br.com.ziipsan.commercial.fiscal.relief.SubstitutionLinkService.MotiveKind.BILL_STATUS;
import static br.com.ziipsan.commercial.fiscal.relief.SubstitutionLinkService.MotiveKind.CANCELLATION_TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Grupo 11 — Fiscal: vinculos de substituicao, guarda de completude e cobertura. */
class SubstitutionLinksTest {

    private SubstitutionLinkService service() {
        var types = new SubstitutionTypes();
        types.loadReference("F-16/art. 898 (teste)");
        return new SubstitutionLinkService(types);
    }

    private static SubstitutionLinkService.Motive motive(
            SubstitutionLinkService.MotiveKind kind, String ref, boolean cancelledOnly) {
        return new SubstitutionLinkService.Motive(kind, ref, cancelledOnly);
    }

    @Test
    @DisplayName("situacao Cancelada sem vinculo: activacao recusada (F-19); com vinculo, resolve pelo motivo")
    void billStatusGuardAndResolution() {
        var service = service();
        var motive = motive(BILL_STATUS, "MOT-CANC-1", true);
        assertThatThrownBy(() -> service.activateMotive(motive))
                .isInstanceOf(IllegalStateException.class);
        service.link(motive, "01");
        service.activateMotive(motive);
        assertThat(service.resolveFor(motive).code()).isEqualTo("01");
    }

    @Test
    @DisplayName("alteracao de fatura sem vinculo: activacao recusada (F-20); situacao nao-cancelada nao exige vinculo")
    void billAlterationGuard() {
        var service = service();
        var alteration = motive(BILL_ALTERATION, "MOT-ALT-1", false);
        assertThatThrownBy(() -> service.activateMotive(alteration))
                .isInstanceOf(IllegalStateException.class);
        // O motivo recusado continua visivel no relatorio de cobertura (e uma falta real);
        // F-19: vinculo de situacao so se aplica a motivos de situacao "Cancelada".
        var emitted = motive(BILL_STATUS, "MOT-EMITIDA", false);
        service.activateMotive(emitted);
        assertThat(service.coverageGaps()).containsExactly(alteration);
    }

    @Test
    @DisplayName("cancelamento sem vinculo: aviso forte (P-02-10 em aberto) e relatorio de cobertura lista a falta")
    void cancellationWarningAndCoverage() {
        var service = service();
        var motive = motive(CANCELLATION_TRANSFER, "MOT-TRF-1", false);
        service.activateMotive(motive);
        assertThat(service.warnings()).hasSize(1);
        assertThat(service.coverageGaps()).containsExactly(motive);
        assertThatThrownBy(() -> service.resolveFor(motive))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("emissao substituta recusada");
    }

    @Test
    @DisplayName("vinculo para tipo inactivo nao resolve")
    void inactiveTypeDoesNotResolve() {
        var types = new SubstitutionTypes();
        types.loadReference("F-16/art. 898 (teste)");
        var service = new SubstitutionLinkService(types);
        var motive = motive(BILL_ALTERATION, "MOT-ALT-9", false);
        service.link(motive, "09");
        types.setActive("09", false);
        assertThatThrownBy(() -> service.resolveFor(motive))
                .isInstanceOf(IllegalStateException.class);
    }
}
