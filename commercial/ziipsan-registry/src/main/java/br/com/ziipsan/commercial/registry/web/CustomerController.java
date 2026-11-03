package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerInput;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerKeysetPage;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerSearch;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerSummary;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerView;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.RevealRequest;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.RevealResult;
import br.com.ziipsan.commercial.registry.application.CustomerService;
import br.com.ziipsan.commercial.registry.application.DeleteRequest;
import br.com.ziipsan.commercial.registry.application.PageResult;
import br.com.ziipsan.commercial.registry.application.PageSpec;
import br.com.ziipsan.commercial.registry.domain.Customer.PersonType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Clientes. A busca livre (nome ou CPF/CNPJ) é {@code POST /customers/search}, com o texto no corpo: na
 * URL ele ficaria em log de servidor e de proxy. O GET de lista recusa {@code q} com 400.
 *
 * <p>P0.16: {@code @Validated} na classe para o {@code @Size} do parâmetro {@code q} do GET ser
 * avaliado (sem ele, a anotação em parâmetro é ignorada). Precedência: {@code q} com mais de 200
 * caracteres dá 422 de validação antes da recusa 400 de busca-na-URL.
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/customers")
@Validated
@PreAuthorize(RegistryRoles.READ)
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<CustomerSummary> list(@RequestParam(required = false) @Size(max = 200, message = "A busca aceita no máximo 200 caracteres.") String q,
                                            @RequestParam(required = false) PersonType personType,
                                            @RequestParam(required = false) Boolean includeDeleted,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String sort) {
        return service.list(q, personType, includeDeleted, PageSpec.of(page, size, sort));
    }

    @PostMapping("/search")
    public PageResult<CustomerSummary> search(@Valid @RequestBody CustomerSearch search) {
        return service.search(search);
    }

    /**
     * Paginação keyset (P0.22) para as listas grandes: uma consulta, sem COUNT, com
     * cursor opaco. Sem cursor, começa do início. A ordenação é fixa (nome crescente);
     * outra ordenação dá 400 e o cliente usa {@code /search} por offset. A spec do
     * OpenAPI é regenerada na integração (este arquivo não é tocado nesta frente).
     */
    @PostMapping("/search/keyset")
    public CustomerKeysetPage searchKeyset(@Valid @RequestBody CustomerSearch search,
                                           @RequestParam(required = false) String cursor) {
        return service.searchAfter(search, cursor);
    }

    @GetMapping("/{id}")
    public CustomerView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public CustomerView create(@Valid @RequestBody CustomerInput in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public CustomerView update(@PathVariable UUID id, @Valid @RequestBody CustomerInput in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RegistryRoles.WRITE)
    public void delete(@PathVariable UUID id, @Valid @RequestBody DeleteRequest body) {
        service.delete(id, body.reason());
    }

    /** Documento em claro, com a finalidade no corpo. Só com o papel próprio; o acesso fica na auditoria. */
    @PostMapping("/{id}/reveal-document")
    @PreAuthorize(RegistryRoles.REVEAL_DOCUMENT)
    public RevealResult revealDocument(@PathVariable UUID id, @Valid @RequestBody RevealRequest body) {
        return service.reveal(id, body.purpose());
    }
}
