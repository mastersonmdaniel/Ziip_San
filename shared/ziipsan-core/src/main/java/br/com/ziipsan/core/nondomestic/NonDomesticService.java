package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.effluent.EffluentStandard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servico do esgoto nao domestico (ED-83): amarra o cadastro, a
 * autorizacao, o automonitoramento, a vistoria por OS, a nao
 * conformidade e a cobranca complementar, consumindo as portas dos
 * vizinhos (ED-22, ED-50 via ED-105, motor de OS, canal de
 * notificacao) sem duplicar nenhum deles.
 *
 * <p>Fluxo do aceite, de ponta a ponta: cadastro (com a rota da
 * ED-22 para quem nao tem cadastro comercial) -> caracterizacao ->
 * termo de compromisso -> autorizacao de lancamento -> amostragem
 * do prestador / automonitoramento -> vistoria por OS -> notificacao
 * -> penalidade -> componente de cobranca para o motor da P2.5.
 */
public final class NonDomesticService {

    private final NonDomesticPorts.CommercialRegistrationPort commercial;
    private final NonDomesticPorts.FieldInspectionOrderPort inspections;
    private final NonDomesticPorts.UserNotificationPort notifications;
    private final ProviderSampling sampling;

    public NonDomesticService(NonDomesticPorts.CommercialRegistrationPort commercial,
                              NonDomesticPorts.FieldInspectionOrderPort inspections,
                              NonDomesticPorts.UserNotificationPort notifications,
                              ProviderSampling sampling) {
        if (commercial == null) throw new IllegalArgumentException("porta do cadastro comercial (ED-22) e obrigatoria");
        if (inspections == null) throw new IllegalArgumentException("porta da OS de vistoria e obrigatoria");
        if (notifications == null) throw new IllegalArgumentException("porta de notificacao ao usuario e obrigatoria");
        if (sampling == null) throw new IllegalArgumentException("amostragem do prestador (ED-50/ED-105) e obrigatoria");
        this.commercial = commercial;
        this.inspections = inspections;
        this.notifications = notifications;
        this.sampling = sampling;
    }

    /**
     * Cadastra o gerador resolvendo a rota do aceite: com cadastro
     * comercial, a unidade do usuario; SEM cadastro comercial, so
     * pela unidade avulsa CASUAL da ED-22 — sem nenhuma das duas,
     * recusa nomeada (usuario sem cadastro nao fica sem atendimento,
     * mas tambem nao ganha cadastro presumido).
     */
    public DischargerRegistration register(UUID id, UUID tenantId, UUID consumerUnitId,
                                           DischargerRegistration.Kind kind, String kindDescription,
                                           String dischargePointCode) {
        if (tenantId == null || consumerUnitId == null)
            throw new IllegalArgumentException("tenant e unidade consumidora sao obrigatorios");
        DischargerRegistration.Route route;
        if (commercial.hasCommercialRegistration(tenantId, consumerUnitId)) {
            route = DischargerRegistration.Route.COMMERCIAL_UNIT;
        } else if (commercial.isCasualUnitOfEd22(tenantId, consumerUnitId)) {
            route = DischargerRegistration.Route.CASUAL_VIA_ED22;
        } else {
            throw new IllegalStateException("unidade " + consumerUnitId + " sem cadastro comercial"
                    + " e sem ser a unidade avulsa (CASUAL) da ED-22: o usuario sem cadastro"
                    + " comercial e atendido pela ED-22 (CAD-70/OPE-13) — cadastrar a unidade"
                    + " avulsa na ED-22 antes deste cadastro");
        }
        return new DischargerRegistration(id, tenantId, consumerUnitId, route, kind,
                kindDescription, dischargePointCode);
    }

    /** Plano de amostragem do prestador a partir da autorizacao vigente e do parametro do tenant. */
    public ProviderSamplingPlan samplingPlanFor(DischargerRegistration discharger, Integer intervalDaysRaw) {
        if (discharger == null) throw new IllegalArgumentException("gerador e obrigatorio");
        int interval = NonDomesticParameters.samplingIntervalDays(intervalDaysRaw);
        if (discharger.authorizationValidFrom() == null)
            throw new IllegalStateException("plano de amostragem exige autorizacao emitida:"
                    + " a ancora do plano e o inicio da vigencia da autorizacao");
        return new ProviderSamplingPlan(discharger.id(), discharger.dischargePointCode(),
                interval, discharger.authorizationValidFrom());
    }

