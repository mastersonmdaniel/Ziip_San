package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ExtensionReportView;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ExtensionRow;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.PrecisionRow;
import br.com.ziipsan.reporting.registry.ExternalReportRegistrar;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Relatórios do cadastro da GIS-08: extensão de rede em qualquer
 * data e resumo de precisão, servidos pelo GIS, e o registro das
 * definições GIS-001/GIS-002 no núcleo de relatórios da P3.22 para
 * a exportação em PDF, XLSX e CSV sair por lá (§11: relatório de
 * extensão "pelo núcleo de relatórios da P3.22").
 *
 * <p>A soma de {@code extensao_m} é o {@code ST_Length} geodésico
 * da coluna gerada da V13_2 somado na função/view — a mesma conta
 * que o teste da IT confere arco a arco.
 */
@Service
@Transactional(readOnly = true)
public class GisRegistryReportService {

    private final JdbcTemplate jdbc;

    public GisRegistryReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Extensão agrupada na data pedida (tempo do negócio, §3.7). */
    public ExtensionReportView extensionAt(LocalDate asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("data do relatório é obrigatória");
        }
        GisThematicContext.tenantId();
        List<ExtensionRow> rows = jdbc.query(GisThematicSql.extensionAtSql(), (rs, n) ->
                new ExtensionRow(
                        rs.getString("dominio"), rs.getString("funcao"),
                        rs.getString("material"),
                        (Integer) rs.getObject("dn_mm"),
                        (Integer) rs.getObject("decada_inicio"),
                        rs.getString("setor_codigo"), rs.getString("setor"),
                        rs.getBigDecimal("extensao_m"), rs.getLong("arcos")),
                asOf);
        BigDecimal total = rows.stream()
                .map(ExtensionRow::extensionM)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ExtensionReportView(asOf, rows, total);
    }

    /** Precisão por classe, método e grupo (§3.8, §13.6: relatório por classe e método). */
    public List<PrecisionRow> precisionSummary() {
        UUID tenantId = GisThematicContext.tenantId();
        return jdbc.query(GisThematicSql.precisionSummarySql(), (rs, n) ->
                new PrecisionRow(
                        rs.getString("classe"), rs.getString("metodo_captura"),
                        rs.getString("grupo_precisao"), rs.getString("dominio"),
                        rs.getLong("feicoes")),
                tenantId);
    }

    /**
     * Registra GIS-001 e GIS-002 no núcleo para o tenant (origem
     * EXTERNAL, persistido pela P3.22). Chamado pela fiação da
     * aplicação na ativação do pacote GIS do tenant; registrar duas
     * vezes o mesmo código é recusado pelo próprio registrador.
     */
    @Transactional
    public void registerReports(UUID tenantId, ExternalReportRegistrar registrar) {
        for (var definition : GisThematicReportDefinitions.definitions()) {
            registrar.register(tenantId, definition);
        }
    }
}
