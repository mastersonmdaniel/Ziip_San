package br.com.ziipsan.intelligence.gis.webedit.web;

import br.com.ziipsan.intelligence.gis.webedit.GisFormSchema;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.DrawnSyncView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinResultView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.LayerPermissionView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.MergeInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.MergeView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.OpenForServiceOrderInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.PlantStructureInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.PlantStructureView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.ProjectInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.ProjectView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.SplitInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.SplitView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.TransformInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.TransformView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserFeatureInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserFeatureView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserLayerInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserLayerView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Editor web completo (GIS-09, ED-53). Operações geométricas são
 * puras (pré-visualização da tela); escritas próprias gravam nas
 * tabelas da GIS-09 (SQL em db/pendente) e a edição da rede segue
 * por pacote da GIS-04.
 */
@RestController
@RequestMapping("/api/v1/gis/web-edit")
public class GisWebEditController {

    private final GisWebEditService service;

    public GisWebEditController(GisWebEditService service) {
        this.service = service;
    }

    @PostMapping("/geometry/transform")
    @PreAuthorize(GisWebEditRoles.READ)
    public TransformView transform(@Valid @RequestBody TransformInput in) {
        return service.transform(in);
    }

    @PostMapping("/geometry/split")
    @PreAuthorize(GisWebEditRoles.READ)
    public SplitView split(@Valid @RequestBody SplitInput in) {
        return service.split(in, false);
    }

    @PostMapping("/geometry/insert-node")
    @PreAuthorize(GisWebEditRoles.READ)
    public SplitView insertNode(@Valid @RequestBody SplitInput in) {
        return service.split(in, true);
    }

    @PostMapping("/geometry/merge")
    @PreAuthorize(GisWebEditRoles.READ)
    public MergeView merge(@Valid @RequestBody MergeInput in) {
        return service.merge(in);
    }

    @GetMapping("/layer-permissions/mine")
    @PreAuthorize(GisWebEditRoles.READ)
    public List<LayerPermissionView> myLayerPermissions() {
        return service.myLayerPermissions();
    }

    @PostMapping("/user-layers")
    @PreAuthorize(GisWebEditRoles.EDIT)
    public UserLayerView createUserLayer(@Valid @RequestBody UserLayerInput in) {
        return service.createUserLayer(in);
    }

    @GetMapping("/user-layers")
    @PreAuthorize(GisWebEditRoles.READ)
    public List<UserLayerView> listUserLayers() {
        return service.listUserLayers();
    }

    /** Feição da camada do usuário (SG-840) — correção D5. */
    @PostMapping("/user-layers/{layerId}/features")
    @PreAuthorize(GisWebEditRoles.EDIT)
    public UserFeatureView addUserFeature(
            @PathVariable UUID layerId,
            @Valid @RequestBody UserFeatureInput in) {
        return service.addUserFeature(layerId, in);
    }

    @GetMapping("/user-layers/{layerId}/features")
    @PreAuthorize(GisWebEditRoles.READ)
    public List<UserFeatureView> listUserFeatures(@PathVariable UUID layerId) {
        return service.listUserFeatures(layerId);
    }

    @PostMapping("/table-joins")
    @PreAuthorize(GisWebEditRoles.EDIT)
    public JoinView createJoin(@Valid @RequestBody JoinInput in) {
        return service.createJoin(in);
    }

    /** Executa a junção definida (SG-841) — correção D5. */
    @PostMapping("/table-joins/{joinId}/execute")
    @PreAuthorize(GisWebEditRoles.READ)
    public JoinResultView executeJoin(@PathVariable UUID joinId) {
        return service.executeJoin(joinId);
    }

    @PostMapping("/plant-structures/draft")
    @PreAuthorize(GisWebEditRoles.EDIT)
    public PlantStructureView draftPlantStructure(
            @Valid @RequestBody PlantStructureInput in) {
        return service.draftPlantStructure(in);
    }

    @PostMapping("/forms/from-schema")
    @PreAuthorize(GisWebEditRoles.READ)
    public GisFormSchema.FormDescriptor formFromSchema(
            @RequestBody Map<String, Object> attributesSchema) {
        return service.formForSchema(attributesSchema);
    }

    @PostMapping("/projects")
    @PreAuthorize(GisWebEditRoles.EDIT)
    public ProjectView createProject(@Valid @RequestBody ProjectInput in) {
        return service.createProject(in);
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize(GisWebEditRoles.READ)
    public ProjectView getProject(@PathVariable UUID id) {
        return service.getProject(id);
    }

    @PostMapping("/service-orders/open-change-set")
    @PreAuthorize(GisWebEditRoles.EDIT)
    public Optional<UUID> openForServiceOrder(
            @Valid @RequestBody OpenForServiceOrderInput in) {
        return service.openChangeSetForServiceOrder(in);
    }

    @PostMapping("/drawn-connections/{gisConnectionId}/sync")
    @PreAuthorize(GisWebEditRoles.EDIT)
    public DrawnSyncView syncDrawnConnection(
            @PathVariable UUID gisConnectionId,
            @RequestParam UUID layerId,
            @RequestParam UUID consumerUnitId,
            @RequestParam String domainKey) {
        return service.syncDrawnConnection(layerId, gisConnectionId,
                consumerUnitId, domainKey);
    }
}
