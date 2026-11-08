package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ThematicInput;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicService.ThematicRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-08 (P3.27): as guardas do serviço no ponto em que a revisão
 * costuma encontrar a falha silenciosa — permissão pessoal ANTES
 * de qualquer consulta, OS fora do canal público e a recusa do
 * temático não configurado. O JdbcTemplate falso conta as chamadas:
 * guarda que consulta antes de recusar reprova aqui.
 */
class GisThematicServiceTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private FakeJdbc jdbc;
    private GisThematicService serviceWithPersonal;
    private GisThematicService serviceWithoutPersonal;

    /** Devolve a linha presetada na leitura por chave e conta todo o resto. */
    static final class FakeJdbc extends JdbcTemplate {
        ThematicRow row;
        /** Linhas de ativo servidas ao RowCallbackHandler (canal público de ativos). */
        int assetRows;
        final AtomicInteger otherCalls = new AtomicInteger();

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("FROM gis_thematic_map")) {
                return row == null ? List.of() : (List<T>) List.of(row);
            }
            otherCalls.incrementAndGet();
            return List.of();
        }

        @Override
        public void query(String sql, RowCallbackHandler rch, Object... args) {
            otherCalls.incrementAndGet();
            ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                    FakeJdbc.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                    (proxy, method, methodArgs) -> {
                        if (method.getName().equals("getString")) {
                            return "IN_SERVICE";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            for (int i = 0; i < assetRows; i++) {
                try {
                    rch.processRow(rs);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private static ThematicRow row(String key, GisThematicSources source, boolean personal,
                                   String categoryField, String measureField,
                                   List<br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ClassDef> classes,
                                   String filterJson) {
        return new ThematicRow(UUID.randomUUID(), key, key, null, source, categoryField,
                measureField, personal, personal ? "SECTOR" : "NONE", classes, filterJson,
                "FACTORY", true);
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(USER);
        jdbc = new FakeJdbc();
        serviceWithPersonal = new GisThematicService(jdbc, () -> true, () -> 5);
        serviceWithoutPersonal = new GisThematicService(jdbc, () -> false, () -> 5);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("backoffice: fonte pessoal sem gis.view-personal recebe 403 ANTES de consultar os dados")
    void personalRenderForbiddenBeforeQuery() {
        jdbc.row = row("consumo-vs-media", GisThematicSources.UNIT_CONSUMPTION, true,
                "comparison", null, List.of(), "{}");
        assertThatThrownBy(() -> serviceWithoutPersonal.render("consumo-vs-media", null))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThat(jdbc.otherCalls).hasValue(0);
    }

    @Test
    @DisplayName("público: OS nunca é publicável — 403 mesmo sendo temático ativo e configurado")
    void serviceOrderNeverPublic() {
        jdbc.row = row("os-no-mapa", GisThematicSources.SERVICE_ORDER, true,
                "service_type_code", null, List.of(), "{\"statuses\":[\"OPEN\"]}");
        assertThatThrownBy(() -> serviceWithPersonal.renderPublic("os-no-mapa"))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThat(jdbc.otherCalls).hasValue(0);
    }

    @Test
    @DisplayName("SG-877: cenário de OS sem os tipos do tenant recusa com 422, não rende vazio")
    void unconfiguredOsScenarioRefused() {
        jdbc.row = row("os-vazamento-rede", GisThematicSources.SERVICE_ORDER, true,
                "service_type_code", null, List.of(),
                "{\"statuses\":[\"OPEN\"],\"serviceTypeCodes\":[]}");
        assertThatThrownBy(() -> serviceWithPersonal.render("os-vazamento-rede", null))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("SG-882: grandes consumidores sem faixas do tenant recusa com 422")
    void unconfiguredLargeConsumersRefused() {
        jdbc.row = row("grandes-consumidores", GisThematicSources.UNIT_CONSUMPTION, true,
                null, "billed_consumption", List.of(), "{}");
        assertThatThrownBy(() -> serviceWithPersonal.render("grandes-consumidores", null))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("criação: fonte fora da lista fechada é 422 sem tocar no banco")
    void createUnknownSourceRefused() {
        ThematicInput input = new ThematicInput("meu-tema", "Meu tema", null,
                "gis_arc", null, null, null, List.of(), "{}");
        assertThatThrownBy(() -> serviceWithPersonal.create(input))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(jdbc.otherCalls).hasValue(0);
    }

    @Test
    @DisplayName("criação: personal vem da fonte — quem cria temático de unidade não escolhe ser não-pessoal")
    void createDerivesPersonalFromSource() {
        // aggregation NONE para fonte pessoal: a validação recusa,
        // provando que o eixo público não é desligável pelo corpo.
        ThematicInput input = new ThematicInput("meu-tema", "Meu tema", null,
                "UNIT_CONSUMPTION", "comparison", null, "NONE",
                List.of(new br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ClassDef(
                        "BELOW_AVERAGE", "Abaixo", "map-thematic-1", null, null)), "{}");
        assertThatThrownBy(() -> serviceWithPersonal.create(input))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("público de ativos: contagem acima do teto declara truncated e não finge mínimo aplicado")
    void publicAssetsTruncationDeclared() {
        jdbc.row = row("rede-agua-operacao", GisThematicSources.NETWORK_ARC, false,
                "lifecycle_status", null, List.of(), "{}");
        // MAX_POINTS + 1 linhas: a extra é o sinal de truncamento; as
        // contagens param no teto e minUnitsApplied é nulo (ramo de
        // ativos não aplica supressão por mínimo de unidades).
        jdbc.assetRows = GisThematicService.MAX_POINTS + 1;
        var view = serviceWithPersonal.renderPublic("rede-agua-operacao");
        assertThat(view.truncated()).isTrue();
        assertThat(view.minUnitsApplied()).isNull();
        assertThat(view.cells()).hasSize(1);
        assertThat(view.cells().get(0).unitCount()).isEqualTo(GisThematicService.MAX_POINTS);
    }

    @Test
    @DisplayName("fábrica é imutável: update e delete de definição FACTORY recebem 403")
    void factoryDefinitionImmutable() {
        jdbc.row = row("consumo-vs-media", GisThematicSources.UNIT_CONSUMPTION, true,
                "comparison", null, List.of(), "{}");
        ThematicInput input = new ThematicInput("consumo-vs-media", "Outro nome", null,
                "UNIT_CONSUMPTION", "comparison", null, "SECTOR",
                List.of(new br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ClassDef(
                        "BELOW_AVERAGE", "Abaixo", "map-thematic-1", null, null)), "{}");
        assertThatThrownBy(() -> serviceWithPersonal.update("consumo-vs-media", input))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> serviceWithPersonal.delete("consumo-vs-media"))
                .isInstanceOf(ApiException.Forbidden.class);
    }
}
