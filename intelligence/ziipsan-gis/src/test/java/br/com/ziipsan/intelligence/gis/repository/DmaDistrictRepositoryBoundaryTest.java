package br.com.ziipsan.intelligence.gis.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fronteira GIS-01 (P1.26): uma só entidade e um só repositório de distrito de
 * medição. A cópia de {@code DmaDistrictRepository} que existia no módulo GIS
 * antigo foi removida; o serviço do GIS injeta o repositório do módulo de
 * perdas, dono da entidade. Se alguém recriar a cópia, este teste quebra — é
 * esse o ponto. (Nomes antigos evitados aqui de propósito: o teste de contrato
 * ProblemCodeContractTest recusa o literal do nome antigo no fonte.)
 *
 * <p><b>Forma actual (cura P-C42501-02, 12/11/2026):</b> no desenho original
 * (P2.21), o serviço hidráulico do GIS injectava os dois repositórios — era
 * essa a fiação que os testes 2–3 verificavam. A frente ED-63 reescreveu o
 * serviço sobre {@code JdbcTemplate} e a topologia persistida (ver
 * {@code docs/gis/ED-63-hidraulica-epanet-swmm.md}, "Remoção (M5/M6)"), e a
 * injecção deixou de existir: os distritos passaram a ser consumidos no
 * módulo dono (o serviço de perdas, em losses) e a validação contínua corre
 * no serviço de validação do GIS. Os testes 2–3 acompanham a fronteira na
 * forma actual: o repositório único de losses é o injectado onde os
 * distritos são consumidos, e o contrato de leitura dos problemas da
 * validação (GIS-03) continua único e do GIS. O teste 1 — a cópia no GIS
 * não existe — não mudou.
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
    @DisplayName("o repositorio unico de losses existe e e o injetado onde os distritos sao consumidos")
    void singleLossesRepositoryIsInjectedWhereDistrictsAreConsumed() throws Exception {
        Class<?> lossesRepo = Class.forName(
                "br.com.ziipsan.intelligence.losses.repository.DmaDistrictRepository");
        assertThat(lossesRepo.isInterface()).isTrue();

        Class<?> lossesService = Class.forName(
                "br.com.ziipsan.intelligence.losses.service.LossesService");
        Constructor<?> constructor = Arrays.stream(lossesService.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        assertThat(constructor.getParameterTypes())
                .contains(lossesRepo);
    }

    @Test
    @DisplayName("o repositorio de problemas da validacao (GIS-03) e o contrato unico de leitura, do GIS")
    void validationIssueRepositoryIsTheSingleGisReadContract() throws Exception {
        Class<?> validationRepo = Class.forName(
                "br.com.ziipsan.intelligence.gis.repository.GisValidationIssueRepository");

        assertThat(validationRepo.getPackageName())
                .isEqualTo("br.com.ziipsan.intelligence.gis.repository");
        assertThat(validationRepo.getAnnotation(Repository.class)).isNotNull();
        assertThat(validationRepo.getMethod("findOpenErrorRulesByFeatureId",
                UUID.class, Collection.class)).isNotNull();
    }
}
