package br.com.ziipsan.commercial.billingrules.fixtures;

import br.com.ziipsan.commercial.billingrules.FieldBillCalculator;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldBillResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContractFixturesTest {

    @Test
    void allContractVectorsPass() {
        List<ContractFixtures.FixtureCase> cases = ContractFixtures.load();
        assertThat(cases).hasSize(6);
        assertThat(ContractFixtures.runAll(cases)).isEmpty();
    }

    @Test
    void twinFileForAndroidIsByteIdenticalWhenPresent() throws Exception {
        // O gemeo do app (campo/src/field/billing-contract-fixtures.json)
        // e conferido na integracao/CI; aqui, quando o arquivo do app esta
        // alcancavel no repo, a igualdade e byte a byte. Fora do repo
        // (harness isolado) o teste se limita ao recurso do artefato.
        java.nio.file.Path twin = findTwin();
        if (twin == null) {
            return;
        }
        byte[] artifactBytes;
        try (var in = ContractFixtures.class.getResourceAsStream(ContractFixtures.RESOURCE_PATH)) {
            artifactBytes = in.readAllBytes();
        }
        assertThat(java.nio.file.Files.readAllBytes(twin)).isEqualTo(artifactBytes);
    }

    private static java.nio.file.Path findTwin() {
        java.nio.file.Path cursor = java.nio.file.Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++) {
            java.nio.file.Path candidate = cursor.resolve(
                    "campo/src/field/billing-contract-fixtures.json");
            if (java.nio.file.Files.exists(candidate)) {
                return candidate;
            }
            java.nio.file.Path inEntrega = cursor.resolve(
                    "entrega/campo/src/field/billing-contract-fixtures.json");
            if (java.nio.file.Files.exists(inEntrega)) {
                return inEntrega;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    @Test
    void differentialGeneratorProducesStableFingerprints() {
        // Gerador do diferencial noturno (10 mil ligacoes): aqui 500 casos
        // provam determinismo e ausencia de excecoes; a comparacao com o
        // emulador Android e do job noturno (pendencia declarada).
        List<FieldBillInput> first = ContractFixtures.differentialCases(500, 42L);
        List<FieldBillInput> second = ContractFixtures.differentialCases(500, 42L);
        assertThat(first).hasSize(500);
        for (int i = 0; i < first.size(); i++) {
            FieldBillResult a = FieldBillCalculator.calculate(first.get(i));
            FieldBillResult b = FieldBillCalculator.calculate(second.get(i));
            assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
        }
    }
}
