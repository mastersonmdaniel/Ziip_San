package br.com.ziipsan.core.sewerraw;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Alerta de vencimento e operabilidade da captacao (aceite ED-80:
 * outorga e licenca "com vigencia, condicionantes e alerta de
 * vencimento"). A janela de alerta e o parametro do tenant
 * ({@link SewerRawParameters#EXPIRY_ALERT_WINDOW_DAYS}) — sem
 * janela declarada, nenhum alerta e calculado (erro do parametro,
 * nunca um numero de fabrica).
 *
 * <p>Condicionantes entram como FATOS declarados de cumprimento
 * (lista de pares descricao/cumprida lida do cadastro do tenant):
 * condicionante nao cumprida nao e presumida cumprida — ela aparece
 * no alerta e bloqueia a operabilidade declarada.
 */
public final class ExpiryAlerts {

    public enum ValidityStatus { NOT_YET_VALID, VALID, EXPIRING_SOON, EXPIRED }

    /** Cumprimento declarado de UMA condicionante. */
    public record ConditionFact(String description, boolean fulfilled) {
        public ConditionFact {
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("descricao da condicionante e obrigatoria");
        }
    }

    public record DocumentAlert(
            String documentKind,
            String documentNumber,
            ValidityStatus status,
            long daysToExpiry,
            int pendingConditions) {}

    public record Operability(
            boolean operable,
            List<String> blockingReasons,
            List<DocumentAlert> alerts) {}

    private ExpiryAlerts() {}

    public static DocumentAlert alertForGrant(WaterGrant grant, List<ConditionFact> facts,
                                              LocalDate referenceDate, int windowDays) {
        return alert("OUTORGA", grant.grantNumber(), grant.validFrom(), grant.validTo(),
                grant.conditions(), facts, referenceDate, windowDays);
    }

    public static DocumentAlert alertForLicense(EnvironmentalLicense license, List<ConditionFact> facts,
                                                  LocalDate referenceDate, int windowDays) {
        return alert("LICENCA_AMBIENTAL", license.licenseNumber(), license.validFrom(), license.validTo(),
                license.conditions(), facts, referenceDate, windowDays);
    }

    private static DocumentAlert alert(String kind, String number, LocalDate from, LocalDate to,
                                       List<String> declaredConditions, List<ConditionFact> facts,
                                       LocalDate referenceDate, int windowDays) {
        if (referenceDate == null) throw new IllegalArgumentException("data de referencia do alerta e obrigatoria");
        if (windowDays <= 0) throw new IllegalArgumentException("janela de alerta deve ser maior que zero");
        long days = ChronoUnit.DAYS.between(referenceDate, to);
        ValidityStatus status;
        if (referenceDate.isBefore(from)) status = ValidityStatus.NOT_YET_VALID;
        else if (referenceDate.isAfter(to)) status = ValidityStatus.EXPIRED;
        else if (days <= windowDays) status = ValidityStatus.EXPIRING_SOON;
        else status = ValidityStatus.VALID;
        int pending = pendingConditions(declaredConditions, facts);
        return new DocumentAlert(kind, number, status, days, pending);
    }

    /**
     * Condicionantes pendentes: as DECLARADAS no documento sem fato
     * de cumprimento correspondente contam como pendentes (nada se
     * presume cumprido); fato para condicionante nao declarada e
     * erro de cadastro.
     */
    public static int pendingConditions(List<String> declared, List<ConditionFact> facts) {
        List<ConditionFact> safe = facts == null ? List.of() : facts;
        for (ConditionFact f : safe) {
            if (!declared.contains(f.description()))
                throw new IllegalArgumentException("fato de cumprimento para condicionante nao declarada: " + f.description());
        }
        int pending = 0;
        for (String d : declared) {
            boolean fulfilled = safe.stream().anyMatch(f -> f.description().equals(d) && f.fulfilled());
            if (!fulfilled) pending++;
        }
        return pending;
    }

    /**
     * Operabilidade da captacao na data: exige captacao ativa,
     * outorga e licenca vigentes (nem vencidas nem futuras) e
     * nenhuma condicionante pendente nos dois documentos. Cada
     * bloqueio e nomeado — operabilidade nunca e presumida.
     */
    public static Operability operability(RawWaterIntake intake, WaterGrant grant, EnvironmentalLicense license,
                                            List<ConditionFact> grantFacts, List<ConditionFact> licenseFacts,
                                            LocalDate referenceDate, Integer rawWindowDays) {
        if (intake == null || grant == null || license == null)
            throw new IllegalArgumentException("captacao, outorga e licenca sao obrigatorios");
        if (!grant.id().equals(intake.grantId()) || !license.id().equals(intake.licenseId()))
            throw new IllegalArgumentException("outorga/licenca nao sao os documentos ligados a captacao " + intake.code());
        if (!grant.tenantId().equals(intake.tenantId()) || !license.tenantId().equals(intake.tenantId()))
            throw new IllegalArgumentException("documentos de outro tenant nao operam esta captacao");
        int window = SewerRawParameters.expiryAlertWindowDays(rawWindowDays);
        DocumentAlert ga = alertForGrant(grant, grantFacts, referenceDate, window);
        DocumentAlert la = alertForLicense(license, licenseFacts, referenceDate, window);
        List<String> blocks = new ArrayList<>();
        if (!intake.active()) blocks.add("captacao inativa: " + intake.code());
        if (ga.status() == ValidityStatus.EXPIRED) blocks.add("outorga vencida: " + grant.grantNumber());
        if (ga.status() == ValidityStatus.NOT_YET_VALID) blocks.add("outorga ainda sem vigencia: " + grant.grantNumber());
        if (la.status() == ValidityStatus.EXPIRED) blocks.add("licenca vencida: " + license.licenseNumber());
        if (la.status() == ValidityStatus.NOT_YET_VALID) blocks.add("licenca ainda sem vigencia: " + license.licenseNumber());
        if (ga.pendingConditions() > 0) blocks.add("outorga com " + ga.pendingConditions() + " condicionante(s) pendente(s)");
        if (la.pendingConditions() > 0) blocks.add("licenca com " + la.pendingConditions() + " condicionante(s) pendente(s)");
        return new Operability(blocks.isEmpty(), List.copyOf(blocks), List.of(ga, la));
    }
}
