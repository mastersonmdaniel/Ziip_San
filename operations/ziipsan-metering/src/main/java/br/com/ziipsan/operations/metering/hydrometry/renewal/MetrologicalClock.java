package br.com.ziipsan.operations.metering.hydrometry.renewal;

import br.com.ziipsan.operations.metering.hydrometry.MeterParameterRules;
import br.com.ziipsan.operations.metering.hydrometry.VerificationSchedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Relógio de verificação metrológica da II-5: classifica cada hidrómetro
 * como vencido, a vencer no horizonte ou em dia, REUTILIZANDO o cálculo
 * da ED-14 — a próxima verificação é a de
 * {@link VerificationSchedule#nextVerificationOn} (última verificação +
 * intervalo do tenant) e o intervalo é validado por
 * {@link MeterParameterRules#validateVerificationIntervalYears}
 * (1..7, "só reduz"). Nada é recalculado nem redigitado aqui.
 *
 * <p><b>Prazo e fonte:</b> o limite de 7 anos está citado no PDCA-01
 * (benchmark B-4: "relógio Inmetro (verificação ≤7 anos, Portaria
 * 155/2022)") e é o padrão/teto já registado na base da ED-14
 * ({@code meter.verification_interval_years}, padrão 7, fonte declarada
 * no catálogo: "Portaria Inmetro 155/2022; aceite da ED-14"). Nesta
 * produção o texto primário da Portaria NÃO foi lido — a citação do
 * PDCA-01 foi confirmada por leitura directa e corroborada por fontes
 * secundárias oficiais (FAQ do SeMAE de São José do Rio Preto/SP e
 * notícias do DMAE/Prefeitura de Uberlândia, que tratam a validade de
 * 7 anos pela Portaria 155/2022); por isso o intervalo entra como
 * PARÂMETRO validado pela regra da base (nunca constante nova) e a
 * leitura do texto primário fica na pendência P-II5-PARAM.
 *
 * <p><b>Regra de fronteira (declarada):</b> a próxima verificação que
 * cai EXACTAMENTE na data de referência está VENCIDA nesse dia
 * ({@code nextVerificationOn <= reference} → {@link ClockStatus#OVERDUE},
 * a mesma semântica inclusiva de {@code VerificationSchedule.isDue} da
 * ED-14); a que cai exactamente em {@code reference + horizonDays} está
 * A VENCER ({@code DUE_WITHIN_HORIZON}, fronteira inclusiva de
 * {@code isDueWithin}). Sem verificação registada, o estado é
 * {@code UNKNOWN_NO_VERIFICATION} — o relógio não inventa data; o
 * hidrómetro continua pontuável e o plano expõe o estado.
 */
public final class MetrologicalClock {
    private MetrologicalClock() {}

    public enum ClockStatus { OVERDUE, DUE_WITHIN_HORIZON, CURRENT, UNKNOWN_NO_VERIFICATION }

    /**
     * Avaliação do relógio de um hidrómetro. {@code daysUntilDue} é
     * {@code reference → nextVerificationOn} em dias (negativo = dias de
     * atraso quando {@code OVERDUE}); {@code null} quando não há
     * verificação registada.
     */
    public record ClockAssessment(UUID meterId, LocalDate lastVerificationOn,
                                  LocalDate nextVerificationOn, ClockStatus status,
                                  Long daysUntilDue) {}

    public static ClockAssessment assess(MeterRenewalSnapshot snapshot, LocalDate reference,
                                         int intervalYears, int horizonDays) {
        if (snapshot == null) throw new IllegalArgumentException("Retrato do hidrômetro é obrigatório");
        if (reference == null) throw new IllegalArgumentException("Data de referência do relógio é obrigatória");
        if (horizonDays < 0) throw new IllegalArgumentException("Horizonte do relógio não pode ser negativo");
        // Validação reutilizada da ED-14 (1..7, só reduz) — chamada mesmo sem
        // verificação registada, para intervalo inválido nunca passar em silêncio.
        MeterParameterRules.validateVerificationIntervalYears(intervalYears);

        LocalDate last = snapshot.lastVerificationOn();
        if (last == null)
            return new ClockAssessment(snapshot.meterId(), null, null,
                    ClockStatus.UNKNOWN_NO_VERIFICATION, null);

        LocalDate next = VerificationSchedule.nextVerificationOn(last, intervalYears);
        ClockStatus status;
        if (VerificationSchedule.isDue(next, reference)) status = ClockStatus.OVERDUE;
        else if (VerificationSchedule.isDueWithin(next, reference, horizonDays)) status = ClockStatus.DUE_WITHIN_HORIZON;
        else status = ClockStatus.CURRENT;
        return new ClockAssessment(snapshot.meterId(), last, next, status,
                ChronoUnit.DAYS.between(reference, next));
    }
}
