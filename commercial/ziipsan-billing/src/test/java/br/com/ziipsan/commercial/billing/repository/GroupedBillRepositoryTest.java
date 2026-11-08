package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.GroupedBillMemberEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-22, correcao pos-revisao (D5): contrato dos repositorios do agrupador -
 * busca por codigo COM tenant (padrao P2.5) e entidade/repositorio dos
 * membros, com a unicidade por conta exposta.
 */
class GroupedBillRepositoryTest {

    @Test
    @DisplayName("D5: GroupedBillRepository busca por (tenant, codigo); a busca sem tenant nao existe")
    void groupedBillRepositoryIsTenantScoped() throws Exception {
        Method scoped = GroupedBillRepository.class.getMethod(
                "findByTenantIdAndCode", UUID.class, String.class);
        assertThat(scoped.getReturnType()).isEqualTo(Optional.class);
        assertThatThrownBy(() -> GroupedBillRepository.class.getMethod("findByCode", String.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("D5: membros tem entidade JPA utilizavel e repositorio por agrupadora e por conta")
    void groupedBillMemberEntityAndRepository() throws Exception {
        UUID grouped = UUID.randomUUID();
        UUID bill = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        GroupedBillMemberEntity member = new GroupedBillMemberEntity();
        member.setGroupedBillId(grouped);
        member.setBillId(bill);
        member.setUnitId(unit);
        member.setTotalValue(new BigDecimal("150.00"));
        assertThat(member.getGroupedBillId()).isEqualTo(grouped);
        assertThat(member.getBillId()).isEqualTo(bill);
        assertThat(member.getUnitId()).isEqualTo(unit);
        assertThat(member.getTotalValue()).isEqualByComparingTo("150.00");
        Method byGrouped = GroupedBillMemberRepository.class.getMethod("findByGroupedBillId", UUID.class);
        assertThat(byGrouped.getReturnType()).isEqualTo(List.class);
        Method byBill = GroupedBillMemberRepository.class.getMethod(
                "findByTenantIdAndBillId", UUID.class, UUID.class);
        assertThat(byBill.getReturnType()).isEqualTo(Optional.class);
    }
}
