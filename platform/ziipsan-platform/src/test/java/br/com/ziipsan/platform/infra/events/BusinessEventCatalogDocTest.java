package br.com.ziipsan.platform.infra.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.38: o documento do catálogo é gerado do manifesto e conferido no CI.
 * Qualquer evento, consumidor ou lacuna do manifesto sem marcador no
 * documento — ou qualquer marcador órfão no documento — falha este teste:
 * o documento desatualizado barra o build até ser regenerado por
 * {@code scripts/generate-business-event-catalog.py}.
 */
class BusinessEventCatalogDocTest {

    private static final Pattern EVENT_MARKER = Pattern.compile("<!-- event: ([^ ]+) -->");
    private static final Pattern CONSUMER_MARKER = Pattern.compile("<!-- consumer: ([^ ]+) -->");
    private static final Pattern GAP_MARKER = Pattern.compile("<!-- gap: ([^ ]+) -->");

    @Test
    @DisplayName("documento do catálogo cobre exatamente eventos, consumidores e lacunas do manifesto")
    void documentMatchesManifest() throws Exception {
        Path root = BusinessEventCatalogTest.repoRoot();
        BusinessEventCatalog catalog = BusinessEventCatalogLoader.load(
                root.resolve("packages/sanitation/vertical.json"));
        String doc = Files.readString(
                root.resolve("docs/arquitetura/business-event-catalog.md"), StandardCharsets.UTF_8);

        Set<String> expectedEvents = new HashSet<>();
        for (BusinessEventCatalog.BusinessEvent event : catalog.events()) {
            expectedEvents.add(event.name() + "@v" + event.version());
        }
        Set<String> expectedConsumers = new HashSet<>();
        for (BusinessEventCatalog.DeclaredConsumer consumer : catalog.declaredConsumers()) {
            expectedConsumers.add(consumer.task());
        }
        Set<String> expectedGaps = new HashSet<>();
        for (BusinessEventCatalog.CatalogGap gap : catalog.gaps()) {
            expectedGaps.add(gap.name());
        }

        assertThat(markers(doc, EVENT_MARKER)).containsExactlyInAnyOrderElementsOf(expectedEvents);
        assertThat(markers(doc, CONSUMER_MARKER)).containsExactlyInAnyOrderElementsOf(expectedConsumers);
        assertThat(markers(doc, GAP_MARKER)).containsExactlyInAnyOrderElementsOf(expectedGaps);
        assertThat(doc).contains("Pacote: **sanitation**");
        if (catalog.events().isEmpty()) {
            assertThat(doc).contains("Nenhum evento de negócio registrado");
        }
    }

    private static Set<String> markers(String doc, Pattern pattern) {
        Set<String> found = new HashSet<>();
        Matcher matcher = pattern.matcher(doc);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
