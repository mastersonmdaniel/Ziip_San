package br.com.ziipsan.compliance.lgpd.privacy;

import br.com.ziipsan.compliance.lgpd.incident.DataCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registro de operações (art. 37): campos obrigatórios, tetos das colunas reais do
 * catálogo e edição versionada. Textos são dados de teste genéricos.
 */
class ProcessingRecordTest {

    private static ProcessingRecord record() {
        return new ProcessingRecord("OP-CADASTRO", "CADASTRO", LegalBasis.CONTRACT,
                Set.of(DataCategory.ID, DataCategory.CT), "clientes do serviço",
                "sem compartilhamento declarado em teste", null, "Operador de Teste",
                List.of(), 1, ProcessingRecord.Origin.TENANT, true);
    }

    @Test
    @DisplayName("edição pelo tenant produz a versão seguinte sem tocar na anterior")
    void editProducesNextVersion() {
        ProcessingRecord original = record();
        ProcessingRecord edited = original.edited("CADASTRO", LegalBasis.CONTRACT,
                Set.of(DataCategory.ID, DataCategory.CT, DataCategory.LOC),
                "clientes do serviço", "compartilhamento declarado em teste", null,
                "Operador de Teste", List.of("Suboperador de Teste"));
        assertThat(original.version()).isEqualTo(1);
        assertThat(edited.version()).isEqualTo(2);
        assertThat(edited.dataCategories()).contains(DataCategory.LOC);
        assertThat(edited.subprocessors()).containsExactly("Suboperador de Teste");
    }

    @Test
    @DisplayName("registro inativo não é editável")
    void inactiveRecordNotEditable() {
        ProcessingRecord inactive = new ProcessingRecord("OP-X", "CADASTRO",
                LegalBasis.CONTRACT, Set.of(DataCategory.ID), "clientes",
                "sem compartilhamento", null, "Operador", List.of(), 3,
                ProcessingRecord.Origin.TENANT, false);
        assertThatThrownBy(() -> inactive.edited("CADASTRO", LegalBasis.CONTRACT,
                Set.of(DataCategory.ID), "clientes", "sem compartilhamento", null,
                "Operador", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    @DisplayName("finalidade além do teto da coluna do catálogo (40) é recusada")
    void purposeBeyondCatalogColumnRefused() {
        String longPurpose = "P".repeat(41);
        assertThatThrownBy(() -> new ProcessingRecord("OP-X", longPurpose,
                LegalBasis.CONTRACT, Set.of(DataCategory.ID), "clientes",
                "sem compartilhamento", null, "Operador", List.of(), 1,
                ProcessingRecord.Origin.TENANT, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("40");
    }

    @Test
    @DisplayName("sem categorias da classificação (DAD-29) o registro não nasce")
    void recordWithoutCategoriesRefused() {
        assertThatThrownBy(() -> new ProcessingRecord("OP-X", "CADASTRO",
                LegalBasis.CONTRACT, Set.of(), "clientes", "sem compartilhamento", null,
                "Operador", List.of(), 1, ProcessingRecord.Origin.TENANT, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DAD-29");
    }
}
