package br.com.ziipsan.commercial.collection.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cartas de cobranca com os filtros de TR-390/SG-375, emitidas pelo motor da P2.30. */
class CollectionLettersTest {

    private static CollectionLetters.LetterCandidate candidate(String route, String amount,
                                                                 int bills, int months) {
        return new CollectionLetters.LetterCandidate(UUID.randomUUID(), route, YearMonth.of(2026, 10),
                new BigDecimal(amount), bills, months, Map.of("route", route));
    }

    @Test
    @DisplayName("filtros combinados: rota, referencia, valor minimo, faturas e meses de atraso")
    void filtersSelect() {
        var in = candidate("R1", "500.00", 3, 4);
        var wrongRoute = candidate("R2", "500.00", 3, 4);
        var tooCheap = candidate("R1", "50.00", 3, 4);
        var tooFewBills = candidate("R1", "500.00", 1, 4);
        var tooRecent = candidate("R1", "500.00", 3, 1);
        var filters = new CollectionLetters.LetterFilters(null, List.of("R1"), YearMonth.of(2026, 10),
                new BigDecimal("100.00"), null, 2, 3);
        var selected = CollectionLetters.select(List.of(in, wrongRoute, tooCheap, tooFewBills, tooRecent), filters);
        assertThat(selected).containsExactly(in);
    }

    @Test
    @DisplayName("emissao: uma carta por selecionada, com codigo e versao do modelo da P2.30")
    void issueRendersPerSelected() {
        var templates = new CutoffExtTestSupport.FakeTemplates();
        var issued = CollectionLetters.issue(UUID.randomUUID(),
                List.of(candidate("R1", "500.00", 3, 4), candidate("R1", "700.00", 2, 5)),
                new CollectionLetters.LetterFilters(null, List.of(), null, null, null, null, null),
                templates, CutoffExtTestSupport.extParams());
        assertThat(issued).hasSize(2);
        assertThat(issued).extracting(CollectionLetters.IssuedLetter::templateCode).containsOnly("CARTA_COBRANCA");
        assertThat(issued).extracting(CollectionLetters.IssuedLetter::templateVersion).containsOnly("v7");
        assertThat(templates.renderedCodes).containsExactly("CARTA_COBRANCA", "CARTA_COBRANCA");
    }
}
