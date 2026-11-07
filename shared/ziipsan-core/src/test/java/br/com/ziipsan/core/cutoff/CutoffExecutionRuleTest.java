package br.com.ziipsan.core.cutoff;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Portao de execucao do corte da P3.4: vedacoes para todos os motivos,
 * emergencia com justificativa e foto, ordem judicial (ressalva da lei
 * estadual) e classes protegidas.
 */
class CutoffExecutionRuleTest {

    private static final ZoneId TZ = ZoneId.of("America/Sao_Paulo");
    private static final CutoffCalendar.CalendarData SC = new CutoffCalendar.CalendarData(
            Set.of(LocalDate.of(2026, 11, 20)), Set.of(), "SC");
    private static final ZonedDateTime MONDAY_10H = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, TZ);
    private static final ZonedDateTime FRIDAY_HOLIDAY_10H = ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, TZ);

    private static CutoffNoticeRule.Notice deliveredNotice() {
        return new CutoffNoticeRule.Notice(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 16),
                "CUTOFF_NOTICE", "3", CutoffEnums.DeliveryProofStatus.DELIVERED);
    }

    private static CutoffExecutionRule.GateInput gate(CutoffEnums.Reason reason, ZonedDateTime when,
            Map<String, String> rawParams) {
        return new CutoffExecutionRule.GateInput(reason, false, when, SC, false, null, null,
                Set.of(), reason == CutoffEnums.Reason.DEFAULT ? deliveredNotice() : null,
                CutoffParameters.parse(rawParams));
    }

    @Test
    @DisplayName("corte por inadimplencia valido: segunda 16/11 as 10h com aviso provado nao levanta")
    void validDebtCutoff() {
        assertThatCode(() -> CutoffExecutionRule.assertAllowed(
                gate(CutoffEnums.Reason.DEFAULT, MONDAY_10H, Map.of()))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("vedacao temporal vale para inadimplencia e para fraude (padrao: todos os motivos)")
    void temporalBanForAllReasonsByDefault() {
        assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(
                gate(CutoffEnums.Reason.DEFAULT, FRIDAY_HOLIDAY_10H, Map.of())))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(
                gate(CutoffEnums.Reason.FRAUD, FRIDAY_HOLIDAY_10H, Map.of())))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(
                gate(CutoffEnums.Reason.REQUEST, FRIDAY_HOLIDAY_10H, Map.of())))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("motivo excluido das vedacoes por aceite formal com norma citada (cut.reasons) executa na sexta")
    void reasonExcludedWithCitation() {
        var raw = Map.of("cut.reasons",
                "{\"FRAUD\":{\"followsTemporalBans\":false,\"normCitation\":\"RN da ERI, art. X\"}}");
        assertThatCode(() -> CutoffExecutionRule.assertAllowed(
                gate(CutoffEnums.Reason.FRAUD, FRIDAY_HOLIDAY_10H, raw))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ordem judicial ressalva a vedacao estadual/federal, mas NAO a classe protegida")
    void judicialOrderException() {
        var judicial = new CutoffExecutionRule.GateInput(CutoffEnums.Reason.DEFAULT, false,
                FRIDAY_HOLIDAY_10H, SC, true, null, null, Set.of(), deliveredNotice(),
                CutoffParameters.parse(Map.of()));
        assertThatCode(() -> CutoffExecutionRule.assertAllowed(judicial)).doesNotThrowAnyException();
        var judicialProtected = new CutoffExecutionRule.GateInput(CutoffEnums.Reason.DEFAULT, false,
                FRIDAY_HOLIDAY_10H, SC, true, null, null,
                Set.of(CutoffEnums.ProtectedClass.ESSENTIAL_USER), deliveredNotice(),
                CutoffParameters.parse(Map.of()));
        assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(judicialProtected))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("emergencia tecnica: sem justificativa ou sem foto, recusada; com os dois, dispensa a vedacao")
    void emergencyRequiresJustificationAndPhoto() {
        var noJustification = new CutoffExecutionRule.GateInput(CutoffEnums.Reason.TECHNICAL_EMERGENCY,
                false, FRIDAY_HOLIDAY_10H, SC, false, null, UUID.randomUUID(), Set.of(), null,
                CutoffParameters.parse(Map.of()));
        assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(noJustification))
                .isInstanceOf(ApiException.Conflict.class);
        var noPhoto = new CutoffExecutionRule.GateInput(CutoffEnums.Reason.TECHNICAL_EMERGENCY,
                false, FRIDAY_HOLIDAY_10H, SC, false, "vazamento na rede", null, Set.of(), null,
                CutoffParameters.parse(Map.of()));
        assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(noPhoto))
                .isInstanceOf(ApiException.Conflict.class);
        var complete = new CutoffExecutionRule.GateInput(CutoffEnums.Reason.TECHNICAL_EMERGENCY,
                false, FRIDAY_HOLIDAY_10H, SC, false, "vazamento na rede", UUID.randomUUID(),
                Set.of(), null, CutoffParameters.parse(Map.of()));
        assertThatCode(() -> CutoffExecutionRule.assertAllowed(complete)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("classe protegida veda para todos os motivos, inclusive fraude e pedido")
    void protectedClassBlocksAllReasons() {
        for (var reason : new CutoffEnums.Reason[]{CutoffEnums.Reason.FRAUD, CutoffEnums.Reason.REQUEST}) {
            var input = new CutoffExecutionRule.GateInput(reason, false, MONDAY_10H, SC, false,
                    null, null, Set.of(CutoffEnums.ProtectedClass.HEALTH_DEPENDENT), null,
                    CutoffParameters.parse(Map.of()));
            assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(input))
                    .isInstanceOf(ApiException.Conflict.class);
        }
    }

    @Test
    @DisplayName("esgoto: vedado sem cut.sewer_allowed; liberado com o parametro aceito")
    void sewerGate() {
        var sewerMonday = new CutoffExecutionRule.GateInput(CutoffEnums.Reason.REQUEST, true,
                MONDAY_10H, SC, false, null, null, Set.of(), null, CutoffParameters.parse(Map.of()));
        assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(sewerMonday))
                .isInstanceOf(ApiException.Conflict.class);
        var allowed = new CutoffExecutionRule.GateInput(CutoffEnums.Reason.REQUEST, true,
                MONDAY_10H, SC, false, null, null, Set.of(), null,
                CutoffParameters.parse(Map.of("cut.sewer_allowed", "true")));
        assertThatCode(() -> CutoffExecutionRule.assertAllowed(allowed)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inadimplencia sem aviso: recusada na validacao; aviso sem prova: conflito de prova")
    void debtReasonNeedsProvenNotice() {
        var noNotice = new CutoffExecutionRule.GateInput(CutoffEnums.Reason.DEFAULT, false,
                MONDAY_10H, SC, false, null, null, Set.of(), null, CutoffParameters.parse(Map.of()));
        assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(noNotice))
                .isInstanceOf(ApiException.Validation.class);
        var unproven = new CutoffNoticeRule.Notice(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 16),
                "CUTOFF_NOTICE", "3", CutoffEnums.DeliveryProofStatus.NOT_DELIVERED);
        var input = new CutoffExecutionRule.GateInput(CutoffEnums.Reason.DEFAULT, false,
                MONDAY_10H, SC, false, null, null, Set.of(), unproven, CutoffParameters.parse(Map.of()));
        assertThatThrownBy(() -> CutoffExecutionRule.assertAllowed(input))
                .isInstanceOf(ApiException.Conflict.class);
    }
}
