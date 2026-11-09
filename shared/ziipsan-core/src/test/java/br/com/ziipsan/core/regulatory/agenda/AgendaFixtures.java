package br.com.ziipsan.core.regulatory.agenda;

import br.com.ziipsan.platform.central.complete.RegulatoryLibrary;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fixtures da ED-98: catalogo REAL da P3.15 (RegulatoryLibrary/NormEntry) adaptado a porta. */
final class AgendaFixtures {

    static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    // Correcao D3: um tenant DISTINTO por natureza no teste GES-05
    // (antes, ente e concessionaria reusavam o mesmo TENANT_B).
    static final UUID TENANT_C = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID RESP = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private AgendaFixtures() { }

    static RegulatoryLibrary.NormEntry norm(String key, String title, String status, LocalDate effectiveFrom) {
        return new RegulatoryLibrary.NormEntry(key, title, "REGULATORY", status, "OFICIAL",
                "REG", "ARIS", null, null, "resumo " + key, effectiveFrom, null,
                List.of(new RegulatoryLibrary.NormEdition("1", effectiveFrom, null)));
    }

    /** Catalogo completo: uma norma por token do aceite, todas VIGENTE. */
    static List<RegulatoryLibrary.NormEntry> fullCatalog() {
        return List.of(
            norm("sinisa-ciclo-anual", "Ciclo anual do SINISA", "VIGENTE", LocalDate.of(2024, 1, 1)),
            norm("relatorio-anual-tarifa-social", "Relatorio anual de tarifa social", "VIGENTE", LocalDate.of(2024, 1, 1)),
            norm("declaracao-anual-quitacao", "Declaracao anual de quitacao (fatura de maio)", "VIGENTE", LocalDate.of(2024, 1, 1)),
            norm("nr-15-balanco-perdas", "Balanco de perdas da NR 15", "VIGENTE", LocalDate.of(2024, 1, 1)),
            norm("in-tc-28-2021-esfinge", "Remessa ao e-Sfinge (IN TC-28/2021)", "VIGENTE", LocalDate.of(2024, 1, 1)),
            norm("sisagua-vigilancia", "SISAGUA — vigilancia da qualidade da agua", "VIGENTE", LocalDate.of(2024, 1, 1)),
            norm("publicacao-reajuste", "Publicacao de reajuste tarifario", "VIGENTE", LocalDate.of(2024, 1, 1)),
            norm("rreo-lrf", "RREO — relatorio resumido da execucao orcamentaria", "VIGENTE", LocalDate.of(2024, 1, 1)),
            norm("rgf-lrf", "RGF — relatorio de gestao fiscal", "VIGENTE", LocalDate.of(2024, 1, 1)),
            norm("dmr-residuos-mtr-cdf", "DMR, MTR e CDF de residuos", "VIGENTE", LocalDate.of(2024, 1, 1)));
    }

    /** Adapta a RegulatoryLibrary REAL da P3.15 a porta da agenda. */
    static AgendaPorts.NormCatalogPort portOf(RegulatoryLibrary library) {
        return () -> library.query(new RegulatoryLibrary.NormFilter(null, null, null, null, null),
                        LocalDate.of(2026, 1, 1)).stream()
                .map(n -> new AgendaPorts.NormRuleView(n.catalogKey(), n.title(), n.status(), n.effectiveFrom()))
                .toList();
    }

    static AgendaPorts.NormCatalogPort fullPort() {
        return portOf(new RegulatoryLibrary(fullCatalog()));
    }

    static final class RecordingReminders implements AgendaPorts.ReminderPort {
        final List<AgendaPorts.ReminderRequest> requests = new ArrayList<>();
        @Override public String remind(AgendaPorts.ReminderRequest request) {
            requests.add(request);
            return "ED07-ALERT:" + request.agendaItemId();
        }
    }

    static TenantObligationProfile profile(UUID tenant, TenantLegalNature nature, boolean fiscal) {
        return new TenantObligationProfile(tenant, nature, MonthDay.of(7, 1), fiscal, RESP, Map.of());
    }
}
