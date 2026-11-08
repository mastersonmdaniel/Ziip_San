package br.com.ziipsan.reporting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova de conteudo das views novas da ED-46 contra PostgreSQL real, no
 * padrao de integracao do projeto (banco descartavel, variaveis
 * ZIIPSAN_IT_*; mesmo harness da P3.22). Aplica a DDL minima das fontes
 * reais, a DDL pendente da ED-46 INTEIRA (ED-46__complete_catalog.sql
 * em db/pendente/) e a massa sintetica, e
 * confere os valores calculados a mao (recalculos no RELATORIO-ED-46).
 *
 * <p>Inclui a medicao RNF-71 das views novas SOBRE ESTA MASSA (pequena):
 * e evidencia de ordem de grandeza, NAO a medicao oficial do RNF-71,
 * que exige a massa da P2.9 (300 mil ligacoes) - pendencia declarada.
 */
class Ed46ViewsDatabaseTest {

    private static final String RUN_ID = Long.toHexString(System.nanoTime() & 0xFFFFFFL);
    private static final String SCHEMA = "itest_ed46_" + RUN_ID;
    private static DataSource dataSource;

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel " + name + " nao definida: exporte-a antes de rodar "
                    + "o teste (o ci.sh gera a senha efemera)");
        }
        return value;
    }

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ZIIPSAN_IT_JDBC_URL",
                "jdbc:postgresql://localhost:55433/ziipsan_it");
        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(requireEnv("ZIIPSAN_IT_DB_USER"));
        ds.setPassword(requireEnv("ZIIPSAN_IT_DB_PASSWORD"));
        dataSource = ds;
        try (Connection connection = ds.getConnection(); Statement st = connection.createStatement()) {
            st.execute("CREATE SCHEMA " + SCHEMA);
            st.execute("SET search_path TO " + SCHEMA);
            st.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
            executeScript(st, resource("ed46/ddl-fontes-minimas.sql"));
            executeScript(st, migrationSql());
            executeScript(st, resource("ed46/massa-sintetica.sql"));
        }
    }

    // ---------- RPT-101 produtividade por atendente ----------
    // A1: 3 protocolos, 2 encerrados (48h e 48h), 1 aberto, 1 no prazo -> media 48,00
    // A2: 1 protocolo, 1 encerrado (12h), no prazo -> media 12,00
    @Test
    void produtividadePorAtendente() throws Exception {
        List<String> rows = query("SELECT atendente_id, protocolos, encerrados, abertos, "
                + "encerrados_no_prazo, tempo_medio_horas FROM rpt_produtividade_atendente ORDER BY atendente_id");
        assertThat(rows).containsExactly(
                "11111111-1111-1111-1111-1111111111a1|3|2|1|1|48.00",
                "22222222-2222-2222-2222-2222222222a2|1|1|0|1|12.00");
    }

    // ---------- RPT-102 reclamacoes por origem externa ----------
    // REGULATOR: 2 protocolos, 2 encerrados, 0 fora, 0,00%, media (48+12)/2 = 30,00
    // COUNTER:   1, 1, 1 fora, 100,00%, 48,00
    // PHONE:     1 aberto -> encerrados 0, percentual NULL, tempo NULL (ausencia explicita)
    @Test
    void reclamacoesPorOrigemExterna() throws Exception {
        List<String> rows = query("SELECT origem, protocolos, encerrados, fora_do_prazo, "
                + "percentual_fora_do_prazo, tempo_medio_horas FROM rpt_reclamacoes_origem_externa ORDER BY origem");
        assertThat(rows).containsExactly(
                "COUNTER|1|1|1|100.00|48.00",
                "PHONE|1|0|0|null|null",
                "REGULATOR|2|2|0|0.00|30.00");
    }

    // ---------- RPT-103 compensacoes (TR-665) ----------
    // So CREDITED entram: PENDING_BASIS (valor NULL) e PENDING_APPROVAL ficam fora.
    // U1 -> RESIDENCIAL (2 economias RES x 1 COM); U2 -> COMERCIAL; formula ARIS credita 0,00.
    @Test
    void compensacoesPorTipoDeUsuarioEDeCompensacao() throws Exception {
        List<String> rows = query("SELECT tipo_usuario, tipo_compensacao, formula, compensacoes, valor_total "
                + "FROM rpt_compensacoes_usuario ORDER BY tipo_usuario, tipo_compensacao");
        assertThat(rows).containsExactly(
                "COMERCIAL|UNDUE_CUT|RECONNECTION_4H_NO_CHARGE|1|0.00",
                "RESIDENCIAL|LATE_RECONNECTION|MANUAL_CREDIT_WITH_APPROVAL|1|50.00",
                "RESIDENCIAL|UNDUE_CUT|GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE|1|100.00");
    }

    // ---------- RPT-104 tempo entre leituras (POC-62) ----------
    // R1 10/10: 3 leituras, total 0,50h, intervalos 600s/1200s -> 900,0
    // R1 11/10: 1 leitura, total 0,00, intervalo NULL
    // R2 10/10: 2 leituras, total 1,00h, intervalo 3600,0
    // A leitura sem leiturista (TELEMETRY) nao gera linha.
    @Test
    void tempoEntreLeiturasPorLeiturista() throws Exception {
        List<String> rows = query("SELECT leiturista_id, data, leituras, tempo_total_horas, "
                + "intervalo_medio_segundos FROM rpt_tempo_entre_leituras_leiturista ORDER BY leiturista_id, data");
        assertThat(rows).containsExactly(
                "33333333-3333-3333-3333-3333333333b1|2026-10-10|3|0.50|900.0",
                "33333333-3333-3333-3333-3333333333b1|2026-10-11|1|0.00|null",
                "44444444-4444-4444-4444-4444444444b2|2026-10-10|2|1.00|3600.0");
    }

    // ---------- RPT-105 acessos por usuario ----------
    @Test
    void acessosPorUsuario() throws Exception {
        List<String> rows = query("SELECT usuario_id, acessos, exportacoes, negados, cotas_excedidas "
                + "FROM rpt_acessos_dados_usuario ORDER BY usuario_id");
        assertThat(rows).containsExactly(
                "55555555-5555-5555-5555-5555555555c1|3|1|1|0",
                "66666666-6666-6666-6666-6666666666c2|2|0|0|2");
    }

    // ---------- catalogo semeado ----------
    @Test
    void catalogoSemeadoCom196ItensEContagemPorStatus() throws Exception {
        assertThat(scalarLong("SELECT COUNT(*) FROM report_catalog_entries")).isEqualTo(196);
        assertThat(scalarLong("SELECT COUNT(*) FROM report_catalog_entries WHERE status = 'EXECUTABLE'"))
                .isEqualTo(7);
        assertThat(scalarLong("SELECT COUNT(*) FROM report_catalog_entries WHERE status = 'DEPENDENT_SOURCE'"))
                .isEqualTo(122);
        assertThat(scalarLong("SELECT COUNT(*) FROM report_definitions WHERE code LIKE 'RPT-10%'"))
                .isEqualTo(5);
    }

    // ---------- RNF-71: medicao sobre a massa sintetica (NAO e a oficial) ----------
    @Test
    void medicaoDeTempoDasViewsNovasNaMassaSintetica() throws Exception {
        for (String view : List.of("rpt_produtividade_atendente", "rpt_reclamacoes_origem_externa",
                "rpt_compensacoes_usuario", "rpt_tempo_entre_leituras_leiturista", "rpt_acessos_dados_usuario")) {
            long start = System.nanoTime();
            query("SELECT * FROM " + view);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("ED-46 RNF-71 (massa sintetica) " + view + ": " + elapsedMs + " ms");
            assertThat(elapsedMs).as(view).isLessThan(120_000);
        }
    }

    // ---------- harness ----------
    private static List<String> query(String sql) throws Exception {
        List<String> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.execute("SET search_path TO " + SCHEMA);
            try (ResultSet rs = st.executeQuery(sql)) {
                int cols = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i <= cols; i++) {
                        if (i > 1) {
                            sb.append('|');
                        }
                        Object v = rs.getObject(i);
                        if (v == null) {
                            sb.append("null");
                        } else if (v instanceof BigDecimal bd) {
                            sb.append(bd.toPlainString());
                        } else {
                            sb.append(v);
                        }
                    }
                    rows.add(sb.toString());
                }
            }
        }
        return rows;
    }

    private static long scalarLong(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.execute("SET search_path TO " + SCHEMA);
            try (ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static String resource(String name) throws Exception {
        try (InputStream in = Ed46ViewsDatabaseTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Recurso de teste ausente: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String migrationSql() throws Exception {
        java.nio.file.Path path = java.nio.file.Path.of(
                "src/main/resources/db/pendente/ED-46__complete_catalog.sql");
        if (!java.nio.file.Files.exists(path)) {
            return resource("db/pendente/ED-46__complete_catalog.sql");
        }
        return java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void executeScript(Statement st, String script) throws Exception {
        // Script inteiro numa chamada, como na P3.22: o driver PG aceita
        // multiplas instrucoes sem parametros, e dividir por ';' quebraria
        // literais e comentarios da migration.
        st.execute(script);
    }
}
