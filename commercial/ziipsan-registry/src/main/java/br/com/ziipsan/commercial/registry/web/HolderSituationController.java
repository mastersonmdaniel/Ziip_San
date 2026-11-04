package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.ChangeRequestView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.DeathAlertInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.DeathAlertView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.DeathReviewInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.HolderStatusView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.CandidateView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.MergeInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.MergeView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.OccupancyInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.RepresentationInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.RepresentationView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.SocialNameInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.SplitInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.SplitView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.TransferValidationView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.ValidationInput;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.ValidationRecordedView;
import br.com.ziipsan.commercial.registry.application.HolderSituationDtos.ValidationView;
import br.com.ziipsan.commercial.registry.application.HolderSituationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Situações do titular e validação externa (P1.41).
 *
 * <p>Toda escrita é {@code registry:write} e passa por OS interna com
 * aprovação por outra pessoa (o serviço confere aprovador ≠ criador); a
 * leitura é {@code registry:read}. Nenhuma permissão nova é inventada: a
 * aprovação por outra pessoa é o controle, não um papel novo. Sem campo de
 * repositório (disciplina do RegistryWriteRoutingArchTest): toda escrita
 * passa pelo serviço.
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/holder-situations")
@Validated
@PreAuthorize(RegistryRoles.READ)
public class HolderSituationController {

    private final HolderSituationService service;

    public HolderSituationController(HolderSituationService service) {
        this.service = service;
    }

    // ---------------------------------------------------------- representações

    @PostMapping("/representations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public RepresentationView requestRepresentation(@Valid @RequestBody RepresentationInput in) {
        return service.requestRepresentation(in);
    }

    @PostMapping("/representations/{id}/approve")
    @PreAuthorize(RegistryRoles.WRITE)
    public RepresentationView approveRepresentation(@PathVariable UUID id) {
        return service.approveRepresentation(id);
    }

    @PostMapping("/representations/{id}/reject")
    @PreAuthorize(RegistryRoles.WRITE)
    public RepresentationView rejectRepresentation(@PathVariable UUID id) {
        return service.rejectRepresentation(id);
    }

    @GetMapping("/representations")
    public List<RepresentationView> listRepresentations(
            @RequestParam(required = false) UUID representedId,
            @RequestParam(required = false) UUID representativeId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listRepresentations(representedId, representativeId, activeOnly);
    }

    // ---------------------------------------------------------- óbito

    @PostMapping("/death-alerts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public DeathAlertView reportDeathAlert(@Valid @RequestBody DeathAlertInput in) {
        return service.reportDeathAlert(in);
    }

    @PostMapping("/death-alerts/{id}/review")
    @PreAuthorize(RegistryRoles.WRITE)
    public DeathAlertView reviewDeathAlert(@PathVariable UUID id, @Valid @RequestBody DeathReviewInput in) {
        return service.reviewDeathAlert(id, in);
    }

