package br.com.ziipsan.intelligence.gis.drain;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.DrainFeatureInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.DrainFeatureView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.ValidationIssueView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correção D1 da revisão da ED-64: a validação de declividade/
 * sentido do §5.4 VALE na gravação — {@code createFeature} calcula
 * o comprimento da geometria de entrada e RECUSA arco com
 * declividade adversa/nula ou sentido contrário às cotas, e o
 * endpoint de validação ({@code validateArc}) invoca
 * {@code checkFlowDirection}. Sem banco: um JdbcTemplate falso
 * registra as escritas — o que se prova é que, na recusa, NENHUM
 * INSERT acontece, e que o arco válido grava. O comportamento em
 * PostgreSQL/PostGIS real é da IT {@code Gis13DrainIT} (CI do dono).
 */
class GisDrainServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    /** Linha de ~99 m em SIRGAS 2000 (lon/lat), sentido oeste→leste. */
    private static final Map<String, Object> LINE = Map.of(
            "type", "LineString",
            "coordinates", List.of(
                    List.of(-48.6700, -26.9900),
                    List.of(-48.6690, -26.9900)));

    /** JdbcTemplate falso: escritas registradas, leituras-fixture. */
    static final class FakeJdbc extends JdbcTemplate {
        final List<String> updateSqls = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updateSqls.add(sql);
            return 1;
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            return List.of(elementType.cast(UUID.randomUUID()));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return (List<T>) List.of(new DrainFeatureView(UUID.randomUUID(), "GAL-01",
                    "STORM_PIPE", "gis_arc", "Galeria", "IN_SERVICE", null, Map.of()));
        }
    }

    private FakeJdbc jdbc;
    private GisDrainService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(USER);
        jdbc = new FakeJdbc();
        service = new GisDrainService(jdbc);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static DrainFeatureInput arc(Double upstream, Double downstream, String flow) {
        return arc(LINE, upstream, downstream, flow);
    }

    private static DrainFeatureInput arc(Map<String, Object> geometry,
                                         Double upstream, Double downstream, String flow) {
        return new DrainFeatureInput("GAL-01", "STORM_PIPE", "Galeria", "IN_SERVICE",
                "DESIGN", null, Map.of("dn_mm", 600), geometry, "PV-01", "PV-02",
                upstream, downstream, flow);
    }

    @Test
    void declividadeAdversaRecusaGravacaoSemInsert() {
        DrainFeatureInput in = arc(9.0, 10.0, "WITH_GEOMETRY");
        assertThatThrownBy(() -> service.createFeature(in))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("STORM_SLOPE_ADVERSE");
        assertThat(jdbc.updateSqls).isEmpty();
    }

    @Test
    void declividadeNulaRecusaGravacaoSemInsert() {
        DrainFeatureInput in = arc(10.0, 10.0, "WITH_GEOMETRY");
        assertThatThrownBy(() -> service.createFeature(in))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("STORM_SLOPE_FLAT");
        assertThat(jdbc.updateSqls).isEmpty();
    }

    @Test
    void sentidoInvertidoRecusaGravacaoSemInsert() {
        // WITH_GEOMETRY com o fundo SUBINDO de montante para jusante:
        // o endpoint de validacao acusa o sentido (checkFlowDirection)
        // e a gravacao recusa — nenhum INSERT.
        DrainFeatureInput in = arc(9.0, 10.0, "WITH_GEOMETRY");
        assertThat(service.validateArc(in, 100.0, null))
                .extracting(ValidationIssueView::ruleCode)
                .contains("STORM_FLOW_AGAINST_INVERTS");
        assertThatThrownBy(() -> service.createFeature(in))
                .isInstanceOf(ApiException.BadRequest.class);
        assertThat(jdbc.updateSqls).isEmpty();
    }

    @Test
    void endpointDeValidacaoAcusaSentidoMesmoSemComprimento() {
        // lengthM = 0 nao silencia mais o sentido: a declividade nao
        // se calcula sem comprimento, mas o sentido contra as cotas
        // continua acusado pelo endpoint que promete "sentido".
        List<ValidationIssueView> issues = service.validateArc(
                arc(9.0, 10.0, "WITH_GEOMETRY"), 0, null);
        assertThat(issues).extracting(ValidationIssueView::ruleCode)
                .containsExactly("STORM_FLOW_AGAINST_INVERTS");
    }

    @Test
    void sentidoContraGeometriaCoerenteGrava() {
        // AGAINST_GEOMETRY: o escoamento vai do no 2 (montante,
        // cota 10) ao no 1 (jusante, cota 9) — coerente, grava.
        DrainFeatureView view = service.createFeature(arc(10.0, 9.0, "AGAINST_GEOMETRY"));
        assertThat(view).isNotNull();
        assertThat(jdbc.updateSqls).hasSize(1);
        assertThat(jdbc.updateSqls.get(0)).contains("INSERT INTO gis_arc");
    }

    @Test
    void arcoValidoComGeometriaGrava() {
        DrainFeatureView view = service.createFeature(arc(10.0, 9.0, "WITH_GEOMETRY"));
        assertThat(view).isNotNull();
        assertThat(jdbc.updateSqls).hasSize(1);
        assertThat(jdbc.updateSqls.get(0)).contains("INSERT INTO gis_arc");
    }

    @Test
    void geometriaSemComprimentoComCotasRecusaGravacao() {
        // Ponto no lugar da linha: com as duas cotas informadas, nao
        // ha comprimento para provar a declividade — recusa, nao
        // grava em silencio (era o nucleo do defeito D1).
        Map<String, Object> point = Map.of(
                "type", "Point", "coordinates", List.of(-48.6700, -26.9900));
        DrainFeatureInput in = arc(point, 10.0, 9.0, "WITH_GEOMETRY");
        assertThatThrownBy(() -> service.createFeature(in))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("comprimento do arco nao determinavel");
        assertThat(jdbc.updateSqls).isEmpty();
    }
}
