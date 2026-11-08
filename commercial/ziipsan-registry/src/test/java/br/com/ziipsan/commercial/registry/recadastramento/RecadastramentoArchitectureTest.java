package br.com.ziipsan.commercial.registry.recadastramento;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ED-16 — testes de arquitetura do aceite:
 * (1) a aprovacao e campo a campo — o servico nao expoe verbo de
 *     decisao ou aplicacao em massa;
 * (2) a correcao do cadastro so ocorre pela porta de efeito da OS
 *     ({@link CadastralChangeApplier}) e pela fila da P3.32 — o servico
 *     nao depende de repositorio nem de servico de escrita de
 *     customers/consumer_units;
 * (3) a maquina de estados do campo coletado e a documentada.
 */
class RecadastramentoArchitectureTest {

    @Test
    void servicoNaoExpoeDecisaoNemAplicacaoEmMassa() {
        for (Method method : RecadastramentoService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertThat(name)
                    .as("metodo do servico: %s", method.getName())
                    .doesNotContain("approveall")
                    .doesNotContain("rejectall")
                    .doesNotContain("decideall")
                    .doesNotContain("bulk")
                    .doesNotContain("inbatchdecision");
            if (name.equals("approve") || name.equals("reject")) {
                // A decisao recebe UM campo coletado por chamada.
                assertThat(method.getParameterTypes()[0])
                        .as("decisao %s recebe um unico campo", method.getName())
                        .isEqualTo(java.util.UUID.class);
            }
        }
    }

    @Test
    void servicoSoEscreveCadastroPelaPortaDeEfeitoDaOs() {
        Set<String> allowedPorts = Set.of(
                "RecadastramentoStore", "CorrectionQueueService", "BatchOrderCloser",
                "CadastralFieldReader", "CadastralChangeApplier", "CollectionDeviceGate");
        for (Field field : RecadastramentoService.class.getDeclaredFields()) {
            String type = field.getType().getSimpleName();
            assertThat(allowedPorts)
                    .as("dependencia %s do tipo %s", field.getName(), type)
                    .contains(type);
            assertThat(type)
                    .as("dependencia %s do tipo %s", field.getName(), type)
                    .doesNotContain("Repository")
                    .doesNotContain("CustomerService")
                    .doesNotContain("ConsumerUnitService");
        }
    }

    @Test
    void estadosDoCampoColetadoSaoOsDocumentados() {
        assertThat(CollectedField.Status.values())
                .extracting(Enum::name)
                .containsExactly("COLLECTED", "APPROVED", "REJECTED", "APPLIED", "STALE", "SUPERSEDED");
    }

    @Test
    void codigosDeErroDeColetaSaoOsDoRelatorioDeErros() {
        assertThat(CollectionErrorCode.values())
                .extracting(Enum::name)
                .containsExactly("FIELD_NOT_PREDEFINED", "VALUE_BLANK", "UNIT_NOT_IN_BATCH",
                        "TASK_PAYLOAD_CONFLICT", "DOCUMENT_NOT_MASKED", "BATCH_NOT_OPEN",
                        "TARGET_MISMATCH");
    }
}
