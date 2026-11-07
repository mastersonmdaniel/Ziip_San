package br.com.ziipsan.reporting.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalogo normativo dos relatorios nativos da P3.22 (espelho em codigo da
 * seed da V15_1). Relatorios de modulos externos (P3.31, certificacoes)
 * NAO entram no catalogo de fabrica: registram-se por
 * {@link ExternalReportRegistrar}, que valida a definicao e a PERSISTE em
 * report_definitions COM o tenant_id (correcao G5 pos-revisao: o registro
 * anterior era so em memoria - sumia no restart e vazava entre tenants,
 * porque o mapa era global e a definicao nao tinha tenant).
 *
 * <p>Builtins sao globais (tenant_id NULL na tabela). Externos sao POR
 * TENANT: {@link #find(String, UUID)} e {@link #allForTenant(UUID)}
 * enxergam os externos do tenant dono e de nenhum outro. O armazenamento
 * e a porta {@link ExternalDefinitionStore} (JDBC sobre
 * report_definitions na fiacao; em memoria nos testes).
 */
public final class ReportRegistry {

    private final Map<String, ReportDefinition> byCode = new ConcurrentHashMap<>();
    private final ExternalDefinitionStore externalStore;
    private final Map<UUID, Map<String, ReportDefinition>> externalByTenant = new ConcurrentHashMap<>();

    public ReportRegistry() {
        this(new InMemoryExternalDefinitionStore());
    }

    public ReportRegistry(ExternalDefinitionStore externalStore) {
        this.externalStore = externalStore;
        builtins().forEach(d -> byCode.put(d.code(), d));
    }

    /** Busca sem tenant: SO o catalogo de fabrica e externos ja carregados. */
    public Optional<ReportDefinition> find(String code) {
        ReportDefinition builtin = byCode.get(code);
        if (builtin != null) {
            return Optional.of(builtin);
        }
        return externalByTenant.values().stream()
                .map(m -> m.get(code)).filter(java.util.Objects::nonNull)
                .findFirst();
    }

    /** Busca no escopo do tenant: fabrica + externos persistidos DELE. */
    public Optional<ReportDefinition> find(String code, UUID tenantId) {
        ReportDefinition builtin = byCode.get(code);
        if (builtin != null) {
            return Optional.of(builtin);
        }
        if (tenantId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tenantExternals(tenantId).get(code));
    }

    /** Catalogo de fabrica (+ externos ja carregados em memoria). */
    public List<ReportDefinition> all() {
        java.util.TreeMap<String, ReportDefinition> merged = new java.util.TreeMap<>(byCode);
        externalByTenant.values().forEach(m -> merged.putAll(m));
        return List.copyOf(merged.values());
    }

    /** Catalogo completo do tenant: fabrica + externos persistidos dele. */
    public List<ReportDefinition> allForTenant(UUID tenantId) {
        java.util.TreeMap<String, ReportDefinition> merged = new java.util.TreeMap<>(byCode);
        if (tenantId != null) {
            merged.putAll(tenantExternals(tenantId));
        }
        return List.copyOf(merged.values());
    }

    /**
     * Registro externo (P3.31): chamado so pelo ExternalReportRegistrar
     * validado. Persiste em report_definitions com o tenant_id ANTES de
     * entrar no cache - se a persistencia falhar, nada fica registrado.
     */
    public void registerExternal(UUID tenantId, ReportDefinition definition) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Registro externo exige o tenant dono");
        }
        if (byCode.containsKey(definition.code())
                || tenantExternals(tenantId).containsKey(definition.code())) {
            throw new IllegalStateException("Relatorio ja registrado: " + definition.code());
        }
        externalStore.save(tenantId, definition);
        tenantExternals(tenantId).put(definition.code(), definition);
    }

    private Map<String, ReportDefinition> tenantExternals(UUID tenantId) {
        return externalByTenant.computeIfAbsent(tenantId, tenant -> {
            Map<String, ReportDefinition> loaded = new ConcurrentHashMap<>();
            externalStore.loadByTenant(tenant).forEach(d -> loaded.put(d.code(), d));
            return loaded;
        });
    }

    public static List<ReportDefinition> builtins() {
        return List.of(
            def("RPT-001", "Arrecadação diária", "rpt_arrecadacao_diaria", "reporting.rpt-001:export", "data", List.of(col("data", ReportColumnType.DATE, false), col("canal", ReportColumnType.TEXT, false), col("pagamentos", ReportColumnType.LONG, false), col("valor_bruto", ReportColumnType.MONEY, false), col("valor_liquido", ReportColumnType.MONEY, false), col("valor_retido", ReportColumnType.MONEY, false), col("tributo_segregado", ReportColumnType.MONEY, false)), List.of("data")),
            def("RPT-002", "Pagamentos não identificados", "rpt_pagamentos_nao_identificados", "reporting.rpt-002:export", "payment_id", List.of(col("payment_id", ReportColumnType.TEXT, false), col("data_pagamento", ReportColumnType.DATE, false), col("canal", ReportColumnType.TEXT, false), col("valor", ReportColumnType.MONEY, false), col("documento_pagador", ReportColumnType.DOCUMENT, true), col("mes_arrecadacao", ReportColumnType.DATE, false), col("recebido_em", ReportColumnType.TIMESTAMP, false)), List.of("data_pagamento", "canal")),
            def("RPT-003", "Pagamentos em duplicidade", "rpt_pagamentos_duplicidade", "reporting.rpt-003:export", "payment_id", List.of(col("payment_id", ReportColumnType.TEXT, false), col("conta_id", ReportColumnType.TEXT, false), col("unidade_id", ReportColumnType.TEXT, false), col("data_pagamento", ReportColumnType.DATE, false), col("canal", ReportColumnType.TEXT, false), col("valor", ReportColumnType.MONEY, false), col("documento_pagador", ReportColumnType.DOCUMENT, true), col("recebido_em", ReportColumnType.TIMESTAMP, false)), List.of("data_pagamento", "canal")),
            def("RPT-004", "Pagamentos por data de remessa, pagamento, crédito e banco", "rpt_pagamentos_datas_banco", "reporting.rpt-004:export", "data_remessa", List.of(col("data_remessa", ReportColumnType.DATE, false), col("data_pagamento", ReportColumnType.DATE, false), col("mes_arrecadacao", ReportColumnType.DATE, false), col("banco_agencia", ReportColumnType.TEXT, false), col("forma_captura", ReportColumnType.TEXT, false), col("forma_pagamento", ReportColumnType.TEXT, false), col("pagamentos", ReportColumnType.LONG, false), col("valor_liquido", ReportColumnType.MONEY, false)), List.of("data_pagamento")),
            def("RPT-005", "Situação da importação do retorno", "rpt_retorno_importacao", "reporting.rpt-005:export", "arquivo_id", List.of(col("arquivo_id", ReportColumnType.TEXT, false), col("nsa", ReportColumnType.TEXT, false), col("arquivo", ReportColumnType.TEXT, false), col("recebido_em", ReportColumnType.DATE, false), col("situacao", ReportColumnType.TEXT, false), col("linhas", ReportColumnType.LONG, false), col("valor_total", ReportColumnType.MONEY, false)), List.of("recebido_em", "situacao")),
            def("RPT-006", "Faturamento sucinto", "rpt_faturamento_sucinto", "reporting.rpt-006:export", "referencia", List.of(col("referencia", ReportColumnType.DATE, false), col("contas", ReportColumnType.LONG, false), col("valor_total", ReportColumnType.MONEY, false), col("valor_agua", ReportColumnType.MONEY, false), col("valor_esgoto", ReportColumnType.MONEY, false), col("valor_debitos", ReportColumnType.MONEY, false), col("valor_creditos", ReportColumnType.MONEY, false)), List.of("referencia")),
            def("RPT-007", "Faturamento detalhado por categoria e faixa", "rpt_faturamento_categoria_faixa", "reporting.rpt-007:export", "referencia", List.of(col("referencia", ReportColumnType.DATE, false), col("categoria_codigo", ReportColumnType.TEXT, false), col("categoria", ReportColumnType.TEXT, false), col("servico", ReportColumnType.TEXT, false), col("faixa_de", ReportColumnType.LONG, false), col("faixa_ate", ReportColumnType.LONG, false), col("contas", ReportColumnType.LONG, false), col("economias", ReportColumnType.LONG, false), col("consumo_m3", ReportColumnType.LONG, false), col("valor_bruto", ReportColumnType.MONEY, false), col("desconto", ReportColumnType.MONEY, false), col("valor_liquido", ReportColumnType.MONEY, false)), List.of("referencia", "categoria_codigo")),
            def("RPT-008", "Fechamento do ciclo por grupo", "rpt_fechamento_ciclo_grupo", "reporting.rpt-008:export", "referencia", List.of(col("referencia", ReportColumnType.DATE, false), col("referencia_situacao", ReportColumnType.TEXT, false), col("grupo_codigo", ReportColumnType.TEXT, false), col("grupo", ReportColumnType.TEXT, false), col("atividades", ReportColumnType.LONG, false), col("realizadas", ReportColumnType.LONG, false), col("pendentes", ReportColumnType.LONG, false)), List.of("referencia", "grupo_codigo")),
            def("RPT-009", "Contas emitidas", "rpt_contas_emitidas", "reporting.rpt-009:export", "referencia", List.of(col("referencia", ReportColumnType.DATE, false), col("emitida_em", ReportColumnType.DATE, false), col("contas", ReportColumnType.LONG, false), col("valor_total", ReportColumnType.MONEY, false)), List.of("referencia")),
            def("RPT-010", "Contas canceladas", "rpt_contas_canceladas", "reporting.rpt-010:export", "conta_id", List.of(col("conta_id", ReportColumnType.TEXT, false), col("unidade_id", ReportColumnType.TEXT, false), col("referencia", ReportColumnType.DATE, false), col("situacao", ReportColumnType.TEXT, false), col("motivo", ReportColumnType.TEXT, false), col("valor", ReportColumnType.MONEY, false), col("cancelada_em", ReportColumnType.TIMESTAMP, false)), List.of("referencia")),
            def("RPT-011", "Contas retificadas", "rpt_contas_retificadas", "reporting.rpt-011:export", "conta_id", List.of(col("conta_id", ReportColumnType.TEXT, false), col("unidade_id", ReportColumnType.TEXT, false), col("referencia", ReportColumnType.DATE, false), col("retificacoes", ReportColumnType.LONG, false), col("conta_origem_id", ReportColumnType.TEXT, false), col("valor", ReportColumnType.MONEY, false), col("atualizada_em", ReportColumnType.TIMESTAMP, false)), List.of("referencia")),
            def("RPT-012", "Leituras retidas", "rpt_leituras_retidas", "reporting.rpt-012:export", "referencia", List.of(col("referencia", ReportColumnType.DATE, false), col("unidade_id", ReportColumnType.TEXT, false), col("servico", ReportColumnType.TEXT, false), col("tipo_consumo", ReportColumnType.TEXT, false), col("consumo", ReportColumnType.LONG, false), col("ocorrencia_id", ReportColumnType.TEXT, false), col("retida_desde", ReportColumnType.TIMESTAMP, false)), List.of("referencia")),
            def("RPT-013", "Ocorrências de leitura", "rpt_ocorrencias_leitura", "reporting.rpt-013:export", "referencia", List.of(col("referencia", ReportColumnType.DATE, false), col("ocorrencia_codigo", ReportColumnType.TEXT, false), col("ocorrencia", ReportColumnType.TEXT, false), col("situacao_leitura", ReportColumnType.TEXT, false), col("leituras", ReportColumnType.LONG, false)), List.of("referencia", "ocorrencia_codigo")),
            def("RPT-014", "Histograma de consumo por faixa", "rpt_histograma_consumo", "reporting.rpt-014:export", "referencia", List.of(col("referencia", ReportColumnType.DATE, false), col("faixa_m3", ReportColumnType.TEXT, false), col("ligacoes", ReportColumnType.LONG, false), col("consumo_total", ReportColumnType.LONG, false)), List.of("referencia")),
            def("RPT-015", "Ligações e economias por situação", "rpt_ligacoes_situacao", "reporting.rpt-015:export", "situacao", List.of(col("situacao", ReportColumnType.TEXT, false), col("ocupacao", ReportColumnType.TEXT, false), col("ligacoes", ReportColumnType.LONG, false), col("economias", ReportColumnType.LONG, false)), List.of("situacao")),
            def("RPT-016", "Ligações e economias por categoria", "rpt_ligacoes_categoria", "reporting.rpt-016:export", "referencia", List.of(col("referencia", ReportColumnType.DATE, false), col("categoria_codigo", ReportColumnType.TEXT, false), col("categoria", ReportColumnType.TEXT, false), col("ligacoes", ReportColumnType.LONG, false), col("economias", ReportColumnType.LONG, false)), List.of("referencia", "categoria_codigo")),
            def("RPT-017", "Valores a receber por tipo de unidade", "rpt_a_receber_tipo_unidade", "reporting.rpt-017:export", "tipo_unidade", List.of(col("tipo_unidade", ReportColumnType.TEXT, false), col("contas_abertas", ReportColumnType.LONG, false), col("valor_a_receber", ReportColumnType.MONEY, false), col("vencimento_mais_antigo", ReportColumnType.DATE, false)), List.of("tipo_unidade")),
            def("RPT-018", "Pendências por usuário", "rpt_pendencias_usuario", "reporting.rpt-018:export", "usuario_id", List.of(col("usuario_id", ReportColumnType.TEXT, false), col("origem", ReportColumnType.TEXT, false), col("pendencias", ReportColumnType.LONG, false), col("mais_antiga_desde", ReportColumnType.TIMESTAMP, false)), List.of("origem")),
            def("RPT-019", "Aptos ao corte", "rpt_aptos_corte", "reporting.rpt-019:export", "unidade_id", List.of(col("unidade_id", ReportColumnType.TEXT, false), col("contas_vencidas", ReportColumnType.LONG, false), col("valor_devido", ReportColumnType.MONEY, false), col("vencimento_mais_antigo", ReportColumnType.DATE, false)), List.of("unidade_id")),
            def("RPT-020", "Notificações emitidas", "rpt_notificacoes_emitidas", "reporting.rpt-020:export", "data_aviso", List.of(col("data_aviso", ReportColumnType.DATE, false), col("situacao", ReportColumnType.TEXT, false), col("prova_entrega", ReportColumnType.TEXT, false), col("avisos", ReportColumnType.LONG, false), col("valor_total", ReportColumnType.MONEY, false)), List.of("data_aviso")),
            def("RPT-021", "Motivos de não geração do aviso", "rpt_motivos_nao_geracao_aviso", "reporting.rpt-021:export", "data", List.of(col("data", ReportColumnType.DATE, false), col("motivo", ReportColumnType.TEXT, false), col("unidades", ReportColumnType.LONG, false)), List.of("data", "motivo")),
            def("RPT-022", "Serviços por protocolo com tempos", "rpt_servicos_protocolo", "reporting.rpt-022:export", "protocol_id", List.of(col("protocol_id", ReportColumnType.TEXT, false), col("servico_codigo", ReportColumnType.TEXT, false), col("servico", ReportColumnType.TEXT, false), col("situacao", ReportColumnType.TEXT, false), col("aberta_em", ReportColumnType.TIMESTAMP, false), col("encerrada_em", ReportColumnType.TIMESTAMP, false), col("horas_ate_encerrar", ReportColumnType.DECIMAL, false)), List.of("situacao", "servico_codigo")),
            def("RPT-023", "Tarifa social", "rpt_tarifa_social", "reporting.rpt-023:export", "situacao", List.of(col("situacao", ReportColumnType.TEXT, false), col("base_elegibilidade", ReportColumnType.TEXT, false), col("inscricoes", ReportColumnType.LONG, false), col("economias_sociais", ReportColumnType.LONG, false)), List.of("situacao")),
            def("RPT-024", "Isenções", "rpt_isencoes", "reporting.rpt-024:export", "categoria_codigo", List.of(col("categoria_codigo", ReportColumnType.TEXT, false), col("vigencia_inicio", ReportColumnType.DATE, false), col("vigencia_fim", ReportColumnType.DATE, false), col("inscricoes", ReportColumnType.LONG, false)), List.of("categoria_codigo")),
            def("RPT-025", "NFAg-e autorizadas, rejeitadas e em contingência", "rpt_nfage_situacao", "reporting.rpt-025:export", "data", List.of(col("data", ReportColumnType.DATE, false), col("situacao_agrupada", ReportColumnType.TEXT, false), col("situacao", ReportColumnType.TEXT, false), col("documentos", ReportColumnType.LONG, false)), List.of("data", "situacao_agrupada")),
            def("RPT-026", "Devoluções", "rpt_devolucoes", "reporting.rpt-026:export", "data", List.of(col("data", ReportColumnType.DATE, false), col("motivo", ReportColumnType.TEXT, false), col("situacao", ReportColumnType.TEXT, false), col("devolucoes", ReportColumnType.LONG, false), col("valor_total", ReportColumnType.MONEY, false)), List.of("data")),
            def("RPT-027", "Reclamações — art. 54 da NR 11", "rpt_reclamacoes_art54", "reporting.rpt-027:export", "mes", List.of(col("mes", ReportColumnType.DATE, false), col("motivo", ReportColumnType.TEXT, false), col("sistema", ReportColumnType.TEXT, false), col("encerrados", ReportColumnType.LONG, false), col("nao_atendidos_no_prazo", ReportColumnType.LONG, false), col("percentual_nao_atendido", ReportColumnType.DECIMAL, false)), List.of("mes", "motivo"))
        );
    }

    private static ReportDefinition def(String code, String title, String view, String permission,
                                        String sort, List<ReportColumn> columns, List<String> filters) {
        return new ReportDefinition(code, title, ReportOrigin.BUILTIN, view, permission,
                columns, filters.stream().map(f -> new ReportFilter(f, ReportFilter.Operator.EQ_OR_RANGE)).toList(),
                sort, ReportDefinition.DEFAULT_MAX_ROWS);
    }

    private static ReportColumn col(String name, ReportColumnType type, boolean personal) {
        return new ReportColumn(name, type, personal);
    }
}
