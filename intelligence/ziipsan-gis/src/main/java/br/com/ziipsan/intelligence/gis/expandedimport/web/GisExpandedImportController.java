package br.com.ziipsan.intelligence.gis.expandedimport.web;

import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.CreateExpandedBatchInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.ExpandedBatchView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.GnssPointInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.GnssPointView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.MatchDecisionInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.MatchQueueView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.MatchRequestInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.ReconciliationInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.ReconciliationView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.RolloutStageInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.RolloutStageView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * GIS-18: abertura de lote ampliado (DGN/INP/GNSS), fila de casamento
 * (SG-1349), conciliacao (SG-1356) e plano de implantacao (SG-1334).
 * Os lotes dos formatos-base sao os da GIS-07 (mesmo prefixo de rota,
 * subcaminhos distintos).
 */
@RestController
@RequestMapping("/api/v1/gis/imports")
public class GisExpandedImportController {

    private final GisExpandedImportService service;

    public GisExpandedImportController(GisExpandedImportService service) {
        this.service = service;
    }

    /** Aceite de lote ampliado (correcao D3): aplica a regra GIS-18. */
    @PostMapping("/batches/expanded")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisExpandedImportRoles.EDIT)
    public ExpandedBatchView openExpandedBatch(
            @Valid @RequestBody CreateExpandedBatchInput in) {
        return service.openExpandedBatch(in);
    }

    @PostMapping("/batches/{batchId}/gnss-points")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisExpandedImportRoles.EDIT)
    public GnssPointView stageGnssPoint(@PathVariable UUID batchId,
                                        @Valid @RequestBody GnssPointInput in) {
        return service.stageGnssPoint(batchId, in);
    }

    @PostMapping("/batches/{batchId}/unit-matches")
    @PreAuthorize(GisExpandedImportRoles.EDIT)
    public MatchQueueView enqueueMatch(@PathVariable UUID batchId,
                                       @Valid @RequestBody MatchRequestInput in) {
        return service.enqueueMatch(batchId, in);
    }

    @GetMapping("/batches/{batchId}/unit-matches/pending")
    @PreAuthorize(GisExpandedImportRoles.READ)
    public List<MatchQueueView> pendingMatches(@PathVariable UUID batchId) {
        return service.listPendingMatches(batchId);
    }

    @PostMapping("/unit-matches/{id}/decision")
    @PreAuthorize(GisExpandedImportRoles.EDIT)
    public MatchQueueView decideMatch(@PathVariable UUID id,
                                      @Valid @RequestBody MatchDecisionInput in) {
        return service.decideMatch(id, in);
    }

    @PostMapping("/batches/{batchId}/reconciliations")
    @PreAuthorize(GisExpandedImportRoles.EDIT)
    public ReconciliationView reconcile(@PathVariable UUID batchId,
                                        @RequestBody ReconciliationInput in) {
        return service.reconcile(batchId, in);
    }

    @PostMapping("/rollout-stages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisExpandedImportRoles.EDIT)
    public RolloutStageView createRolloutStage(
            @Valid @RequestBody RolloutStageInput in) {
        return service.createRolloutStage(in);
    }

    @GetMapping("/rollout-stages")
    @PreAuthorize(GisExpandedImportRoles.READ)
    public List<RolloutStageView> rolloutStages() {
        return service.listRolloutStages();
    }
}
