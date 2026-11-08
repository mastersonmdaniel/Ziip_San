package br.com.ziipsan.commercial.collection.dunning.prescription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Relogio da prescricao (RAS-15) com datas calculadas a mao. Tabela de
 * teste = as linhas de fabrica do SQL pendente (versao 1).
 */
class PrescriptionTest {

    private static final LocalDate V1 = LocalDate.of(2026, 1, 1);

    private static Prescription.Table factoryTable() {
        return new Prescription.Table(List.of(
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW,
                        Prescription.ClaimType.GENERAL, 10, "Codigo Civil, art. 205", false, 1, V1),
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW,
                        Prescription.ClaimType.UNDUE_PAYMENT_REPETITION, 10,
                        "Sumula 412 do STJ; Codigo Civil, art. 205", false, 1, V1),
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW,
                        Prescription.ClaimType.TARIFF_EXECUTION, null,
                        "STJ, Temas 251 a 254 (execucao de tarifa)", false, 1, V1),
                new Prescription.RuleRow(Prescription.ProviderNature.PUBLIC_LAW,
                        Prescription.ClaimType.AGAINST_PUBLIC_ENTITY, null,
                        "Decreto 20.910/1932", true, 1, V1),
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW,
                        Prescription.ClaimType.ARTICLE_206_UNSPECIFIED, null,
                        "Codigo Civil, art. 206 — inciso e prazo nao fixados nas specs: lacuna declarada",
                        false, 1, V1)));
    }

    @Test
    @DisplayName("art. 205 (10 anos): vencimento 10/03/2016 prescreve em 10/03/2026, nao na vespera")
    void generalClockBoundary() {
        var table = factoryTable();
        var due = LocalDate.of(2016, 3, 10);
        var before = Prescription.evaluate(table, Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.GENERAL, due, null, false, LocalDate.of(2026, 3, 9));
        assertThat(before.outcome()).isEqualTo(Prescription.Outcome.NOT_PRESCRIBED);
        assertThat(before.deadline()).isEqualTo(LocalDate.of(2026, 3, 10));
        var onDay = Prescription.evaluate(table, Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.GENERAL, due, null, false, LocalDate.of(2026, 3, 10));
        assertThat(onDay.outcome()).isEqualTo(Prescription.Outcome.PRESCRIBED);
    }

    @Test
    @DisplayName("relogio da promessa: reconhecimento em 15/01/2020 reinicia o prazo (novo termo 15/01/2030)")
    void promiseInterruptsClock() {
        var table = factoryTable();
        var due = LocalDate.of(2016, 3, 10);
        var promise = LocalDate.of(2020, 1, 15);
        var eval = Prescription.evaluate(table, Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.GENERAL, due, promise, false, LocalDate.of(2029, 12, 31));
        assertThat(eval.outcome()).isEqualTo(Prescription.Outcome.NOT_PRESCRIBED);
        assertThat(eval.clockStart()).isEqualTo(promise);
        assertThat(eval.deadline()).isEqualTo(LocalDate.of(2030, 1, 15));
        var after = Prescription.evaluate(table, Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.GENERAL, due, promise, false, LocalDate.of(2030, 1, 15));
        assertThat(after.outcome()).isEqualTo(Prescription.Outcome.PRESCRIBED);
    }

    @Test
    @DisplayName("execucao de tarifa (Temas 251-254) sem prazo confirmado: UNDETERMINED, nunca um chute")
    void tariffExecutionUndetermined() {
        var eval = Prescription.evaluate(factoryTable(), Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.TARIFF_EXECUTION, LocalDate.of(2010, 1, 1), null, false,
                LocalDate.of(2026, 11, 7));
        assertThat(eval.outcome()).isEqualTo(Prescription.Outcome.UNDETERMINED);
        assertThat(eval.reason()).contains("Temas 251 a 254");
    }

    @Test
    @DisplayName("Decreto 20.910/1932: sem analise juridica e sem prazo, UNDETERMINED mesmo com analise se o prazo nao vier")
    void publicEntityUndetermined() {
        var noAnalysis = Prescription.evaluate(factoryTable(), Prescription.ProviderNature.PUBLIC_LAW,
                Prescription.ClaimType.AGAINST_PUBLIC_ENTITY, LocalDate.of(2010, 1, 1), null, false,
                LocalDate.of(2026, 11, 7));
        assertThat(noAnalysis.outcome()).isEqualTo(Prescription.Outcome.UNDETERMINED);
        var withAnalysis = Prescription.evaluate(factoryTable(), Prescription.ProviderNature.PUBLIC_LAW,
                Prescription.ClaimType.AGAINST_PUBLIC_ENTITY, LocalDate.of(2010, 1, 1), null, true,
                LocalDate.of(2026, 11, 7));
        assertThat(withAnalysis.outcome()).isEqualTo(Prescription.Outcome.UNDETERMINED);
    }

    @Test
    @DisplayName("linha ausente na tabela: erro explicito, nao presuncao")
    void missingRowIsExplicitError() {
        var empty = new Prescription.Table(List.of());
        assertThatThrownBy(() -> Prescription.evaluate(empty, Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.GENERAL, LocalDate.of(2020, 1, 1), null, false,
                LocalDate.of(2026, 11, 7)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("versao nova da tabela prevalece sobre a anterior na mesma data")
    void newerVersionWins() {
        var table = new Prescription.Table(List.of(
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW,
                        Prescription.ClaimType.TARIFF_EXECUTION, null,
                        "STJ, Temas 251 a 254", false, 1, V1),
                new Prescription.RuleRow(Prescription.ProviderNature.PRIVATE_LAW,
                        Prescription.ClaimType.TARIFF_EXECUTION, 10,
                        "STJ, Temas 251 a 254; Codigo Civil, art. 205", false, 2, LocalDate.of(2026, 6, 1))));
        var eval = Prescription.evaluate(table, Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.TARIFF_EXECUTION, LocalDate.of(2016, 3, 10), null, false,
                LocalDate.of(2026, 11, 7));
        assertThat(eval.outcome()).isEqualTo(Prescription.Outcome.PRESCRIBED);
        assertThat(eval.row().tableVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("D7: art. 206 do CC tem linha propria, SEM prazo (lacuna declarada) — UNDETERMINED, nunca silencio nem chute")
    void article206DeclaredGap() {
        var eval = Prescription.evaluate(factoryTable(), Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.ARTICLE_206_UNSPECIFIED, LocalDate.of(2010, 1, 1), null, false,
                LocalDate.of(2026, 11, 7));
        assertThat(eval.outcome()).isEqualTo(Prescription.Outcome.UNDETERMINED);
        assertThat(eval.row().legalSource()).contains("art. 206");
        assertThat(eval.row().periodYears()).isNull();
    }
}
