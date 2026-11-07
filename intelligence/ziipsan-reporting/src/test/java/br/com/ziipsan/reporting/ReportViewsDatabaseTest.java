package br.com.ziipsan.reporting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova de conteudo das views da V15_1 contra PostgreSQL real, no padrao de
 * integracao do projeto (banco descartavel de integracao, variaveis
 * ZIIPSAN_IT_*; ver TenantRlsIsolationTest no core). Cria um schema itest_
 * descartavel, sobe as tabelas-fonte minimas, aplica a V15_1 INTEIRA e
 * confere os valores calculados a mao da massa sintetica
 * (src/test/resources/p322/massa-sintetica.sql).
 *
 * <p>Pos-revisao: a DDL minima inclui as tabelas reais da V4_4
 * (optional_category_enrollments) e da V6_5/P3.29 (refund_requests,
 * refund_executions, unidentified_payments, payment_events) - a omissao
 * delas era o que deixava RPT-024/026 "passarem" vazias. Cobertura nova:
 * RPT-004 (INNER JOIN), RPT-009, RPT-011, RPT-016, ramo OS da RPT-018,
 * RPT-024 e RPT-026 COM dados, RPT-008 sem CANCELLED pendente, RPT-015
 * ligacao x economia, RPT-019 com protecao e o mes sem reclamacoes do
 * art. 54 (ausencia explicita). Todos os esperados estao recalculados no
 * RELATORIO-P3.22.md, secao de correcoes.
 */
class ReportViewsDatabaseTest {

