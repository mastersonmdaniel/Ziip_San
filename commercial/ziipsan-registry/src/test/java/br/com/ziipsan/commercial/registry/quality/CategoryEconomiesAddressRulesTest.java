package br.com.ziipsan.commercial.registry.quality;

import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.BlockFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.RouteFact;
import br.com.ziipsan.commercial.registry.quality.CadastralSnapshot.UnitFact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.quality.QualityFixture.REF;
import static br.com.ziipsan.commercial.registry.quality.QualityFixture.id;
import static br.com.ziipsan.commercial.registry.quality.QualityFixture.only;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.32 - categoria x CNAE, economias x area e endereco (coordenada/setor)
 * sobre massa sintetica, com os numeros conferidos a mao nos comentarios.
 */
class CategoryEconomiesAddressRulesTest {

    private final CadastralQualityEngine engine = new CadastralQualityEngine();

    private UnitFact unitWith(UUID unitId, String cnae, String area) {
        return new UnitFact(unitId, "ACTIVE", null, null, null, null, id(800),
                true, Boolean.TRUE, cnae, area == null ? null : new BigDecimal(area), false);
    }

    @Test
    void categoriaIncompativelComCnaeGeraECompativelNao() {
        // Divisao 47 (comercio varejista) so aceita COMMERCIAL na massa.
        var snapshot = CadastralSnapshot.builder()
                .unit(unitWith(id(1), "4711301", null))
                .economy(QualityFixture.economies(id(1), "RESIDENTIAL", 1))
                .unit(unitWith(id(2), "4711301", null))
                .economy(QualityFixture.economies(id(2), "COMMERCIAL", 1))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        var findings = only(result, QualityRuleCode.CATEGORY_CNAE_MISMATCH);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).unitId()).isEqualTo(id(1));
        assertThat(findings.get(0).detail()).contains("4711301").contains("RESIDENTIAL");
    }

    @Test
    void divisaoSemPoliticaECnaeAusenteOuMalformadoNaoSaoJulgados() {
        var snapshot = CadastralSnapshot.builder()
                .unit(unitWith(id(3), "9999999", null))
                .economy(QualityFixture.economies(id(3), "RESIDENTIAL", 1))
                .unit(unitWith(id(4), null, null))
                .economy(QualityFixture.economies(id(4), "RESIDENTIAL", 1))
                .unit(unitWith(id(5), "4711", null))
                .economy(QualityFixture.economies(id(5), "RESIDENTIAL", 1))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.CATEGORY_CNAE_MISMATCH)).isEmpty();
    }

    @Test
    void categoriaPrincipalEAMDeMaisEconomiasComDesempateAlfabetico() {
        // Empate 2 x 2 entre COMMERCIAL e RESIDENTIAL: principal = COMMERCIAL
        // (alfabetico), compativel com a divisao 47 -> sem achado.
        var snapshot = CadastralSnapshot.builder()
                .unit(unitWith(id(6), "4711301", null))
                .economy(QualityFixture.economies(id(6), "RESIDENTIAL", 2))
                .economy(QualityFixture.economies(id(6), "COMMERCIAL", 2))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.CATEGORY_CNAE_MISMATCH)).isEmpty();
    }

    @Test
    void economiasAcimaDoMaximoDaAreaGeramENoLimiteNao() {
        // RESIDENTIAL: 20 m2/economia. Area 100 -> maximo piso(100/20) = 5.
        // Unidade A com 6 economias: gera. Unidade B com 5: no limite, nao gera.
        var snapshot = CadastralSnapshot.builder()
                .unit(unitWith(id(10), null, "100"))
                .economy(QualityFixture.economies(id(10), "RESIDENTIAL", 6))
                .unit(unitWith(id(11), null, "100"))
                .economy(QualityFixture.economies(id(11), "RESIDENTIAL", 5))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        var findings = only(result, QualityRuleCode.ECONOMIES_AREA_MISMATCH);
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).unitId()).isEqualTo(id(10));
        assertThat(findings.get(0).detail()).contains("6").contains("5");
    }

    @Test
    void areaMenorQueAMinimaToleraUmaEconomiaSo() {
        // Area 10, minima 20 -> piso(10/20) = 0, maximo efetivo max(1, 0) = 1.
        // 1 economia passa; 2 geram.
        var snapshot = CadastralSnapshot.builder()
                .unit(unitWith(id(12), null, "10"))
                .economy(QualityFixture.economies(id(12), "RESIDENTIAL", 1))
                .unit(unitWith(id(13), null, "10"))
                .economy(QualityFixture.economies(id(13), "RESIDENTIAL", 2))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.ECONOMIES_AREA_MISMATCH))
                .extracting(QualityFinding::unitId)
                .containsExactly(id(13));
    }

    @Test
    void semAreaOuSemLimiarDaClasseNaoJulga() {
        var snapshot = CadastralSnapshot.builder()
                .unit(unitWith(id(14), null, null))
                .economy(QualityFixture.economies(id(14), "RESIDENTIAL", 50))
                .unit(unitWith(id(15), null, "100"))
                .economy(QualityFixture.economies(id(15), "PUBLIC", 50))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        // PUBLIC nao tem limiar na massa: nao julgada. Sem area: nao julgada.
        assertThat(only(result, QualityRuleCode.ECONOMIES_AREA_MISMATCH)).isEmpty();
    }

    @Test
    void enderecoSemCoordenadaGeraESemEnderecoTambem() {
        var semCoord = new UnitFact(id(20), "ACTIVE", null, null, null, null, id(801),
                false, Boolean.FALSE, null, null, false);
        var coordNaUnidade = new UnitFact(id(21), "ACTIVE", null, null, null, null, id(802),
                true, Boolean.FALSE, null, null, false);
        var coordNoEndereco = new UnitFact(id(22), "ACTIVE", null, null, null, null, id(803),
                false, Boolean.TRUE, null, null, false);
        var semEndereco = new UnitFact(id(23), "ACTIVE", null, null, null, null, null,
                false, null, null, null, false);
        var snapshot = CadastralSnapshot.builder()
                .unit(semCoord).unit(coordNaUnidade).unit(coordNoEndereco).unit(semEndereco)
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.ADDRESS_WITHOUT_COORDINATES))
                .extracting(QualityFinding::unitId)
                .containsExactlyInAnyOrder(id(20), id(23));
    }

    @Test
    void enderecoForaDoSetorQuandoRotaEstaEmOutroSetor() {
        var setorA = id(100);
        var setorB = id(101);
        var quadra = id(110);
        var rota = id(120);
        var fora = new UnitFact(id(30), "ACTIVE", quadra, 1, 1, rota, id(804),
                true, Boolean.TRUE, null, null, false);
        var dentro = new UnitFact(id(31), "ACTIVE", quadra, 2, 1, id(121), id(805),
                true, Boolean.TRUE, null, null, false);
        var snapshot = CadastralSnapshot.builder()
                .unit(fora).unit(dentro)
                .block(new BlockFact(quadra, setorA))
                .route(new RouteFact(rota, setorB))
                .route(new RouteFact(id(121), setorA))
                .build();

        var result = engine.evaluate(snapshot, QualityFixture.fullParams(), REF);

        assertThat(only(result, QualityRuleCode.ADDRESS_OUT_OF_SECTOR))
                .extracting(QualityFinding::unitId)
                .containsExactly(id(30));
    }

    @Test
    void semMapaCnaeOuSemMapaDeAreaRegraFicaNaoConfigurada() {
        var params = QualityFixture.paramsWithRules("{\"rules\": {}}");
        var snapshot = CadastralSnapshot.builder()
                .unit(unitWith(id(40), "4711301", "100"))
                .economy(QualityFixture.economies(id(40), "RESIDENTIAL", 9))
                .build();

        var result = engine.evaluate(snapshot, params, REF);

        assertThat(result.skipped())
                .filteredOn(s -> s.reason() == CadastralQualityEngine.SkipReason.UNCONFIGURED)
                .extracting(s -> s.code().name())
                .contains("CATEGORY_CNAE_MISMATCH", "ECONOMIES_AREA_MISMATCH",
                        "UNIT_WITHOUT_READING", "METER_VERIFICATION_EXPIRED", "BILL_UNDELIVERED");
    }
}
