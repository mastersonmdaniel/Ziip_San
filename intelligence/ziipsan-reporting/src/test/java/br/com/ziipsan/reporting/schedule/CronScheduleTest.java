package br.com.ziipsan.reporting.schedule;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronScheduleTest {

    @Test
    void diarioAsSeisVaiParaODiaSeguinteDepoisDasSeis() {
        CronSchedule cron = CronSchedule.parse("0 6 * * *");
        ZonedDateTime after = ZonedDateTime.parse("2026-10-05T07:00:00Z");
        assertThat(cron.nextAfter(after)).isEqualTo(ZonedDateTime.parse("2026-10-06T06:00:00Z"));
        ZonedDateTime before = ZonedDateTime.parse("2026-10-05T05:00:00Z");
        assertThat(cron.nextAfter(before)).isEqualTo(ZonedDateTime.parse("2026-10-05T06:00:00Z"));
    }

    @Test
    void semanalSegundaAsOito() {
        CronSchedule cron = CronSchedule.parse("0 8 * * 1");
        ZonedDateTime sunday = ZonedDateTime.parse("2026-10-04T09:00:00Z");
        ZonedDateTime next = cron.nextAfter(sunday);
        assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(next).isEqualTo(ZonedDateTime.parse("2026-10-05T08:00:00Z"));
    }

    @Test
    void mensalDiaPrimeiro() {
        CronSchedule cron = CronSchedule.parse("30 5 1 * *");
        assertThat(cron.nextAfter(ZonedDateTime.parse("2026-10-15T00:00:00Z")))
                .isEqualTo(ZonedDateTime.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    void recusaPassoECamposInvalidos() {
        assertThatThrownBy(() -> CronSchedule.parse("*/5 * * * *"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CronSchedule.parse("0 25 * * *"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CronSchedule.parse("0 6 * *"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
