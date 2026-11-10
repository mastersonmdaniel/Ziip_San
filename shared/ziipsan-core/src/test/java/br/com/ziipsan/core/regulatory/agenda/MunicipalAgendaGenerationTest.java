package br.com.ziipsan.core.regulatory.agenda;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MM-06: geracao municipal da agenda ED-98. Reusa as fixtures REAIS da
 * ED-98 (AgendaFixtures — catalogo real da P3.15) sem as alterar.
 * IBGEs de teste sinteticos de 7 digitos (9000001/9000002), marcados
 * como massa de teste — nenhum municipio real e afirmado.
 * Natureza AUTARQUIA (direito publico): gera ESFINGE (x6 bimestres) e
 * nao gera RREO/RGF (D2 da ED-98); SISAGUA anual x1.
 */
class MunicipalAgendaGenerationTest {

    static final String IBGE_A = "9000001";
    static final String IBGE_B = "9000002";

    private MunicipalAgendaGenerationService service() {
        return new MunicipalAgendaGenerationService(new ObligationCalendarService(
                AgendaFixtures.fullPort(), new AgendaFixtures.RecordingReminders()));
    }

    private TenantObligationProfile autarquia() {
        return AgendaFixtures.profile(AgendaFixtures.TENANT_A, TenantLegalNature.AUTARQUIA, false);
    }

    @Test void municipalKindsSetIsExactlyTheEvidencedTwo() {
        assertEquals(Set.of(ObligationKind.ESFINGE_BIMONTHLY, ObligationKind.SISAGUA),
                MunicipalObligationKinds.MUNICIPAL_KINDS);
        assertFalse(MunicipalObligationKinds.isMunicipal(ObligationKind.WATER_LOSS_BALANCE_NR15));
        assertFalse(MunicipalObligationKinds.isMunicipal(ObligationKind.RREO));
        assertFalse(MunicipalObligationKinds.isMunicipal(ObligationKind.EXTERNAL_INSPECTION_FOLLOWUP));
    }

    @Test void twoMunicipalitiesGenerateOneItemPerMunicipalityForMunicipalKinds() {
        var gen = service().generate(autarquia(), 2027, List.of(IBGE_A, IBGE_B));
        // SISAGUA: anual x2 municipios; ESFINGE: 6 bimestres x2 municipios.
        assertEquals(2, gen.itemsOf(ObligationKind.SISAGUA).size());
        assertEquals(12, gen.itemsOf(ObligationKind.ESFINGE_BIMONTHLY).size());
        assertEquals(Set.of(IBGE_A, IBGE_B), gen.itemsOf(ObligationKind.SISAGUA).stream()
                .map(MunicipalAgendaItem::municipalityIbge).collect(Collectors.toSet()));
        // Nenhum item municipal fica no escopo-empresa.
        assertTrue(gen.itemsOf(ObligationKind.SISAGUA).stream().noneMatch(MunicipalAgendaItem::isCompanyScope));
        assertTrue(gen.itemsOf(ObligationKind.ESFINGE_BIMONTHLY).stream().noneMatch(MunicipalAgendaItem::isCompanyScope));
        // Cada item municipal tem id proprio (persistivel em separado).
        assertEquals(2, gen.itemsOf(ObligationKind.SISAGUA).stream()
                .map(i -> i.item().id()).collect(Collectors.toSet()).size());
        // Periodos preservados da geracao vigente.
        assertEquals(Set.of("2027-ANNUAL"), gen.itemsOf(ObligationKind.SISAGUA).stream()
                .map(i -> i.item().periodRef()).collect(Collectors.toSet()));
    }

    @Test void companyKindsStayExactlyAsTodayWithMunicipalProfilesPresent() {
        var gen = service().generate(autarquia(), 2027, List.of(IBGE_A, IBGE_B));
        var base = new ObligationCalendarService(AgendaFixtures.fullPort(),
                new AgendaFixtures.RecordingReminders()).generate(autarquia(), 2027);
        for (ObligationKind kind : ObligationKind.values()) {
            if (MunicipalObligationKinds.isMunicipal(kind)) continue;
            long inBase = base.itemsOf(kind).size();
            long inScoped = gen.itemsOf(kind).size();
            assertEquals(inBase, inScoped, "kind de empresa nao muda de contagem: " + kind);
            assertTrue(gen.itemsOf(kind).stream().allMatch(MunicipalAgendaItem::isCompanyScope),
                    "kind de empresa fica com municipio NULL: " + kind);
        }
    }

