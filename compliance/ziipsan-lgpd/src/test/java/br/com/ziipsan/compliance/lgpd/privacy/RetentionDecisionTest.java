package br.com.ziipsan.compliance.lgpd.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Eliminação × retenção (LGPD, art. 16). O caso central é o do aceite: dado FISCAL em
 * guarda fica bloqueado, com justificativa na resposta, e só é disposto ao fim da guarda.
 * As datas e o motivo são dados de teste genéricos (a guarda real é a que o tenant
 * declarar na lgpd_retention_schedule — nenhum prazo é presumido pelo código).
 */
class RetentionDecisionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

    private static RetentionDecision.RetentionEntry fiscalEmGuarda() {
        return new RetentionDecision.RetentionEntry("fiscal_document", "documentos fiscais",
                "DELETE", "guarda legal ou regulatória declarada pelo tenant",
                LocalDate.of(2031, 1, 31));
    }

    @Test
    @DisplayName("dado fiscal em guarda: bloqueado até o fim da guarda, com justificativa")
    void fiscalDataUnderRetentionIsBlockedWithJustification() {
        RetentionDecision.Decision decision =
                RetentionDecision.decide(List.of(fiscalEmGuarda()), TODAY);
        assertThat(decision.outcome())
                .isEqualTo(RetentionDecision.Outcome.BLOCKED_UNTIL_GUARD_END);
        assertThat(decision.disposalDate()).isEqualTo(LocalDate.of(2031, 1, 31));
        assertThat(decision.justification())
                .contains("art. 16")
                .contains("guarda legal ou regulatória declarada pelo tenant")
                .contains("2031-01-31");
    }

    @Test
    @DisplayName("guarda encerrada: disposição imediata pela ação declarada")
    void expiredRetentionDisposesNow() {
        RetentionDecision.RetentionEntry expired = new RetentionDecision.RetentionEntry(
                "fiscal_document", "documentos fiscais", "DELETE",
                "guarda legal ou regulatória declarada pelo tenant",
                LocalDate.of(2026, 1, 31));
        RetentionDecision.Decision decision = RetentionDecision.decide(List.of(expired), TODAY);
        assertThat(decision.outcome()).isEqualTo(RetentionDecision.Outcome.ELIMINATE_NOW);
        assertThat(decision.disposalDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("ação declarada de anonimização produz anonimização ao fim da guarda")
    void anonymizeActionProducesAnonymization() {
        RetentionDecision.RetentionEntry entry = new RetentionDecision.RetentionEntry(
                "customer", "cadastro", "ANONYMIZE", null, LocalDate.of(2026, 1, 31));
        RetentionDecision.Decision decision = RetentionDecision.decide(List.of(entry), TODAY);
        assertThat(decision.outcome()).isEqualTo(RetentionDecision.Outcome.ANONYMIZE_NOW);
    }

    @Test
    @DisplayName("sem linha de retenção aplicável: eliminação imediata")
    void noRetentionEliminatesNow() {
        RetentionDecision.Decision decision = RetentionDecision.decide(List.of(), TODAY);
        assertThat(decision.outcome()).isEqualTo(RetentionDecision.Outcome.ELIMINATE_NOW);
        assertThat(decision.disposalDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("bloqueio sem justificativa na retenção é impossível")
    void blockWithoutJustificationRefused() {
        RetentionDecision.RetentionEntry semMotivo = new RetentionDecision.RetentionEntry(
                "fiscal_document", "documentos fiscais", "DELETE", " ",
                LocalDate.of(2031, 1, 31));
        assertThatThrownBy(() -> RetentionDecision.decide(List.of(semMotivo), TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("justificativa");
    }
}
