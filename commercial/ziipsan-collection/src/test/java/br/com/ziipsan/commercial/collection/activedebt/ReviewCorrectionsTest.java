package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Correcoes da revisao independente da ED-31 (REVISAO-ED-31.md, achados
 * D1–D6). Os cenarios D1/D2/D3/D4/D5 reproduzem as provas adversariais do
 * revisor (revisao-provas/Adv.java) contra a API corrigida.
 */
class ReviewCorrectionsTest {

    private static ActiveDebtEnrollment.DebtItem item(String amount, LocalDate due, LocalDate ack) {
        return new ActiveDebtEnrollment.DebtItem(UUID.randomUUID(), due, new BigDecimal(amount), ack);
    }

    private static DunningPorts.LegalOpinionPort favorable() {
        return (t, s) -> Optional.of(new DunningPorts.LegalOpinionPort.Opinion("Procuradoria", Instant.now(), "PARECER-PD10-TESTE"));
    }

    /**
     * Inscricao MISTA do cenario do revisor: criada em 09/03/2026 com
     * A (venc. 10/03/2016, sem reconhecimento — prescreve em 10/03/2026)
     * + B (venc. 10/01/2025, reconhecimento 01/06/2025), total 1.200,00.
     * Em 09/03/2026 nenhum dos dois esta prescrito: a inscricao e legitima;
     * o furo estava nos chamadores POSTERIORES (ajuizamento/protesto).
     */
    private ActiveDebtEnrollment.Enrollment mixedEnrollment() {
        var svc = new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 3, 9)),
                new ActiveDebtEnrollment.Book(), Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        var a = item("600.00", LocalDate.of(2016, 3, 10), null);
        var b = item("600.00", LocalDate.of(2025, 1, 10), LocalDate.of(2025, 6, 1));
        var enr = svc.enrollIndividual(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(a, b), false, Set.of());
        assertThat(enr.total()).isEqualByComparingTo("1200.00");
        return enr;
    }

    // ---------- D1: prescricao ITEM A ITEM no ajuizamento e no protesto ----------

    @Test @DisplayName("D1: inscricao mista com item prescrito NAO ajuiza (reconhecimento de B nao salva A)")
    void mixedEnrollmentFilingBlockedPerItem() {
        var enr = mixedEnrollment();
        var filing = new FilingRecord(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                ActiveDebtTestSupport.params(Map.of()),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        assertThatThrownBy(() -> filing.register(enr, "0001234-56.2026.8.24.0000", "1a Vara", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FILING").hasMessageContaining("PRESCRIBED");
        assertThatThrownBy(() -> filing.assertEligible(enr, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PRESCRIBED");
    }

    @Test @DisplayName("D1: inscricao mista com item prescrito NAO protesta — nem com analise juridica registrada")
    void mixedEnrollmentProtestBlockedPerItem() {
        var enr = mixedEnrollment();
        var protest = new ProtestControl(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                ActiveDebtTestSupport.params(Map.of("da.protest_enabled", "true")), favorable(),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        assertThatThrownBy(() -> protest.authorize(enr, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROTEST").hasMessageContaining("PRESCRIBED");
        // Item PRESCRIBED nao e "curado" por analise juridica: bloqueia tambem com true.
        assertThatThrownBy(() -> protest.authorize(enr, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PRESCRIBED");
    }

    @Test @DisplayName("D1: item UNDETERMINED na inscricao tambem bloqueia o ajuizamento")
    void filingBlockedWhenAnyItemUndetermined() {
        // Inscricao montada direto no livro (sem passar pelo portao de inscricao):
        // a pretensao TARIFF_EXECUTION nao tem prazo confirmado (Temas 251-254).
        var book = new ActiveDebtEnrollment.Book();
        var enr = book.append(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(item("1500.00", LocalDate.of(2020, 1, 1), null)), LocalDate.of(2026, 11, 8));
        var filing = new FilingRecord(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                ActiveDebtTestSupport.params(Map.of()),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.TARIFF_EXECUTION);
        assertThatThrownBy(() -> filing.register(enr, "0001234-56.2026.8.24.0000", "1a Vara", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UNDETERMINED");
    }

    // ---------- D2: analise juridica do protesto como entrada EXPLICITA ----------

    @Test @DisplayName("D2: linha Decreto 20.910 (exige analise) sem analise registrada => protesto UNDETERMINED; com analise explicita, autoriza")
    void protestUndeterminedBlocksWithoutExplicitLegalAnalysis() {
        // Inscricao criada COM analise registrada no ato (cenario B0 do revisor):
        // venc. 10/01/2024 + 5 anos = termo 10/01/2029, nao alcancado em 08/11/2026.
        var svc = new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                new ActiveDebtEnrollment.Book(),
                Prescription.ProviderNature.PUBLIC_LAW, Prescription.ClaimType.AGAINST_PUBLIC_ENTITY);
        var enr = svc.enrollIndividual(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(item("1500.00", LocalDate.of(2024, 1, 10), null)), true, Set.of());
        var protest = new ProtestControl(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                ActiveDebtTestSupport.params(Map.of("da.protest_enabled", "true")), favorable(),
                Prescription.ProviderNature.PUBLIC_LAW, Prescription.ClaimType.AGAINST_PUBLIC_ENTITY);
        // Sem analise juridica de prescricao registrada NA CHAMADA: UNDETERMINED bloqueia
        // (o parecer PD-10 consultado e de OUTRO assunto e nao substitui a analise).
        assertThatThrownBy(() -> protest.authorize(enr, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROTEST").hasMessageContaining("UNDETERMINED");
        // Controle: o ajuizamento com false tambem bloqueia — protesto nao e mais fraco.
        var filing = new FilingRecord(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                ActiveDebtTestSupport.params(Map.of()),
                Prescription.ProviderNature.PUBLIC_LAW, Prescription.ClaimType.AGAINST_PUBLIC_ENTITY);
        assertThatThrownBy(() -> filing.register(enr, "0009999-99.2026.8.24.0000", "Vara", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UNDETERMINED");
        // Com o registro EXPLICITO da analise, a linha se resolve (termo 2029) e o protesto autoriza.
        var auth = protest.authorize(enr, true);
        assertThat(auth.opinionReference()).isEqualTo("PARECER-PD10-TESTE");
    }

    // ---------- D3: inscricao periodica respeita o mes congelado ----------

    @Test @DisplayName("D3: inscricao PERIODICA em mes congelado e recusada como a individual — e nada entra no livro")
    void periodicEnrollmentInFrozenMonthIsRefused() {
        var book = new ActiveDebtEnrollment.Book();
        var svc = new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)), book,
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        var req = new ActiveDebtEnrollment.EnrollmentRequest(UUID.randomUUID(), UUID.randomUUID(),
                List.of(item("500.00", LocalDate.of(2025, 1, 10), null)));
        assertThatThrownBy(() -> svc.enrollPeriodic(UUID.randomUUID(), List.of(req), false, Set.of(YearMonth.of(2026, 11))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("congelado");
        assertThat(book.entries()).isEmpty();
        // Controle: sem o mes congelado, o mesmo lote inscreve.
        var r = svc.enrollPeriodic(UUID.randomUUID(), List.of(req), false, Set.of());
        assertThat(r.enrolled()).hasSize(1);
        assertThat(r.blocked()).isEmpty();
    }

    // ---------- D4: parcelamento com portao de prescricao ----------

    @Test @DisplayName("D4: divida que prescreveu depois da inscricao NAO parcela")
    void installmentOfDebtPrescribedAfterEnrollmentIsBlocked() {
        var svc = new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 3, 9)),
                new ActiveDebtEnrollment.Book(), Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        var enr = svc.enrollIndividual(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(item("1000.00", LocalDate.of(2016, 3, 10), null)), false, Set.of());
        // Prescreveu em 10/03/2026; plano pedido em 08/11/2026 (cenario D do revisor):
        assertThatThrownBy(() -> ActiveDebtInstallment.plan(enr, 3, LocalDate.of(2027, 1, 10),
                        ActiveDebtTestSupport.params(Map.of()),
                        ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                        Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INSTALLMENT").hasMessageContaining("PRESCRIBED");
    }

    // ---------- D5: Enrollment valida total == soma(itens) ----------

    @Test @DisplayName("D5: Enrollment com total divergente da soma dos itens e rejeitado na construcao (cenario E do revisor)")
    void enrollmentTotalMustEqualSumOfItems() {
        var forgedItem = item("10.00", LocalDate.of(2025, 1, 10), null);
        assertThatThrownBy(() -> new ActiveDebtEnrollment.Enrollment(UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), UUID.randomUUID(), 99L, List.of(forgedItem),
                        new BigDecimal("999999.00"), LocalDate.of(2026, 11, 8), ActiveDebtEnrollment.Status.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("soma dos itens");
        // Total correto — inclusive com escala diferente (10.0 == 10.00 por valor) — constroi.
        var ok = new ActiveDebtEnrollment.Enrollment(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1L, List.of(forgedItem),
                new BigDecimal("10.0"), LocalDate.of(2026, 11, 8), ActiveDebtEnrollment.Status.ACTIVE);
        assertThat(ok.total()).isEqualByComparingTo("10.00");
    }

    // ---------- D6a: CDA e handoff reavaliam a prescricao na emissao/remessa ----------

    private ActiveDebtEnrollment.Enrollment enrolledOnTheEdge() {
        var svc = new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 3, 9)),
                new ActiveDebtEnrollment.Book(), Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        return svc.enrollIndividual(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(item("1500.00", LocalDate.of(2016, 3, 10), null)), false, Set.of());
    }

    @Test @DisplayName("D6a: CDA NAO e emitida para inscricao que prescreveu depois de inscrita (e o motor nem e chamado)")
    void cdaIssueReevaluatesPrescription() {
        var enr = enrolledOnTheEdge(); // prescreve em 10/03/2026
        var fake = new CdaAndHandoffTest.FakeTemplate();
        var cda = new CdaDocument(fake, ActiveDebtTestSupport.params(Map.of()),
                ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        assertThatThrownBy(() -> cda.issue(enr, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CDA_ISSUANCE").hasMessageContaining("PRESCRIBED");
        assertThat(fake.calls.get()).isZero();
    }

    @Test @DisplayName("D6a: remessa a procuradoria NAO inclui inscricao que prescreveu depois de inscrita")
    void handoffReevaluatesPrescription() {
        var enr = enrolledOnTheEdge();
        var fake = new CdaAndHandoffTest.FakeTemplate();
        var h = new ProsecutionHandoff(fake, ActiveDebtTestSupport.params(Map.of()),
                ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        assertThatThrownBy(() -> h.generate(enr.tenantId(), List.of(enr), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROSECUTION_HANDOFF").hasMessageContaining("PRESCRIBED");
        assertThat(fake.calls.get()).isZero();
    }

    // ---------- D6b: register marca a inscricao como FILED no livro ----------

    @Test @DisplayName("D6b: com o livro fornecido, register marca a inscricao como FILED")
    void filingRegisterMarksEnrollmentFiledInBook() {
        var book = new ActiveDebtEnrollment.Book();
        var svc = new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)), book,
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        var enr = svc.enrollIndividual(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(item("1500.00", LocalDate.of(2025, 1, 10), null)), false, Set.of());
        assertThat(book.find(enr.id()).status()).isEqualTo(ActiveDebtEnrollment.Status.ACTIVE);
        var filing = new FilingRecord(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                ActiveDebtTestSupport.params(Map.of()),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, book);
        filing.register(enr, "0001234-56.2026.8.24.0000", "1a Vara", false);
        assertThat(book.find(enr.id()).status()).isEqualTo(ActiveDebtEnrollment.Status.FILED);
    }

    // ---------- D6c: cobertura SAMAE 2.15 aprofundada ----------

    @Test @DisplayName("D6c: cobertura 2.15 tem 10 exigencias distintas, rastreaveis as classes/parametros reais do pacote")
    void samaeCoverageEntriesAreCompleteAndTraceable() {
        assertThat(Samae215DaCoverage.ED31_SLICE).hasSize(10);
        assertThat(Samae215DaCoverage.requirements()).doesNotHaveDuplicates();
        assertThat(Samae215DaCoverage.ED31_SLICE)
                .allSatisfy(e -> {
                    assertThat(e.requirement()).isNotBlank();
                    assertThat(e.feature()).isNotBlank();
                });
        var requirements = String.join(" | ", Samae215DaCoverage.requirements());
        assertThat(requirements).contains("POC-34").contains("POC-35").contains("POC-36")
                .contains("PD-10").contains("ADR-0028");
        var features = Samae215DaCoverage.ED31_SLICE.stream().map(Samae215DaCoverage.Entry::feature).toList();
        assertThat(String.join(" | ", features))
                .contains("ActiveDebtEnrollment").contains("CdaDocument").contains("ActiveDebtInstallment")
                .contains("ActiveDebtMonthlyClosing").contains("ProsecutionHandoff").contains("FilingRecord")
                .contains("PrescriptionGate").contains("ProtestControl")
                .contains("da.min_filing_amount").contains("da.protest_enabled");
    }
}
