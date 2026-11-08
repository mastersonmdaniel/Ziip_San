package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.core.cutoff.CutoffCalendar;
import br.com.ziipsan.core.cutoff.CutoffNoticeRule;
import br.com.ziipsan.core.cutoff.CutoffParameters;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Apoio dos testes da ED-29: fixtures declaradas, portas falsas em memoria (padrao dos vizinhos). */
public final class CutoffExtTestSupport {

    private CutoffExtTestSupport() {
    }

    public static final ZoneId TZ = ZoneId.of("America/Sao_Paulo");
    /** Sexta 20/11/2026: feriado nacional (Consciencia Negra) — registro sintetico de cal.holidays. */
    public static final LocalDate HOLIDAY = LocalDate.of(2026, 11, 20);

    public static CutoffCalendar.CalendarData scCalendar() {
        return new CutoffCalendar.CalendarData(Set.of(HOLIDAY), Set.of(), "SC");
    }

    /**
     * Parametros da P3.4 por parse do JSON cru (como nos testes da P3.4):
     * SLA de religacao declarado pelo fixture; sewerAllowed conforme o caso.
     */
    public static CutoffParameters.Values cutoffParams(boolean sewerAllowed) {
        var raw = new HashMap<String, String>();
        raw.put("rec.sla_hours", "{\"normalHours\":48,\"urgentHours\":4,\"undueHours\":4}");
        raw.put("cut.sewer_allowed", Boolean.toString(sewerAllowed));
        return CutoffParameters.parse(raw);
    }

    /** Aviso entregue, emitido qui 15/10/2026; primeira data de corte = seg 16/11/2026 (caso da P3.4). */
    public static CutoffNoticeRule.Notice deliveredNotice(UUID unitId) {
        var params = cutoffParams(false);
        LocalDate noticeDate = LocalDate.of(2026, 10, 15);
        return new CutoffNoticeRule.Notice(UUID.randomUUID(), unitId, noticeDate,
                CutoffNoticeRule.earliestCutDate(noticeDate, params, scCalendar()),
                "AVISO_CORTE", "v1", br.com.ziipsan.core.cutoff.CutoffEnums.DeliveryProofStatus.DELIVERED);
    }

    /** Parametros da ED-29: cota, prioridade, calendarios e modelos declarados pelo fixture. */
    public static CutoffExtParameters extParams() {
        return extParamsWithSchedule(scheduleJson(
                "\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"", "\"08:00\"", "\"18:00\""));
    }

    /** Variante com calendario de nivel em todos os dias/horas: isola o portao da P3.4 (Lei 14.015). */
    public static CutoffExtParameters extParamsAllWeek() {
        return extParamsWithSchedule(scheduleJson(
                "\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"",
                "\"00:00\"", "\"23:59\""));
    }

    private static String scheduleJson(String weekdays, String from, String to) {
        String level = "{\"weekdays\":[" + weekdays + "],\"from\":" + from + ",\"to\":" + to
                + ",\"minDaysSincePreviousLevel\":15}";
        String first = "{\"weekdays\":[" + weekdays + "],\"from\":" + from + ",\"to\":" + to
                + ",\"minDaysSincePreviousLevel\":0}";
        return "{\"VALVE\":" + first + ",\"BRANCH\":" + level + ",\"SUPPRESSION\":" + level + "}";
    }

    public static CutoffExtParameters extParamsWithSchedule(String levelScheduleJson) {
        var raw = new HashMap<String, String>();
        raw.put(CutoffExtParameters.DAILY_ORDER_LIMIT, "2");
        raw.put(CutoffExtParameters.PRIORITY_ORDER,
                "{\"order\":[\"DUE_DATE\",\"AMOUNT\",\"LOCATION\",\"TARIFF\",\"CATEGORY\",\"DEBTOR_PROFILE\"],"
                        + "\"ranks\":{\"TARIFF\":{\"SOCIAL\":1,\"RESIDENTIAL\":2},"
                        + "\"CATEGORY\":{\"RESIDENTIAL\":1,\"COMMERCIAL\":2},"
                        + "\"DEBTOR_PROFILE\":{\"RECURRENT\":1,\"OCCASIONAL\":2}}}");
        raw.put(CutoffExtParameters.LEVEL_SCHEDULE, levelScheduleJson);
        raw.put(CutoffExtParameters.COLLECTION_LETTER_TEMPLATE, "\"CARTA_COBRANCA\"");
        raw.put(CutoffExtParameters.CUT_UNIT_NOTICE_TEMPLATE, "\"NOTIFICACAO_POS_CORTE\"");
        raw.put(CutoffExtParameters.SEWER_RELEASE_TEMPLATE, "\"LIBERACAO_ESGOTO\"");
        raw.put(CutoffExtParameters.RULER_CUTOFF_STEP_CODE, "\"D60_CORTE\"");
        return CutoffExtParameters.fromValues(raw);
    }

    /** Motor de modelos falso: registra os codigos renderizados e devolve versao fixa. */
    public static final class FakeTemplates implements DunningPorts.TemplatePort {
        public final List<String> renderedCodes = new ArrayList<>();

        @Override
        public RenderedDocument render(UUID tenantId, String templateCode, Map<String, Object> variables) {
            renderedCodes.add(templateCode);
            return new RenderedDocument(templateCode, "v7", "doc-" + templateCode + "-" + renderedCodes.size());
        }
    }
}
