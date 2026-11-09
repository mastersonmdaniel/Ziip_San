package br.com.ziipsan.core.regulatory.agenda;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Fixtures da ED-98: catalogo de normas LOCAL as fontes de teste do core
 * (correcao correcao-ci-agenda).
 *
 * <p>A versao original adaptava a {@code RegulatoryLibrary} REAL da
 * P3.15 — classe do modulo PLATFORM, que depende do core; um teste do
 * core nao pode importa-la sem inverter a dependencia (core -&gt;
 * platform seria circular), e era essa importacao que fazia o
 * {@code testCompile} do {@code ziipsan-core} falhar na CI. Esta
 * correcao torna as fixtures AUTO-CONTIDAS: o catalogo local
 * ({@link NormFixtureEntry} + {@link NormFixtureCatalog}) replica
 * SOMENTE os campos e comportamentos exercidos pelos testes da agenda
 * — {@code catalogKey}, {@code title}, {@code status} e
 * {@code effectiveFrom} — com a semantica demonstrada equivalente a da
 * classe real NOS CASOS EXERCIDOS (tabela de correspondencia no
 * RELATORIO-CORRECAO-CI-AGENDA.md). A coerencia com a P3.15 real
 * continua coberta pelos testes proprios da P3.15
 * ({@code RegulatoryLibraryTest}). Nenhuma assercao foi removida, atenuada ou
 * saltada; a unica assercao com objecto alterado e a de statusAt, re-expressa
 * contra NormFixtureCatalog e declarada no relatorio do pacote
 * correcao-ci-agenda. A API usada pelos restantes ficheiros de
 * teste ({@code norm/fullCatalog/portOf/fullPort/profile/
 * RecordingReminders/TENANT_A/B/C/RESP}) mantem-se.</p>
 */
final class AgendaFixtures {

    static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    // Correcao D3: um tenant DISTINTO por natureza no teste GES-05
    // (antes, ente e concessionaria reusavam o mesmo TENANT_B).
    static final UUID TENANT_C = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID RESP = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private AgendaFixtures() { }

    /**
     * Entrada LOCAL do catalogo de teste — subconjunto exercido da
     * {@code NormEntry} real da P3.15 (13 componentes): ficam os 4
     * campos que a agenda le ({@code catalogKey}, {@code title},
     * {@code status} gravado, {@code effectiveFrom} DA NORMA,
     * anulavel como na V9_8/P1.34). Os restantes componentes da classe
     * real (standardClass, verificationSeal, area, agency, module,
     * officialSourceUrl, scopeSummary, effectiveTo, editions) nao sao
     * exercidos por nenhum teste da agenda: a {@code query} real com o
     * filtro todo nulo nao filtra por eles e o {@code statusAt} real
     * nao os le (a regra de transicao usa SO o status gravado e a data
     * de efeito DA NORMA — ver RELATORIO).
     */
    record NormFixtureEntry(String catalogKey, String title, String status, LocalDate effectiveFrom) {
        NormFixtureEntry {
            Objects.requireNonNull(catalogKey, "catalogKey da norma e obrigatoria");
            Objects.requireNonNull(title, "titulo da norma e obrigatorio");
            Objects.requireNonNull(status, "situacao da norma e obrigatoria");
            // effectiveFrom ANULAVEL de proposito, como na classe real:
            // norma em vigencia futura sem data permanece VIGENCIA_FUTURA.
        }
    }

    /**
     * Catalogo LOCAL de teste — equivalente exercido da
     * {@code RegulatoryLibrary} real da P3.15.
     *
     * <p>{@link #query(LocalDate)} replica a chamada UNICA que a agenda
     * faz a classe real: {@code query(new NormFilter(null, null, null,
     * null, null), referencia)} — com os cinco eixos do filtro nulos,
     * nenhum predicado da query real elimina entrada alguma (a
     * referencia so afectaria o eixo {@code status}, que e nulo), logo
     * o resultado e o catalogo inteiro, na ordem de construcao. Filtros
     * nao nulos NAO sao replicados: nenhum teste da agenda os exerce
     * (limite declarado no RELATORIO).</p>
     *
     * <p>{@link #statusAt(NormFixtureEntry, LocalDate)} reproduz, linha
     * a linha, a regra UNICA de transicao da P1.34 tal como a classe
     * real a implementa: norma gravada {@code VIGENCIA_FUTURA} com
     * {@code effectiveFrom} nao nulo e {@code <= data} le-se
     * {@code VIGENTE}; sem data, permanece {@code VIGENCIA_FUTURA};
     * as demais situacoes gravadas nao mudam; {@code effectiveTo} nao
     * participa.</p>
     */
    static final class NormFixtureCatalog {

        private final List<NormFixtureEntry> catalog;

        NormFixtureCatalog(List<NormFixtureEntry> catalog) {
            if (catalog == null) {
                throw new IllegalArgumentException("Catalogo de normas e obrigatorio");
            }
            this.catalog = List.copyOf(catalog);
        }

        /** Replica de {@code query(NormFilter todo nulo, reference)} da classe real: devolve tudo, na ordem. */
        List<NormFixtureEntry> query(LocalDate reference) {
            Objects.requireNonNull(reference, "referencia e obrigatoria");
            return catalog;
        }

        /** Regra de transicao da P1.34, identica a {@code RegulatoryLibrary.statusAt} real. */
        static String statusAt(NormFixtureEntry norm, LocalDate date) {
            Objects.requireNonNull(norm, "norma e obrigatoria");
            Objects.requireNonNull(date, "data e obrigatoria");
            if (!"VIGENCIA_FUTURA".equals(norm.status())) {
                return norm.status();
            }
            if (norm.effectiveFrom() != null && !norm.effectiveFrom().isAfter(date)) {
                return "VIGENTE";
            }
            return "VIGENCIA_FUTURA";
        }
    }

    static NormFixtureEntry norm(String key, String title, String status, LocalDate effectiveFrom) {
        return new NormFixtureEntry(key, title, status, effectiveFrom);
    }

    /** Catalogo completo: uma norma por token do aceite, todas VIGENTE. */
    static List<NormFixtureEntry> fullCatalog() {
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

    /**
     * Adapta o catalogo LOCAL a porta da agenda — mesma projecao da
     * versao original sobre a classe real: consulta na data fixa
     * 2026-01-01 e mapeia catalogKey/title/status GRAVADO/effectiveFrom
     * para {@link AgendaPorts.NormRuleView} (o status projectado e o
     * gravado, nao o derivado — exactamente como na versao original).
     */
    static AgendaPorts.NormCatalogPort portOf(NormFixtureCatalog library) {
        return () -> library.query(LocalDate.of(2026, 1, 1)).stream()
                .map(n -> new AgendaPorts.NormRuleView(n.catalogKey(), n.title(), n.status(), n.effectiveFrom()))
                .toList();
    }

    static AgendaPorts.NormCatalogPort fullPort() {
        return portOf(new NormFixtureCatalog(fullCatalog()));
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
