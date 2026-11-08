package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.BlockFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.UnitFact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.ziipsan.commercial.registry.quality.QualityFixture.id;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.32 - pontuacao, completude e tendencia recomputadas a mao:
 * pontuacao = max(0, 100 - (pontos x 10) / unidades);
 * completude = unidades sem achado / unidades x 100.
 */
class QualityScoreCalculatorTest {

    private final QualityScoreCalculator calculator = new QualityScoreCalculator();

    private List<UnitFact> units(int routeN, int count, int firstId) {
        List<UnitFact> units = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            units.add(new UnitFact(id(firstId + i), "ACTIVE", null, null, null, id(routeN),
                    id(900 + i), true, Boolean.TRUE, null, null, false));
        }
        return units;
    }

    private QualityFinding finding(QualityRuleCode code, QualitySeverity severity,
                                   java.util.UUID unitId) {
        return new QualityFinding(code, severity, unitId, null, "evidencia de teste");
    }

    @Test
    void pontuacaoECompletudeDoTenantConferidasAMao() {
        // 10 unidades; achados: CRITICAL(10) + HIGH(5) + MEDIUM(2) + MEDIUM(2)
        // + LOW(1) = 20 pontos, em 4 unidades distintas.
        // pontuacao = 100 - (20 x 10)/10 = 100 - 20 = 80.00
        // completude = 6/10 x 100 = 60.00
        var units = units(500, 10, 1000);
        var findings = List.of(
                finding(QualityRuleCode.DUPLICATE_DOCUMENT, QualitySeverity.CRITICAL, id(1000)),
                finding(QualityRuleCode.DOCUMENT_MISSING, QualitySeverity.HIGH, id(1001)),
                finding(QualityRuleCode.CONTACT_UNVERIFIED, QualitySeverity.MEDIUM, id(1002)),
                finding(QualityRuleCode.ADDRESS_WITHOUT_COORDINATES, QualitySeverity.MEDIUM, id(1003)),
                finding(QualityRuleCode.BILL_UNDELIVERED, QualitySeverity.LOW, id(1003)));

        var score = calculator.tenantScore(units, findings);

        assertThat(score.groupType()).isEqualTo("TENANT");
        assertThat(score.totalUnits()).isEqualTo(10);
        assertThat(score.unitsWithFindings()).isEqualTo(4);
        assertThat(score.findingsCount()).isEqualTo(5);
        assertThat(score.penaltyPoints()).isEqualTo(20);
        assertThat(score.score()).isEqualByComparingTo("80.00");
        assertThat(score.completenessPercent()).isEqualByComparingTo("60.00");
    }

    @Test
    void pontuacaoPorRotaSeparaOsGrupos() {
        // Rota A (id 500): 4 unidades, 1 achado HIGH(5) -> 100 - 50/4 = 87.50;
        // completude 3/4 = 75.00. Rota B (id 501): 2 unidades, sem achado -> 100.00 / 100.00.
        var unitsA = units(500, 4, 2000);
        var unitsB = units(501, 2, 3000);
        var all = new ArrayList<>(unitsA);
        all.addAll(unitsB);
        var findings = List.of(
                finding(QualityRuleCode.UNIT_WITHOUT_READING, QualitySeverity.HIGH, id(2000)));

        var byRoute = calculator.byRoute(all, findings);

        assertThat(byRoute).hasSize(2);
        var routeA = byRoute.stream().filter(g -> g.groupId().equals(id(500))).findFirst().orElseThrow();
        var routeB = byRoute.stream().filter(g -> g.groupId().equals(id(501))).findFirst().orElseThrow();
        assertThat(routeA.score()).isEqualByComparingTo("87.50");
        assertThat(routeA.completenessPercent()).isEqualByComparingTo("75.00");
        assertThat(routeB.score()).isEqualByComparingTo("100.00");
        assertThat(routeB.completenessPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void pontuacaoPorSetorUsaAQuadraDaUnidade() {
        // Setor S (id 600) via quadra Q (id 610): 5 unidades, penalidade 10
        // (1 CRITICAL) -> 100 - 100/5 = 80.00. Unidade sem quadra nao entra.
        var sector = id(600);
        var block = id(610);
        List<UnitFact> units = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            units.add(new UnitFact(id(4000 + i), "ACTIVE", block, i, 1, null, id(950 + i),
                    true, Boolean.TRUE, null, null, false));
        }
        units.add(new UnitFact(id(4999), "ACTIVE", null, null, null, null, id(959),
                true, Boolean.TRUE, null, null, false));
        var findings = List.of(
                finding(QualityRuleCode.DUPLICATE_INSCRIPTION, QualitySeverity.CRITICAL, id(4000)));

        var bySector = calculator.bySector(units, findings, List.of(new BlockFact(block, sector)));

        assertThat(bySector).hasSize(1);
        assertThat(bySector.get(0).groupId()).isEqualTo(sector);
        assertThat(bySector.get(0).totalUnits()).isEqualTo(5);
        assertThat(bySector.get(0).score()).isEqualByComparingTo("80.00");
    }

    @Test
    void pontuacaoNuncaFicaNegativa() {
        // 1 unidade, 3 CRITICAL = 30 pontos -> 100 - 300 = -200 -> piso 0.00.
        var units = units(500, 1, 5000);
        var findings = List.of(
                finding(QualityRuleCode.DUPLICATE_DOCUMENT, QualitySeverity.CRITICAL, id(5000)),
                finding(QualityRuleCode.DUPLICATE_DOCUMENT, QualitySeverity.CRITICAL, id(5000)),
                finding(QualityRuleCode.DUPLICATE_DOCUMENT, QualitySeverity.CRITICAL, id(5000)));

        var score = calculator.tenantScore(units, findings);

        assertThat(score.score()).isEqualByComparingTo("0.00");
        assertThat(score.completenessPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void achadoDeClienteContaNoTenantENoGrupoPeloMapa() {
        // Achado de cliente (sem unidade) com mapa cliente -> unidade da
        // rota: penalidade HIGH(5) em 4 unidades -> 100 - 50/4 = 87.50.
        var units = units(500, 4, 6000);
        var customerFinding = new QualityFinding(QualityRuleCode.DOCUMENT_MISSING,
                QualitySeverity.HIGH, null, id(7000), "documento ausente");
        var withMap = new QualityScoreCalculator().withCustomerUnits(Map.of(id(7000), id(6001)));

        var tenant = withMap.tenantScore(units, List.of(customerFinding));
        assertThat(tenant.penaltyPoints()).isEqualTo(5);
        assertThat(tenant.unitsWithFindings()).isEqualTo(1);

        var byRoute = withMap.byRoute(units, List.of(customerFinding));
        assertThat(byRoute.get(0).score()).isEqualByComparingTo("87.50");

        // Sem o mapa, o achado de cliente NAO entra na rota (so no tenant).
        var noMapRoute = calculator.byRoute(units, List.of(customerFinding));
        assertThat(noMapRoute.get(0).penaltyPoints()).isZero();
        var noMapTenant = calculator.tenantScore(units, List.of(customerFinding));
        assertThat(noMapTenant.penaltyPoints()).isEqualTo(5);
        assertThat(noMapTenant.unitsWithFindings()).isZero();
    }

    @Test
    void pontuacaoNoMeioExatoArredondaSoNoResultado() {
        // Correcao R1: 16 unidades, 1 achado LOW = 1 ponto de penalidade.
        // Formula: 100 - (1 x 10)/16 = 100 - 0,625 = 99,375 -> HALF_UP 99.38.
        // (Arredondar a divisao a 2 casas ANTES de subtrair dava 99,37.)
        var units = units(500, 16, 7000);
        var findings = List.of(
                finding(QualityRuleCode.BILL_UNDELIVERED, QualitySeverity.LOW, id(7000)));

        var score = calculator.tenantScore(units, findings);

        assertThat(score.score()).isEqualByComparingTo("99.38");
    }

    @Test
    void withCustomerUnitsNaoMutaAInstanciaOriginal() {
        // Correcao R2: a calculadora e imutavel; o mapa vai numa instancia
        // nova e a original continua sem atribuicao cliente -> unidade.
        var units = units(500, 4, 8000);
        var customerFinding = new QualityFinding(QualityRuleCode.DOCUMENT_MISSING,
                QualitySeverity.HIGH, null, id(9000), "documento ausente");

        var original = new QualityScoreCalculator();
        var derived = original.withCustomerUnits(Map.of(id(9000), id(8001)));

        assertThat(derived).isNotSameAs(original);
        assertThat(original.byRoute(units, List.of(customerFinding)).get(0).penaltyPoints())
                .isZero();
        assertThat(derived.byRoute(units, List.of(customerFinding)).get(0).penaltyPoints())
                .isEqualTo(5);
    }

    @Test
    void tendenciaSobeDesceEFica() {
        var up = QualityScoreCalculator.Trend.compare(new BigDecimal("80.00"), new BigDecimal("85.50"));
        assertThat(up.delta()).isEqualByComparingTo("5.50");
        assertThat(up.direction()).isEqualTo(QualityScoreCalculator.Trend.Direction.UP);

        var down = QualityScoreCalculator.Trend.compare(new BigDecimal("85.50"), new BigDecimal("80.00"));
        assertThat(down.delta()).isEqualByComparingTo("-5.50");
        assertThat(down.direction()).isEqualTo(QualityScoreCalculator.Trend.Direction.DOWN);

        var flat = QualityScoreCalculator.Trend.compare(new BigDecimal("80.00"), new BigDecimal("80.00"));
        assertThat(flat.delta()).isEqualByComparingTo("0.00");
        assertThat(flat.direction()).isEqualTo(QualityScoreCalculator.Trend.Direction.FLAT);
    }
}
