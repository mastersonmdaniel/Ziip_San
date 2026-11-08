package br.com.ziipsan.reporting.exportprofile;

import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.IndicatorClosing;
import br.com.ziipsan.reporting.indicator.IndicatorDefinition;
import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;
import br.com.ziipsan.reporting.indicator.Semaphore;
import br.com.ziipsan.reporting.sheet.IndicatorSheet;
import br.com.ziipsan.reporting.sheet.SheetFramework;
import br.com.ziipsan.reporting.sheet.SheetLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Massa sintetica sobre as classes REAIS da P3.41 e da ED-77. */
final class ExportProfileFixtures {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000082");
    static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static IndicatorSheet sheet(String code) {
        IndicatorDefinition def = new IndicatorDefinition(TENANT, code, "Indicador " + code,
                "Definicao sintetica de " + code, "%", IndicatorPeriodicity.ANNUAL,
                ConfidenceGrade.MEDIUM, "contexto sintetico", "NR9_ANA", Set.of(),
                List.of(IndicatorVariable.fromReportView("numerador", "rpt_sintetica")));
        return new IndicatorSheet(def, SheetFramework.NR9_ANA, SheetLevel.NA, List.of());
    }

    static IndicatorClosing closing(String code, String start, String value, IndicatorClosing.Status status) {
        LocalDate s = LocalDate.parse(start);
        boolean reopened = status == IndicatorClosing.Status.REOPENED;
        return new IndicatorClosing(UUID.randomUUID(), TENANT, code, s, s.plusYears(1).minusDays(1),
                null, new BigDecimal(value), 1, "{\"numerador\":1}", Semaphore.GREEN, status,
                ACTOR, Instant.parse("2026-01-10T00:00:00Z"),
                reopened ? ACTOR : null, reopened ? Instant.parse("2026-02-01T00:00:00Z") : null,
                reopened ? "motivo sintetico" : null);
    }

    static IndicatorSeries series(String code, String... years) {
        java.util.List<SeriesPoint> pts = new java.util.ArrayList<>();
        for (String y : years) {
            pts.add(SeriesPoint.fromClosing(closing(code, y + "-01-01", "50.0", IndicatorClosing.Status.CLOSED)));
        }
        return new IndicatorSeries(sheet(code), pts);
    }
}