    private static final String RUN_ID = Long.toHexString(System.nanoTime() & 0xFFFFFFL);
    private static final String SCHEMA = "itest_p322_" + RUN_ID;
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
            st.execute("CREATE OR REPLACE FUNCTION register_tenant_table(text, boolean, boolean) "
                    + "RETURNS void LANGUAGE sql AS 'SELECT'");
            st.execute("CREATE OR REPLACE FUNCTION register_tenant_table(text) "
                    + "RETURNS void LANGUAGE sql AS 'SELECT'");
            executeScript(st, resource("p322/ddl-fontes-minimas.sql"));
            executeScript(st, migrationSql());
            executeScript(st, resource("p322/massa-sintetica.sql"));
        }
    }

    private static String resource(String name) throws Exception {
        try (InputStream in = ReportViewsDatabaseTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("Recurso de teste ausente: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String migrationSql() throws Exception {
        Path path = Path.of("src/main/resources/db/migration/tenant/V15_1__reporting_core_and_views.sql");
        if (!Files.exists(path)) {
            path = Path.of("intelligence/ziipsan-reporting").resolve(path);
        }
        return Files.readString(path);
    }

    private static void executeScript(Statement st, String sql) throws Exception {
        // Script inteiro numa chamada: o driver PG aceita multiplas instrucoes
        // sem parametros, e a V15_1 tem blocos DO $$...$$ com ';' internos que
        // uma divisao ingenua por ';' quebraria.
        st.execute(sql);
    }

    private BigDecimal scalar(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement st = connection.createStatement()) {
            st.execute("SET search_path TO " + SCHEMA);
            try (ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private long count(String sql) throws Exception {
        return scalar(sql).longValueExact();
    }

    @Test
    void arrecadacaoDiariaPoc58() throws Exception {
        assertThat(count("SELECT COALESCE(SUM(pagamentos),0) FROM rpt_arrecadacao_diaria")).isEqualTo(2);
        assertThat(scalar("SELECT COALESCE(SUM(valor_liquido),0) FROM rpt_arrecadacao_diaria"))
                .isEqualByComparingTo("345.50");
        assertThat(scalar("SELECT COALESCE(SUM(valor_retido),0) FROM rpt_arrecadacao_diaria"))
                .isEqualByComparingTo("5.00");
    }

    @Test
    void naoIdentificadosLeFilaRealDaP329() throws Exception {
        // d4 (status NAO_IDENTIFICADO, 75,25) tambem esta na fila: o UNION
        // deduplica. d5 (status RECEBIDO, 33,40) so existe via fila
        // unidentified_payments. Total: 2 linhas, 75,25 + 33,40 = 108,65.
        assertThat(count("SELECT COUNT(*) FROM rpt_pagamentos_nao_identificados")).isEqualTo(2);
        assertThat(scalar("SELECT COALESCE(SUM(valor),0) FROM rpt_pagamentos_nao_identificados"))
                .isEqualByComparingTo("108.65");
        assertThat(count("SELECT COUNT(*) FROM rpt_pagamentos_duplicidade")).isEqualTo(1);
    }

    @Test
    void pagamentosDatasBancoSoComRetornoPoc60() throws Exception {
        // B3: INNER JOIN com o detalhe de retorno - so d1 (100,00) e d2
        // (245,50) tem retorno; duplicidade (100,00) e nao identificado
        // (75,25) ficam fora. 100,00 + 245,50 = 345,50 (com LEFT JOIN era
        // 520,75 e o teste esperava 345,50: vermelho no PostgreSQL real).
        assertThat(scalar("SELECT COALESCE(SUM(valor_liquido),0) FROM rpt_pagamentos_datas_banco"))
                .isEqualByComparingTo("345.50");
        assertThat(count("SELECT COALESCE(SUM(pagamentos),0) FROM rpt_pagamentos_datas_banco"))
                .isEqualTo(2);
        assertThat(scalar("SELECT valor_total FROM rpt_retorno_importacao")).isEqualByComparingTo("345.50");
    }

    @Test
    void faturamentoECiclo() throws Exception {
        // Referencia 2026-10: b1 (300) + b3 (150) + b5 (80) = 530, 3 contas
        // (b2 cancelada fora). Referencia 2026-09: so b4 = 120, 1 conta.
        assertThat(scalar("SELECT valor_total FROM rpt_faturamento_sucinto WHERE referencia = DATE '2026-10-01'"))
                .isEqualByComparingTo("530.00");
        assertThat(count("SELECT contas FROM rpt_faturamento_sucinto WHERE referencia = DATE '2026-10-01'"))
                .isEqualTo(3);
        assertThat(scalar("SELECT valor_total FROM rpt_faturamento_sucinto WHERE referencia = DATE '2026-09-01'"))
                .isEqualByComparingTo("120.00");
        assertThat(count("SELECT COUNT(*) FROM rpt_contas_canceladas")).isEqualTo(1);
        // RPT-007: itens de b1 e b5 na mesma categoria/faixa: contas
        // distintas 2, economias 1+2=3, consumo 8+10=18, liquido 200+80=280.
        assertThat(scalar("SELECT valor_liquido FROM rpt_faturamento_categoria_faixa"))
                .isEqualByComparingTo("280.00");
        assertThat(count("SELECT economias FROM rpt_faturamento_categoria_faixa")).isEqualTo(3);
        assertThat(count("SELECT consumo_m3 FROM rpt_faturamento_categoria_faixa")).isEqualTo(18);
        // RPT-008 (R3d): 3 DONE + 1 PENDING + 1 CANCELLED = 5 atividades,
        // 3 realizadas, pendentes 1 (CANCELLED NAO e pendencia).
        assertThat(count("SELECT atividades FROM rpt_fechamento_ciclo_grupo")).isEqualTo(5);
        assertThat(count("SELECT realizadas FROM rpt_fechamento_ciclo_grupo")).isEqualTo(3);
        assertThat(count("SELECT pendentes FROM rpt_fechamento_ciclo_grupo")).isEqualTo(1);
    }

    @Test
    void contasEmitidasERetificadas() throws Exception {
        // RPT-009: (2026-10, emitida 2026-10-06) b1+b3+b5 = 3 contas, 530;
        // (2026-09, 2026-09-06) b4 = 1 conta, 120. Total 4 contas, 650.
        assertThat(count("SELECT contas FROM rpt_contas_emitidas WHERE referencia = DATE '2026-10-01'"))
                .isEqualTo(3);
        assertThat(scalar("SELECT COALESCE(SUM(valor_total),0) FROM rpt_contas_emitidas"))
                .isEqualByComparingTo("650.00");
        assertThat(count("SELECT COALESCE(SUM(contas),0) FROM rpt_contas_emitidas")).isEqualTo(4);
        // RPT-011: so b4 tem retificacoes (2), valor 120, origem b2.
        assertThat(count("SELECT COUNT(*) FROM rpt_contas_retificadas")).isEqualTo(1);
        assertThat(count("SELECT retificacoes FROM rpt_contas_retificadas")).isEqualTo(2);
        assertThat(scalar("SELECT valor FROM rpt_contas_retificadas")).isEqualByComparingTo("120.00");
    }

    @Test
    void medicaoHistogramaPoc56ERetidas() throws Exception {
        assertThat(count("SELECT COALESCE(SUM(ligacoes),0) FROM rpt_histograma_consumo")).isEqualTo(4);
        assertThat(count("SELECT COALESCE(SUM(consumo_total),0) FROM rpt_histograma_consumo")).isEqualTo(80);
        assertThat(count("SELECT ligacoes FROM rpt_histograma_consumo WHERE faixa_m3 = '51+'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM rpt_leituras_retidas")).isEqualTo(1);
        assertThat(count("SELECT leituras FROM rpt_ocorrencias_leitura")).isEqualTo(2);
    }

    @Test
    void ligacoesEconomiasPorSituacaoECategoria() throws Exception {
        // RPT-015 (R3a): unidades u1 (raiz), u2 (raiz), u3 (filha de u1) e
        // u4 (raiz): ligacoes = raizes = 3; economias = unidades = 4.
        // Grupo ACTIVE/OCCUPIED (u1, u3, u4): ligacoes 2, economias 3.
        assertThat(count("SELECT COALESCE(SUM(ligacoes),0) FROM rpt_ligacoes_situacao")).isEqualTo(3);
        assertThat(count("SELECT COALESCE(SUM(economias),0) FROM rpt_ligacoes_situacao")).isEqualTo(4);
        assertThat(count("SELECT ligacoes FROM rpt_ligacoes_situacao WHERE ocupacao = 'OCCUPIED'"))
                .isEqualTo(2);
        assertThat(count("SELECT economias FROM rpt_ligacoes_situacao WHERE ocupacao = 'OCCUPIED'"))
                .isEqualTo(3);
        // RPT-016: via itens faturados de b1/b5 - ligacoes (contas
        // distintas) 2, economias 1+2 = 3.
        assertThat(count("SELECT ligacoes FROM rpt_ligacoes_categoria")).isEqualTo(2);
        assertThat(count("SELECT economias FROM rpt_ligacoes_categoria")).isEqualTo(3);
        // RPT-017: RESIDENCIAL (u1: b1+b3; u4: b5) = 3 contas, 530; o grupo
        // sem perfil (u2: b4) soma mais 120 no total = 650.
        assertThat(scalar("SELECT valor_a_receber FROM rpt_a_receber_tipo_unidade WHERE tipo_unidade = 'RESIDENCIAL'"))
                .isEqualByComparingTo("530.00");
        assertThat(scalar("SELECT COALESCE(SUM(valor_a_receber),0) FROM rpt_a_receber_tipo_unidade"))
                .isEqualByComparingTo("650.00");
    }

    @Test
    void aptosAoCorteRespeitamProtecaoDaP34() throws Exception {
        // R3b: u1 deve 450 (b1+b3) e u2 deve 120 (b4), mas AMBAS tem
        // PROTECTED_CLASS em cutoff_non_generation_reasons: fora. So u4
        // (b5, 80, sem protecao) e apta.
        assertThat(count("SELECT COUNT(*) FROM rpt_aptos_corte")).isEqualTo(1);
        assertThat(count("SELECT contas_vencidas FROM rpt_aptos_corte")).isEqualTo(1);
        assertThat(scalar("SELECT valor_devido FROM rpt_aptos_corte")).isEqualByComparingTo("80.00");
        assertThat(count("SELECT COUNT(*) FROM rpt_aptos_corte "
                + "WHERE unidade_id IN ('a0000000-0000-0000-0000-000000000001',"
                + "'a0000000-0000-0000-0000-000000000002')")).isEqualTo(0);
        assertThat(count("SELECT COALESCE(SUM(unidades),0) FROM rpt_motivos_nao_geracao_aviso"))
                .isEqualTo(2);
        assertThat(count("SELECT avisos FROM rpt_notificacoes_emitidas")).isEqualTo(1);
    }

    @Test
    void pendenciasProtocoloEOrdemServico() throws Exception {
        // RPT-018: protocolo aberto p3 (usuario ...001) = 1; OS nao
        // encerradas: os3 IN_EXECUTION (usuario ...001) = 1 e os2 OPEN
        // (usuario ...002) = 1. os1 CLOSED_EXECUTED nao conta.
        assertThat(count("SELECT pendencias FROM rpt_pendencias_usuario "
                + "WHERE origem = 'PROTOCOLO' AND usuario_id = '20000000-0000-0000-0000-000000000001'"))
                .isEqualTo(1);
        assertThat(count("SELECT pendencias FROM rpt_pendencias_usuario "
                + "WHERE origem = 'ORDEM_SERVICO' AND usuario_id = '20000000-0000-0000-0000-000000000001'"))
                .isEqualTo(1);
        assertThat(count("SELECT pendencias FROM rpt_pendencias_usuario "
                + "WHERE origem = 'ORDEM_SERVICO' AND usuario_id = '20000000-0000-0000-0000-000000000002'"))
                .isEqualTo(1);
        assertThat(scalar("SELECT horas_ate_encerrar FROM rpt_servicos_protocolo "
                + "WHERE protocol_id = '50000000-0000-0000-0000-000000000001'"))
                .isEqualByComparingTo("10");
    }

    @Test
    void art54ComPercentualNaoAtendido() throws Exception {
        assertThat(count("SELECT encerrados FROM rpt_reclamacoes_art54")).isEqualTo(2);
        assertThat(count("SELECT nao_atendidos_no_prazo FROM rpt_reclamacoes_art54")).isEqualTo(1);
        assertThat(scalar("SELECT percentual_nao_atendido FROM rpt_reclamacoes_art54"))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void art54MesSemReclamacoesNaoGeraLinha() throws Exception {
        // Item 5/NR 11: a massa so tem encerramentos em 2026-10. O mes de
        // 2026-09 NAO pode gerar linha (ausencia explicita, nao zero
        // inventado): a view agrupa o que existe.
        assertThat(count("SELECT COUNT(*) FROM rpt_reclamacoes_art54 WHERE mes = DATE '2026-09-01'"))
                .isEqualTo(0);
    }

    @Test
    void isencoesPorCategoriaEVigencia() throws Exception {
        // B1 contra a V4_4 real: 3 enquadramentos, 1 por categoria, cada
        // um com sua vigencia (valid_from/valid_to).
        assertThat(count("SELECT COALESCE(SUM(inscricoes),0) FROM rpt_isencoes")).isEqualTo(3);
        assertThat(count("SELECT inscricoes FROM rpt_isencoes WHERE categoria_codigo = 'VULNERABLE_RESIDENTIAL'"))
                .isEqualTo(1);
        assertThat(count("SELECT inscricoes FROM rpt_isencoes WHERE categoria_codigo = 'ASSISTANCE_ENTITY'"))
                .isEqualTo(1);
        assertThat(count("SELECT inscricoes FROM rpt_isencoes WHERE categoria_codigo = 'MICRO_SMALL_BUSINESS'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM rpt_isencoes "
                + "WHERE categoria_codigo = 'VULNERABLE_RESIDENTIAL' "
                + "AND vigencia_inicio = DATE '2026-01-01' AND vigencia_fim = DATE '2026-12-31'"))
                .isEqualTo(1);
    }

    @Test
    void devolucoesContraTabelasReaisDaP329() throws Exception {
        // B2 contra a V6_5 real: rr1 ENCERRADA/DUPLICATE 100,00; rr2
        // APROVADA/UNDUE 100,00 (dobro sobre base 50); rr3 PENDENTE sem
        // valor (COALESCE 0). Total: 3 devolucoes, 200,00.
        assertThat(count("SELECT COALESCE(SUM(devolucoes),0) FROM rpt_devolucoes")).isEqualTo(3);
        assertThat(scalar("SELECT COALESCE(SUM(valor_total),0) FROM rpt_devolucoes"))
                .isEqualByComparingTo("200.00");
        assertThat(scalar("SELECT valor_total FROM rpt_devolucoes WHERE situacao = 'ENCERRADA'"))
                .isEqualByComparingTo("100.00");
        assertThat(scalar("SELECT valor_total FROM rpt_devolucoes WHERE situacao = 'PENDENTE'"))
                .isEqualByComparingTo("0.00");
        assertThat(count("SELECT devolucoes FROM rpt_devolucoes WHERE motivo = 'UNDUE_CHARGE' AND situacao = 'APROVADA'"))
                .isEqualTo(1);
        // Fatos da P3.29 que sustentam o relatorio: 1 execucao e 1 evento
        // REFUND_APPLIED na massa.
        assertThat(count("SELECT COUNT(*) FROM refund_executions")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM payment_events WHERE event_type = 'REFUND_APPLIED'"))
                .isEqualTo(1);
    }

    @Test
    void socialFiscalESeed() throws Exception {
        assertThat(count("SELECT inscricoes FROM rpt_tarifa_social")).isEqualTo(1);
        assertThat(count("SELECT documentos FROM rpt_nfage_situacao WHERE situacao_agrupada = 'AUTORIZADA'"))
                .isEqualTo(2);
        assertThat(count("SELECT documentos FROM rpt_nfage_situacao WHERE situacao_agrupada = 'CONTINGENCIA'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM report_definitions WHERE tenant_id IS NULL"))
                .isEqualTo(27);
    }
}
