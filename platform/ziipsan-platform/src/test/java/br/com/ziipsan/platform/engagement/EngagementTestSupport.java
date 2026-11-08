package br.com.ziipsan.platform.engagement;

import br.com.ziipsan.platform.portal.selfcare.SelfCareParameters;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Fakes em memoria das portas da ED-101 e da ED-38 (padrao ED-38/ED-41). */
final class EngagementTestSupport {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000101");
    static final UUID CUSTOMER = UUID.fromString("00000000-0000-0000-0000-000000000201");
    static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000301");
    static final YearMonth PERIOD = YearMonth.of(2026, 10);

    private EngagementTestSupport() {
    }

    /** Uma unica fake implementa as duas portas de parametros (ED-101 + ED-38). */
    static final class FakeParams implements EngagementParameters, SelfCareParameters {
        boolean feature = true;
        Integer engMin;
        String milestones;
        String invoiceTemplate;
        boolean billPeer = true;
        Integer billMin;

        public boolean peerReportEnabled(UUID t) { return feature; }
        public Optional<Integer> peerGroupMinSize(UUID t) { return Optional.ofNullable(engMin); }
        public Optional<String> gamificationMilestonesRaw(UUID t) { return Optional.ofNullable(milestones); }
        public Optional<String> invoiceTemplate(UUID t) { return Optional.ofNullable(invoiceTemplate); }

        public boolean govbrEnabled(UUID t) { return false; }
        public Optional<String> govbrMinLevelRaw(UUID t) { return Optional.empty(); }
        public boolean peerComparisonEnabled(UUID t) { return billPeer; }
        public Optional<Integer> peerMinUnits(UUID t) { return Optional.ofNullable(billMin); }
        public boolean passkeyEnabled(UUID t) { return false; }
    }

    static final class FakeEnrollments implements EngagementStores.EnrollmentStore {
        final Map<String, UserEnrollment> rows = new HashMap<>();
        public Optional<UserEnrollment> find(UUID t, UUID c, UUID u) {
            return Optional.ofNullable(rows.get(t + "|" + c + "|" + u));
        }
        public void save(UserEnrollment e) {
            rows.put(e.tenantId() + "|" + e.customerId() + "|" + e.unitId(), e);
        }
    }

    static final class FakeGoals implements EngagementStores.GoalStore {
        final Map<String, ConsumptionGoal> rows = new HashMap<>();
        public Optional<ConsumptionGoal> find(UUID t, UUID u, YearMonth p) {
            return Optional.ofNullable(rows.get(t + "|" + u + "|" + p));
        }
        public void save(ConsumptionGoal g) {
            rows.put(g.tenantId() + "|" + g.unitId() + "|" + g.period(), g);
        }
    }

    static final class FakeConsumption implements ConsumptionPorts.BilledConsumptionPort,
            ConsumptionPorts.TelemetryConsumptionPort {
        BigDecimal billed;
        BigDecimal telemetry;
        public Optional<BigDecimal> billedM3(UUID t, UUID u, YearMonth p) {
            return Optional.ofNullable(billed);
        }
        public Optional<BigDecimal> telemetryM3(UUID t, UUID u, YearMonth p) {
            return Optional.ofNullable(telemetry);
        }
    }

    static final class FakeReminders implements ReminderPorts.ReminderPort, ReminderPorts.ReminderLog {
        final List<String> sent = new ArrayList<>();
        final Set<String> log = new HashSet<>();
        public void requestReminder(UUID t, UUID c, UUID u, ReminderPorts.ReminderKind kind,
                                    YearMonth period, Map<String, String> variables) {
            sent.add(kind + "|" + period + "|" + variables);
        }
        public boolean alreadySent(UUID t, UUID u, ReminderPorts.ReminderKind kind, YearMonth period) {
            return log.contains(t + "|" + u + "|" + kind + "|" + period);
        }
        public void record(UUID t, UUID u, ReminderPorts.ReminderKind kind, YearMonth period) {
            log.add(t + "|" + u + "|" + kind + "|" + period);
        }
    }

    /** Monta o grafo de servicos sobre as fakes dadas. */
    static final class Fixture {
        final FakeParams params = new FakeParams();
        final FakeEnrollments enrollments = new FakeEnrollments();
        final FakeGoals goals = new FakeGoals();
        final FakeConsumption consumption = new FakeConsumption();
        final FakeReminders reminders = new FakeReminders();
        final GoalService goalService = new GoalService(params, enrollments, goals, consumption, consumption);
        final PeerReportService peerService = new PeerReportService(params, params, goalService);
        final GamificationService gamification = new GamificationService(params, goalService);
        final GoalReminderService reminderService = new GoalReminderService(reminders, reminders, goalService);
        final InvoiceMessageService invoiceService = new InvoiceMessageService(params);

        Fixture enrolled() {
            enrollments.save(UserEnrollment.optIn(TENANT, CUSTOMER, UNIT,
                    java.time.Instant.parse("2026-10-01T10:00:00Z")));
            return this;
        }

        ConsumptionGoal goal(String target) {
            return goalService.defineGoal(TENANT, CUSTOMER, UNIT, PERIOD, new BigDecimal(target),
                    java.time.Instant.parse("2026-10-02T10:00:00Z"));
        }
    }

    static PeerCohort cohort(String... values) {
        List<BigDecimal> list = new ArrayList<>();
        for (String v : values) {
            list.add(new BigDecimal(v));
        }
        return new PeerCohort("GRUPO-TESTE", list);
    }

    static PeerCohort cohortN(int n, String value) {
        List<BigDecimal> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new BigDecimal(value));
        }
        return new PeerCohort("GRUPO-TESTE", list);
    }
}
