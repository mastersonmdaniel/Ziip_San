package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Aceite E4/E5: seleccao por criterio explicito e whitelist do minimo de dados. */
class ThirdPartySelectionMinimalDataTest {

    private final LocalDate asOf = LocalDate.of(2026, 3, 1);
    private final ThirdPartyPorts.CandidateDebt eligible =
            ThirdPartyFixtures.candidate("11", "1000.00", LocalDate.of(2026, 1, 10)); // 50 dias de atraso
    private final ThirdPartyPorts.CandidateDebt tooRecent =
            ThirdPartyFixtures.candidate("12", "1000.00", LocalDate.of(2026, 2, 20)); // 9 dias
    private final ThirdPartyPorts.CandidateDebt tooSmall =
            ThirdPartyFixtures.candidate("13", "50.00", LocalDate.of(2026, 1, 10));
    private final ThirdPartyPorts.CandidateDebt tooBig =
            ThirdPartyFixtures.candidate("14", "9000.00", LocalDate.of(2026, 1, 10));

    @Test void selectionAppliesDeclaredCriteriaOnly() {
        var criteria = new AssignmentCriteria(30, new BigDecimal("100.00"), new BigDecimal("5000.00"), null, null);
        var s = ThirdPartyFixtures.service(List.of(eligible, tooRecent, tooSmall, tooBig), Set.of());
        var selected = s.selectCandidates(ThirdPartyFixtures.TENANT, criteria, asOf);
        assertEquals(List.of(eligible), selected);
    }

    @Test void selectionHonoursDeclaredExclusions() {
        var byDebt = new AssignmentCriteria(30, new BigDecimal("100.00"), null,
                Set.of(eligible.debtId()), null);
        var byUnit = new AssignmentCriteria(30, new BigDecimal("100.00"), null,
                null, Set.of(eligible.unitId()));
        var s = ThirdPartyFixtures.service(List.of(eligible), Set.of());
        assertTrue(s.selectCandidates(ThirdPartyFixtures.TENANT, byDebt, asOf).isEmpty());
        assertTrue(s.selectCandidates(ThirdPartyFixtures.TENANT, byUnit, asOf).isEmpty());
    }

    @Test void selectionBoundaryDaysAndAmountAreInclusive() {
        var exactlyThirtyDays = ThirdPartyFixtures.candidate("15", "100.00", LocalDate.of(2026, 1, 30));
        var s = ThirdPartyFixtures.service(List.of(exactlyThirtyDays), Set.of());
        assertEquals(1, s.selectCandidates(ThirdPartyFixtures.TENANT,
                ThirdPartyFixtures.criteria(), asOf).size(), "30 dias e 100.00 exactos entram (limites incluidos)");
    }

    @Test void criteriaWithoutDeclaredValuesAreRefused() {
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> new AssignmentCriteria(null, new BigDecimal("100.00"), null, null, null));
        assertEquals("CRITERIA_MIN_DAYS_REQUIRED", ex.code());
        var ex2 = assertThrows(ThirdPartyCollectionException.class,
                () -> new AssignmentCriteria(30, null, null, null, null));
        assertEquals("CRITERIA_MIN_AMOUNT_REQUIRED", ex2.code());
        var ex3 = assertThrows(ThirdPartyCollectionException.class,
                () -> new AssignmentCriteria(30, new BigDecimal("500.00"), new BigDecimal("100.00"), null, null));
        assertEquals("CRITERIA_RANGE_INVERTED", ex3.code());
    }

    @Test void eligibleDebtsPortNotWiredRefusesSelection() {
        var s = new ThirdPartyCollectionService(new InMemoryThirdPartyCollectionStore(),
                ThirdPartyFixtures.eligibleDirectory(), null, (t, d) -> true);
        var ex = assertThrows(ThirdPartyCollectionException.class,
                () -> s.selectCandidates(ThirdPartyFixtures.TENANT, ThirdPartyFixtures.criteria(), asOf));
        assertEquals("ELIGIBLE_DEBTS_PORT_NOT_WIRED", ex.code());
    }

    @Test void minimalDataWhitelistIsExactlyTheFiveDeclaredFields() {
        Set<String> fields = Arrays.stream(MinimalDebtorData.class.getRecordComponents())
                .map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("debtId", "unitId", "amount", "dueDate", "debtorIdentifier"), fields,
                "whitelist fechada (E5): nenhum campo fora da lista pode existir no payload");
    }

    @Test void assignmentCarriesOnlyMinimalData() {
        var s = ThirdPartyFixtures.service(List.of(eligible), Set.of());
        var c = ThirdPartyFixtures.readyContract(s, ThirdPartyFixtures.contract(ThirdPartyFixtures.COMPANY, "10"));
        var created = s.assignDebts(ThirdPartyFixtures.TENANT, c.id(), ThirdPartyFixtures.criteria(), asOf);
        assertEquals(1, created.size());
        assertEquals(eligible.toMinimalData(), created.get(0).minimalData());
    }
}
