package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.intelligence.gis.validation.Gis05OrphanKpi.Category;
import br.com.ziipsan.intelligence.gis.validation.Gis05OrphanKpi.Chain;
import br.com.ziipsan.intelligence.gis.validation.Gis05OrphanKpi.Correction;
import br.com.ziipsan.intelligence.gis.validation.Gis05OrphanKpi.Report;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ConnectionFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.DetectedIssue;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ServiceConnectionFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KPI de orfaos da GIS-05 (Ciclo 4 do programa 5x PDCA GIS): a cadeia
 * feicao (ramal) <-> ligacao <-> imovel <-> unidades, medida sobre
 * fixtures em memoria — nenhuma base real e lida aqui; o zero real de
 * um tenant so se declara com a fiacao de leitura em BD
 * (pendencia P-PDCAGIS4-01).
 */
class Gis05OrphanKpiTest {

    private static final UUID PROPERTY = UUID.randomUUID();
    private static final UUID CONNECTION = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID LINE = UUID.randomUUID();

    private static ConnectionFeature line(UUID id, String status, UUID serviceConnectionId) {
        return new ConnectionFeature(id, "WATER_SERVICE_LINE", "SERVICE_LINE", "WATER",
                status, UUID.randomUUID(), null, serviceConnectionId,
                20, "PVC", false, "GPS", "EXACT", null);
    }

    private static ServiceConnectionFeature connection(UUID id, UUID property, String role,
                                                       boolean open, long lineCount) {
        return new ServiceConnectionFeature(id, property, "WATER", role, open, lineCount);
    }

    private static Chain completeChain() {
        return new Chain(
                List.of(line(LINE, "IN_SERVICE", CONNECTION)),
                List.of(connection(CONNECTION, PROPERTY, "ACTIVE", true, 1)),
                Set.of(PROPERTY),
                Map.of(CONNECTION, List.of(UNIT)));
    }

    @Test
    @DisplayName("cadeia completa (ramal<->ligacao<->imovel<->unidade) mede zero")
    void completeChainIsZero() {
        Report report = Gis05OrphanKpi.measure(completeChain());
        assertThat(report.zero()).isTrue();
        assertThat(report.total()).isZero();
        assertThat(report.orphans()).isEmpty();
        assertThat(report.countByCategory()).allSatisfy((c, n) -> assertThat(n).isZero());
    }

    @Test
    @DisplayName("ramal activo sem ligacao e orfao RAMAL_SEM_LIGACAO, correccao pelo pacote GIS-04")
    void lineWithoutConnectionIsOrphan() {
        Chain chain = new Chain(
                List.of(line(LINE, "IN_SERVICE", null)),
                List.of(connection(CONNECTION, PROPERTY, "ACTIVE", true, 1)),
                Set.of(PROPERTY),
                Map.of(CONNECTION, List.of(UNIT)));
        Report report = Gis05OrphanKpi.measure(chain);
        assertThat(report.byCategory(Category.RAMAL_SEM_LIGACAO)).hasSize(1);
        assertThat(report.byCategory(Category.RAMAL_SEM_LIGACAO).get(0).featureId()).isEqualTo(LINE);
        assertThat(report.byCategory(Category.RAMAL_SEM_LIGACAO).get(0).correction())
                .isEqualTo(Correction.VINCULAR_EM_PACOTE_DE_CORRECAO_GIS04);
    }

    @Test
    @DisplayName("ramal activo com vinculo para ligacao fora do retrato e orfao de vinculo pendurado")
    void lineWithDanglingLinkIsOrphan() {
        UUID dangling = UUID.randomUUID();
        Chain chain = new Chain(
                List.of(line(LINE, "IN_SERVICE", dangling)),
                List.of(connection(CONNECTION, PROPERTY, "ACTIVE", true, 1)),
                Set.of(PROPERTY),
                Map.of(CONNECTION, List.of(UNIT)));
        Report report = Gis05OrphanKpi.measure(chain);
        assertThat(report.byCategory(Category.RAMAL_COM_VINCULO_PENDURADO)).hasSize(1);
        assertThat(report.byCategory(Category.RAMAL_COM_VINCULO_PENDURADO).get(0).relatedId())
                .isEqualTo(dangling);
    }