    @GetMapping("/death-alerts")
    public List<DeathAlertView> listDeathAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID unitId) {
        return service.listDeathAlerts(status, unitId);
    }

    // ---------------------------------------------------------- validação externa

    @PostMapping("/document-validations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public ValidationRecordedView recordDocumentValidation(@Valid @RequestBody ValidationInput in) {
        return service.recordDocumentValidation(in);
    }

    @GetMapping("/document-validations")
    public List<ValidationView> listValidations(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "false") boolean irregularOnly) {
        return service.listValidations(customerId, irregularOnly);
    }

    @GetMapping("/customers/{id}/transfer-validation")
    public TransferValidationView transferValidation(@PathVariable UUID id) {
        return service.transferValidationStatus(id);
    }

    // ---------------------------------------------------------- fusão e separação

    @PostMapping("/merges")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public MergeView requestMerge(@Valid @RequestBody MergeInput in) {
        return service.requestMerge(in);
    }

    @PostMapping("/merges/{id}/approve")
    @PreAuthorize(RegistryRoles.WRITE)
    public MergeView approveMerge(@PathVariable UUID id) {
        return service.approveMerge(id);
    }

    @PostMapping("/merges/{id}/reject")
    @PreAuthorize(RegistryRoles.WRITE)
    public MergeView rejectMerge(@PathVariable UUID id) {
        return service.rejectMerge(id);
    }

    @PostMapping("/merges/{id}/reverse")
    @PreAuthorize(RegistryRoles.WRITE)
    public MergeView requestMergeReverse(@PathVariable UUID id) {
        return service.requestMergeReverse(id);
    }

    @PostMapping("/merges/{id}/reverse/approve")
    @PreAuthorize(RegistryRoles.WRITE)
    public MergeView approveMergeReverse(@PathVariable UUID id) {
        return service.approveMergeReverse(id);
    }

    @PostMapping("/merges/{id}/reverse/reject")
    @PreAuthorize(RegistryRoles.WRITE)
    public MergeView rejectMergeReverse(@PathVariable UUID id) {
        return service.rejectMergeReverse(id);
    }

    @GetMapping("/merges/{id}")
    public MergeView getMerge(@PathVariable UUID id) {
        return service.getMerge(id);
    }

    @GetMapping("/merge-candidates")
    public List<CandidateView> findMergeCandidates(
            @RequestParam(required = false) String documentHash,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) LocalDate birthDate) {
        return service.findMergeCandidates(documentHash, name, birthDate);
    }

    @PostMapping("/splits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public SplitView requestSplit(@Valid @RequestBody SplitInput in) {
        return service.requestSplit(in);
    }

    @PostMapping("/splits/{id}/approve")
    @PreAuthorize(RegistryRoles.WRITE)
    public SplitView approveSplit(@PathVariable UUID id) {
        return service.approveSplit(id);
    }

    @PostMapping("/splits/{id}/reject")
    @PreAuthorize(RegistryRoles.WRITE)
    public SplitView rejectSplit(@PathVariable UUID id) {
        return service.rejectSplit(id);
    }

    @PostMapping("/splits/{id}/reverse")
    @PreAuthorize(RegistryRoles.WRITE)
    public SplitView requestSplitReverse(@PathVariable UUID id) {
        return service.requestSplitReverse(id);
    }

    @PostMapping("/splits/{id}/reverse/approve")
    @PreAuthorize(RegistryRoles.WRITE)
    public SplitView approveSplitReverse(@PathVariable UUID id) {
        return service.approveSplitReverse(id);
    }

    @PostMapping("/splits/{id}/reverse/reject")
    @PreAuthorize(RegistryRoles.WRITE)
    public SplitView rejectSplitReverse(@PathVariable UUID id) {
        return service.rejectSplitReverse(id);
    }

    @GetMapping("/splits/{id}")
    public SplitView getSplit(@PathVariable UUID id) {
        return service.getSplit(id);
    }

    // ---------------------------------------------------------- ocupação e nome social

    @PostMapping("/units/{unitId}/occupancy")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public ChangeRequestView requestOccupancyChange(@PathVariable UUID unitId,
                                                     @Valid @RequestBody OccupancyInput in) {
        return service.requestOccupancyChange(unitId, in);
    }

    @PostMapping("/customers/{customerId}/social-name")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public ChangeRequestView requestSocialNameChange(@PathVariable UUID customerId,
                                                      @Valid @RequestBody SocialNameInput in) {
        return service.requestSocialNameChange(customerId, in);
    }

    @PostMapping("/changes/{changeId}/approve")
    @PreAuthorize(RegistryRoles.WRITE)
    public ChangeRequestView approveChange(@PathVariable UUID changeId) {
        return service.approveChange(changeId);
    }

    @PostMapping("/changes/{changeId}/reject")
    @PreAuthorize(RegistryRoles.WRITE)
    public ChangeRequestView rejectChange(@PathVariable UUID changeId) {
        return service.rejectChange(changeId);
    }

    @GetMapping("/changes")
    public List<ChangeRequestView> listChanges(@RequestParam String targetType, @RequestParam UUID targetId) {
        return service.listChanges(targetType, targetId);
    }

    // ---------------------------------------------------------- situação da unidade

    @GetMapping("/units/{unitId}/holder-status")
    public HolderStatusView holderStatus(@PathVariable UUID unitId) {
        return service.holderStatus(unitId);
    }
}
