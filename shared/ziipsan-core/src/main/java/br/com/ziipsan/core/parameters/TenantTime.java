package br.com.ziipsan.core.parameters;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * Tempo do tenant (P0.19): instantes gravados em UTC, datas de negócio como data local.
 *
 * <p>Um {@code TenantTime} é um fuso (identificador IANA, classe d, padrão de fábrica
 * {@code America/Sao_Paulo}) mais um relógio. O relógio é injetado para que testes usem
 * relógio simulado ({@link Clock#fixed}) em vez de "hoje" implícito — o plano exige
 * teste com relógio simulado em tenants de Manaus, Rio Branco e Noronha, e o mesmo
 * instante tem data local diferente em cada um deles.
 *
 * <p>Regras:
 * <ul>
 *   <li>o identificador tem de existir na base IANA da JVM (um deslocamento fixo como
 *       {@code +03:00} não é identificador IANA e é recusado);</li>
 *   <li>{@link #nowLocalDate()} é a data de negócio "hoje" do tenant;</li>
 *   <li>nenhum método aqui grava nada: a persistência continua em {@link Instant} (UTC)
 *       e a conversão só acontece na borda (leitura/apresentação).</li>
 * </ul>
 */
public final class TenantTime {

    /** Padrão de fábrica do parâmetro {@code tenant.timezone} (P0.19). */
    public static final ZoneId FACTORY_DEFAULT = ZoneId.of("America/Sao_Paulo");

    private final ZoneId zone;
    private final Clock clock;

    private TenantTime(ZoneId zone, Clock clock) {
        this.zone = zone;
        this.clock = clock;
    }

    /**
     * Valida o identificador IANA e cria o tempo do tenant com o relógio do sistema (UTC).
     *
     * @throws IllegalArgumentException se o identificador não for IANA
     */
    public static TenantTime of(String ianaId) {
        return of(ianaId, Clock.systemUTC());
    }

    /** Como {@link #of(String)}, com relógio injetado (simulado nos testes). */
    public static TenantTime of(String ianaId, Clock clock) {
        if (ianaId == null || ianaId.isBlank()) {
            throw new IllegalArgumentException("tenant.timezone é obrigatório: informe um identificador IANA");
        }
        Set<String> available = ZoneId.getAvailableZoneIds();
        if (!available.contains(ianaId)) {
            throw new IllegalArgumentException(
                    "tenant.timezone '" + ianaId + "' não é um identificador IANA válido");
        }
        if (clock == null) {
            throw new IllegalArgumentException("O relógio é obrigatório");
        }
        return new TenantTime(ZoneId.of(ianaId), clock);
    }

    /** Tempo do tenant com o padrão de fábrica (America/Sao_Paulo). */
    public static TenantTime factoryDefault(Clock clock) {
        return new TenantTime(FACTORY_DEFAULT, clock);
    }

    public ZoneId zone() {
        return zone;
    }

    /** Instante atual (UTC) segundo o relógio injetado. */
    public Instant nowInstant() {
        return clock.instant();
    }

    /** Data de negócio atual do tenant (data local do instante atual). */
    public LocalDate nowLocalDate() {
        return LocalDateTime.ofInstant(clock.instant(), zone).toLocalDate();
    }

    /** Data local do tenant para um instante gravado em UTC. */
    public LocalDate localDateOf(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("O instante é obrigatório");
        }
        return LocalDateTime.ofInstant(instant, zone).toLocalDate();
    }

    /** Data/hora local do tenant para um instante gravado em UTC. */
    public LocalDateTime localDateTimeOf(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("O instante é obrigatório");
        }
        return LocalDateTime.ofInstant(instant, zone);
    }
}
