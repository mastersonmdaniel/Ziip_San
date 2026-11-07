package br.com.ziipsan.commercial.registry.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ED-12: o registro dos 22 catálogos da SAMAE 2.8.10 confere com a lista
 * fechada do levantamento 18d, seção E2 — nem um a mais, nem um a menos,
 * com os requisitos SG da origem.
 */
class SamaeAuxiliaryCatalogsTest {

    /** Lista de requisitos cobertos do 18d E2, na ordem do documento. */
    private static final List<String> EXPECTED_SG = List.of(
            "SG-095", "SG-102", "SG-104", "SG-105", "SG-107", "SG-108", "SG-110", "SG-111",
            "SG-112", "SG-115", "SG-116", "SG-119", "SG-120", "SG-121", "SG-122", "SG-127",
            "SG-137", "SG-138", "SG-139", "SG-140", "SG-143", "SG-144");

    @Test
    @DisplayName("são exatamente os 22 do 18d E2, na ordem e sem repetições")
    void exactlyTheTwentyTwo() {
        assertThat(SamaeAuxiliaryCatalogs.ALL).hasSize(22);
        assertThat(SamaeAuxiliaryCatalogs.ALL)
                .extracting(SamaeAuxiliaryCatalogs.AuxiliaryCatalog::sg)
                .containsExactlyElementsOf(EXPECTED_SG);
        assertThat(SamaeAuxiliaryCatalogs.ALL)
                .extracting(SamaeAuxiliaryCatalogs.AuxiliaryCatalog::proposedTable)
                .doesNotHaveDuplicates();
        assertThat(SamaeAuxiliaryCatalogs.ALL)
                .allSatisfy(c -> {
                    assertThat(c.name()).startsWith("Cadastro de ");
                    assertThat(c.ownerModule()).isNotBlank();
                });
    }

    @Test
    @DisplayName("estado civil e nacionalidade são os únicos marcados como dado pessoal com finalidade")
    void personalDataFlags() {
        assertThat(SamaeAuxiliaryCatalogs.ALL)
                .filteredOn(SamaeAuxiliaryCatalogs.AuxiliaryCatalog::personalData)
                .extracting(SamaeAuxiliaryCatalogs.AuxiliaryCatalog::sg)
                .containsExactlyInAnyOrder("SG-102", "SG-121");
    }

    @Test
    @DisplayName("pavimentos ficam no registry (a unidade os referencia); busca por SG fora da lista devolve nulo")
    void lookups() {
        assertThat(SamaeAuxiliaryCatalogs.bySg("SG-138").ownerModule()).isEqualTo("registry");
        assertThat(SamaeAuxiliaryCatalogs.bySg("SG-139").proposedTable()).isEqualTo("sidewalk_pavements");
        assertThat(SamaeAuxiliaryCatalogs.bySg("SG-096")).isNull();
        assertThat(SamaeAuxiliaryCatalogs.bySg(null)).isNull();
    }
}
