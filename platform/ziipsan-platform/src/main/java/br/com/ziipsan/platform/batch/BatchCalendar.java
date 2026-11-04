package br.com.ziipsan.platform.batch;

import br.com.ziipsan.core.parameters.TenantTime;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * P2.1: agendamento e data de referência no fuso e no calendário
 * operacional do tenant (P0.19, PLT-14).
 *
 * <p>O conjunto de dias não úteis já vem filtrado pelo chamador: são os
 * feriados do calendário do tenant (tabela {@code holidays}, V1_5) com o
 * escopo do município/UF aplicado e a decisão do tenant sobre pontos
 * facultativos já tomada — essa filtragem é regra do
 * {@code BusinessCalendar} (P0.19), não da fundação de lote, e a
 * plataforma não depende do módulo de cobrança para usá-la. Aqui só
 * vivem as regras de calendário que o lote precisa: fim de semana não
 * é dia útil, e o agendamento cai no próximo dia útil.
 *
 * <p>Toda data de negócio sai de {@link TenantTime}: instantes gravados
 * em UTC, datas como data local do tenant. Um mesmo instante tem data
 * local diferente em Manaus, Rio Branco e Noronha — e é essa data que
 * vai para {@code job_runs.reference_date}.
 */
public final class BatchCalendar {

    private final TenantTime tenantTime;
    private final Set<LocalDate> nonBusinessDays;

    public BatchCalendar(TenantTime tenantTime, Set<LocalDate> nonBusinessDays) {
        this.tenantTime = Objects.requireNonNull(tenantTime, "O tempo do tenant é obrigatório");
        this.nonBusinessDays = nonBusinessDays == null ? Set.of() : Set.copyOf(nonBusinessDays);
    }

    public TenantTime tenantTime() {
        return tenantTime;
    }

    /** Sábado, domingo e feriado do calendário do tenant não são dia útil. */
    public boolean isBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "A data é obrigatória");
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
                && !nonBusinessDays.contains(date);
    }

    /** Menor dia útil igual ou posterior à data. */
    public LocalDate nextBusinessDayOnOrAfter(LocalDate date) {
        LocalDate d = Objects.requireNonNull(date, "A data é obrigatória");
        while (!isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** Data de negócio do tenant para um instante gravado em UTC. */
    public LocalDate referenceDateAt(Instant instant) {
        return tenantTime.localDateOf(instant);
    }

    /** Data de negócio "hoje" do tenant, pelo relógio injetado. */
    public LocalDate todayReferenceDate() {
        return tenantTime.nowLocalDate();
    }

    /**
     * Próximo agendamento: o horário local no primeiro dia útil igual ou
     * posterior a {@code fromDate}, convertido para instante UTC.
     */
    public Instant scheduleAt(LocalTime localTime, LocalDate fromDate) {
        Objects.requireNonNull(localTime, "O horário é obrigatório");
        LocalDate day = nextBusinessDayOnOrAfter(fromDate);
        return ZonedDateTime.of(day, localTime, tenantTime.zone()).toInstant();
    }
}
