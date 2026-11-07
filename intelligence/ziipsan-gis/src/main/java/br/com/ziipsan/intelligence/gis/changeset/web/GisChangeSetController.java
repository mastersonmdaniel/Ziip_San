package br.com.ziipsan.intelligence.gis.changeset.web;

import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ApproveInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ChangeSetView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.CreateInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ItemInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ItemView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.LockInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.LockView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.PageView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.PendingReviewView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ReviewDetailView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ReviewInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Pacotes de edição do GIS (P2.22, §6): criação e itens pelo autor,
 * submissão, tela de revisão (antes/depois), aprovação que aplica numa
 * só transação, devolução, rejeição, rebase e travas por área. Autor
 * aprovando o próprio pacote recebe 403 (a guarda do banco recusa
 * também por SQL direto).
 */
@RestController
@RequestMapping(GisChangeSetRoles.BASE)
@PreAuthorize(GisChangeSetRoles.READ)
public class GisChangeSetController {

    private final GisChangeSetService service;

    public GisChangeSetController(GisChangeSetService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisChangeSetRoles.EDIT)
    public ChangeSetView create(@Valid @RequestBody CreateInput in) {
        return service.create(in);
    }

    @GetMapping
    public PageView<ChangeSetView> list(@RequestParam(required = false) String status,
                                        @RequestParam(defaultValue = "false") boolean onlyMine,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.list(status, onlyMine, page, size);
    }

    /** Fila de revisão: a consulta que a P2.28 (minhas tarefas) consome. */
    @GetMapping("/pending-review")
    @PreAuthorize(GisChangeSetRoles.REVIEW)
    public List<PendingReviewView> pendingReview() {
        return service.pendingReview();
    }

    @GetMapping("/{id}")
    public ChangeSetView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisChangeSetRoles.EDIT)
    public ItemView addItem(@PathVariable UUID id, @Valid @RequestBody ItemInput in) {
        return service.addItem(id, in);
    }

    @GetMapping("/{id}/items")
    public List<ItemView> listItems(@PathVariable UUID id) {
        return service.listItems(id);
    }

    @PostMapping("/{id}/items/{itemId}/rebase")
    @PreAuthorize(GisChangeSetRoles.EDIT)
    public ItemView rebaseItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        return service.rebaseItem(id, itemId);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(GisChangeSetRoles.EDIT)
    public ChangeSetView submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @GetMapping("/{id}/review")
    @PreAuthorize(GisChangeSetRoles.REVIEW)
    public ReviewDetailView reviewDetail(@PathVariable UUID id) {
        return service.reviewDetail(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(GisChangeSetRoles.REVIEW)
    public ChangeSetView approve(@PathVariable UUID id,
                                 @RequestBody(required = false) ApproveInput in) {
        return service.approve(id, in);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(GisChangeSetRoles.REVIEW)
    public ChangeSetView returnToAuthor(@PathVariable UUID id, @Valid @RequestBody ReviewInput in) {
        return service.returnToAuthor(id, in);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(GisChangeSetRoles.REVIEW)
    public ChangeSetView reject(@PathVariable UUID id, @Valid @RequestBody ReviewInput in) {
        return service.reject(id, in);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(GisChangeSetRoles.EDIT)
    public ChangeSetView cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/locks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisChangeSetRoles.EDIT)
    public LockView createLock(@PathVariable UUID id, @Valid @RequestBody LockInput in) {
        return service.createLock(id, in);
    }

    @GetMapping("/{id}/locks")
    public List<LockView> listLocks(@PathVariable UUID id) {
        return service.listLocks(id);
    }

    @DeleteMapping("/{id}/locks/{lockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(GisChangeSetRoles.EDIT)
    public void releaseLock(@PathVariable UUID id, @PathVariable UUID lockId) {
        service.releaseLock(id, lockId);
    }
}
