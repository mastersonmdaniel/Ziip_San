package br.com.ziipsan.intelligence.gis.epanet;

import br.com.ziipsan.intelligence.gis.epanet.ElementSpec.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão da view de compatibilidade (GIS-01, M3), no nível em que o Java
 * pode prová-la sem banco: dada a MESMA leitura de elementos — a que a tabela
 * antiga devolvia e a que a view devolve — o .inp gerado é byte a byte igual.
 *
 * <p>A outra metade da prova (a view devolver de fato as mesmas colunas que a
 * tabela antiga) é da IT Gis01MigrationTest, que compara linha a linha no
 * PostgreSQL real. Nenhuma das duas metades, sozinha, prova o .inp igual; as
 * duas juntas, sim: colunas iguais entram no builder, e o builder é função
 * determinística dessas colunas.
 *
 * <p>Desde a GIS-03 (M4) a leitura inclui {@code node_1_code}/{@code node_2_code}
 * dos arcos, que a view passou a expor da topologia persistida; a massa de
 * referência os declara e a prova continua a mesma.
 */
class GisCompatibilityViewInpTest {

    private static final GeometryFactory GF = new GeometryFactory();

    private static Point pt(double lon, double lat) {
        return GF.createPoint(new Coordinate(lon, lat));
    }

    private static LineString line(double... lonLat) {
        Coordinate[] cs = new Coordinate[lonLat.length / 2];
        for (int i = 0; i < cs.length; i++) cs[i] = new Coordinate(lonLat[2 * i], lonLat[2 * i + 1]);
        return GF.createLineString(cs);
    }

    private static Map<String, BigDecimal> nums(Object... kv) {
        Map<String, BigDecimal> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], new BigDecimal(kv[i + 1].toString()));
        return m;
    }

    /** Massa de referência: poço, duas junções e dois tubos, como a IT monta no banco. */
    private static List<ElementSpec> referenceMass() {
        return List.of(
                new ElementSpec("W1", "W1", Kind.RESERVOIR, pt(-46.0000, -23.0), null, null, null, null,
                        nums("head_m", 100)),
                new ElementSpec("J1", "J1", Kind.JUNCTION, pt(-46.0010, -23.0), null, null, null, null,
                        nums("elevation_m", 90.0, "demand_lps", 2.5)),
                new ElementSpec("J2", "J2", Kind.JUNCTION, pt(-46.0020, -23.0), null, null, null, null,
                        nums("elevation_m", 85.0, "demand_lps", 1.0)),
                new ElementSpec("P1", "P1", Kind.PIPE, line(-46.0000, -23.0, -46.0010, -23.0), 150,
                        new BigDecimal("110"), "PVC", null, new HashMap<>(), "W1", "J1"),
                new ElementSpec("P2", "P2", Kind.PIPE, line(-46.0010, -23.0, -46.0020, -23.0), 100,
                        new BigDecimal("110"), "Aço", null, new HashMap<>(), "J1", "J2"));
    }

    @Test
    @DisplayName("mesma leitura de elementos, mesmo .inp — duas construções independentes")
    void sameSpecsProduceSameInp() {
        String fromLegacyTable = InpWriter.write(
                NetworkTopologyBuilder.build("DMA", "d", referenceMass()));
        String fromCompatibilityView = InpWriter.write(
                NetworkTopologyBuilder.build("DMA", "d", referenceMass()));

        assertThat(fromCompatibilityView).isEqualTo(fromLegacyTable);
        assertThat(fromCompatibilityView).contains("W1").contains("J1").contains("P1");
    }

    @Test
    @DisplayName("comprimento cadastrado vence o geodesico — por isso a view favorece o declarado")
    void declaredLengthWinsOverGeodesic() {
        EpanetNetwork network = NetworkTopologyBuilder.build("DMA", "d", referenceMass());
        assertThat(network.getPipes()).hasSize(2);
        assertThat(network.getPipes().get(0).getLength()).isEqualTo(110.0);
    }
}
