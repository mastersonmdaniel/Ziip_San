package br.com.ziipsan.commercial.registry.quality;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.32 - teste de arquitetura do aceite ("a correcao so ocorre por OS"):
 * o motor e a fila nao tem NENHUMA porta de escrita para o cadastro —
 * nenhuma dependencia de entidade, repositorio ou servico de escrita de
 * {@code customers}/{@code consumer_units}, e nenhum metodo publico de
 * correcao direta. Se uma fiacao futura tentar corrigir cadastro a partir
 * da qualidade, este teste quebra antes da CI de integracao.
 */
class CorrectionOnlyViaServiceOrderTest {

    private static final Set<String> FORBIDDEN_TYPE_FRAGMENTS = Set.of(
            "Customer", "ConsumerUnit", "Repository", "CustomerService", "ConsumerUnitService");

    @Test
    void motorEFilaNaoDependemDeEscritaDeCadastro() {
        for (Class<?> type : Set.of(CadastralQualityEngine.class, CorrectionQueueService.class,
                QualityScoreCalculator.class)) {
            for (Field field : type.getDeclaredFields()) {
                String fieldType = field.getType().getSimpleName();
                assertThat(FORBIDDEN_TYPE_FRAGMENTS)
                        .as("campo %s.%s do tipo %s", type.getSimpleName(), field.getName(), fieldType)
                        .noneMatch(fieldType::contains);
            }
        }
    }

    @Test
    void filaNaoExpoeMetodoDeCorrecaoDireta() {
        // Os unicos verbos publicos de mudanca da fila: entrar, agrupar em
        // OS, resolver PELA OS e triagem. Nada de correct/apply/update de
        // cadastro.
        for (Method method : CorrectionQueueService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            // "openCorrectionBatch" e o nome legitimo do agrupamento em OS;
            // o que nao pode existir e verbo de correcao SOBRE o cadastro.
            assertThat(name)
                    .as("metodo publico da fila: %s", method.getName())
                    .doesNotContain("correctcustomer")
                    .doesNotContain("correctunit")
                    .doesNotContain("applycorrection")
                    .doesNotContain("fixcustomer")
                    .doesNotContain("fixunit")
                    .doesNotContain("updatecustomer")
                    .doesNotContain("updateunit");
            if (name.startsWith("resolve")) {
                // Toda resolucao exige o serviceOrderId no contrato.
                assertThat(method.getParameterTypes())
                        .as("resolucao %s exige a OS", method.getName())
                        .contains(java.util.UUID.class);
            }
        }
    }

    @Test
    void achadoNaoCarregaCorrecaoNemEfeito() {
        // QualityFinding e um record de sinal: regra, severidade, alvo e
        // evidencia. Nenhum componente de valor novo/correcao.
        var components = QualityFinding.class.getRecordComponents();
        assertThat(components).extracting(c -> c.getName())
                .containsExactly("ruleCode", "severity", "unitId", "customerId", "detail");
    }
}
