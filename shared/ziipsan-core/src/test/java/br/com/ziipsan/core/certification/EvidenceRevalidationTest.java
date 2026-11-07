package br.com.ziipsan.core.certification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-76: lista de evidencias a revalidar por tenant numa nova edicao.
 * Tenants, clausulas e evidencias sao sinteticos minimos.
 */
class EvidenceRevalidationTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID TENANT_C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static ClauseCuration.ClauseEntry entry(String number, String title, String summary, int order) {
        return new ClauseCuration.ClauseEntry(number, title, summary, order);
    }

    private static ClauseMapRevision.ClauseMapDiff diff() {
        ClauseCuration.EditionClauseMap before = new ClauseCuration.EditionClauseMap(
                "SYN-EXEMPLO-1", "1", List.of(
                        entry("1.1", "Titulo ficticio um", "Resumo ficticio um.", 1),
                        entry("2.1", "Titulo ficticio dois", "Resumo ficticio dois.", 2),
                        entry("3.1", "Titulo ficticio tres", "Resumo ficticio tres.", 3)));
        ClauseCuration.EditionClauseMap after = new ClauseCuration.EditionClauseMap(
                "SYN-EXEMPLO-1", "2", List.of(
                        entry("1.1", "Titulo ficticio um", "Resumo ficticio um.", 1),
                        entry("2.1", "Titulo ficticio dois revisto", "Resumo ficticio dois.", 2),
                        entry("5.1", "Titulo ficticio cinco", "Resumo ficticio cinco.", 3)));
        return ClauseMapRevision.compare(before, after);
    }

    private static EvidenceRevalidation.EvidenceRef evidence() {
        return new EvidenceRevalidation.EvidenceRef(
                CertificationService.EvidenceKind.DOCUMENT, UUID.randomUUID(), "DOC-SINTETICO-1");
    }

    @Test
    @DisplayName("cláusula alterada e removida geram revalidação das evidências vinculadas")
    void changedAndRemovedClausesProduceRevalidationItems() {
        EvidenceRevalidation.EvidenceRef evidence = evidence();
        List<EvidenceRevalidation.TenantClauseEvidence> usages = List.of(
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_A, "SYN-EXEMPLO-1", "1",
                        "2.1", UUID.randomUUID(), List.of(evidence)),
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_A, "SYN-EXEMPLO-1", "1",
                        "3.1", UUID.randomUUID(), List.of(evidence)),
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_A, "SYN-EXEMPLO-1", "1",
                        "1.1", UUID.randomUUID(), List.of(evidence)));

        EvidenceRevalidation.RevalidationPlan plan = EvidenceRevalidation.plan(diff(), usages);

        assertThat(plan.byReason(EvidenceRevalidation.Reason.CLAUSE_CHANGED))
                .extracting(EvidenceRevalidation.RevalidationItem::clauseNumber)
                .containsExactly("2.1");
        assertThat(plan.byReason(EvidenceRevalidation.Reason.CLAUSE_CHANGED).get(0).evidences())
                .containsExactly(evidence);
        assertThat(plan.byReason(EvidenceRevalidation.Reason.CLAUSE_REMOVED))
                .extracting(EvidenceRevalidation.RevalidationItem::clauseNumber)
                .containsExactly("3.1");
        assertThat(plan.items()).noneMatch(i -> i.clauseNumber().equals("1.1")
                && i.reason() != EvidenceRevalidation.Reason.CLAUSE_ADDED);
    }

    @Test
    @DisplayName("cláusula incluída gera lacuna vazia, uma por tenant que usa a edição anterior")
    void addedClauseProducesGapPerTenantOnOldEdition() {
        List<EvidenceRevalidation.TenantClauseEvidence> usages = List.of(
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_A, "SYN-EXEMPLO-1", "1",
                        "1.1", UUID.randomUUID(), List.of(evidence())),
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_B, "SYN-EXEMPLO-1", "1",
                        "1.1", UUID.randomUUID(), List.of(evidence())));

        EvidenceRevalidation.RevalidationPlan plan = EvidenceRevalidation.plan(diff(), usages);

        List<EvidenceRevalidation.RevalidationItem> gaps =
                plan.byReason(EvidenceRevalidation.Reason.CLAUSE_ADDED);
        assertThat(gaps).extracting(EvidenceRevalidation.RevalidationItem::tenantId)
                .containsExactly(TENANT_A, TENANT_B);
        assertThat(gaps).allMatch(i -> i.clauseNumber().equals("5.1") && i.evidences().isEmpty());
        assertThat(plan.tenantIds()).containsExactly(TENANT_A, TENANT_B);
    }

    @Test
    @DisplayName("tenant noutra edição ou com outra norma não entra na lista")
    void otherEditionAndOtherStandardAreExcluded() {
        List<EvidenceRevalidation.TenantClauseEvidence> usages = List.of(
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_C, "SYN-EXEMPLO-1", "0",
                        "2.1", UUID.randomUUID(), List.of(evidence())),
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_C, "SYN-EXEMPLO-2", "1",
                        "2.1", UUID.randomUUID(), List.of(evidence())));

        EvidenceRevalidation.RevalidationPlan plan = EvidenceRevalidation.plan(diff(), usages);

        assertThat(plan.items()).isEmpty();
        assertThat(plan.tenantIds()).isEmpty();
    }

    @Test
    @DisplayName("o item carrega o clauseMapId da linha e a lacuna nasce sem clauseMapId")
    void itemCarriesClauseMapIdAndGapHasNone() {
        UUID clauseMapId = UUID.randomUUID();
        List<EvidenceRevalidation.TenantClauseEvidence> usages = List.of(
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_A, "SYN-EXEMPLO-1", "1",
                        "2.1", clauseMapId, List.of(evidence())));

        EvidenceRevalidation.RevalidationPlan plan = EvidenceRevalidation.plan(diff(), usages);

        assertThat(plan.byReason(EvidenceRevalidation.Reason.CLAUSE_CHANGED))
                .extracting(EvidenceRevalidation.RevalidationItem::clauseMapId)
                .containsExactly(clauseMapId);
        assertThat(plan.byReason(EvidenceRevalidation.Reason.CLAUSE_ADDED))
                .allMatch(i -> i.clauseMapId() == null);
    }

    @Test
    @DisplayName("vínculo desativado (active=false, V21_2) não entra nas evidências do item")
    void inactiveEvidenceLinkIsFilteredOut() {
        EvidenceRevalidation.EvidenceRef active = evidence();
        EvidenceRevalidation.EvidenceRef inactive = new EvidenceRevalidation.EvidenceRef(
                CertificationService.EvidenceKind.DOCUMENT, UUID.randomUUID(), "DOC-SINTETICO-2", false);
        List<EvidenceRevalidation.TenantClauseEvidence> usages = List.of(
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_A, "SYN-EXEMPLO-1", "1",
                        "2.1", UUID.randomUUID(), List.of(active, inactive)));

        EvidenceRevalidation.RevalidationPlan plan = EvidenceRevalidation.plan(diff(), usages);

        assertThat(plan.byReason(EvidenceRevalidation.Reason.CLAUSE_CHANGED).get(0).evidences())
                .containsExactly(active);
    }

    @Test
    @DisplayName("item de revalidação com lista de evidências nula é recusado com erro claro, não NPE")
    void revalidationItemRefusesNullEvidences() {
        assertThatThrownBy(() -> new EvidenceRevalidation.RevalidationItem(TENANT_A,
                "SYN-EXEMPLO-1", "1", "2", "5.1", null,
                EvidenceRevalidation.Reason.CLAUSE_ADDED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem mudança entre edições, a lista sai vazia")
    void noChangeProducesEmptyPlan() {
        ClauseCuration.EditionClauseMap same = new ClauseCuration.EditionClauseMap(
                "SYN-EXEMPLO-1", "1", List.of(entry("1.1", "Titulo ficticio um", "Resumo ficticio um.", 1)));
        ClauseMapRevision.ClauseMapDiff noDiff = ClauseMapRevision.compare(same, same);
        EvidenceRevalidation.RevalidationPlan plan = EvidenceRevalidation.plan(noDiff, List.of(
                new EvidenceRevalidation.TenantClauseEvidence(TENANT_A, "SYN-EXEMPLO-1", "1",
                        "1.1", UUID.randomUUID(), List.of(evidence()))));
        assertThat(plan.items()).isEmpty();
    }
}
