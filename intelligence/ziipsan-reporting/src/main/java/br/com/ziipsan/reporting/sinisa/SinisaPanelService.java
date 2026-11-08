package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.reporting.indicator.IndicatorRegistryService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Painel gerencial/SINISA exportavel da ED-48. Apura cada celula pelo
 * registro unico da P3.41 — logo o FECHAMENTO (valor congelado) e o
 * SEMAFORO (inclusive {@code NO_TARGET} sem meta, nunca verde presumido)
 * do registro valem para o painel sem reimplementacao — filtra por
 * periodo, setor, categoria e tipo de ligacao e exporta em CSV e em JSON
 * (JSON so pela classe {@code Json} do core).
 *
 * <p>A exportacao e sincrona e em memoria, como os escritores da P3.22;
 * a entrega assincrona em Arquivos gerados e a fiacao da P3.22/P1.12 na
 * integracao, nao uma segunda implementacao aqui.
 */
public final class SinisaPanelService {

    private final IndicatorRegistryService registry;

    public SinisaPanelService(IndicatorRegistryService registry) {
        this.registry = registry;
    }

    /** Apura e filtra as celulas; ordenacao deterministica para exportacao estavel. */
    public List<PanelRow> buildPanel(UUID tenantId, List<PanelCell> cells, PanelFilter filter) {
        PanelFilter effective = filter == null ? PanelFilter.none() : filter;
        List<PanelRow> rows = new ArrayList<>();
        for (PanelCell cell : cells) {
            // O setor da celula e a area da apuracao no registro (meta por
            // area e fechamento por area da P3.41 usam a mesma chave).
            IndicatorRegistryService.Computation computation = registry.compute(tenantId,
                    cell.indicatorCode(), cell.periodStart(), cell.periodEnd(), cell.sector(),
                    cell.variableValues());
            PanelRow row = new PanelRow(cell.indicatorCode(), cell.periodStart(), cell.periodEnd(),
                    cell.sector(), cell.category(), cell.connectionType(),
                    computation.value(), computation.semaphore(), computation.frozen());
            if (effective.matches(row)) {
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(PanelRow::indicatorCode)
                .thenComparing(PanelRow::periodStart)
                .thenComparing(r -> r.sector() == null ? "" : r.sector())
                .thenComparing(r -> r.category() == null ? "" : r.category())
                .thenComparing(r -> r.connectionType() == null ? "" : r.connectionType()));
        return List.copyOf(rows);
    }

    /**
     * CSV do painel (separador ';', padrao das planilhas pt-BR; decimais em
     * texto integral por {@code toPlainString}, sem arredondamento novo).
     * Dimensao ausente sai vazia — ausencia declarada, nao rotulo inventado.
     */
    public String exportCsv(List<PanelRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("indicador;periodo_inicio;periodo_fim;setor;categoria;tipo_ligacao;valor;semaforo;fechado\n");
        for (PanelRow row : rows) {
            sb.append(csv(row.indicatorCode())).append(';')
                    .append(row.periodStart()).append(';')
                    .append(row.periodEnd()).append(';')
                    .append(csv(row.sector())).append(';')
                    .append(csv(row.category())).append(';')
                    .append(csv(row.connectionType())).append(';')
                    .append(row.value().toPlainString()).append(';')
                    .append(row.semaphore().name()).append(';')
                    .append(row.frozen() ? "SIM" : "NAO").append('\n');
        }
        return sb.toString();
    }

    /** JSON do painel, serializado so pela classe Json do core. */
    public String exportJson(List<PanelRow> rows) {
        List<Map<String, String>> out = new ArrayList<>();
        for (PanelRow row : rows) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("indicador", row.indicatorCode());
            item.put("periodo_inicio", row.periodStart().toString());
            item.put("periodo_fim", row.periodEnd().toString());
            item.put("setor", row.sector());
            item.put("categoria", row.category());
            item.put("tipo_ligacao", row.connectionType());
            item.put("valor", row.value().toPlainString());
            item.put("semaforo", row.semaphore().name());
            item.put("fechado", row.frozen() ? "SIM" : "NAO");
            out.add(item);
        }
        return Json.write(out);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        // Dimensao e rotulo declarado por quem alimenta; separador e quebra
        // de linha dentro do rotulo quebrariam a grade — substituidos por
        // espaco, sem alterar numeros (numeros nao passam por aqui).
        return value.replace(';', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
