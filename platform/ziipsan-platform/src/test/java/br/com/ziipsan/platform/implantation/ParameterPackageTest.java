package br.com.ziipsan.platform.implantation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParameterPackageTest {

    private static ParameterPackage.Entry accepted(String key, String valueJson) {
        return new ParameterPackage.Entry(key, valueJson,
                LocalDate.of(2026, 1, 1), null, "proponente-1", "aprovador-2", true);
    }

    @Test
    @DisplayName("exportação só carrega versões aceitas; a viagem de ida e volta preserva as entradas")
    void exportOnlyAcceptedAndRoundTrip() {
        ParameterPackage.Entry draft = new ParameterPackage.Entry("bill.due_day", "15",
                LocalDate.of(2026, 1, 1), null, "proponente-1", "aprovador-2", false);
        ParameterPackage.Package pkg = ParameterPackage.export("HOMOLOGATION",
                List.of(accepted("tenant.timezone", "\"America/Sao_Paulo\""), draft,
                        accepted("fee.fine_pct", "2")));
        assertThat(pkg.entries()).hasSize(2);
        List<ParameterPackage.Entry> back = ParameterPackage.importPackage(ParameterPackage.toJson(pkg));
        assertThat(back).hasSize(2);
        assertThat(back).extracting(ParameterPackage.Entry::definitionKey)
                .containsExactly("tenant.timezone", "fee.fine_pct");
    }

    @Test
    @DisplayName("pacote adulterado depois da exportação é recusado pelo hash")
    void tamperedPackageRefused() {
        ParameterPackage.Package pkg = ParameterPackage.export("HOMOLOGATION",
                List.of(accepted("fee.fine_pct", "2")));
        String json = ParameterPackage.toJson(pkg).replace("\"fee.fine_pct\"", "\"fee.fine_pct_x\"");
        assertThatThrownBy(() -> ParameterPackage.importPackage(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash");
    }

    @Test
    @DisplayName("quem propõe igual a quem aprova é recusado já na construção da entrada (P0.19)")
    void sameProposerApproverRefused() {
        assertThatThrownBy(() -> new ParameterPackage.Entry("fee.fine_pct", "2",
                LocalDate.of(2026, 1, 1), null, "mesma-pessoa", "mesma-pessoa", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propõe");
    }

    @Test
    @DisplayName("entrada não aceita dentro do pacote recusa a importação")
    void nonAcceptedEntryRefused() {
        ParameterPackage.Entry ok = accepted("fee.fine_pct", "2");
        String json = ParameterPackage.toJson(ParameterPackage.export("HOMOLOGATION", List.of(ok)));
        String forged = json.replace("\"accepted\":true", "\"accepted\":false");
        assertThatThrownBy(() -> ParameterPackage.importPackage(forged))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aceitos");
    }

    @Test
    @DisplayName("vigências sobrepostas da mesma chave dentro do pacote são recusadas na importação")
    void overlappingValidityRefused() {
        ParameterPackage.Entry a = new ParameterPackage.Entry("fee.fine_pct", "2",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "p1", "a2", true);
        ParameterPackage.Entry b = new ParameterPackage.Entry("fee.fine_pct", "3",
                LocalDate.of(2026, 6, 1), null, "p1", "a2", true);
        // A exportação já recusa a sobreposição (teste exportOverlappingValidityRefused),
        // então o pacote sobreposto da importação é montado à mão, com o hash
        // correto do conteúdo — como viria de um exportador externo a esta régua.
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("sourceEnvironment", "HOMOLOGATION");
        root.put("contentHash", ParameterPackage.hashOf(List.of(a, b)));
        java.util.List<Object> entries = new java.util.ArrayList<>();
        for (ParameterPackage.Entry e : List.of(a, b)) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("definitionKey", e.definitionKey());
            m.put("value", br.com.ziipsan.core.json.Json.readTree(e.valueJson()));
            m.put("validFrom", e.validFrom().toString());
            m.put("validTo", e.validTo() == null ? null : e.validTo().toString());
            m.put("proposedBy", e.proposedBy());
            m.put("approvedBy", e.approvedBy());
            m.put("accepted", e.accepted());
            entries.add(m);
        }
        root.put("entries", entries);
        String json = br.com.ziipsan.core.json.Json.write(root);
        assertThatThrownBy(() -> ParameterPackage.importPackage(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobrepostas");
    }

    @Test
    @DisplayName("exportação recusa vigências sobrepostas da mesma chave (a mesma régua da importação)")
    void exportOverlappingValidityRefused() {
        ParameterPackage.Entry a = new ParameterPackage.Entry("fee.fine_pct", "2",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "p1", "a2", true);
        ParameterPackage.Entry b = new ParameterPackage.Entry("fee.fine_pct", "3",
                LocalDate.of(2026, 6, 1), null, "p1", "a2", true);
        assertThatThrownBy(() -> ParameterPackage.export("HOMOLOGATION", List.of(a, b)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sobrepostas")
                .hasMessageContaining("fee.fine_pct");
    }

    @Test
    @DisplayName("valor que não é JSON válido é recusado como IllegalArgumentException nomeada, não IllegalStateException")
    void invalidJsonValueRefusedAsIllegalArgument() {
        assertThatThrownBy(() -> new ParameterPackage.Entry("fee.fine_pct", "{invalid",
                LocalDate.of(2026, 1, 1), null, "p1", "a2", true))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fee.fine_pct");
    }

    @Test
    @DisplayName("vigências encostadas (uma termina, outra começa no dia seguinte) são aceitas")
    void adjacentValidityAccepted() {
        ParameterPackage.Entry a = new ParameterPackage.Entry("fee.fine_pct", "2",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "p1", "a2", true);
        ParameterPackage.Entry b = new ParameterPackage.Entry("fee.fine_pct", "3",
                LocalDate.of(2026, 7, 1), null, "p1", "a2", true);
        List<ParameterPackage.Entry> back = ParameterPackage.importPackage(
                ParameterPackage.toJson(ParameterPackage.export("HOMOLOGATION", List.of(a, b))));
        assertThat(back).hasSize(2);
    }
}
