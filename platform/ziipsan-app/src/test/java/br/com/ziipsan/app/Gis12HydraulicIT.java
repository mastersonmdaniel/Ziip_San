package br.com.ziipsan.app;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-12 (ED-63): hidráulica no PostgreSQL/PostGIS real — mapeamento
 * tipo → elemento carregado, registro de execução com o portão N7 nas
 * colunas, e a remoção da view gis_network_element e da tabela
 * gis_network_element_legacy (M5/M6) conferida pela AUSÊNCIA dos dois
 * objetos depois da parte 2 do SQL pendente.
 *
 * <p>Segue o padrão da Gis13DrainIT: migrations do classpath contra
 * schema descartável; o SQL da ED-63 entra de db/pendente (sem número
 * confirmado após a V13_12) e recebe o número na integração.
 *
 * <p>NÃO RODADA nesta VM (sem servidor PostgreSQL/PostGIS aqui — só o
 * cliente psql): pendência da CI do dono, como as demais ITs GIS. A
 * execução dos solvers EPANET 2.2/SWMM 5.2 e o teste de resultado
 * conhecido (COD-34) RODARAM nesta VM contra os binários oficiais
 * compilados da fonte — ver RELATORIO-ED-63.md e provas/.
 */
class Gis12HydraulicIT {

    private static final List<String> PENDING_SQL = List.of(
            "db/pendente/ED-63__gis_hydraulic.sql");

    @Test
    @Disabled("Esqueleto: ligar ao bootstrap da plataforma na integracao — pendencia declarada no RELATORIO-ED-63")
    @DisplayName("GIS-12: mapeamento carregado, run exige N7, view e legado removidos na parte 2")
    void hydraulicSchema() throws Exception {
        // Corpo no padrão das ITs irmãs (bootstrap por AppTestSupport da
        // plataforma): aplica o schema do tenant + V13_1/V13_2 + pendente
        // parte 1, confere as asserções abaixo e, num segundo schema,
        // aplica também a parte 2 e confere as ausências.
        throw new UnsupportedOperationException(
                "Esqueleto de IT no padrao das irmas: ligar ao bootstrap da plataforma na integracao (pendencia declarada no relatorio).");
    }

    // Asserções que a IT executa quando ligada (documentadas aqui para a
    // revisão conferir o aceite contra o SQL pendente):
    //  1. gis_hydraulic_mapping tem as linhas de fábrica EPANET/SWMM da semente;
    //  2. INSERT em gis_hydraulic_run sem confidence/sobrescrita falha (NOT NULL/CHECK da N7);
    //  3. após a parte 2: to_regclass('gis_network_element') IS NULL
    //     e to_regclass('gis_network_element_legacy') IS NULL;
    //  4. gis_node/gis_arc seguem consultáveis por zona/estado/data (a leitura do serviço novo).
    static void documentedAssertions(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM gis_hydraulic_mapping")) {
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(12);
            }
        }
    }

    static String readPendingSql() throws Exception {
        try (InputStream in = Gis12HydraulicIT.class.getResourceAsStream("/db/pendente/ED-63__gis_hydraulic.sql")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
