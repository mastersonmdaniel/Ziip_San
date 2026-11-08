package br.com.ziipsan.intelligence.gis.thematic.web;

import br.com.ziipsan.intelligence.gis.thematic.GisRegistryReportService;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ExtensionReportView;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.PrecisionRow;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Relatórios do cadastro da GIS-08 servidos pelo GIS: extensão em
 * qualquer data ({@code ?asOf=AAAA-MM-DD}, tempo do negócio do
 * §3.7) e o resumo de precisão por classe e método. A exportação
 * em PDF/XLSX/CSV dos mesmos dados sai pelo núcleo da P3.22 com
 * as definições GIS-001/GIS-002 registradas por
 * {@link br.com.ziipsan.intelligence.gis.thematic.GisThematicReportDefinitions}.
 */
@RestController
@RequestMapping(GisThematicRoles.REPORTS_BASE)
@PreAuthorize(GisThematicRoles.VIEW)
public class GisRegistryReportController {

    private final GisRegistryReportService service;

    public GisRegistryReportController(GisRegistryReportService service) {
        this.service = service;
    }

    @GetMapping("/extension")
    public ExtensionReportView extension(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.extensionAt(asOf);
    }

    @GetMapping("/precision")
    public List<PrecisionRow> precision() {
        return service.precisionSummary();
    }
}