    /**
     * Avalia o automonitoramento informado pelo usuario contra o
     * catalogo do prestador. Exige lancamento autorizado no periodo.
     * Devolve um veredito por resultado reportado.
     */
    public List<UserStandardCatalog.Assessment> evaluateSelfMonitoring(
            DischargerRegistration discharger, SelfMonitoringReport report,
            UserStandardCatalog catalog, EffluentStandard.Jurisdiction jurisdiction, String stateCode) {
        if (discharger == null || report == null || catalog == null)
            throw new IllegalArgumentException("gerador, relatorio e catalogo sao obrigatorios");
        if (!report.dischargerId().equals(discharger.id()))
            throw new IllegalArgumentException("relatorio de automonitoramento de outro gerador");
        if (!discharger.authorizedOn(report.periodStart()) || !discharger.authorizedOn(report.periodEnd()))
            throw new IllegalStateException("automonitoramento exige lancamento autorizado"
                    + " vigente em todo o periodo reportado");
        List<UserStandardCatalog.Assessment> out = new ArrayList<>();
        for (SelfMonitoringReport.ReportedResult r : report.results()) {
            out.add(catalog.evaluate(r.parameterKey(), r.numericValue(), r.qualitativeValue(),
                    jurisdiction, stateCode, report.periodEnd()));
        }
        return List.copyOf(out);
    }

    /** Abre a OS de vistoria no ponto de lancamento (aceite: vistoria por OS). */
    public UUID orderInspection(DischargerRegistration discharger, String reason) {
        if (discharger == null) throw new IllegalArgumentException("gerador e obrigatorio");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("vistoria por OS exige o motivo declarado");
        return inspections.openInspectionOrder(discharger.tenantId(), discharger.id(),
                discharger.dischargePointCode(), reason);
    }

    /**
     * Abre o caso de nao conformidade a partir de um veredito
     * NON_CONFORMING (amostragem do prestador ou automonitoramento).
     * Veredito CONFORMING ou INDETERMINATE nao abre caso: o
     * indeterminado nao fundamenta notificacao nem penalidade.
     */
    public NonConformityCase openNonConformity(DischargerRegistration discharger,
                                               NonConformityCase.Origin origin,
                                               UserStandardCatalog.Assessment assessment,
                                               Instant detectedAt) {
        if (discharger == null || assessment == null)
            throw new IllegalArgumentException("gerador e veredito sao obrigatorios");
        if (assessment.verdict() != UserStandardCatalog.Assessment.Verdict.NON_CONFORMING)
            throw new IllegalArgumentException("caso de nao conformidade so nasce de veredito"
                    + " NON_CONFORMING; veredito recebido: " + assessment.verdict()
                    + " (indeterminado nao fundamenta caso, notificacao ou penalidade)");
        String detail = assessment.reason() + " | referencia: " + assessment.normReference()
                + " | origem do padrao: " + assessment.source();
        return new NonConformityCase(UUID.randomUUID(), discharger.tenantId(), discharger.id(),
                origin, assessment.parameterKey(), detail, detectedAt);
    }

    /** Notifica o usuario pelo canal vigente e registra a prova no caso. */
    public void notifyNonConformity(DischargerRegistration discharger, NonConformityCase kase, Instant at) {
        if (discharger == null || kase == null)
            throw new IllegalArgumentException("gerador e caso sao obrigatorios");
        if (!kase.dischargerId().equals(discharger.id()))
            throw new IllegalArgumentException("caso de outro gerador");
        String ref = notifications.notifyUser(discharger.tenantId(), discharger.id(), kase.id(),
                "nao conformidade no parametro " + kase.parameterKey() + ": " + kase.detail());
        kase.recordNotification(ref, at);
    }

    /** Amostragem do prestador (delegacao nomeada, para o fluxo de ponta a ponta ler num lugar so). */
    public ProviderSampling sampling() { return sampling; }
}
