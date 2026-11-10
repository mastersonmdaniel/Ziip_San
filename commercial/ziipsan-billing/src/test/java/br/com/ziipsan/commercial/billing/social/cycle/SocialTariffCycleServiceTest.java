package br.com.ziipsan.commercial.billing.social.cycle;

import br.com.ziipsan.commercial.billing.social.SocialBenefitConference;
import br.com.ziipsan.commercial.billing.social.SocialProgramListPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static br.com.ziipsan.commercial.billing.social.cycle.SocialCycleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * II-3/E2-E5 integrados - a corrida completa do ciclo, a loja
 * minimizada (LGPD) e a idempotencia de inscritos entre corridas.
 */
class SocialTariffCycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-11T12:00:00Z");

    private static String listCsv() {
        return csv(
                row(F1, P1, "HOLDER", "CADUNICO", "2025-06-01", "2026-01-01", "", "MUN-A"),
                row(F1, P2, "MEMBER", "CADUNICO", "2025-06-01", "2026-01-01", "", "MUN-A"),
                row(F2, P9, "HOLDER", "BPC", "", "2026-01-01", "", "MUN-A"),
                row(F4, P7, "HOLDER", "CADUNICO", "2024-12-10", "2026-01-01", "", ""));
    }

    private static SocialBenefitConference.ListMetadata list(SocialProgramListPolicy.ListSource source) {
        return new SocialBenefitConference.ListMetadata(source, "Lei 14.898/2024", "1", LocalDate.of(2026, 12, 1));
    }

    private SocialTariffCycleService service(InMemorySocialCycleStore store) {
        return new SocialTariffCycleService(new DeclaredCsvSocialListImporter(), store);
    }

    private SocialTariffCycleService.CycleReport run(InMemorySocialCycleStore store) {
        return service(store).runCycle(listCsv(), DeclaredSocialListFormat.proofFormat(),
                SocialCrossingEngineTest.units(), list(SocialProgramListPolicy.ListSource.REGULATOR_LIST),
                SocialProgramListPolicy.LegalNature.PRIVATE, REF,
                SocialTariffCycleParameters.proofParameters(), NOW, "cycle-test");
    }

    @Test
    @DisplayName("corrida completa: contagens e conjuntos exactos de ponta a ponta")
    void fullCycleExactReport() {
        InMemorySocialCycleStore store = new InMemorySocialCycleStore();
        SocialTariffCycleService.CycleReport report = run(store);
        assertThat(report.importedRows()).isEqualTo(4);
        assertThat(report.duplicatesRemoved()).isEqualTo(0);
        assertThat(report.importRejections()).isEmpty();
        assertThat(report.crossing().matched())
                .extracting(SocialCrossingEngine.CrossedFamily::familyCodeHash).containsExactly(F1);
        assertThat(report.crossing().unmatched())
                .extracting(SocialCrossingEngine.UnmatchedFamily::familyCodeHash).containsExactly(F2);
        assertThat(report.crossing().ineligible())
                .extracting(SocialCrossingEngine.IneligibleFamily::familyCodeHash).containsExactly(F4);
        assertThat(report.grantsCreated()).singleElement()
                .extracting(SocialGrantLifecycle.Grant::familyCodeHash).isEqualTo(F1);
        assertThat(report.alreadyEnrolled()).isEqualTo(0);
        assertThat(report.grantRefusals()).isEmpty();
        // Busca activa = elegiveis - inscritos: fica so F2 (sem unidade).
        assertThat(report.activeSearch())
                .extracting(SocialActiveSearch.ActiveSearchEntry::familyCodeHash).containsExactly(F2);
        // Loja coerente com o relatorio.
        assertThat(store.enrolledFamilyHashes()).containsExactly(F1);
        assertThat(store.activeSearch()).isEqualTo(report.activeSearch());
        assertThat(store.crossingSummaries())
                .extracting(s -> s.familyCodeHash() + ":" + s.outcome())
                .containsExactlyInAnyOrder(F1 + ":MATCHED", F2 + ":UNMATCHED", F4 + ":INELIGIBLE");
    }

    @Test
    @DisplayName("segunda corrida nao duplica concessao: ja inscrita conta a parte")
    void secondRunDoesNotGrantTwice() {
        InMemorySocialCycleStore store = new InMemorySocialCycleStore();
        run(store);
        SocialTariffCycleService.CycleReport second = run(store);
        assertThat(second.grantsCreated()).isEmpty();
        assertThat(second.alreadyEnrolled()).isEqualTo(1);
        assertThat(second.activeSearch())
                .extracting(SocialActiveSearch.ActiveSearchEntry::familyCodeHash).containsExactly(F2);
        assertThat(store.enrolledFamilyHashes()).containsExactly(F1);
    }

    @Test
    @DisplayName("corrida antes da vigencia legal: zero concessoes, casadas elegiveis ficam na busca activa")
    void runBeforeLegalRuleLeavesMatchedInActiveSearch() {
        InMemorySocialCycleStore store = new InMemorySocialCycleStore();
        SocialTariffCycleService.CycleReport report = service(store).runCycle(listCsv(),
                DeclaredSocialListFormat.proofFormat(), SocialCrossingEngineTest.units(),
                list(SocialProgramListPolicy.ListSource.REGULATOR_LIST),
                SocialProgramListPolicy.LegalNature.PRIVATE, REF_BEFORE_LEGAL,
                SocialTariffCycleParameters.proofParameters(), NOW, "cycle-test");
        assertThat(report.grantsCreated()).isEmpty();
        // Na vespera, F4 ainda e elegivel: o CadUnico de 2024-12-10 completa
        // exactamente 24 meses em 2026-12-10 (a fronteira da P1.5 anda um
        // dia) - por isso F4 tambem casa, tambem recusa pela data legal e
        // tambem fica na busca activa. Conjuntos exactos nessa data:
        assertThat(report.crossing().matched())
                .extracting(SocialCrossingEngine.CrossedFamily::familyCodeHash)
                .containsExactlyInAnyOrder(F1, F4);
        assertThat(report.crossing().ineligible()).isEmpty();
        assertThat(report.grantRefusals())
                .extracting(r -> r.familyCodeHash() + ":" + r.reason())
                .containsExactlyInAnyOrder(F1 + ":LEGAL_RULE_NOT_YET_APPLICABLE",
                        F4 + ":LEGAL_RULE_NOT_YET_APPLICABLE");
        assertThat(report.activeSearch())
                .extracting(SocialActiveSearch.ActiveSearchEntry::familyCodeHash)
                .containsExactlyInAnyOrder(F1, F2, F4);
    }

    @Test
    @DisplayName("trava PLT-36 recusa a corrida inteira antes de importar")
    void listSourceLockRefusesWholeRun() {
        InMemorySocialCycleStore store = new InMemorySocialCycleStore();
        assertThatThrownBy(() -> service(store).runCycle(listCsv(), DeclaredSocialListFormat.proofFormat(),
                SocialCrossingEngineTest.units(), list(SocialProgramListPolicy.ListSource.CONECTA_API),
                SocialProgramListPolicy.LegalNature.PRIVATE, REF,
                SocialTariffCycleParameters.proofParameters(), NOW, "cycle-test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decreto 11.016/2022");
        assertThat(store.crossingSummaries()).isEmpty();
    }

    @Test
    @DisplayName("LGPD: nenhum tipo do ciclo tem campo de documento, nome ou renda em claro")
    void noClearPersonalDataFieldsAnywhere() {
        List<Class<?>> types = List.of(
                SocialListImportPort.ImportedBeneficiaryRow.class,
                SocialCrossingEngine.CrossedFamily.class,
                SocialCrossingEngine.UnmatchedFamily.class,
                SocialGrantLifecycle.Grant.class,
                SocialGrantLifecycle.TrailEntry.class,
                SocialCycleStore.FamilyCrossingSummary.class,
                SocialActiveSearch.ActiveSearchEntry.class);
        for (Class<?> type : types) {
            Set<String> components = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName).collect(Collectors.toSet());
            // O invariante LGPD exacto: documento so pode existir como
            // indice cego (nome terminado em Hash); nome/renda/documento
            // em claro nao existem em campo nenhum.
            assertThat(components).as(type.getSimpleName())
                    .noneMatch(n -> n.toLowerCase().contains("cpf") || n.toLowerCase().contains("income")
                            || n.toLowerCase().contains("renda") || n.toLowerCase().contains("nome")
                            || n.equals("name")
                            || (n.toLowerCase().contains("document") && !n.endsWith("Hash")));
        }
    }

    @Test
    @DisplayName("LGPD: resumo persistido nao tem indice cego de pessoa nem a trilha tem indice cego de familia")
    void storeAndTrailCarryOnlyKeysAndOutcomes() {
        Set<String> summaryComponents = Arrays.stream(SocialCycleStore.FamilyCrossingSummary.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        // personCount (uma contagem) e legitimo; indice cego de pessoa, nao.
        assertThat(summaryComponents).noneMatch(n -> n.toLowerCase().contains("persondocument"));
        Set<String> trailComponents = Arrays.stream(SocialGrantLifecycle.TrailEntry.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(trailComponents).noneMatch(n -> n.contains("family") || n.contains("hash"));
    }
}