    @Test
    @DisplayName("ligacao activa aberta sem ramal e orfao LIGACAO_SEM_RAMAL, correccao pelo ramal automatico GIS-05")
    void connectionWithoutLineIsOrphan() {
        Chain chain = new Chain(
                List.of(),
                List.of(connection(CONNECTION, PROPERTY, "ACTIVE", true, 0)),
                Set.of(PROPERTY),
                Map.of(CONNECTION, List.of(UNIT)));
        Report report = Gis05OrphanKpi.measure(chain);
        assertThat(report.byCategory(Category.LIGACAO_SEM_RAMAL)).hasSize(1);
        assertThat(report.byCategory(Category.LIGACAO_SEM_RAMAL).get(0).correction())
                .isEqualTo(Correction.GERAR_RAMAL_AUTOMATICO_GIS05);
    }

    @Test
    @DisplayName("ligacao NO_CONNECTION sem ramal nao e orfao (mesma excecao do motor de validacao)")
    void noConnectionRoleWithoutLineIsNotOrphan() {
        Chain chain = new Chain(
                List.of(),
                List.of(connection(CONNECTION, PROPERTY, "NO_CONNECTION", true, 0)),
                Set.of(PROPERTY),
                Map.of(CONNECTION, List.of(UNIT)));
        assertThat(Gis05OrphanKpi.measure(chain).byCategory(Category.LIGACAO_SEM_RAMAL)).isEmpty();
    }

    @Test
    @DisplayName("ligacao encerrada nao gera orfaos de ligacao")
    void endedConnectionGeneratesNoOrphans() {
        Chain chain = new Chain(
                List.of(),
                List.of(connection(CONNECTION, UUID.randomUUID(), "ACTIVE", false, 0)),
                Set.of(),
                Map.of());
        assertThat(Gis05OrphanKpi.measure(chain).total()).isZero();
    }

    @Test
    @DisplayName("ligacao aberta com ancora fora do retrato de imoveis e orfao LIGACAO_SEM_IMOVEL")
    void connectionWithoutPropertyIsOrphan() {
        Chain chain = new Chain(
                List.of(line(LINE, "IN_SERVICE", CONNECTION)),
                List.of(connection(CONNECTION, UUID.randomUUID(), "ACTIVE", true, 1)),
                Set.of(PROPERTY),
                Map.of(CONNECTION, List.of(UNIT)));
        Report report = Gis05OrphanKpi.measure(chain);
        assertThat(report.byCategory(Category.LIGACAO_SEM_IMOVEL)).hasSize(1);
        assertThat(report.byCategory(Category.LIGACAO_SEM_IMOVEL).get(0).correction())
                .isEqualTo(Correction.REANCORAR_LIGACAO_NO_CADASTRO);
    }

    @Test
    @DisplayName("ligacao aberta sem nenhuma unidade consumidora vinculada e orfao LIGACAO_SEM_UNIDADE")
    void connectionWithoutUnitsIsOrphan() {
        Chain chain = new Chain(
                List.of(line(LINE, "IN_SERVICE", CONNECTION)),
                List.of(connection(CONNECTION, PROPERTY, "ACTIVE", true, 1)),
                Set.of(PROPERTY),
                Map.of());
        Report report = Gis05OrphanKpi.measure(chain);
        assertThat(report.byCategory(Category.LIGACAO_SEM_UNIDADE)).hasSize(1);
        assertThat(report.byCategory(Category.LIGACAO_SEM_UNIDADE).get(0).correction())
                .isEqualTo(Correction.VINCULAR_UNIDADES_NO_CADASTRO);
    }

