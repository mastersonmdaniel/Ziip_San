package br.com.ziipsan.integration.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2.26: massa sintética do ambiente de teste — identificadores fixos,
 * estáveis para o teste de contrato por versão, sem nenhum dado pessoal.
 */
class SandboxFixturesTest {

    private static final List<String> PERSONAL_KEYS = List.of(
            "name", "holderName", "customerName", "document", "cpf", "cnpj",
            "address", "phone", "email", "socialName", "birthDate");

    @Test
    @DisplayName("as fixtures têm identificadores fixos e nenhum campo de dado pessoal")
    void fixturesAreStableAndPersonalDataFree() {
        assertThat(SandboxFixtures.properties()).hasSize(2);
        assertThat(SandboxFixtures.bills()).hasSize(1);
        assertThat(SandboxFixtures.payments()).hasSize(1);
        assertThat(SandboxFixtures.serviceOrders()).hasSize(1);
        assertThat(SandboxFixtures.properties().get(0).get("id"))
                .isEqualTo("10000000-0000-4000-8000-000000000001");
        for (var list : List.of(SandboxFixtures.properties(), SandboxFixtures.bills(),
                SandboxFixtures.payments(), SandboxFixtures.serviceOrders())) {
            for (var item : list) {
                assertThat(item.keySet()).doesNotContainAnyElementsOf(PERSONAL_KEYS);
            }
        }
    }

    @Test
    @DisplayName("a conta sintética referencia a unidade sintética, como a real")
    void billReferencesProperty() {
        var bill = SandboxFixtures.bills().get(0);
        assertThat(bill.get("unitId")).isEqualTo(SandboxFixtures.properties().get(0).get("id"));
    }
}
