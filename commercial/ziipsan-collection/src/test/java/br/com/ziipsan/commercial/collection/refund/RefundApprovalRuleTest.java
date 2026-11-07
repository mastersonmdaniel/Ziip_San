package br.com.ziipsan.commercial.collection.refund;

import br.com.ziipsan.commercial.collection.retorno.DoubleApprovalRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.29 - aprovacao dupla ACIMA da alcada. Alcada de teste: 500,00
 * (valor de teste, nao padrao de produto: a alcada real vem do tenant).
 */
class RefundApprovalRuleTest {

    private static final BigDecimal THRESHOLD = new BigDecimal("500.00");
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID APPROVER_A = UUID.randomUUID();
    private static final UUID APPROVER_B = UUID.randomUUID();

    @Test
    void naAlcadaENaoAcimaAprovacaoSimples() {
        // 500,00 == alcada: NAO e "acima" -> simples.
        assertThat(RefundApprovalRule.requiresDoubleApproval(new BigDecimal("500.00"), THRESHOLD)).isFalse();
        assertThat(RefundApprovalRule.requiresDoubleApproval(new BigDecimal("499.99"), THRESHOLD)).isFalse();
        var p = RefundApprovalRule.approve(false, REQUESTER, null, null, APPROVER_A);
        assertThat(p.isApproved()).isTrue();
    }

    @Test
    void acimaDaAlcadaExigeDoisAprovadoresDistintos() {
        // 500,01 > 500,00 -> dupla.
        assertThat(RefundApprovalRule.requiresDoubleApproval(new BigDecimal("500.01"), THRESHOLD)).isTrue();
        var first = RefundApprovalRule.approve(true, REQUESTER, null, null, APPROVER_A);
        assertThat(first.state()).isEqualTo(DoubleApprovalRule.ApprovalState.AWAITING_SECOND);
        var done = RefundApprovalRule.approve(true, REQUESTER, first.firstApproverId(), null, APPROVER_B);
        assertThat(done.isApproved()).isTrue();
        // Mesmo aprovador duas vezes nao aprova.
        assertThatThrownBy(() -> RefundApprovalRule.approve(true, REQUESTER, APPROVER_A, null, APPROVER_A))
                .isInstanceOf(IllegalArgumentException.class);
        // Solicitante nunca aprova a propria devolucao.
        assertThatThrownBy(() -> RefundApprovalRule.approve(true, REQUESTER, null, null, REQUESTER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alcadaAusenteEErroExplicitoNuncaValorPresumido() {
        assertThatThrownBy(() -> RefundApprovalRule.requiresDoubleApproval(new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alcada");
    }
}
