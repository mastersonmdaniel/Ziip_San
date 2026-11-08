package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.cutoff.CutoffEnums;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O teste do aceite: geracao de OS de corte FORA da janela da
 * Lei 14.015/2020 e RECUSADA. Calendario real de 2026: 20/11 (sexta) e
 * feriado; 19/11 (quinta) e a vespera; 16/11 (segunda) e a primeira data
 * permitida do aviso de 15/10 (caso da P3.4).
 */
class CutoffGenerationGateTest {

    private static final UUID UNIT = UUID.randomUUID();

    private static CutoffGenerationGate.GenerationInput input(ZonedDateTime when, CutoffLevel level) {
        return new CutoffGenerationGate.GenerationInput(level, CutoffEnums.Reason.DEFAULT, false, when,
                CutoffExtTestSupport.scCalendar(), false, null, null, Set.of(),
                CutoffExtTestSupport.deliveredNotice(UNIT), CutoffExtTestSupport.cutoffParams(false));
    }

    private static ZonedDateTime at(int day, int hour) {
        return ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, CutoffExtTestSupport.TZ);
    }

    @Test
    @DisplayName("segunda 16/11 as 10h, dentro da janela legal e do calendario do nivel: geracao permitida")
    void allowedMonday() {
        assertThatCode(() -> CutoffGenerationGate.assertGenerationAllowed(
                input(at(16, 10), CutoffLevel.VALVE), CutoffExtTestSupport.extParams()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ACEITE: sexta-feira — geracao recusada pela vedacao da Lei 14.015/2020 (codigo da P3.4)")
    void fridayRefusedByFederalBan() {
        // Calendario do nivel cobrindo a sexta: quem recusa e o portao da P3.4.
        assertThatThrownBy(() -> CutoffGenerationGate.assertGenerationAllowed(
                input(at(13, 10), CutoffLevel.VALVE), CutoffExtTestSupport.extParamsAllWeek()))
                .isInstanceOf(ApiException.Conflict.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("cutoff-temporal-ban");
    }

    @Test
    @DisplayName("ACEITE: sabado e domingo — geracao recusada pela Lei 14.015/2020")
    void weekendRefusedByFederalBan() {
        for (int day : new int[]{14, 15}) {
            assertThatThrownBy(() -> CutoffGenerationGate.assertGenerationAllowed(
                    input(at(day, 10), CutoffLevel.VALVE), CutoffExtTestSupport.extParamsAllWeek()))
                    .isInstanceOf(ApiException.Conflict.class)
                    .extracting(e -> ((ApiException) e).code()).isEqualTo("cutoff-temporal-ban");
        }
    }

    @Test
    @DisplayName("ACEITE: vespera de feriado (qui 19/11) e o feriado (sex 20/11) — recusados")
    void holidayEveAndHolidayRefused() {
        for (int day : new int[]{19, 20}) {
            assertThatThrownBy(() -> CutoffGenerationGate.assertGenerationAllowed(
                    input(at(day, 10), CutoffLevel.VALVE), CutoffExtTestSupport.extParamsAllWeek()))
                    .isInstanceOf(ApiException.Conflict.class)
                    .extracting(e -> ((ApiException) e).code()).isEqualTo("cutoff-temporal-ban");
        }
    }

    @Test
    @DisplayName("ACEITE (SC): segunda as 7h esta dentro da janela [sex 8h, seg 8h) da Lei SC — recusado; as 8h, permitido")
    void scMondayBeforeEightRefused() {
        assertThatThrownBy(() -> CutoffGenerationGate.assertGenerationAllowed(
                input(at(16, 7), CutoffLevel.VALVE), CutoffExtTestSupport.extParamsAllWeek()))
                .isInstanceOf(ApiException.Conflict.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("cutoff-temporal-ban");
        assertThatCode(() -> CutoffGenerationGate.assertGenerationAllowed(
                input(at(16, 8), CutoffLevel.VALVE), CutoffExtTestSupport.extParamsAllWeek()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("fora dos dias/horarios do nivel (sabado no calendario seg–sex): recusa do calendario do nivel")
    void outsideLevelWindowRefused() {
        assertThatThrownBy(() -> CutoffGenerationGate.assertGenerationAllowed(
                input(at(14, 10), CutoffLevel.VALVE), CutoffExtTestSupport.extParams()))
                .isInstanceOf(ApiException.Conflict.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo("cutoff-generation-outside-level-window");
    }

    @Test
    @DisplayName("bloqueios da P3.4 valem na geracao: classe protegida recusa, aviso sem prova recusa")
    void p34BlocksApply() {
        var protectedInput = new CutoffGenerationGate.GenerationInput(CutoffLevel.VALVE,
                CutoffEnums.Reason.DEFAULT, false, at(16, 10), CutoffExtTestSupport.scCalendar(),
                false, null, null, Set.of(CutoffEnums.ProtectedClass.ESSENTIAL_USER),
                CutoffExtTestSupport.deliveredNotice(UNIT), CutoffExtTestSupport.cutoffParams(false));
        assertThatThrownBy(() -> CutoffGenerationGate.assertGenerationAllowed(
                protectedInput, CutoffExtTestSupport.extParams()))
                .isInstanceOf(ApiException.Conflict.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("cutoff-protected-class");

        var notice = CutoffExtTestSupport.deliveredNotice(UNIT);
        var noProof = new br.com.ziipsan.core.cutoff.CutoffNoticeRule.Notice(notice.id(), UNIT,
                notice.noticeDate(), notice.earliestCutDate(), notice.templateCode(), notice.templateVersion(),
                CutoffEnums.DeliveryProofStatus.NOT_DELIVERED);
        var noProofInput = new CutoffGenerationGate.GenerationInput(CutoffLevel.VALVE,
                CutoffEnums.Reason.DEFAULT, false, at(16, 10), CutoffExtTestSupport.scCalendar(),
                false, null, null, Set.of(), noProof, CutoffExtTestSupport.cutoffParams(false));
        assertThatThrownBy(() -> CutoffGenerationGate.assertGenerationAllowed(
                noProofInput, CutoffExtTestSupport.extParams()))
                .isInstanceOf(ApiException.Conflict.class)
                .extracting(e -> ((ApiException) e).code())
                .asString().startsWith("cutoff-notice-");
    }

    @Test
    @DisplayName("nivel sem calendario declarado: erro explicito, nunca geracao presumida")
    void levelWithoutScheduleIsExplicitError() {
        var params = CutoffExtTestSupport.extParamsWithSchedule("{\"VALVE\":{\"weekdays\":[\"MONDAY\"],"
                + "\"from\":\"08:00\",\"to\":\"18:00\",\"minDaysSincePreviousLevel\":0}}");
        assertThatThrownBy(() -> CutoffGenerationGate.assertGenerationAllowed(
                input(at(16, 10), CutoffLevel.BRANCH), params))
                .isInstanceOf(br.com.ziipsan.core.parameters.MissingParameterException.class);
    }
}
