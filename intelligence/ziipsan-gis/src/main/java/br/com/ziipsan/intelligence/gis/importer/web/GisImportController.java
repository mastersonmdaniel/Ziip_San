package br.com.ziipsan.intelligence.gis.importer.web;

import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.BatchView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.ContextFeatureInput;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.ContextFeatureView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.CreateBatchInput;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.LayerMapInput;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.LayerMapView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.PageView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.ProfileView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.QualityReportInput;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.RawView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.StageRowInput;
import br.com.ziipsan.intelligence.gis.importer.GisImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Importação do cadastro técnico (P3.26, §9): lotes, staging, perfis e
 * mapas de camada, contexto, relatório ISO 19157 e publicação por
 * pacote IMPORT. A publicação só submete o pacote — aplicar é da
 * GIS-04, por revisor diferente do autor.
 */
@RestController
@RequestMapping(GisImportRoles.BASE)
@PreAuthorize(GisImportRoles.READ)
public class GisImportController {

    private final GisImportService service;

    public GisImportController(GisImportService service) {
        this.service = service;
    }

    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisImportRoles.EDIT)
    public BatchView createBatch(@Valid @RequestBody CreateBatchInput in) {
        return service.createBatch(in);
    }

    @GetMapping("/batches")
    public PageView<BatchView> listBatches(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listBatches(status, page, size);
    }

    @GetMapping("/batches/{id}")
    public BatchView getBatch(@PathVariable UUID id) {
        return service.getBatch(id);
    }

    @PostMapping("/batches/{id}/stage")
    @PreAuthorize(GisImportRoles.EDIT)
    public BatchView markStaged(@PathVariable UUID id) {
        return service.markStaged(id);
    }

    @PostMapping("/batches/{id}/rows")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisImportRoles.EDIT)
    public RawView stageRow(@PathVariable UUID id, @Valid @RequestBody StageRowInput in) {
        return service.stageRow(id, in);
    }

    @GetMapping("/batches/{id}/rows")
    public List<RawView> listRaw(@PathVariable UUID id,
                                 @RequestParam(required = false) String status) {
        return service.listRaw(id, status);
    }

    @PostMapping("/batches/{id}/map")
    @PreAuthorize(GisImportRoles.EDIT)
    public BatchView applyMapping(@PathVariable UUID id) {
        return service.applyMapping(id);
    }

    @PostMapping("/batches/{id}/labels")
    @PreAuthorize(GisImportRoles.EDIT)
    public Map<String, Integer> extractLabels(@PathVariable UUID id) {
        return Map.of("arcsLabelled", service.extractLabels(id));
    }

    @PostMapping("/batches/{id}/clean")
    @PreAuthorize(GisImportRoles.EDIT)
    public BatchView clean(@PathVariable UUID id) {
        return service.clean(id);
    }

    @PostMapping("/batches/{id}/context-features")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisImportRoles.EDIT)
    public ContextFeatureView addContextFeature(@PathVariable UUID id,
                                                @Valid @RequestBody ContextFeatureInput in) {
        return service.addContextFeature(id, in);
    }

    @GetMapping("/batches/{id}/context-features")
    public List<ContextFeatureView> listContextFeatures(@PathVariable UUID id) {
        return service.listContextFeatures(id);
    }

    @PostMapping("/batches/{id}/quality-report")
    @PreAuthorize(GisImportRoles.EDIT)
    public BatchView saveQualityReport(@PathVariable UUID id,
                                       @Valid @RequestBody QualityReportInput in) {
        return service.saveQualityReport(id, in);
    }

    @GetMapping("/batches/{id}/quality-report")
    public Map<String, Object> getQualityReport(@PathVariable UUID id) {
        return service.getQualityReport(id);
    }

    /** Publica o lote como pacote IMPORT submetido; devolve o pacote. */
    @PostMapping("/batches/{id}/publish")
    @PreAuthorize(GisImportRoles.EDIT)
    public Map<String, UUID> publish(@PathVariable UUID id) {
        return Map.of("changeSetId", service.publish(id));
    }

    @GetMapping("/profiles")
    public List<ProfileView> listProfiles() {
        return service.listProfiles();
    }

    @GetMapping("/profiles/{profileId}/layer-maps")
    public List<LayerMapView> listLayerMaps(@PathVariable UUID profileId) {
        return service.listLayerMaps(profileId);
    }

    @PostMapping("/profiles/{profileId}/layer-maps")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisImportRoles.EDIT)
    public LayerMapView saveLayerMap(@PathVariable UUID profileId,
                                     @Valid @RequestBody LayerMapInput in) {
        return service.saveLayerMap(profileId, in);
    }
}
