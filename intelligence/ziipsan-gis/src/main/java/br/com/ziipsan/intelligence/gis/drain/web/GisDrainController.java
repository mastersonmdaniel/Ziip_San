package br.com.ziipsan.intelligence.gis.drain.web;

import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.CadastroReportRow;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.CleaningPlanInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.CleaningRequestView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.ConservationClassCount;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.DrainFeatureInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.DrainFeatureView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.FloodOccurrenceInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.FloodOccurrenceView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.GenerationResult;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.InspectionInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.InspectionView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.PhotoInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.RainImportView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.RainStationInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.SubcatchmentInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.SymbolManifestView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.ValidationIssueView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainMaintenance.DrainIndicators;
import br.com.ziipsan.intelligence.gis.drain.GisDrainService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GIS-13: drenagem — feições do §4.4, validação de declividade e
 * sentido, inspeção (conservação/obstrução/foto), sub-bacias SWMM,
 * plano de limpeza por classe com pedidos recorrentes, chuva
 * importada (Cemaden por download ou pluviômetro próprio), ocorrência
 * de alagamento ligada à chuva, temático de conservação, relatórios
 * GIS-003/GIS-004 e manifesto de símbolos.
 */
@RestController
@RequestMapping("/api/v1/gis/drain")
public class GisDrainController {

    private final GisDrainService service;

    public GisDrainController(GisDrainService service) {
        this.service = service;
    }

    @PostMapping("/features")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisDrainRoles.EDIT)
    public DrainFeatureView createFeature(@Valid @RequestBody DrainFeatureInput in) {
        return service.createFeature(in);
    }

    @GetMapping("/features/{id}")
    @PreAuthorize(GisDrainRoles.READ)
    public DrainFeatureView getFeature(@PathVariable UUID id) {
        return service.getFeature(id);
    }

    /** Validação de declividade/sentido de um arco, sem gravar (§5.4). */
    @PostMapping("/features/validate-arc")
    @PreAuthorize(GisDrainRoles.READ)
    public List<ValidationIssueView> validateArc(
            @Valid @RequestBody DrainFeatureInput in,
            @RequestParam double lengthM,
            @RequestParam(required = false) Double tenantMinSlope) {
        return service.validateArc(in, lengthM, tenantMinSlope);
    }

    @PostMapping("/features/{featureTable}/{id}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisDrainRoles.EDIT)
    public void attachPhoto(@PathVariable String featureTable, @PathVariable UUID id,
                            @Valid @RequestBody PhotoInput in) {
        service.attachPhoto(featureTable, id, in);
    }

    @PostMapping("/features/{featureTable}/{id}/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisDrainRoles.EDIT)
    public InspectionView registerInspection(@PathVariable String featureTable,
                                             @PathVariable UUID id,
                                             @Valid @RequestBody InspectionInput in) {
        return service.registerInspection(featureTable, id, in);
    }

    @GetMapping("/features/{featureTable}/{id}/inspections")
    @PreAuthorize(GisDrainRoles.READ)
    public List<InspectionView> inspections(@PathVariable String featureTable,
                                            @PathVariable UUID id) {
        return service.inspectionsOf(featureTable, id);
    }

    @PostMapping("/subcatchments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisDrainRoles.EDIT)
    public DrainFeatureView createSubcatchment(@Valid @RequestBody SubcatchmentInput in) {
        return service.createSubcatchment(in);
    }

    /** Plano de limpeza por classe (NR 12/ANA arts. 11–12; intervalos do tenant). */
    @PutMapping("/cleaning-plan")
    @PreAuthorize(GisDrainRoles.EDIT)
    public void upsertCleaningPlan(@Valid @RequestBody CleaningPlanInput in) {
        service.upsertCleaningPlan(in);
    }

    /** Gera os pedidos recorrentes de limpeza devidos na data (OS é da ED-36). */
    @PostMapping("/cleaning-requests/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisDrainRoles.EDIT)
    public GenerationResult generateCleaningRequests(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        return service.generateCleaningRequests(referenceDate);
    }

    @GetMapping("/cleaning-requests")
    @PreAuthorize(GisDrainRoles.READ)
    public List<CleaningRequestView> cleaningRequests(
            @RequestParam(required = false) String status) {
        return service.cleaningRequests(status);
    }

    @PostMapping("/rain-stations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisDrainRoles.EDIT)
    public UUID createRainStation(@Valid @RequestBody RainStationInput in) {
        return service.createRainStation(in);
    }

    /** Importação do download mensal (Cemaden) ou do pluviômetro próprio — sem API. */
    @PostMapping("/rain-stations/{id}/import")
    @PreAuthorize(GisDrainRoles.EDIT)
    public RainImportView importRain(@PathVariable UUID id,
                                     @RequestParam String sourceRef,
                                     @RequestBody String csvContent) {
        return service.importRain(id, sourceRef, csvContent);
    }

    /** Ocorrência de alagamento ligada à chuva do período (causa da ED-92). */
    @PostMapping("/flood-occurrences")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisDrainRoles.EDIT)
    public FloodOccurrenceView registerFloodOccurrence(
            @Valid @RequestBody FloodOccurrenceInput in) {
        return service.registerFloodOccurrence(in);
    }

    /** GIS-003: relatório do cadastro de drenagem (extensão por material e estado). */
    @GetMapping("/reports/cadastro")
    @PreAuthorize(GisDrainRoles.READ)
    public List<CadastroReportRow> cadastroReport() {
        return service.cadastroReport();
    }

    /** GIS-004: indicadores operacionais da drenagem no período (OPE-32). */
    @GetMapping("/reports/indicadores")
    @PreAuthorize(GisDrainRoles.READ)
    public DrainIndicators indicators(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.indicators(from, to);
    }

    /** Temático de conservação: feições por estado (§4.4/GIS-08). */
    @GetMapping("/thematic/conservation")
    @PreAuthorize(GisDrainRoles.READ)
    public List<ConservationClassCount> conservationThematic() {
        return service.conservationThematic();
    }

    /**
     * Manifesto de símbolos (inventário de chaves por tipo × estado).
     * A biblioteca informada é a da GIS-06 na integração; sem arte da
     * P1.30, presentInLibrary vem falso — marcador declarado, e o
     * aceite do dono sobre os símbolos é pendência dele.
     */
    @GetMapping("/symbols")
    @PreAuthorize(GisDrainRoles.READ)
    public List<SymbolManifestView> symbols() {
        return service.symbolManifest(Set.of());
    }
}
