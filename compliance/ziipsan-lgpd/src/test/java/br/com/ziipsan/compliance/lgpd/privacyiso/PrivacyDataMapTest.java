package br.com.ziipsan.compliance.lgpd.privacyiso;

import br.com.ziipsan.compliance.lgpd.incident.DataCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite da ED-74: mapa de dados apoiado na classificação por coluna da P0.11 (DAD-29). */
class PrivacyDataMapTest {

    private static Iso27701Activation on() {
        return Iso27701Activation.enabled("2025", Iso27701Activation.EditionUse.TARGET,
                "escopo", "sgpi");
    }

    private static PrivacyDataMap.Entry entry(String table, String column, String... codes) {
        return new PrivacyDataMap.Entry(table, column, DataCategory.ID, Set.of(codes));
    }

    @Test
    @DisplayName("coluna classificada sem registro ligado aparece como pendência, em ordem estável")
    void uncoveredColumnsArePendencies() {
        var map = PrivacyDataMap.build(on(), List.of(
                entry("consumers", "name", "ATENDIMENTO"),
                entry("consumers", "document"),
                entry("bills", "holder_name")));
        assertThat(PrivacyDataMap.uncovered(map).stream().map(PrivacyDataMap.Entry::key).toList())
                .containsExactly("bills.holder_name", "consumers.document");
    }

    @Test
    @DisplayName("mapa totalmente coberto não tem pendência")
    void fullyCoveredHasNoPendency() {
        var map = PrivacyDataMap.build(on(), List.of(entry("consumers", "name", "ATENDIMENTO")));
        assertThat(PrivacyDataMap.uncovered(map)).isEmpty();
    }

    @Test
    @DisplayName("coluna repetida no mapa é recusada (esconderia pendência)")
    void duplicateColumnRefused() {
        assertThatThrownBy(() -> PrivacyDataMap.build(on(), List.of(
                entry("consumers", "name", "A"), entry("consumers", "name"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("código de registro em branco é recusado e não suprime a pendência da coluna (D1)")
    void blankRecordCodeRefused() {
        assertThatThrownBy(() -> entry("consumers", "name", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        // Sem a entrada inválida, a coluna sem código válido continua pendência:
        var map = PrivacyDataMap.build(on(), List.of(entry("consumers", "name")));
        assertThat(PrivacyDataMap.uncovered(map)).hasSize(1);
        assertThat(map.get(0).uncovered()).isTrue();
    }

    @Test
    @DisplayName("código de registro nulo é recusado (D1)")
    void nullRecordCodeRefused() {
        var codes = new HashSet<String>();
        codes.add(null);
        assertThatThrownBy(() -> new PrivacyDataMap.Entry("consumers", "name",
                DataCategory.ID, codes)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("código de registro acima do VARCHAR(60) da V23_1 é recusado; 60 exatos passam (D1)")
    void recordCodeOverSqlMaxRefused() {
        assertThatThrownBy(() -> entry("consumers", "name", "X".repeat(61)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(entry("consumers", "name", "X".repeat(60)).uncovered()).isFalse();
    }

    @Test
    @DisplayName("coluna repetida só muda a caixa é recusada: no PostgreSQL é a mesma coluna (D2)")
    void duplicateColumnDifferentCaseRefused() {
        assertThatThrownBy(() -> PrivacyDataMap.build(on(), List.of(
                entry("Consumers", "name", "A"), entry("consumers", "name"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrivacyDataMap.build(on(), List.of(
                entry("consumers", "NAME", "A"), entry("consumers", "name"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("classe do mapa é a da classificação DAD-29, nunca texto livre")
    void categoryIsEnum() {
        var e = new PrivacyDataMap.Entry("photos", "image", DataCategory.SENS, Set.of());
        assertThat(e.dataClass()).isEqualTo(DataCategory.SENS);
        assertThatThrownBy(() -> new PrivacyDataMap.Entry("photos", "image", null, Set.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("com a norma desligada, o mapa do SGPI não nasce (a P3.40 segue com o relatório dela)")
    void disabledRefuses() {
        assertThatThrownBy(() -> PrivacyDataMap.build(Iso27701Activation.disabled(),
                List.of(entry("consumers", "name", "A"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
