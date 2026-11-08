package br.com.ziipsan.commercial.billing.altsolution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MandatoryConnectionCycleTest {

    private static final UUID PROP = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final LocalDate COMM = LocalDate.of(2026, 1, 10); // comunicacao da disponibilidade
    private static final LocalDate NOTICE = LocalDate.of(2026, 2, 1); // aviso na fatura anterior

    private static MandatoryConnectionCycle.AvailabilityCase communicated() {
        return MandatoryConnectionCycle.communicate(PROP, UNIT, COMM);
    }

    private static MandatoryConnectionCycle.AvailabilityCase noticed() {
        // 01/02/2026 + 90 dias = 02/05/2026: primeira cobranca no limite exato do piso
        return MandatoryConnectionCycle.issueBillNotice(communicated(), NOTICE, LocalDate.of(2026, 5, 2), 90);
    }

    @Test @DisplayName("aviso na fatura: cobranca 89 dias apos o aviso e recusada; 90 dias aceita")
    void noticeFloorEnforced() {
        assertThatThrownBy(() -> MandatoryConnectionCycle.issueBillNotice(
                communicated(), NOTICE, LocalDate.of(2026, 5, 1), 90))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("90 dias");
        assertThatThrownBy(() -> MandatoryConnectionCycle.issueBillNotice(
                communicated(), NOTICE, LocalDate.of(2026, 5, 2), 60))
                .isInstanceOf(IllegalArgumentException.class); // noticeDays abaixo do piso da classe c
        assertThat(noticed().firstAvailabilityChargeOn()).isEqualTo(LocalDate.of(2026, 5, 2));
    }

    @Test @DisplayName("prazo para ligar: comunicacao + 12 meses (teto) = 10/01/2027; 13 meses recusado no prazo")
    void deadlineCeiling() {
        assertThat(MandatoryConnectionCycle.connectDeadline(communicated(), 12))
                .isEqualTo(LocalDate.of(2027, 1, 10));
        assertThatThrownBy(() -> MandatoryConnectionCycle.connectDeadline(communicated(), 13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("elegibilidade a cobranca de disponibilidade (valor e da P2.5/RAS-22): so apos o aviso+piso e sem ligar/dispensar")
    void availabilityChargeEligibility() {
        var c = noticed();
        assertThat(MandatoryConnectionCycle.availabilityChargeApplies(c, LocalDate.of(2026, 5, 1))).isFalse();
        assertThat(MandatoryConnectionCycle.availabilityChargeApplies(c, LocalDate.of(2026, 5, 2))).isTrue();
        assertThat(MandatoryConnectionCycle.availabilityChargeApplies(communicated(), LocalDate.of(2026, 6, 1)))
                .isFalse(); // sem aviso na fatura, nao cobra
    }

    @Test @DisplayName("dispensa sem prova e recusada; dispensa com prova de inviabilidade fecha o caso")
    void exemptionRequiresProof() {
        var c = noticed();
        assertThatThrownBy(() -> MandatoryConnectionCycle.exempt(c, null, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sem prova");
        assertThatThrownBy(() -> MandatoryConnectionCycle.exempt(c, " ", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MandatoryConnectionCycle.exempt(c, "LAUDO-77", false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("classe a");
        var ex = MandatoryConnectionCycle.exempt(c, "LAUDO-INVIABILIDADE-77", true);
        assertThat(ex.status()).isEqualTo(MandatoryConnectionCycle.Status.EXEMPTED);
        assertThat(MandatoryConnectionCycle.availabilityChargeApplies(ex, LocalDate.of(2026, 6, 1))).isFalse();
    }

    @Test @DisplayName("compulsoria: antes do prazo e recusada; depois do prazo, com multa por item da ED-34")
    void compulsoryAfterDeadlineOnly() {
        var c = noticed();
        assertThatThrownBy(() -> MandatoryConnectionCycle.issueCompulsory(c,
                MandatoryConnectionCycle.CompulsoryKind.COMPULSORY_CONNECTION_ORDER,
                LocalDate.of(2027, 1, 10), 12, "MULTA_LIGACAO_COMPULSORIA"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("apos o prazo");
        var comp = MandatoryConnectionCycle.issueCompulsory(c,
                MandatoryConnectionCycle.CompulsoryKind.COMPULSORY_CONNECTION_ORDER,
                LocalDate.of(2027, 1, 11), 12, "MULTA_LIGACAO_COMPULSORIA");
        assertThat(comp.status()).isEqualTo(MandatoryConnectionCycle.Status.COMPULSORY_ORDER_ISSUED);
        assertThat(comp.finePriceItemCode()).isEqualTo("MULTA_LIGACAO_COMPULSORIA");
        assertThat(MandatoryConnectionCycle.ED13_CONNECT_EFFECT_CODE).isEqualTo("CONNECTION_CONNECT");
    }

    @Test @DisplayName("ligacao pela ED-13 desativa a solucao individual do imovel e encerra a cobranca de disponibilidade")
    void connectionDeactivatesAltSolution() {
        AltSolution s = AltSolution.register(UUID.randomUUID(), PROP, UNIT,
                Set.of(AltSolution.Component.SEPTIC_TANK), new BigDecimal("5000.00"),
                null, "ETE municipal", LocalDate.of(2025, 12, 1));
        UUID os = UUID.randomUUID();
        var result = MandatoryConnectionCycle.connect(noticed(), LocalDate.of(2026, 8, 20), os, 12, s);
        assertThat(result.availabilityCase().status()).isEqualTo(MandatoryConnectionCycle.Status.CONNECTED);
        assertThat(result.connectedLate()).isFalse();
        assertThat(result.altSolution().status()).isEqualTo(AltSolution.Status.DEACTIVATED_BY_CONNECTION);
        assertThat(result.altSolution().deactivationServiceOrderId()).isEqualTo(os);
        assertThat(MandatoryConnectionCycle.availabilityChargeApplies(
                result.availabilityCase(), LocalDate.of(2026, 9, 1))).isFalse();
    }

    @Test @DisplayName("ligacao apos o prazo: aceita e marcada como tardia; solucao de outro imovel nao e desativada")
    void lateConnectionFlagged() {
        var result = MandatoryConnectionCycle.connect(noticed(), LocalDate.of(2027, 2, 1),
                UUID.randomUUID(), 12, null);
        assertThat(result.connectedLate()).isTrue();
        AltSolution other = AltSolution.register(UUID.randomUUID(), UUID.randomUUID(), UNIT,
                Set.of(AltSolution.Component.SOAKAWAY), new BigDecimal("3000.00"),
                null, "ETE municipal", LocalDate.of(2025, 12, 1));
        assertThatThrownBy(() -> MandatoryConnectionCycle.connect(noticed(), LocalDate.of(2026, 8, 20),
                UUID.randomUUID(), 12, other)).isInstanceOf(IllegalArgumentException.class);
    }
}
