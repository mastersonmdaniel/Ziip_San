package br.com.ziipsan.commercial.registry.rules;

import br.com.ziipsan.commercial.registry.rules.UnitConsistency.BlockNetwork;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency.Policy;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency.Relation;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency.Role;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency.Severity;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency.Status;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency.SubcategoryLine;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency.UnitSnapshot;
import br.com.ziipsan.commercial.registry.rules.UnitConsistency.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UnitConsistencyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID SUB_A = UUID.randomUUID();
    private static final UUID SUB_B = UUID.randomUUID();
    private static final Policy POLICY = new Policy(true, 50);

    private static Relation user(boolean showOnBill, boolean hasDoc) {
        return new Relation(UUID.randomUUID(), Role.USER, LocalDate.of(2025, 1, 1), null, showOnBill, hasDoc);
    }

    /** Unidade correta: 1 endereço, quadra com água e esgoto, 1 usuário com documento e nome na conta, 1 subcategoria. */
    private static UnitSnapshot valid() {
        return new UnitSnapshot(false, 1, new BlockNetwork(true, true),
                new Status("LIGADO", true), new Status("LIGADO", true),
                List.of(user(true, true)), List.of(new SubcategoryLine(SUB_A, 1)), TODAY);
    }

    private static UnitSnapshot with(UnitSnapshot u, boolean provisional, int addresses, BlockNetwork block,
                                     Status water, Status sewer, List<Relation> relations, List<SubcategoryLine> subs) {
        return new UnitSnapshot(provisional, addresses, block, water, sewer, relations, subs, u.today());
    }

    private static List<String> codes(List<Violation> v) {
        return v.stream().map(Violation::code).toList();
    }

    @Test
    @DisplayName("unidade correta não tem violação")
    void validUnit() {
        assertThat(UnitConsistency.check(valid(), POLICY)).isEmpty();
    }

    @Test
    @DisplayName("exatamente 1 endereço físico: 0 e 2 são erro")
    void exactlyOnePhysicalAddress() {
        UnitSnapshot u = valid();
        assertThat(codes(UnitConsistency.check(with(u, false, 0, u.block(), u.water(), u.sewer(), u.relations(), u.subcategories()), POLICY)))
                .containsExactly("UNIT_PHYSICAL_ADDRESS_COUNT");
        assertThat(codes(UnitConsistency.check(with(u, false, 2, u.block(), u.water(), u.sewer(), u.relations(), u.subcategories()), POLICY)))
                .containsExactly("UNIT_PHYSICAL_ADDRESS_COUNT");
    }

    @Test
    @DisplayName("situação de água que exige rede, em quadra sem rede de água: erro")
    void waterStatusRequiresNetwork() {
        UnitSnapshot u = valid();
        List<Violation> v = UnitConsistency.check(with(u, false, 1, new BlockNetwork(false, true),
                u.water(), u.sewer(), u.relations(), u.subcategories()), POLICY);
        assertThat(codes(v)).containsExactly("WATER_STATUS_REQUIRES_NETWORK");
    }

    @Test
    @DisplayName("situação de esgoto que exige rede, em quadra sem rede de esgoto: erro")
    void sewerStatusRequiresNetwork() {
        UnitSnapshot u = valid();
        List<Violation> v = UnitConsistency.check(with(u, false, 1, new BlockNetwork(true, false),
                u.water(), u.sewer(), u.relations(), u.subcategories()), POLICY);
        assertThat(codes(v)).containsExactly("SEWER_STATUS_REQUIRES_NETWORK");
    }

    @Test
    @DisplayName("situação que não exige rede (ex.: potencial) é aceita em quadra sem rede")
    void statusWithoutNetworkRequirement() {
        UnitSnapshot u = valid();
        List<Violation> v = UnitConsistency.check(with(u, false, 1, new BlockNetwork(false, false),
                new Status("POTENCIAL", false), new Status("POTENCIAL", false), u.relations(), u.subcategories()), POLICY);
        assertThat(v).isEmpty();
    }

    @Test
    @DisplayName("sem quadra é erro, exceto em unidade provisória")
    void blockRequiredUnlessProvisional() {
        UnitSnapshot u = valid();
        assertThat(codes(UnitConsistency.check(with(u, false, 1, null, u.water(), u.sewer(), u.relations(), u.subcategories()), POLICY)))
                .containsExactly("UNIT_BLOCK_REQUIRED");
        assertThat(UnitConsistency.check(with(u, true, 1, null, u.water(), u.sewer(), u.relations(), u.subcategories()), POLICY))
                .isEmpty();
    }

    @Test
    @DisplayName("sem usuário vigente é erro; vínculo encerrado não conta")
    void needsACurrentUser() {
        UnitSnapshot u = valid();
        Relation ended = new Relation(UUID.randomUUID(), Role.USER, LocalDate.of(2020, 1, 1), LocalDate.of(2024, 1, 1), true, true);
        Relation owner = new Relation(UUID.randomUUID(), Role.OWNER, LocalDate.of(2020, 1, 1), null, true, true);
        List<Violation> v = UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(),
                List.of(ended, owner), u.subcategories()), POLICY);
        assertThat(codes(v)).containsExactly("UNIT_NO_CURRENT_USER");
    }

    @Test
    @DisplayName("documento do usuário é obrigatório conforme a política do tenant")
    void userDocumentByPolicy() {
        UnitSnapshot u = valid();
        UnitSnapshot noDoc = with(u, false, 1, u.block(), u.water(), u.sewer(), List.of(user(true, false)), u.subcategories());
        assertThat(codes(UnitConsistency.check(noDoc, new Policy(true, 50)))).containsExactly("UNIT_USER_DOCUMENT_REQUIRED");
        assertThat(UnitConsistency.check(noDoc, new Policy(false, 50))).isEmpty();
    }

    @Test
    @DisplayName("exatamente 1 nome na conta: 0 e 2 são erro")
    void exactlyOneBillName() {
        UnitSnapshot u = valid();
        assertThat(codes(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(),
                List.of(user(false, true)), u.subcategories()), POLICY))).containsExactly("UNIT_BILL_NAME_COUNT");
        assertThat(codes(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(),
                List.of(user(true, true), user(true, true)), u.subcategories()), POLICY))).containsExactly("UNIT_BILL_NAME_COUNT");
    }

    @Test
    @DisplayName("o nome na conta pode ser o proprietário ou o responsável, e não só o usuário")
    void billNameMayBeAnyRole() {
        UnitSnapshot u = valid();
        Relation responsible = new Relation(UUID.randomUUID(), Role.RESPONSIBLE, LocalDate.of(2025, 1, 1), null, true, true);
        assertThat(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(),
                List.of(user(false, true), responsible), u.subcategories()), POLICY)).isEmpty();
    }

    @Test
    @DisplayName("mesmo cliente com o mesmo papel duas vezes é erro")
    void duplicateRelation() {
        UnitSnapshot u = valid();
        Relation a = new Relation(CUSTOMER, Role.USER, LocalDate.of(2025, 1, 1), null, true, true);
        Relation b = new Relation(CUSTOMER, Role.USER, LocalDate.of(2025, 2, 1), null, false, true);
        assertThat(codes(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(),
                List.of(a, b), u.subcategories()), POLICY))).contains("RELATION_DUPLICATE");
    }

    @Test
    @DisplayName("datas do vínculo: início futuro e fim anterior ao início são erro")
    void relationDates() {
        UnitSnapshot u = valid();
        Relation future = new Relation(UUID.randomUUID(), Role.OWNER, TODAY.plusDays(1), null, false, true);
        Relation inverted = new Relation(UUID.randomUUID(), Role.OWNER, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 4, 1), false, true);
        List<Relation> rel = new ArrayList<>(u.relations());
        rel.add(future);
        rel.add(inverted);
        List<String> c = codes(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(), rel, u.subcategories()), POLICY));
        assertThat(c).contains("RELATION_START_IN_FUTURE", "RELATION_END_BEFORE_START");
    }

    @Test
    @DisplayName("subcategorias: ao menos 1, economias ≥ 1, sem repetição")
    void subcategories() {
        UnitSnapshot u = valid();
        assertThat(codes(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(), u.relations(), List.of()), POLICY)))
                .containsExactly("UNIT_NO_SUBCATEGORY");
        assertThat(codes(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(), u.relations(),
                List.of(new SubcategoryLine(SUB_A, 0))), POLICY))).contains("SUBCATEGORY_ECONOMIES_INVALID");
        assertThat(codes(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(), u.relations(),
                List.of(new SubcategoryLine(SUB_A, 1), new SubcategoryLine(SUB_A, 2))), POLICY))).contains("SUBCATEGORY_DUPLICATE");
    }

    @Test
    @DisplayName("economias acima do limite do tenant é AVISO confirmável, não erro")
    void economiesOverLimitIsAWarning() {
        UnitSnapshot u = valid();
        List<Violation> v = UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(), u.relations(),
                List.of(new SubcategoryLine(SUB_A, 30), new SubcategoryLine(SUB_B, 21))), POLICY);

        assertThat(v).hasSize(1);
        assertThat(v.get(0).code()).isEqualTo("UNIT_ECONOMIES_OVER_LIMIT");
        assertThat(v.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(UnitConsistency.hasErrors(v)).as("aviso não bloqueia").isFalse();
        // exatamente no limite não avisa: 30 + 20 = 50
        assertThat(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(), u.relations(),
                List.of(new SubcategoryLine(SUB_A, 30), new SubcategoryLine(SUB_B, 20))), POLICY)).isEmpty();
        // limite 0 desliga o aviso
        assertThat(UnitConsistency.check(with(u, false, 1, u.block(), u.water(), u.sewer(), u.relations(),
                List.of(new SubcategoryLine(SUB_A, 999))), new Policy(true, 0))).isEmpty();
    }

    @Test
    @DisplayName("hasErrors distingue erro de aviso")
    void hasErrors() {
        assertThat(UnitConsistency.hasErrors(List.of())).isFalse();
        assertThat(UnitConsistency.hasErrors(List.of(new Violation("X", Severity.WARNING, "f", "m")))).isFalse();
        assertThat(UnitConsistency.hasErrors(List.of(new Violation("X", Severity.ERROR, "f", "m")))).isTrue();
    }
}
