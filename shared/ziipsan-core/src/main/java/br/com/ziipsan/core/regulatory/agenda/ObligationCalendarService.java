package br.com.ziipsan.core.regulatory.agenda;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ED-98 (GES-05): geracao do calendario de obrigacoes por tenant, dos
 * lembretes (ED-07) e do alerta antecipado de norma (P1.34).
 *
 * <p>Geracao: para cada regra padrao aplicavel a natureza juridica (e,
 * para RREO/RGF "quando aplicavel", a natureza de ENTE combinada a
 * flag fiscal — correcao D2), a norma da regra e
 * LIDA do catalogo da P3.15 pela {@link AgendaPorts.NormCatalogPort} —
 * regra cuja norma NAO esta no catalogo nao vira item: e devolvida em
 * {@link Generation#unresolvedKinds()} (nada e inventado). Vencimentos
 * sem fonte nascem {@code [A DEFINIR]} no rotulo, sem data fingida; as
 * ancoras com fonte sao maio (quitacao) e data-base − 30 dias
 * (reajuste), ambas do aceite.</p>
 */
public final class ObligationCalendarService {

    /** Calendario gerado + os tipos cuja norma nao foi encontrada no catalogo. */
    public record Generation(UUID tenantId, int year, List<AgendaItem> items,
                             List<ObligationKind> unresolvedKinds) {
        public List<AgendaItem> itemsOf(ObligationKind kind) {
            return items.stream().filter(i -> i.kind() == kind).toList();
        }
    }

    /** Alerta antecipado de norma com data de efeito futura (P1.34). */
    public record NormEarlyWarning(String catalogKey, String title, LocalDate effectiveFrom,
                                   String reminderRef) { }

    private final AgendaPorts.NormCatalogPort catalog;
    private final AgendaPorts.ReminderPort reminders;

    public ObligationCalendarService(AgendaPorts.NormCatalogPort catalog,
                                     AgendaPorts.ReminderPort reminders) {
        this.catalog = Objects.requireNonNull(catalog, "catalogo de normas da P3.15 e obrigatorio");
        this.reminders = Objects.requireNonNull(reminders, "porta de lembretes da ED-07 e obrigatoria");
    }

    public Generation generate(TenantObligationProfile profile, int year) {
        Objects.requireNonNull(profile, "perfil do tenant e obrigatorio");
        List<AgendaPorts.NormRuleView> norms = catalog.listNorms();
        List<AgendaItem> items = new ArrayList<>();
        List<ObligationKind> unresolved = new ArrayList<>();
        for (ObligationRule rule : ObligationRule.standardRules()) {
            if (!rule.appliesTo(profile.legalNature())) continue;
            if (rule.requiresFiscalFlag() && !profile.fiscalReportsApplicable()) continue;
            Optional<AgendaPorts.NormRuleView> norm = norms.stream()
                    .filter(n -> n.matchesToken(rule.kind().normToken())).findFirst();
            if (norm.isEmpty()) { unresolved.add(rule.kind()); continue; }
            UUID responsible = profile.responsibleFor(rule.kind());
            if (responsible == null)
                throw new IllegalArgumentException("obrigacao " + rule.kind()
                        + " sem responsavel no perfil do tenant — nenhum responsavel e inventado");
            items.addAll(itemsFor(profile, rule, norm.get(), year, responsible));
        }
        return new Generation(profile.tenantId(), year, List.copyOf(items), List.copyOf(unresolved));
    }

    private List<AgendaItem> itemsFor(TenantObligationProfile profile, ObligationRule rule,
                                      AgendaPorts.NormRuleView norm, int year, UUID responsible) {
        List<AgendaItem> out = new ArrayList<>();
        switch (rule.periodicity()) {
            case ANNUAL -> out.add(annualItem(profile, rule, norm, year, responsible, year + "-ANNUAL"));
            case BIMONTHLY -> {
                for (int b = 1; b <= 6; b++)
                    out.add(periodicItem(profile, rule, norm, responsible, year + "-B" + b));
            }
            case QUADRIMESTRAL -> {
                for (int q = 1; q <= 3; q++)
                    out.add(periodicItem(profile, rule, norm, responsible, year + "-Q" + q));
            }
            case EVENT_DRIVEN -> { /* itens por evento nascem de outro servico (fiscalizacao) */ }
        }
        return out;
    }

    private AgendaItem annualItem(TenantObligationProfile profile, ObligationRule rule,
                                  AgendaPorts.NormRuleView norm, int year, UUID responsible, String periodRef) {
        LocalDate due = null;
        String label = null;
        if (rule.daysBeforeBaseDate() != null) {
            // Aceite: publicacao do reajuste >= 30 dias antes da data-base do perfil reg.*.
            if (profile.tariffBaseDate() != null) {
                due = profile.tariffBaseDate().atYear(year).minusDays(rule.daysBeforeBaseDate());
            } else {
                label = "[A DEFINIR] data-base tarifaria ausente no perfil reg.* do tenant";
            }
        } else if (rule.annualMonth() != null) {
            // Aceite: fatura de maio. O DIA exato nao tem fonte (D5: a
            // regra expoe so o MES): rotulo no mes com o dia marcado
            // [A DEFINIR], sem data fingida.
            label = monthNamePt(rule.annualMonth()) + "/" + year + " (dia [A DEFINIR])";
        } else if (!rule.periodicityDefined()) {
            // D6: periodicidade sem fonte marcada no proprio rotulo do
            // item (P-ED98-01 ampliada), alem do vencimento [A DEFINIR].
            label = ObligationRule.UNDEFINED_PERIODICITY_MARK + "; vencimento [A DEFINIR]";
        } else {
            label = "[A DEFINIR] vencimento anual sem fonte no aceite nem no catalogo";
        }
        return new AgendaItem(UUID.randomUUID(), profile.tenantId(), rule.kind(),
                rule.kind().name(), norm.catalogKey(), periodRef, due, label,
                responsible, null, AgendaItem.Status.PENDING, rule.kind().isManual(),
                null, null, null, null);
    }

    /** Nome do mes em pt-BR minusculo, para o rotulo da ancora mensal (D5). */
    private static String monthNamePt(java.time.Month month) {
        return switch (month) {
            case JANUARY -> "janeiro";
            case FEBRUARY -> "fevereiro";
            case MARCH -> "marco";
            case APRIL -> "abril";
            case MAY -> "maio";
            case JUNE -> "junho";
            case JULY -> "julho";
            case AUGUST -> "agosto";
            case SEPTEMBER -> "setembro";
            case OCTOBER -> "outubro";
            case NOVEMBER -> "novembro";
            case DECEMBER -> "dezembro";
        };
    }

    private AgendaItem periodicItem(TenantObligationProfile profile, ObligationRule rule,
                                    AgendaPorts.NormRuleView norm, UUID responsible, String periodRef) {
        return new AgendaItem(UUID.randomUUID(), profile.tenantId(), rule.kind(),
                rule.kind().name(), norm.catalogKey(), periodRef, null,
                "[A DEFINIR] dia de vencimento do periodo " + periodRef + " sem fonte",
                responsible, null, AgendaItem.Status.PENDING, rule.kind().isManual(),
                null, null, null, null);
    }

    /**
     * Lembretes pelos alertas da ED-07: um pedido por item, ao
     * responsavel do item, com codigos de titulo/corpo na convencao da
     * ED-07 (codigos do catalogo de mensagens). Devolve os itens com a
     * referencia do lembrete gravada.
     */
    public List<AgendaItem> scheduleReminders(Generation generation) {
        Objects.requireNonNull(generation, "calendario e obrigatorio");
        List<AgendaItem> out = new ArrayList<>();
        for (AgendaItem item : generation.items()) {
            String ref = reminders.remind(new AgendaPorts.ReminderRequest(
                    item.tenantId(), item.id(), item.responsibleUserId(),
                    "ED98-AGENDA-ITEM:" + item.id(),
                    "agenda.obligation.reminder.title", "agenda.obligation.reminder.body",
                    item.dueDate()));
            out.add(item.withReminderRef(ref));
        }
        return List.copyOf(out);
    }

    /**
     * Alerta antecipado (P1.34): normas do catalogo em VIGENCIA_FUTURA
     * com data de efeito na janela geram aviso ao responsavel padrao do
     * tenant, pela mesma porta de lembretes da ED-07. Norma sem data de
     * efeito NAO gera alerta (regra da P1.34: sem data nao ha o que
     * antecipar).
     */
    public List<NormEarlyWarning> earlyNormWarnings(TenantObligationProfile profile,
                                                    LocalDate today, int daysAhead) {
        Objects.requireNonNull(profile, "perfil do tenant e obrigatorio");
        Objects.requireNonNull(today, "data de referencia e obrigatoria");
        if (profile.defaultResponsibleUserId() == null)
            throw new IllegalArgumentException("alerta antecipado exige responsavel padrao no perfil");
        List<NormEarlyWarning> out = new ArrayList<>();
        for (AgendaPorts.NormRuleView norm : catalog.listNorms()) {
            if (!norm.isUpcomingAt(today, daysAhead)) continue;
            String ref = reminders.remind(new AgendaPorts.ReminderRequest(
                    profile.tenantId(), UUID.nameUUIDFromBytes(
                            ("ED98-NORM:" + norm.catalogKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    profile.defaultResponsibleUserId(),
                    "ED98-NORM-EFFECT:" + norm.catalogKey(),
                    "agenda.norm.upcoming.title", "agenda.norm.upcoming.body",
                    norm.effectiveFrom()));
            out.add(new NormEarlyWarning(norm.catalogKey(), norm.title(), norm.effectiveFrom(), ref));
        }
        return List.copyOf(out);
    }
}
