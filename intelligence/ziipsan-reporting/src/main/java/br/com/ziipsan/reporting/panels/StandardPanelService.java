package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;
import br.com.ziipsan.reporting.indicator.IndicatorTarget;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ED-49: monta os sete paineis padrao LENDO do registro unico de
 * indicadores da P3.41 (JOR-19). E a regra central do aceite: indicador,
 * meta e semaforo sao os do registro — esta classe NAO importa o
 * avaliador de formulas, NAO conhece expressao nenhuma e NAO recalcula
 * semaforo; valor, semaforo e versao da formula de cada linha sao
 * exatamente os da apuracao {@code IndicatorRegistryService.compute}
 * (inclusive o valor CONGELADO de periodo fechado), nome e unidade sao
 * os da definicao, e a meta exibida e a meta vigente do registro.
 * Se a formula muda no registro (nova versao), o painel mostra o novo
 * valor sem nenhuma alteracao aqui — provado em teste.
 *
 * <p>A meta exibida segue a precedencia documentada da P3.41 (meta da
 * area prevalece sobre a geral; vigencia cobrindo o inicio do periodo).
 * Isto e LEITURA da meta para exibicao: o semaforo exibido continua
 * sendo o que o registro apurou, nao um semaforo refeito pelo painel.
 *
 * <p><b>Identidade exibido x registro (correcao D1a da ED-49).</b> O
 * valor transportado e o {@code BigDecimal} da apuracao, verbatim, e a
 * identidade afirmada e ESTRITA em valor e escala
 * ({@link PanelRowView#matchesRegistryValueExactly(java.math.BigDecimal)}):
 * cada linha montada e conferida contra a apuracao que a originou e uma
 * divergencia de representacao (por exemplo um {@code setScale} futuro
 * nesta camada) e recusada nomeada
 * ({@code PANEL_DISPLAYED_VALUE_DIVERGES_FROM_REGISTRY}), nunca exibida
 * como se fosse o valor do registro. A igualdade numerica cega a escala
 * do registro (P3.41) NAO e a semantica desta camada — pendencia
 * P-ED49-02, da dona do registro.
 *
 * <p>Exportacao sincrona em memoria (CSV/JSON), como o painel da ED-48;
 * JSON so pela classe {@code Json} do core. Correcao D3 da revisao: a
 * exportacao recusa linha nao selada
 * ({@code PANEL_ROW_NOT_FROM_REGISTRY}) — a identidade exibido x
 * registro nao vive so nesta montagem; ver {@link PanelRowView}.
 * A criacao dos dashboards no
 * Superset e fiacao da integracao (ver
 * {@link PanelSupersetManifest} e o doc de integracao).
 */
public final class StandardPanelService {

    private final IndicatorRegistryService registry;

    public StandardPanelService(IndicatorRegistryService registry) {
        this.registry = registry;
    }

    /** Monta um painel: so os pedidos daquele painel, na ordem deterministica de exibicao. */
    public List<PanelRowView> buildPanel(UUID tenantId, PanelKind kind,
                                         List<PanelCellRequest> requests) {
        List<PanelRowView> rows = new ArrayList<>();
        for (PanelCellRequest request : requests) {
            if (request.panel() != kind) {
                continue;
            }
            rows.add(buildRow(tenantId, request));
        }
        rows.sort(Comparator.comparing(PanelRowView::indicatorCode)
                .thenComparing(PanelRowView::periodStart)
                .thenComparing(r -> r.areaCode() == null ? "" : r.areaCode()));
        return List.copyOf(rows);
    }

    /** Monta os sete paineis de uma vez; painel sem pedido entra com lista vazia declarada. */
    public Map<PanelKind, List<PanelRowView>> buildAll(UUID tenantId,
                                                       List<PanelCellRequest> requests) {
        Map<PanelKind, List<PanelRowView>> all = new EnumMap<>(PanelKind.class);
        for (PanelKind kind : PanelKind.values()) {
            all.put(kind, buildPanel(tenantId, kind, requests));
        }
        return Map.copyOf(all);
    }

    private PanelRowView buildRow(UUID tenantId, PanelCellRequest request) {
        // Apuracao do registro: valor + semaforo + versao + congelado.
        // Indicador inexistente neste tenant propaga a recusa nomeada do
        // registro (INDICATOR_NOT_FOUND) — painel nao exibe o que o
        // registro nao tem, e ligacao pendurada falha nomeada, nao zero.
        IndicatorRegistryService.Computation computation = registry.compute(tenantId,
                request.indicatorCode(), request.periodStart(), request.periodEnd(),
                request.areaCode(), request.variableValues());
        var definition = registry.definition(tenantId, request.indicatorCode());
        IndicatorTarget target = targetForDisplay(tenantId, request);
        // Fabrica selada (correcao D3): linha de painel so nasce da
        // apuracao do registro, por esta via — ver PanelRowView.
        PanelRowView row = PanelRowView.fromRegistryComputation(request.panel(),
                request.indicatorCode(), definition.name(),
                definition.unit(), request.periodStart(), request.periodEnd(), request.areaCode(),
                computation.value(),
                target == null ? null : target.targetValue(),
                target == null ? null : target.direction().name(),
                computation.semaphore(), computation.frozen(), computation.formulaVersion());
        // Correcao D1a: a identidade exibido x registro conferida na
        // montagem, estrita em valor E escala. O transporte acima e
        // verbatim, entao a conferencia passa por construcao; ela existe
        // para que nenhuma normalizacao futura nesta camada (setScale,
        // arredondamento, reformatacao) passe a exibir uma representacao
        // diferente da do registro fingindo ser ela.
        if (!row.matchesRegistryValueExactly(computation.value())) {
            throw new IndicatorValidationException(
                    "PANEL_DISPLAYED_VALUE_DIVERGES_FROM_REGISTRY",
                    "valor exibido do indicador " + request.indicatorCode()
                            + " (" + row.value().toPlainString() + ", escala " + row.value().scale()
                            + ") diverge em valor ou escala do valor apurado pelo registro ("
                            + computation.value().toPlainString() + ", escala "
                            + computation.value().scale() + "): o painel nao afirma igualdade"
                            + " numerica como se fosse identidade de representacao (D1a/P-ED49-02)");
        }
        return row;
    }

    /**
     * Meta vigente do registro para exibicao (periodo/area do pedido),
     * com a precedencia da P3.41: a da area vence a geral. Nenhum valor
     * e criado aqui — sem meta vigente, devolve {@code null} declarado.
     *
     * <p><b>Ressalva D1 da revisao (duplicacao conhecida e forcada):</b>
     * este metodo DUPLICA a logica de selecao de meta da P3.41 porque a
     * apuracao ({@code Computation}) nao expoe a meta escolhida.
     * Pendencia nomeada <b>P5</b> (RELATORIO-ED-49, adendo de correcao):
     * a P3.41 expor a meta na propria apuracao e este metodo ser
     * aposentado, passando o servico a consumi-la. Ate la, a paridade
     * comportamental esta travada em teste
     * ({@code areaTargetPrevailsOverGeneralInTheDisplayedTarget}).
     */
    private IndicatorTarget targetForDisplay(UUID tenantId, PanelCellRequest request) {
        IndicatorTarget general = null;
        for (IndicatorTarget target : registry.targets(tenantId, request.indicatorCode())) {
            if (!target.covers(request.periodStart())) {
                continue;
            }
            if (request.areaCode() != null && request.areaCode().equals(target.areaCode())) {
                return target;
            }
            if (target.areaCode() == null) {
                general = target;
            }
        }
        return general;
    }

    /**
     * Correcao D3 da revisao: a exportacao so emite linha SELADA —
     * montada por este servico a partir da apuracao do registro (ver
     * {@link PanelRowView}). Linha construida por fora (valor/semaforo
     * inventados) e recusada nomeada ANTES de qualquer saida, nunca
     * exportada como se fosse valor do registro.
     */
    private static void requireRegistrySealed(List<PanelRowView> rows) {
        for (PanelRowView row : rows) {
            if (!row.registrySealed()) {
                throw new IndicatorValidationException(
                        "PANEL_ROW_NOT_FROM_REGISTRY",
                        "exportacao recusada: a linha do indicador " + row.indicatorCode()
                                + " nao foi montada pelo StandardPanelService a partir da"
                                + " apuracao do registro (P3.41) — valor e semaforo exibidos"
                                + " so existem como leitura conferida do registro (D3)");
            }
        }
    }

    /** CSV do painel (';' pt-BR; decimais por toPlainString, sem arredondamento novo). */
    public String exportCsv(List<PanelRowView> rows) {
        requireRegistrySealed(rows);
        StringBuilder sb = new StringBuilder();
        sb.append("painel;indicador;nome;unidade;periodo_inicio;periodo_fim;area;valor;meta;"
                + "direcao_meta;semaforo;fechado;versao_formula\n");
        for (PanelRowView row : rows) {
            sb.append(row.panel().name()).append(';')
                    .append(csv(row.indicatorCode())).append(';')
                    .append(csv(row.indicatorName())).append(';')
                    .append(csv(row.unit())).append(';')
                    .append(row.periodStart()).append(';')
                    .append(row.periodEnd()).append(';')
                    .append(csv(row.areaCode())).append(';')
                    .append(row.value().toPlainString()).append(';')
                    .append(row.targetValue() == null ? "" : row.targetValue().toPlainString()).append(';')
                    .append(csv(row.targetDirection())).append(';')
                    .append(row.semaphore().name()).append(';')
                    .append(row.frozen() ? "SIM" : "NAO").append(';')
                    .append(row.formulaVersion()).append('\n');
        }
        return sb.toString();
    }

    /** JSON do painel, serializado so pela classe Json do core. */
    public String exportJson(List<PanelRowView> rows) {
        requireRegistrySealed(rows);
        List<Map<String, String>> out = new ArrayList<>();
        for (PanelRowView row : rows) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("painel", row.panel().name());
            item.put("indicador", row.indicatorCode());
            item.put("nome", row.indicatorName());
            item.put("unidade", row.unit());
            item.put("periodo_inicio", row.periodStart().toString());
            item.put("periodo_fim", row.periodEnd().toString());
            item.put("area", row.areaCode());
            item.put("valor", row.value().toPlainString());
            item.put("meta", row.targetValue() == null ? null : row.targetValue().toPlainString());
            item.put("direcao_meta", row.targetDirection());
            item.put("semaforo", row.semaphore().name());
            item.put("fechado", row.frozen() ? "SIM" : "NAO");
            item.put("versao_formula", Integer.toString(row.formulaVersion()));
            out.add(item);
        }
        return Json.write(out);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(';', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** Atalho de leitura usado pelo painel estrategico: a data e o periodo do pedido. */
    static LocalDate periodStartOf(PanelCellRequest request) {
        return request.periodStart();
    }
}
