package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.commercial.collection.surcharge.FineRule;
import br.com.ziipsan.commercial.collection.surcharge.InterestRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P0.20: a politica do banco converte para as regras de calculo, com o aceite da multa. */
class CollectionPolicyTest {

    private static CollectionPolicy base() {
        CollectionPolicy policy = new CollectionPolicy();
        policy.setTenantId(UUID.randomUUID());
        policy.setGraceBusinessDays(0);
        policy.setDueDateRollsToBusinessDay(false);
        policy.setClassifyTolerance(BigDecimal.ZERO);
        policy.setAdjustTolerance(BigDecimal.ZERO);
        policy.setCorrectionMode(CollectionPolicy.CorrectionMode.NONE);
        return policy;
    }

    @Test
    @DisplayName("DAILY_RATE vira InterestRule.DailyRate com a taxa diaria (ex.: SC 0,033%)")
    void dailyRateConverts() {
        CollectionPolicy policy = base();
        policy.setFinePercent(new BigDecimal("2.00"));
        policy.setInterestMode(CollectionPolicy.InterestMode.DAILY_RATE);
        policy.setInterestDailyPercent(new BigDecimal("0.033"));
        var surcharge = policy.toSurchargePolicy(code -> {
            throw new IllegalStateException("sem indice neste teste");
        });
        assertThat(surcharge.interest())
                .isInstanceOfSatisfying(InterestRule.DailyRate.class,
                        rate -> assertThat(rate.dailyPercent()).isEqualByComparingTo("0.033"));
    }

    @Test
    @DisplayName("teto de multa acima de 2% sem aceite reforçado falha na conversão; com aceite, passa")
    void fineAboveLegalCapNeedsApproval() {
        CollectionPolicy policy = base();
        policy.setFinePercent(new BigDecimal("5.00"));
        policy.setFineCapPercent(new BigDecimal("10.00"));
        policy.setFineCapJustification("Contrato cl. 12");
        policy.setInterestMode(CollectionPolicy.InterestMode.NONE);
        assertThatThrownBy(() -> policy.toSurchargePolicy(code -> {
            throw new IllegalStateException("sem indice neste teste");
        })).isInstanceOf(IllegalArgumentException.class);

        policy.setFineCapNormCitation("Contrato cl. 12");
        policy.setFineCapProposedBy(UUID.randomUUID());
        policy.setFineCapApprovedBy(UUID.randomUUID());
        policy.setFineCapAcceptanceRef("aceite-0001");
        policy.setFineCapRiskAlertAck(true);
        var surcharge = policy.toSurchargePolicy(code -> {
            throw new IllegalStateException("sem indice neste teste");
        });
        assertThat(surcharge.fine()).isInstanceOfSatisfying(FineRule.class,
                fine -> assertThat(fine.ratePercent()).isEqualByComparingTo("5.00"));
    }
}
