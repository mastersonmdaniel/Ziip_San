package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.commercial.billing.bill.BillStatus;
import br.com.ziipsan.commercial.billing.domain.BillEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P2.20: nova versao sem apagar a anterior + trilha por campo. */
class BillVersionFactoryTest {

    static BillEntity currentBill() {
        BillEntity bill = new BillEntity();
        bill.setId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        bill.setTenantId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
        bill.setUnitId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        bill.setReferenceMonth(LocalDate.of(2026, 3, 1));
        bill.setStatus("NORMAL");
        bill.setWaterConsumption(10);
        bill.setSewerVolume(10);
        bill.setWaterValue(new BigDecimal("60.00"));
        bill.setSewerValue(new BigDecimal("40.00"));
        bill.setFixedPartValue(new BigDecimal("0.00"));
        bill.setDebitsValue(new BigDecimal("0.00"));
        bill.setCreditsValue(new BigDecimal("0.00"));
        bill.setTaxesValue(new BigDecimal("0.00"));
        bill.setTotalValue(new BigDecimal("100.00"));
        bill.setDueDate(LocalDate.of(2026, 3, 10));
        bill.setRectificationCount(0);
        bill.setDueChangeCount(0);
        bill.setTaxTreatment("WITHHOLDING_ONLY");
        bill.setVersionNo(1);
        return bill;
    }

    @Test
    @DisplayName("versao seguinte: numero 2, origem na anterior, retificacoes +1, anterior sai por CANCELLED_BY_RECTIFICATION")
    void nextVersion() {
        BillEntity current = currentBill();
        UUID order = UUID.fromString("00000000-0000-0000-0000-000000000030");
        var versioned = BillVersionFactory.nextVersion(current, BillStatus.RECTIFIED, order, null);
        BillEntity next = versioned.newVersion();
        assertThat(next.getId()).isNotEqualTo(current.getId());
        assertThat(next.getVersionNo()).isEqualTo(2);
        assertThat(next.getOriginBillId()).isEqualTo(current.getId());
        assertThat(next.getRectificationCount()).isEqualTo(1);
        assertThat(next.getServiceOrderId()).isEqualTo(order);
        assertThat(next.getProtocolId()).isNull();
        assertThat(next.getTotalValue()).isEqualByComparingTo("100.00");
        assertThat(versioned.supersededStatus()).isEqualTo(BillStatus.CANCELLED_BY_RECTIFICATION);
        // A anterior nao foi tocada pela fabrica (quem muda o status dela e o servico, ao persistir).
        assertThat(current.getStatus()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("versao morta (CANCELLED) nao gera nova versao")
    void deadBillRefused() {
        BillEntity current = currentBill();
        current.setStatus("CANCELLED");
        assertThatThrownBy(() -> BillVersionFactory.nextVersion(current, BillStatus.RECTIFIED, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("trilha por campo: so campos mudados, com anterior e posterior fotografados")
    void fieldTrail() {
        BillEntity current = currentBill();
        BillEntity next = currentBill();
        next.setId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        next.setStatus("RECTIFIED");
        next.setTotalValue(new BigDecimal("80.00"));
        next.setWaterValue(new BigDecimal("48.00"));
        next.setSewerValue(new BigDecimal("32.00"));
        var changes = FieldChangeTrail.diff(current, next);
        assertThat(changes).extracting(FieldChangeTrail.FieldChange::field)
                .containsExactlyInAnyOrder("status", "total_value", "water_value", "sewer_value");
        assertThat(changes).filteredOn(c -> c.field().equals("total_value"))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.previousValue()).isEqualTo("100.00");
                    assertThat(c.newValue()).isEqualTo("80.00");
                });
    }
}
