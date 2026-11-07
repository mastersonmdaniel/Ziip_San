package br.com.ziipsan.commercial.collection.retorno;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.1 - aprovacao dupla das classificacoes manuais. */
class DoubleApprovalRuleTest {

    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID APPROVER_A = UUID.randomUUID();
    private static final UUID APPROVER_B = UUID.randomUUID();

    @Test
    void duasAprovacoesDistintasAprovam() {
        var first = DoubleApprovalRule.approve(REQUESTER, null, null, APPROVER_A);
        assertThat(first.state()).isEqualTo(DoubleApprovalRule.ApprovalState.AWAITING_SECOND);
        assertThat(first.isApproved()).isFalse();
        var second = DoubleApprovalRule.approve(REQUESTER, first.firstApproverId(), null, APPROVER_B);
        assertThat(second.state()).isEqualTo(DoubleApprovalRule.ApprovalState.APPROVED);
        assertThat(second.isApproved()).isTrue();
    }

    @Test
    void solicitanteNaoAprovaASiMesmo() {
        assertThatThrownBy(() -> DoubleApprovalRule.approve(REQUESTER, null, null, REQUESTER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void solicitanteTambemNaoPodeSerOSegundoAprovador() {
        assertThatThrownBy(() -> DoubleApprovalRule.approve(REQUESTER, APPROVER_A, null, REQUESTER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mesmoAprovadorDuasVezesNaoContaComoDupla() {
        assertThatThrownBy(() -> DoubleApprovalRule.approve(REQUESTER, APPROVER_A, null, APPROVER_A))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