    @Test
    @DisplayName("imovel sem nenhuma ligacao aberta e orfao IMOVEL_SEM_LIGACAO, correccao criar a ligacao no cadastro")
    void propertyWithoutConnectionIsOrphan() {
        Chain chain = new Chain(List.of(), List.of(), Set.of(PROPERTY), Map.of());
        Report report = Gis05OrphanKpi.measure(chain);
        assertThat(report.byCategory(Category.IMOVEL_SEM_LIGACAO)).hasSize(1);
        assertThat(report.byCategory(Category.IMOVEL_SEM_LIGACAO).get(0).featureId()).isEqualTo(PROPERTY);
        assertThat(report.byCategory(Category.IMOVEL_SEM_LIGACAO).get(0).correction())
                .isEqualTo(Correction.CRIAR_LIGACAO_NO_CADASTRO);
    }

    @Test
    @DisplayName("ramal ABANDONED ou PLANNED sem ligacao nao e orfao de vinculo")
    void abandonedOrPlannedLinesAreNotLinkOrphans() {
        Chain chain = new Chain(
                List.of(line(UUID.randomUUID(), "ABANDONED", null),
                        line(UUID.randomUUID(), "PLANNED", null)),
                List.of(connection(CONNECTION, PROPERTY, "ACTIVE", true, 1)),
                Set.of(PROPERTY),
                Map.of(CONNECTION, List.of(UNIT)));
        Report report = Gis05OrphanKpi.measure(chain);
        assertThat(report.byCategory(Category.RAMAL_SEM_LIGACAO)).isEmpty();
        assertThat(report.byCategory(Category.RAMAL_COM_VINCULO_PENDURADO)).isEmpty();
    }

    @Test
    @DisplayName("as categorias partilhadas coincidem com as regras do motor de validacao no mesmo retrato")
    void sharedCategoriesAgreeWithValidationEngine() {
        ConnectionFeature loose = line(LINE, "IN_SERVICE", null);
        ServiceConnectionFeature active = connection(CONNECTION, PROPERTY, "ACTIVE", true, 0);
        Chain chain = new Chain(List.of(loose), List.of(active), Set.of(PROPERTY),
                Map.of(CONNECTION, List.of(UNIT)));
        Report report = Gis05OrphanKpi.measure(chain);

        List<DetectedIssue> issues = GisValidationEngine.detect(new GisValidationEngine.Snapshot(
                List.of(), List.of(), List.of(loose), List.of(), List.of(), Set.of(), List.of(active)));
        long engineWithoutLink = issues.stream().filter(i -> i.ruleCode().equals("CONNECTION_WITHOUT_LINK")).count();
        long engineWithoutLine = issues.stream().filter(i -> i.ruleCode().equals("ACTIVE_CONNECTION_WITHOUT_SERVICE_LINE")).count();
        assertThat(report.countByCategory().get(Category.RAMAL_SEM_LIGACAO)).isEqualTo(engineWithoutLink);
        assertThat(report.countByCategory().get(Category.LIGACAO_SEM_RAMAL)).isEqualTo(engineWithoutLine);
        assertThat(engineWithoutLink).isEqualTo(1);
        assertThat(engineWithoutLine).isEqualTo(1);
    }

    @Test
    @DisplayName("relatorio agrega por categoria, soma o total e e deterministico")
    void reportAggregatesDeterministically() {
        Chain chain = new Chain(
                List.of(line(UUID.randomUUID(), "IN_SERVICE", null),
                        line(UUID.randomUUID(), "IN_SERVICE", UUID.randomUUID())),
                List.of(connection(CONNECTION, PROPERTY, "ACTIVE", true, 0)),
                Set.of(PROPERTY, UUID.randomUUID()),
                Map.of());
        Report first = Gis05OrphanKpi.measure(chain);
        Report second = Gis05OrphanKpi.measure(chain);
        assertThat(first.orphans()).isEqualTo(second.orphans());
        long sum = first.countByCategory().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sum).isEqualTo(first.total());
        assertThat(first.zero()).isFalse();
        // 2 ramais orfaos + ligacao sem ramal + ligacao sem unidade + 1 imovel solto
        assertThat(first.total()).isEqualTo(5);
    }
}
