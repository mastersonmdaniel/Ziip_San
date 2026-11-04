package br.com.ziipsan.platform.infra.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paridade do catálogo de runtime (P1.23): a imagem não embarca
 * {@code packages/}, então o publicador usa {@link SupportEventCatalog} em
 * vez de ler o manifesto — mas a declaração tem de ser idêntica à do
 * manifesto, ou o build barra aqui. Quando a imagem embarcar o manifesto,
 * esta classe e o teste somem junto com a duplicação.
 */
class SupportEventCatalogParityTest {

    @Test
    @DisplayName("catálogo de runtime é idêntico aos eventos de chamado do manifesto")
    void runtimeCatalogMatchesManifest() {
        Path root = BusinessEventCatalogTest.repoRoot();
        BusinessEventCatalog manifest = BusinessEventCatalogLoader.load(
                root.resolve("packages/sanitation/vertical.json"));
        BusinessEventCatalog runtime = SupportEventCatalog.catalog();

        Map<String, BusinessEventCatalog.BusinessEvent> manifestEvents = manifest.events().stream()
                .filter(e -> e.name().startsWith("support."))
                .collect(Collectors.toMap(BusinessEventCatalog.BusinessEvent::name, e -> e));
        Map<String, BusinessEventCatalog.BusinessEvent> runtimeEvents = runtime.events().stream()
                .collect(Collectors.toMap(BusinessEventCatalog.BusinessEvent::name, e -> e));

        assertThat(runtimeEvents.keySet()).containsExactlyInAnyOrderElementsOf(manifestEvents.keySet());
        for (Map.Entry<String, BusinessEventCatalog.BusinessEvent> entry : manifestEvents.entrySet()) {
            BusinessEventCatalog.BusinessEvent expected = entry.getValue();
            BusinessEventCatalog.BusinessEvent actual = runtimeEvents.get(entry.getKey());
            assertThat(actual.version()).as("versão de " + entry.getKey()).isEqualTo(expected.version());
            assertThat(actual.payloadSchema()).as("esquema de " + entry.getKey()).isEqualTo(expected.payloadSchema());
            assertThat(actual.requiredPermission()).as("permissão de " + entry.getKey())
                    .isEqualTo(expected.requiredPermission());
            assertThat(actual.consumers()).as("consumidores de " + entry.getKey())
                    .containsExactlyElementsOf(expected.consumers());
            assertThat(actual.originTask()).as("tarefa de " + entry.getKey()).isEqualTo(expected.originTask());
        }
        assertThat(runtime.declaredConsumers().stream().map(BusinessEventCatalog.DeclaredConsumer::task))
                .contains("P1.17");
    }
}