    @Test void tenantWithoutMunicipalProfilesBehavesExactlyAsToday() {
        var base = new ObligationCalendarService(AgendaFixtures.fullPort(),
                new AgendaFixtures.RecordingReminders()).generate(autarquia(), 2027);
        for (List<String> none : List.of(List.<String>of())) {
            var gen = service().generate(autarquia(), 2027, none);
            assertEquals(base.items().size(), gen.allItems().size());
            assertTrue(gen.allItems().stream().allMatch(MunicipalAgendaItem::isCompanyScope));
            // Mesmos (kind, periodo) na mesma ordem que o gerador vigente.
            assertEquals(base.items().stream().map(i -> i.kind() + "|" + i.periodRef()).toList(),
                    gen.allItems().stream().map(i -> i.item().kind() + "|" + i.item().periodRef()).toList());
            assertEquals(base.unresolvedKinds(), gen.unresolvedKinds());
        }
        var genNull = service().generate(autarquia(), 2027, null);
        assertEquals(base.items().size(), genNull.allItems().size());
    }

    @Test void unfilteredQueryReturnsCompanyAndMunicipalCoexisting() {
        var gen = service().generate(autarquia(), 2027, List.of(IBGE_A, IBGE_B));
        // Sem filtro: todos os itens (empresa + municipais).
        assertEquals(gen.items().size(), gen.allItems().size());
        assertFalse(gen.companyItems().isEmpty());
        assertEquals(2, gen.itemsOfMunicipality(IBGE_A).stream()
                .filter(i -> MunicipalObligationKinds.isMunicipal(i.item().kind()))
                .map(i -> i.item().kind()).collect(Collectors.toSet()).size());
        // Recorte por municipio nao inclui itens de empresa nem do outro municipio.
        assertTrue(gen.itemsOfMunicipality(IBGE_A).stream()
                .allMatch(i -> IBGE_A.equals(i.municipalityIbge())));
        assertTrue(gen.itemsOfMunicipality(IBGE_B).stream()
                .noneMatch(i -> IBGE_A.equals(i.municipalityIbge())));
    }

    @Test void duplicateMunicipalitiesInInputAreDeduplicatedAndInvalidRejected() {
        var gen = service().generate(autarquia(), 2027, List.of(IBGE_B, IBGE_A, IBGE_B));
        assertEquals(2, gen.itemsOf(ObligationKind.SISAGUA).size());
        assertThrows(IllegalArgumentException.class,
                () -> service().generate(autarquia(), 2027, List.of("123")));
        assertThrows(IllegalArgumentException.class,
                () -> MunicipalAgendaItem.municipal(
                        gen.itemsOf(ObligationKind.SISAGUA).get(0).item(), "ABC"));
    }

    @Test void scopeUniquenessMirrorMatchesSqlSemantics() {
        var gen = service().generate(autarquia(), 2027, List.of(IBGE_A, IBGE_B));
        var sisagua = gen.itemsOf(ObligationKind.SISAGUA);
        // Mesmo kind/periodo em 2 municipios: chaves distintas (coexiste).
        assertEquals(2, sisagua.stream().map(MunicipalAgendaItem::uniquenessKey)
                .collect(Collectors.toSet()).size());
        // Duplicado municipal exato: recusado.
        assertThrows(IllegalStateException.class, () ->
                MunicipalAgendaGenerationService.validateNoDuplicateScope(
                        List.of(sisagua.get(0), sisagua.get(0))));
        // Duplicado de empresa (NULL x NULL, mesmo kind/periodo): recusado
        // (o COALESCE do SQL faz o NULL colidir consigo mesmo).
        var company = gen.companyItems().get(0);
        assertThrows(IllegalStateException.class, () ->
                MunicipalAgendaGenerationService.validateNoDuplicateScope(
                        List.of(company, company)));
        assertEquals("", company.scopeKey());
    }

    @Test void mm06SqlConsistency() throws Exception {
        var p = java.nio.file.Path.of("src/main/resources/db/pendente/MM-06__reg_obligation_items_municipality.sql");
        if (!java.nio.file.Files.exists(p)) p = java.nio.file.Path.of(System.getProperty("user.dir"),
            "entrega/shared/ziipsan-core/src/main/resources/db/pendente/MM-06__reg_obligation_items_municipality.sql");
        if (!java.nio.file.Files.exists(p)) p = java.nio.file.Path.of(System.getProperty("user.dir"),
            "multimunicipio/mm-06-agenda-ed98/entrega/shared/ziipsan-core/src/main/resources/db/pendente/MM-06__reg_obligation_items_municipality.sql");
        String s = java.nio.file.Files.readString(p);
        assertTrue(s.contains("ADD COLUMN IF NOT EXISTS municipality_ibge VARCHAR(7)"));
        assertTrue(s.contains("municipality_ibge ~ '^[0-9]{7}$'"));
        assertTrue(s.contains("COALESCE(municipality_ibge, '')"));
        assertTrue(s.contains("uq_reg_obligation_item_period"));
        assertTrue(s.contains("(tenant_id, kind, period_ref, COALESCE(municipality_ibge, ''))"));
        assertFalse(s.contains("DROP TABLE"));
        assertFalse(s.contains("DELETE FROM"));
    }
}
