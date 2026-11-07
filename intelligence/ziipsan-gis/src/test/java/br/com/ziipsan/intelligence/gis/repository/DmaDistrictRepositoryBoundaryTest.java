package br.com.ziipsan.intelligence.gis.repository;

import br.com.ziipsan.intelligence.gis.epanet.EpanetSimulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fronteira GIS-01 (P1.26): uma só entidade e um só repositório de distrito de
 * medição. A cópia de {@code DmaDistrictRepository} que existia no módulo GIS
 * antigo foi removida; o serviço do GIS injeta o repositório do módulo de
 * perdas, dono da entidade. Se alguém recriar a cópia, este teste quebra — é
 * esse o ponto. (Nomes antigos evitados aqui de propósito: o teste de contrato
 * ProblemCodeContractTest recusa o literal do nome antigo no fonte.)
 */
class DmaDistrictRepositoryBoundaryTest {

    @Test
    @DisplayName("a copia do DmaDistrictRepository no modulo GIS antigo nao existe")
    void gisCopyOfDmaDistrictRepositoryIsGone() {
        assertThatThrownBy(() -> Class.forName(
                "br.com.ziipsan.intelligence.gis.repository.DmaDistrictRepository"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("o repositorio unico de losses existe e e o injetado no servico do GIS")
    void singleLossesRepositoryIsInjectedIntoGisService() throws Exception {
        Class<?> lossesRepo = Class.forName(
                "br.com.ziipsan.intelligence.losses.repository.DmaDistrictRepository");
        assertThat(lossesRepo.isInterface()).isTrue();

        Constructor<?> constructor = Arrays.stream(EpanetSimulationService.class.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 4)
                .findFirst()
                .orElseThrow();
        assertThat(constructor.getParameterTypes())
                .contains(lossesRepo);
    }

    @Test
    @DisplayName("o repositorio de problemas da validacao (GIS-03) e injetado no servico do GIS")
    void validationIssueRepositoryIsInjectedIntoGisService() throws Exception {
        Class<?> validationRepo = Class.forName(
                "br.com.ziipsan.intelligence.gis.repository.GisValidationIssueRepository");

        Constructor<?> constructor = Arrays.stream(EpanetSimulationService.class.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 4)
                .findFirst()
                .orElseThrow();
        assertThat(constructor.getParameterTypes())
                .contains(validationRepo);
    }
}
