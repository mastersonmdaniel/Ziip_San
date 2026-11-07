package br.com.ziipsan.reporting.web;

import br.com.ziipsan.reporting.export.ExportFormat;
import br.com.ziipsan.reporting.export.ReportExportService;
import br.com.ziipsan.reporting.query.ReportQuery;
import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.registry.ReportRegistry;
import br.com.ziipsan.reporting.spi.ReportUserContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Borda HTTP minima do nucleo. O contexto do usuario chega resolvido pela
 * fiacao de seguranca do ziipsan-app (atributo de requisicao
 * {@code ziipsan.reportUser}).
 *
 * <p>CORRECOES G2 (pos-revisao):
 * <ul>
 *   <li>a exportacao MONTA o ReportQuery dos parametros HTTP (antes
 *       executava sempre ReportQuery.empty(), ignorando filtro,
 *       ordenacao e agrupamento pedidos). Convencao: {@code sort} +
 *       {@code dir} (asc/desc), {@code groupBy} (lista por virgula) e
 *       filtros como {@code <coluna>=valor} ou faixa
 *       {@code <coluna>_from}/{@code <coluna>_to}; coluna nao declarada
 *       como filtro e recusada pelo ReportQueryBuilder;</li>
 *   <li>ausencia do atributo de usuario e RECUSA EXPLICITA
 *       (IllegalStateException): a versao anterior FABRICAVA um contexto
 *       com tenantId aleatorio e userId nulo quando o atributo faltava -
 *       contexto fabricado nunca e base para exportar dado de tenant.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reporting/reports")
public class ReportController {

    private static final Set<String> RESERVED = Set.of("format", "sort", "dir", "groupBy");

    private final ReportRegistry registry;
    private final ReportExportService exportService;

    public ReportController(ReportRegistry registry, ReportExportService exportService) {
        this.registry = registry;
        this.exportService = exportService;
    }

    @GetMapping
    public List<ReportDefinition> list(jakarta.servlet.http.HttpServletRequest request) {
        ReportUserContext user = requireUser(request);
        return registry.allForTenant(user.tenantId());
    }

    @PostMapping("/{code}/export")
    public ResponseEntity<byte[]> export(@PathVariable String code,
                                         @RequestParam(defaultValue = "PDF") ExportFormat format,
                                         jakarta.servlet.http.HttpServletRequest request) {
        ReportUserContext user = requireUser(request);
        ReportDefinition definition = registry.find(code, user.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Relatorio desconhecido: " + code));
        ReportQuery query = queryFrom(request);
        var outcome = exportService.export(definition, query, format, user, false);
        if (outcome.isQueued()) {
            return ResponseEntity.accepted()
                    .header("X-Generated-File-Id", outcome.generatedFileId().toString())
                    .body(new byte[0]);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, format.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + code.toLowerCase() + "." + format.extension() + "\"")
                .contentType(MediaType.parseMediaType(format.contentType()))
                .body(outcome.content());
    }

    /** Contexto obrigatorio: sem atributo valido, recusa - nunca fabrica. */
    static ReportUserContext requireUser(jakarta.servlet.http.HttpServletRequest request) {
        Object attribute = request.getAttribute("ziipsan.reportUser");
        if (attribute instanceof ReportUserContext context && context.tenantId() != null) {
            return context;
        }
        throw new IllegalStateException(
                "Contexto de usuario ausente (atributo ziipsan.reportUser): exportacao recusada");
    }

    /** Monta a query dos parametros HTTP (convencao no javadoc da classe). */
    static ReportQuery queryFrom(jakarta.servlet.http.HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        Map<String, ReportQuery.FilterValue> filters = new LinkedHashMap<>();
        Map<String, String[]> ranges = new LinkedHashMap<>();
        for (var entry : params.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue().length == 0 ? null : entry.getValue()[0];
            if (RESERVED.contains(name) || value == null) {
                continue;
            }
            if (name.endsWith("_from")) {
                ranges.computeIfAbsent(name.substring(0, name.length() - 5), k -> new String[2])[0] = value;
            } else if (name.endsWith("_to")) {
                ranges.computeIfAbsent(name.substring(0, name.length() - 3), k -> new String[2])[1] = value;
            } else {
                filters.put(name, ReportQuery.FilterValue.eq(value));
            }
        }
        ranges.forEach((column, range) ->
                filters.put(column, ReportQuery.FilterValue.range(range[0], range[1])));
        String sort = first(params, "sort");
        boolean ascending = !"desc".equalsIgnoreCase(first(params, "dir"));
        String groupByParam = first(params, "groupBy");
        List<String> groupBy = groupByParam == null || groupByParam.isBlank()
                ? List.of()
                : List.of(groupByParam.split(","));
        return new ReportQuery(filters, sort, ascending, groupBy);
    }

    private static String first(Map<String, String[]> params, String name) {
        String[] values = params.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }
}
