package br.com.ziipsan.commercial.billing.cycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O catalogo do produto contem exatamente as atividades que o aceite da P2.5
 * implica (leitura, transmissao, faturamento, fechamento), em cadeia de
 * precedencia sem salto e sem ciclo, conferido a mao.
 */
class ProductActivityCatalogTest {

    @Test
    @DisplayName("quatro atividades, na ordem 1 a 4, com os codigos do aceite")
    void exactlyTheImpliedActivitiesInOrder() {
        List<ProductActivityCatalog.Definition> definitions = ProductActivityCatalog.definitions();

        assertThat(definitions).extracting(ProductActivityCatalog.Definition::code)
                .containsExactly(CycleActivityCode.READING, CycleActivityCode.TRANSMISSION,
                        CycleActivityCode.BILLING, CycleActivityCode.CLOSING);
        assertThat(definitions).extracting(ProductActivityCatalog.Definition::sequenceOrder)
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("precedencia em cadeia: cada atividade (menos a primeira) exige a anterior")
    void precedenceIsAChain() {
        List<ProductActivityCatalog.Definition> definitions = ProductActivityCatalog.definitions();

        assertThat(definitions.get(0).predecessor()).isNull();
        for (int i = 1; i < definitions.size(); i++) {
            assertThat(definitions.get(i).predecessor())
                    .as("precedente de %s", definitions.get(i).code())
                    .isEqualTo(definitions.get(i - 1).code());
        }
        // O faturamento exige a transmissao: e o que sustenta a recusa de rota nao transmitida.
        assertThat(ProductActivityCatalog.of(CycleActivityCode.BILLING).predecessor())
                .isEqualTo(CycleActivityCode.TRANSMISSION);
    }

    @Test
    @DisplayName("todas obrigatorias; repetiveis so leitura e transmissao (faturamento e travado pela idempotencia da conta)")
    void mandatoryAndRepeatableFlags() {
        assertThat(ProductActivityCatalog.definitions())
                .allMatch(ProductActivityCatalog.Definition::mandatory);
        assertThat(ProductActivityCatalog.of(CycleActivityCode.READING).repeatable()).isTrue();
        assertThat(ProductActivityCatalog.of(CycleActivityCode.TRANSMISSION).repeatable()).isTrue();
        assertThat(ProductActivityCatalog.of(CycleActivityCode.BILLING).repeatable()).isFalse();
        assertThat(ProductActivityCatalog.of(CycleActivityCode.CLOSING).repeatable()).isFalse();
    }
}
