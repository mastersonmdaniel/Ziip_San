package br.com.ziipsan.platform.templates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2.30: as 14 fábricas do aceite — uma por tipo da lista, sem variável
 * fora do catálogo, sem bloco travado (o conteúdo legal é da P2.32) e com
 * os 5 canais cobertos.
 */
class FactoryTemplatesTest {

    @Test
    @DisplayName("P2.30: 14 modelos de fábrica, chaves únicas, sem variável fora do catálogo")
    void fourteenFactoryTemplatesAllValid() {
        List<FactoryTemplates.FactoryTemplate> all = FactoryTemplates.ALL;
        assertThat(all).hasSize(14);
        assertThat(all.stream().map(FactoryTemplates.FactoryTemplate::key).toList())
                .doesNotHaveDuplicates();
        for (FactoryTemplates.FactoryTemplate factory : all) {
            List<TemplateBlock> blocks = List.of(new TemplateBlock("main",
                    TemplateBlock.Kind.FREE, TemplateBlock.Origin.TENANT, factory.body()));
            assertThat(TemplateEngine.validationProblems(factory.documentType(), blocks, factory.variables()))
                    .as("fábrica inválida: %s", factory.key())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("P2.30: os 5 canais estão cobertos pelas fábricas")
    void allChannelsCovered() {
        Set<TemplateChannel> channels = new HashSet<>();
        for (FactoryTemplates.FactoryTemplate factory : FactoryTemplates.ALL) {
            channels.add(factory.channel());
        }
        assertThat(channels).containsExactlyInAnyOrder(TemplateChannel.values());
    }

    @Test
    @DisplayName("P2.30: toda fábrica tem chave buscável e a OS impressa é térmica")
    void factoryLookupAndThermalServiceOrder() {
        assertThat(FactoryTemplates.byKey("service-order-print"))
                .hasValueSatisfying(f -> assertThat(f.channel()).isEqualTo(TemplateChannel.THERMAL));
        assertThat(FactoryTemplates.byKey("inexistente")).isEmpty();
    }
}
