package br.com.ziipsan.reporting.forecast;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia do SQL pendente da ED-100: o arquivo esta em db/pendente
 * porque o plano nao reserva numero para a ED-100 (V15_1 = P3.22 e
 * V15_2 = P3.41 ocupados) — o teste trava a declaracao da pendencia, as
 * tabelas e a correspondencia 1:1 entre as views fc_v_* do SQL e os
 * conjuntos de dados dos paineis do BI (ForecastBiPanels).
 */
class ForecastMigrationConsistencyTest {

    private String sql() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/db/pendente/ED-100__forecast.sql")) {
            assertThat(in).as("SQL pendente da ED-100 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void pendenciaDeNumeracaoDeclaradaNoCabecalho() throws Exception {
        String sql = sql();
        assertThat(sql).contains("NUMERACAO PENDENTE");
        assertThat(sql).contains("V15_1").contains("V15_2");
    }

    @Test
    void tabelasDaProjecaoPresentes() throws Exception {
        String sql = sql();
        assertThat(sql).contains("forecast_runs")
                .contains("forecast_scenarios")
                .contains("forecast_collection_curve")
                .contains("forecast_loss_curve")
                .contains("forecast_monthly")
                .contains("register_tenant_table('forecast_monthly'");
    }

    @Test
    void viewsDoSqlCorrespondemAosPaineisDoBi() throws Exception {
        String sql = sql();
        Set<String> panelViews = ForecastBiPanels.all().stream()
                .map(ForecastBiPanels.Panel::datasetView).collect(Collectors.toSet());
        assertThat(panelViews).hasSize(4);
        for (String view : panelViews) {
            assertThat(sql).as("view %s definida no SQL pendente", view)
                    .contains("VIEW " + view + " AS");
        }
    }
}
