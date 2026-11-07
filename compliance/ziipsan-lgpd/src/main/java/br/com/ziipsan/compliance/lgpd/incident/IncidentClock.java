package br.com.ziipsan.compliance.lgpd.incident;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Relógio do incidente com dado pessoal (P3.10; PLT-34).
 *
 * <p>O relógio começa no <b>conhecimento</b> do incidente pelo Ziip SAN (operador), não na
 * ocorrência do fato: é desse instante que corre o prazo para o operador informar o tenant
 * (controlador) sem demora injustificada e dentro do prazo contratual
 * ({@code lgpd.operator_notice_hours}, classe c, só reduz, teto no contrato da P3.9).
 *
 * <p>Alertas para a Central: às 24 h e às 48 h do conhecimento. O relógio NÃO conhece o
 * estado do aviso (que vive no registro do incidente,
 * {@code PersonalDataIncident.controllerNotifiedAt}): suprimir os alertas depois que o
 * aviso estiver registrado é decisão de quem chama, consultando o registro — ver
 * procedimento §3. Classe pura e determinística: o {@link Clock} é injetado, para o
 * teste exercitar o relógio sem dormir nem depender do relógio da máquina.
 */
public final class IncidentClock {

    /** Marcos de alerta na Central, em horas desde o conhecimento (spec da P3.10). */
    public static final List<Integer> CENTRAL_ALERT_HOURS = List.of(24, 48);

    private final Instant knownAt;
    private final int operatorNoticeHours;

    private IncidentClock(Instant knownAt, int operatorNoticeHours) {
        this.knownAt = knownAt;
        this.operatorNoticeHours = operatorNoticeHours;
    }

    /**
     * Inicia o relógio no conhecimento.
     *
     * @param clock                relógio injetado (nos testes, {@code Clock.fixed})
     * @param operatorNoticeHours valor vigente de {@code lgpd.operator_notice_hours} do tenant,
     *                             já validado contra o teto contratual por
     *                             {@link LgpdIncidentParameters#validateOperatorNoticeHours(int, Integer)}
     */
    public static IncidentClock start(Clock clock, int operatorNoticeHours) {
        Objects.requireNonNull(clock, "clock é obrigatório");
        if (operatorNoticeHours < 1) {
            throw new IllegalArgumentException(
                    "lgpd.operator_notice_hours precisa ser ao menos 1; recebido: " + operatorNoticeHours);
        }
        return new IncidentClock(clock.instant(), operatorNoticeHours);
    }

    /** Retoma um relógio já iniciado (registro persistido), sem reiniciar a contagem. */
    public static IncidentClock resume(Instant knownAt, int operatorNoticeHours) {
        Objects.requireNonNull(knownAt, "knownAt é obrigatório");
        if (operatorNoticeHours < 1) {
            throw new IllegalArgumentException(
                    "lgpd.operator_notice_hours precisa ser ao menos 1; recebido: " + operatorNoticeHours);
        }
        return new IncidentClock(knownAt, operatorNoticeHours);
    }

    public Instant knownAt() {
        return knownAt;
    }

    /** Prazo do aviso do operador ao controlador: conhecimento + {@code lgpd.operator_notice_hours}. */
    public Instant operatorNoticeDeadline() {
        return knownAt.plus(Duration.ofHours(operatorNoticeHours));
    }

    /** Instante de cada alerta da Central (conhecimento + 24 h e + 48 h), em ordem. */
    public List<Instant> centralAlertInstants() {
        List<Instant> instants = new ArrayList<>();
        for (int hours : CENTRAL_ALERT_HOURS) {
            instants.add(knownAt.plus(Duration.ofHours(hours)));
        }
        return List.copyOf(instants);
    }

    /**
     * Alertas devidos em {@code now}: marcos já alcançados e ainda não emitidos.
     *
     * @param alreadyEmittedHours marcos (24/48) cujo alerta já foi entregue à Central
     * @return marcos devidos, em ordem crescente; vazio se nenhum
     */
    public List<Integer> dueCentralAlerts(Instant now, List<Integer> alreadyEmittedHours) {
        Objects.requireNonNull(now, "now é obrigatório");
        List<Integer> due = new ArrayList<>();
        for (int hours : CENTRAL_ALERT_HOURS) {
            boolean reached = !now.isBefore(knownAt.plus(Duration.ofHours(hours)));
            boolean emitted = alreadyEmittedHours != null && alreadyEmittedHours.contains(hours);
            if (reached && !emitted) {
                due.add(hours);
            }
        }
        return List.copyOf(due);
    }

    /** Verdadeiro se {@code now} passou do prazo do aviso ao controlador. */
    public boolean operatorNoticeOverdue(Instant now) {
        Objects.requireNonNull(now, "now é obrigatório");
        return now.isAfter(operatorNoticeDeadline());
    }
}
