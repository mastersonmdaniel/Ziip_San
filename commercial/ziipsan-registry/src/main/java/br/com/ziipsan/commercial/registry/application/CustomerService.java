package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.CustomerDtos.AddressView;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.Billing;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CoordinatesView;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerAddressInput;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerInput;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerSearch;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerSummary;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.CustomerView;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.EmailInput;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.EmailView;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.PhoneInput;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.PhoneView;
import br.com.ziipsan.commercial.registry.application.CustomerDtos.RevealResult;
import br.com.ziipsan.commercial.registry.domain.Address;
import br.com.ziipsan.commercial.registry.domain.Customer;
import br.com.ziipsan.commercial.registry.domain.CustomerAddress;
import br.com.ziipsan.commercial.registry.domain.Customer.PersonType;
import br.com.ziipsan.commercial.registry.domain.CustomerContact;
import br.com.ziipsan.commercial.registry.repository.AddressRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerAddressRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerContactRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import br.com.ziipsan.commercial.registry.service.CustomerDocumentService;
import br.com.ziipsan.commercial.registry.service.PersonalDataAccessService;
import br.com.ziipsan.core.api.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.application.JpqlQuery.LIKE_ESCAPE;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.blankToNull;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.containsFolded;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.startsWithFolded;

/**
 * Cadastro de clientes. O documento passa SEMPRE pelo {@link CustomerDocumentService} (cifra, índice
 * cego e máscara); esta classe nunca lê nem grava as colunas do documento por conta própria.
 *
 * <p>A busca livre aceita nome ou CPF/CNPJ: documento válido vira índice cego e casa por igualdade
 * (com ou sem pontuação dá o mesmo índice); o resto é busca por nome com dobra de acento em
 * {@code immutable_unaccent(lower(name))}, servida pelo índice GIN trigram da V3_9 (P0.22) —
 * "joao" casa "João" sem varrer o tenant. Antes da P0.22 a busca era {@code lower(name)} com o
 * índice de V3_1 e o "contém" varria o tenant.
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    /** Tempo sugerido para a tela mostrar o documento revelado (o frontend usa o menor entre este e o seu). */
    static final int REVEAL_SECONDS = 15;

    private static final Map<String, List<String>> SORT = Map.of(
            "name", List.of("c.name"), "createdAt", List.of("c.createdAt"));

    /** entity_type da tabela polimorfica addresses para enderecos de cliente (cf. "consumer_units"). */
    static final String CUSTOMER_ENTITY_TYPE = "customers";

    /** Fabrica SIRGAS 2000 (EPSG:4674), o mesmo SRID da coluna addresses.coordinates. */
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4674);

    @PersistenceContext
    private EntityManager em;

    private final CustomerRepository customers;
    private final CustomerContactRepository contacts;
    private final AddressRepository addresses;
    private final CustomerDocumentService documents;
    private final PersonalDataAccessService accessLog;
    /** P1.3: nulos no construtor antigo (testes legados) — nesse caso a validação de catálogo e a OS são puladas. */
    private final CatalogLookup catalogs;
    private final InternalOrderService orders;
    private final CadastralChangeService changes;
    private final CustomerAddressRepository customerAddresses;

    public CustomerService(CustomerRepository customers, CustomerContactRepository contacts,
                           AddressRepository addresses, CustomerDocumentService documents,
                           PersonalDataAccessService accessLog) {
        this(customers, contacts, addresses, documents, accessLog, null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CustomerService(CustomerRepository customers, CustomerContactRepository contacts,
                           AddressRepository addresses, CustomerDocumentService documents,
                           PersonalDataAccessService accessLog, CatalogLookup catalogs,
                           InternalOrderService orders, CadastralChangeService changes,
                           CustomerAddressRepository customerAddresses) {
        this.customers = customers;
        this.contacts = contacts;
        this.addresses = addresses;
        this.documents = documents;
        this.accessLog = accessLog;
        this.catalogs = catalogs;
        this.orders = orders;
        this.changes = changes;
        this.customerAddresses = customerAddresses;
    }

    // ------------------------------------------------------------------ leitura

    /** Lista por GET: sem busca livre. Nome e documento são dado pessoal e não vão na URL (C5). */
    public PageResult<CustomerSummary> list(String q, PersonType personType, Boolean includeDeleted, PageSpec spec) {
        if (blankToNull(q) != null) {
            throw new ApiException.BadRequest("search-in-url",
                    "A busca de clientes por nome ou documento vai no corpo de POST /api/registry/customers/search, "
                            + "nunca na URL.");
        }
        return find(null, null, personType, null, includeDeleted, spec);
    }

    public PageResult<CustomerSummary> search(CustomerSearch s) {
        // P0.16 opcao 1: customerTypeId agora e coluna de customers, entao o filtro passa a
        // funcionar (antes, filter-unavailable). O filtro city continua indisponivel: os
        // enderecos sao gravados, mas a semantica do filtro (qual endereco? correspondencia?
        // qualquer?) ainda nao foi decidida — nao inventada aqui.
        if (blankToNull(s.city()) != null) {
            throw new ApiException.BadRequest("filter-unavailable",
                    "O filtro city ainda não é suportado.");
        }
        return find(s.q(), s.nameMatch(), s.personType(), s.customerTypeId(), s.includeDeleted(),
                PageSpec.of(s.page(), s.size(), s.sort()));
    }

    private PageResult<CustomerSummary> find(String q, String nameMatch, PersonType personType, UUID customerTypeId,
                                             Boolean includeDeleted, PageSpec spec) {
        Filtered filtered = filters(q, nameMatch, personType, customerTypeId, includeDeleted);
        PageResult<Customer> page = filtered.query().page(em, Customer.class, spec, spec.orderBy(SORT, "name", "c.id"));
        // P0.11: o resultado da busca por documento é registrado depois da consulta.
        filtered.documentHash().ifPresent(hash -> accessLog.recordDocumentSearch(hash, !page.content().isEmpty()));
        Map<UUID, Long> counts = unitCounts(page.content().stream().map(Customer::getId).toList());
        return page.map(c -> new CustomerSummary(c.getId(), c.getName(), c.getPersonType(), c.getDocumentMasked(),
                counts.getOrDefault(c.getId(), 0L), c.getDeletedAt() != null));
    }

    /**
     * Paginação keyset (P0.22) sobre os mesmos filtros de {@link #search}: uma única
     * consulta, sem COUNT, ordenada por {@code lower(name) asc, id asc}. Só essa
     * ordenação é suportada — outra ordenação pedida em {@code sort} dá 400, e o
     * cliente usa a paginação por offset. O cursor é opaco (base64url do nome em
     * minúsculas + "." + id); cursor malformado dá 400, nunca 500. O teto de página
     * da P0.16 vale aqui via {@link PageSpec#of}.
     */
    public CustomerDtos.CustomerKeysetPage searchAfter(CustomerSearch s, String cursor) {
        if (blankToNull(s.city()) != null) {
            throw new ApiException.BadRequest("filter-unavailable",
                    "O filtro city ainda não é suportado.");
        }
        PageSpec spec = PageSpec.of(0, s.size(), s.sort());
        if ((spec.sortKey() != null && !spec.sortKey().equals("name")) || !spec.ascending()) {
            throw new ApiException.BadRequest("invalid-sort",
                    "A paginação keyset ordena por nome crescente. Use a busca por offset para outra ordenação.");
        }
        Filtered filtered = filters(s.q(), s.nameMatch(), s.personType(), s.customerTypeId(), s.includeDeleted());
        KeysetPage<Customer> page = filtered.query().pageAfter(em, Customer.class, spec.size(),
                "lower(c.name)", "c.id", decodeCursor(cursor),
                c -> new KeysetCursor(c.getName().toLowerCase(Locale.ROOT), c.getId()));
        // P0.11: o resultado da busca por documento é registrado depois da consulta.
        filtered.documentHash().ifPresent(hash -> accessLog.recordDocumentSearch(hash, !page.content().isEmpty()));
        Map<UUID, Long> counts = unitCounts(page.content().stream().map(Customer::getId).toList());
        List<CustomerSummary> content = page.content().stream()
                .map(c -> new CustomerSummary(c.getId(), c.getName(), c.getPersonType(), c.getDocumentMasked(),
                        counts.getOrDefault(c.getId(), 0L), c.getDeletedAt() != null))
                .toList();
        return new CustomerDtos.CustomerKeysetPage(content,
                page.hasNext() ? encodeCursor(page.nextCursor()) : null, page.hasNext());
    }

    /**
     * Mesmos filtros de {@link #find}, sem paginar: compartilhados pelo offset e pelo keyset.
     * Devolve também o hash do documento quando a busca foi por documento, para o chamador
     * registrar o resultado depois da consulta (regra P0.11) — o refactor da P0.22 separou
     * os filtros da paginação, e o registro não pode ficar dentro daqui (a consulta ainda
     * não executou).
     */
    private record Filtered(JpqlQuery query, Optional<String> documentHash) {
    }

    private Filtered filters(String q, String nameMatch, PersonType personType, UUID customerTypeId,
                             Boolean includeDeleted) {
        JpqlQuery query = new JpqlQuery("c", "Customer c")
                .where("c.tenantId = :t", "t", RegistryContext.tenantId())
                .whereIfPresent("c.personType = :personType", "personType", personType)
                .whereIfPresent("c.customerTypeId = :customerTypeId", "customerTypeId", customerTypeId);
        if (!Boolean.TRUE.equals(includeDeleted)) {
            query.where("c.deletedAt is null");
        }
        String text = blankToNull(q);
        Optional<String> documentHash = Optional.empty();
        if (text != null) {
            Optional<String> hash = documents.searchIndex(text);
            if (hash.isPresent()) {
                // P0.11, ADR-0008: busca por documento exige a permissão própria e conta na
                // cota; sem ela, 403. O resultado é registrado depois da consulta.
                accessLog.authorizeDocumentSearch(hash.get());
                documentHash = hash;
                query.where("c.documentHash = :hash", "hash", hash.get());
            } else {
                String match = nameMatch == null ? "contains" : nameMatch;
                if (!match.equals("contains") && !match.equals("startsWith")) {
                    throw new ApiException.BadRequest("invalid-filter", "nameMatch aceita contains ou startsWith.");
                }
                // P0.22: dobra de acento dos dois lados + índice trigram da V3_9.
                // Chamada direta (não function()): a função está registrada no
                // Hibernate com retorno String (ImmutableUnaccentFunctionContributor).
                // function() a tiparia como Object e o LIKE falharia na validação
                // semântica do Hibernate 7 com 500 em toda busca por nome.
                query.where("immutable_unaccent(lower(c.name)) like :name" + LIKE_ESCAPE, "name",
                        match.equals("startsWith") ? startsWithFolded(text) : containsFolded(text));
            }
        }
        return new Filtered(query, documentHash);
    }

    /** Cursor opaco da API → par (nome minúsculo, id). Nulo = primeira página. */
    static KeysetCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        int dot = cursor.lastIndexOf('.');
        if (dot <= 0 || dot == cursor.length() - 1) {
            throw new ApiException.BadRequest("invalid-cursor", "O cursor de paginação é inválido.");
        }
        try {
            String sortValue = new String(java.util.Base64.getUrlDecoder().decode(cursor.substring(0, dot)),
                    java.nio.charset.StandardCharsets.UTF_8);
            UUID id = UUID.fromString(cursor.substring(dot + 1));
            return new KeysetCursor(sortValue, id);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("invalid-cursor", "O cursor de paginação é inválido.");
        }
    }

    static String encodeCursor(KeysetCursor cursor) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(cursor.sortValue().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + cursor.id();
    }

    public CustomerView get(UUID id) {
        // P1.15: registro excluído é 404 nesta leitura, como se não existisse — as
        // listas já o escondem por padrão (deletedAt is null), mas o GET por id
        // devolvia o cliente excluído com dado pessoal para qualquer perfil com
        // registry:read (VIEWER). O helper customer(id) NÃO muda: update/delete/
        // reveal precisam dele para os próprios erros de "já excluído".
        Customer c = customer(id);
        if (c.getDeletedAt() != null) {
            throw new ApiException.NotFound("Cliente não encontrado.");
        }
        // P0.11: leitura de cadastro com dado pessoal fica registrada (sem o documento em claro).
        CustomerView view = view(c);
        accessLog.recordPersonalRead("customers", id);
        return view;
    }

    // ------------------------------------------------------------------ gravação

    @Transactional
    public CustomerView create(CustomerInput in) {
        Checks checks = new Checks();
        Customer c = RegistryContext.forCreate(new Customer());
        c.setPersonType(in.personType());
        if (blankToNull(in.document()) != null) {
            assignDocument(checks, c, in.document());
        }
        checkContacts(checks, in);
        checkAddresses(checks, in);
        checkR2(checks, in, null);
        checks.throwIfAny();
        apply(c, in);
        customers.saveAndFlush(c);
        replaceContacts(c, in);
        replaceAddresses(c, in);
        recordWrite(c, in, null);
        return view(c);
    }

    @Transactional
    public CustomerView update(UUID id, CustomerInput in) {
        Customer c = customer(id);
        if (c.getDeletedAt() != null) {
            throw new ApiException.Conflict("record-deleted", "Cliente excluído não pode ser alterado.");
        }
        RegistryContext.checkVersion(c, in.version());
        Checks checks = new Checks();
        PersonType before = c.getPersonType();
        c.setPersonType(in.personType());
        if (blankToNull(in.document()) != null) {
            assignDocument(checks, c, in.document());
        } else if (before != in.personType() && c.getDocumentHash() != null) {
            checks.error("document", "DOCUMENT_REQUIRED", "Ao mudar o tipo de pessoa, informe o documento de novo.");
        }
        checkContacts(checks, in);
        checkAddresses(checks, in);
        checkR2(checks, in, c);
        checks.throwIfAny();
        Customer beforeCopy = copyForDiff(c);
        apply(c, in);
        RegistryContext.touch(c);
        // Versão nova a cada PUT, mesmo quando só os contatos mudam: a versão protege o cadastro inteiro.
        c.setUpdatedAt(OffsetDateTime.now());
        customers.saveAndFlush(c);
        replaceContacts(c, in);
        replaceAddresses(c, in);
        recordWrite(c, in, beforeCopy);
        return view(c);
    }

    /** Cópia rasa dos campos comparados no diff da OS (nunca o documento em claro). */
    private static Customer copyForDiff(Customer c) {
        Customer copy = new Customer();
        copy.setId(c.getId());
        copy.setPersonType(c.getPersonType());
        copy.setName(c.getName());
        copy.setTradeName(c.getTradeName());
        copy.setCustomerTypeId(c.getCustomerTypeId());
        copy.setParentCustomerId(c.getParentCustomerId());
        copy.setGovernmentSphere(c.getGovernmentSphere());
        copy.setDocumentMasked(c.getDocumentMasked());
        return copy;
    }

    /**
     * Regras R2 do cliente (P1.3, V3_12), com o campo no erro (400/422 nomeia o campo).
     * Sem CatalogLookup (construtor antigo), só as regras sem catálogo valem.
     */
    private void checkR2(Checks checks, CustomerInput in, Customer current) {
        if (in.personType() == PersonType.PF && blankToNull(in.tradeName()) != null) {
            checks.error("tradeName", "TRADE_NAME_ON_PF", "Pessoa física não tem nome fantasia.");
        }
        if (catalogs == null) {
            return;
        }
        if (in.customerTypeId() != null) {
            CatalogLookup.CatalogRow row = catalogs.find("customer_types", in.customerTypeId());
            if (row == null) {
                checks.error("customerTypeId", "NOT_FOUND", "Tipo de cliente não encontrado.");
            } else if (!row.active()) {
                checks.error("customerTypeId", "INACTIVE", "Tipo de cliente desativado.");
            } else {
                String applies = catalogs.text("customer_types", in.customerTypeId(), "applies_to");
                if ("PF".equals(applies) && in.personType() != PersonType.PF
                        || "PJ".equals(applies) && in.personType() != PersonType.PJ) {
                    checks.error("customerTypeId", "INCOMPATIBLE", "Tipo de cliente incompatível com o tipo de pessoa.");
                }
                Boolean isPublic = catalogs.flag("customer_types", in.customerTypeId(), "is_public_entity");
                if (Boolean.TRUE.equals(isPublic) && blankToNull(in.governmentSphere()) == null) {
                    checks.error("governmentSphere", "REQUIRED", "Ente público: informe a esfera de governo.");
                }
            }
        }
        if (in.documentExemptionReasonId() != null) {
            CatalogLookup.CatalogRow row = catalogs.find("document_exemption_reasons", in.documentExemptionReasonId());
            if (row == null) {
                checks.error("documentExemptionReasonId", "NOT_FOUND", "Motivo de dispensa não encontrado.");
            } else if (!row.active()) {
                checks.error("documentExemptionReasonId", "INACTIVE", "Motivo de dispensa desativado.");
            }
            // Ressalva: requires_permission=true (padrão protetivo, Lei 13.460/2017 art. 10-A)
            // ainda não tem gate de permissão próprio (registry:document-exemption) no controller;
            // a flag existe no catálogo e o gate é follow-up, não inventado aqui.
        }
        if (in.deleteReasonId() != null) {
            CatalogLookup.CatalogRow row = catalogs.find("customer_delete_reasons", in.deleteReasonId());
            if (row == null) {
                checks.error("deleteReasonId", "NOT_FOUND", "Motivo de exclusão não encontrado.");
            } else if (!row.active()) {
                checks.error("deleteReasonId", "INACTIVE", "Motivo de exclusão desativado.");
            }
        }
        if (in.parentCustomerId() != null) {
            if (current != null && in.parentCustomerId().equals(current.getId())) {
                checks.error("parentCustomerId", "CYCLE", "O cliente não pode ser o próprio órgão centralizador.");
            } else {
                Customer parent = customers.findById(in.parentCustomerId()).orElse(null);
                if (parent == null || !RegistryContext.tenantId().equals(parent.getTenantId())
                        || parent.getDeletedAt() != null) {
                    checks.error("parentCustomerId", "NOT_FOUND", "Órgão centralizador não encontrado.");
                } else {
                    // Cadeia de centralizadores, com teto (o trigger do banco é o backstop).
                    UUID walking = parent.getParentCustomerId();
                    for (int depth = 0; depth < 100 && walking != null; depth++) {
                        if (current != null && walking.equals(current.getId())) {
                            checks.error("parentCustomerId", "CYCLE", "Órgão centralizador forma ciclo.");
                            break;
                        }
                        Customer next = customers.findById(walking).orElse(null);
                        walking = next == null ? null : next.getParentCustomerId();
                    }
                }
            }
        }
    }

    /**
     * Toda escrita de cadastro gera OS interna da P1.31 (um OS por tipo distinto entre os
     * campos alterados) e registra cada campo em cadastral_change_requests, com o valor
     * mascarado quando for documento. Sem wiring (construtor antigo), a escrita segue sem OS.
     */
    private void recordWrite(Customer c, CustomerInput in, Customer before) {
        if (orders == null || changes == null) {
            return;
        }
        Map<String, String[]> fields = new LinkedHashMap<>();
        putField(fields, "name", before, before == null ? null : before.getName(), c.getName());
        putField(fields, "tradeName", before, before == null ? null : before.getTradeName(), c.getTradeName());
        putField(fields, "customerTypeId", before, before == null ? null : str(before.getCustomerTypeId()),
                str(c.getCustomerTypeId()));
        putField(fields, "parentCustomerId", before, before == null ? null : str(before.getParentCustomerId()),
                str(c.getParentCustomerId()));
        putField(fields, "governmentSphere", before, before == null ? null : before.getGovernmentSphere(),
                c.getGovernmentSphere());
        if (before == null || !java.util.Objects.equals(before.getDocumentMasked(), c.getDocumentMasked())) {
            fields.put("document", new String[]{before == null ? null : before.getDocumentMasked(), c.getDocumentMasked()});
        }
        if (in.emails() != null || in.phones() != null) {
            fields.put("emails", new String[]{null, null});
            fields.put("phones", new String[]{null, null});
        }
        if (in.addresses() != null) {
            fields.put("addresses", new String[]{null, null});
        }
        if (fields.isEmpty()) {
            return;
        }
        Map<String, List<String>> byType = new LinkedHashMap<>();
        for (String field : fields.keySet()) {
            String type = CadastralChangeService.osTypeFor("CUSTOMER", field);
            if (type != null) {
                byType.computeIfAbsent(type, k -> new ArrayList<>()).add(field);
            }
        }
        for (Map.Entry<String, List<String>> entry : byType.entrySet()) {
            UUID orderId = orders.open(entry.getKey());
            for (String field : entry.getValue()) {
                String[] values = fields.get(field);
                changes.record("CUSTOMER", c.getId(), field, values[0], values[1], orderId);
            }
            orders.executeAndClose(orderId);
        }
    }

    private static void putField(Map<String, String[]> fields, String field, Customer before, String oldValue,
                                 String newValue) {
        if (before == null || !java.util.Objects.equals(oldValue, newValue)) {
            fields.put(field, new String[]{oldValue, newValue});
        }
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }

    /** Exclusão lógica. Cliente com vínculo vigente com imóvel não sai: a conta ficaria sem titular. */
    @Transactional
    public void delete(UUID id, String reason) {
        Customer c = customer(id);
        if (c.getDeletedAt() != null) {
            throw new ApiException.Conflict("record-already-deleted", "O cliente já está excluído.");
        }
        if (unitCounts(List.of(id)).getOrDefault(id, 0L) > 0) {
            throw new ApiException.Conflict("customer-has-active-link",
                    "O cliente tem vínculo vigente com imóvel. Encerre os vínculos antes de excluir.");
        }
        c.setDeletedAt(OffsetDateTime.now());
        c.setDeletedReason(reason.trim());
        RegistryContext.touch(c);
        customers.saveAndFlush(c);
    }

    /** Documento em claro. O serviço de documento registra o acesso (quem, quando, finalidade) sem o valor. */
    @Transactional
    public RevealResult reveal(UUID id, String purpose) {
        Customer c = customer(id);
        if (c.getDocumentCiphertext() == null) {
            throw new ApiException.Conflict("customer-without-document", "O cliente não tem documento cadastrado.");
        }
        RevealResult result = new RevealResult(documents.reveal(c, purpose.trim()), REVEAL_SECONDS);
        // P0.11: a revelação também entra no registro unificado de acesso a dado pessoal.
        accessLog.recordDocumentReveal("customers", id, c.getDocumentHash(), purpose.trim());
        return result;
    }

    // ------------------------------------------------------------------ apoio

    Customer customer(UUID id) {
        return RegistryContext.require(customers.findById(id), () -> "Cliente não encontrado.");
    }

    private void assignDocument(Checks checks, Customer c, String raw) {
        try {
            documents.assign(c, raw);
        } catch (CustomerDocumentService.DuplicateDocumentException e) {
            checks.error("document", "DOCUMENT_IN_USE", "Já existe cliente ativo com este documento.");
        } catch (IllegalArgumentException e) {
            // A mensagem do serviço não vai ao usuário: pode citar o tipo detectado do documento.
            checks.error("document", "DOCUMENT_INVALID", "CPF/CNPJ inválido ou incompatível com o tipo de pessoa.");
        }
    }

    private static void apply(Customer c, CustomerInput in) {
        c.setName(in.name().trim());
        c.setTradeName(blankToNull(in.tradeName()));
        c.setCustomerTypeId(in.customerTypeId());
        c.setActivityBranchId(in.activityBranchId());
        c.setParentCustomerId(in.parentCustomerId());
        c.setDocumentExemptionReason(blankToNull(in.documentExemptionReason()));
        c.setDocumentExemptionReasonId(in.documentExemptionReasonId());
        c.setGovernmentSphere(blankToNull(in.governmentSphere()));
        c.setDeleteReasonId(in.deleteReasonId());
        c.setBirthDate(in.birthDate());
        Integer dueDay = in.billing() == null ? null : in.billing().dueDay();
        c.setDueDay(dueDay == null ? null : dueDay.shortValue());
        // Flags ausentes leem false: payloads antigos sem billing/flags nao mudam de sentido.
        c.setNextMonthDue(in.billing() != null && Boolean.TRUE.equals(in.billing().nextMonthDue()));
        c.setAdvanceInvoice(in.billing() != null && Boolean.TRUE.equals(in.billing().advanceInvoice()));
        c.setBlockNegativation(in.billing() != null && Boolean.TRUE.equals(in.billing().blockNegativation()));
    }

    private static void checkContacts(Checks checks, CustomerInput in) {
        if (in.phones() != null && in.phones().stream().filter(p -> Boolean.TRUE.equals(p.primary())).count() > 1) {
            checks.error("phones", "PRIMARY_DUPLICATE", "Só um telefone pode ser o principal.");
        }
        if (in.emails() != null) {
            Set<String> seen = new HashSet<>();
            for (EmailInput e : in.emails()) {
                if (!seen.add(e.address().trim().toLowerCase(Locale.ROOT))) {
                    checks.error("emails", "DUPLICATE", "O mesmo e-mail foi informado duas vezes.");
                }
            }
        }
    }

    private record DesiredContact(CustomerContact.Kind kind, String value, String label, boolean primary, UUID consentId,
                                  String extension, String contactName) {
        String key() {
            return kind + "|" + value;
        }
    }

    /** Coordenada pela metade nao vale: o formulario exige as duas juntas ou nenhuma. */
    private static void checkAddresses(Checks checks, CustomerInput in) {
        if (in.addresses() == null) {
            return;
        }
        for (int i = 0; i < in.addresses().size(); i++) {
            CustomerAddressInput a = in.addresses().get(i);
            if (a.address() != null && a.address().coordinates() != null
                    && ((a.address().coordinates().lat() == null) != (a.address().coordinates().lng() == null))) {
                checks.error("addresses[" + i + "].address.coordinates", "INCOMPLETE",
                        "Informe latitude e longitude juntas, ou deixe as duas em branco.");
            }
        }
    }

    /**
     * Grava o conjunto de contatos pedido. Lista nula mantém os contatos daquele tipo. Em duas fases por
     * causa do índice único de contato principal: primeiro desmarca e remove, depois marca e insere.
     */
    private void replaceContacts(Customer c, CustomerInput in) {
        Map<String, DesiredContact> desired = new LinkedHashMap<>();
        Set<CustomerContact.Kind> touched = new HashSet<>();
        if (in.emails() != null) {
            touched.add(CustomerContact.Kind.EMAIL);
            boolean first = true;
            for (EmailInput e : in.emails()) {
                DesiredContact d = new DesiredContact(CustomerContact.Kind.EMAIL, e.address().trim().toLowerCase(Locale.ROOT),
                        null, first, e.consentId(), null, null);
                desired.put(d.key(), d);
                first = false;
            }
        }
        if (in.phones() != null) {
            touched.add(CustomerContact.Kind.PHONE);
            for (PhoneInput p : in.phones()) {
                DesiredContact d = new DesiredContact(CustomerContact.Kind.PHONE, p.number().trim(),
                        blankToNull(p.type()), Boolean.TRUE.equals(p.primary()), null,
                        blankToNull(p.extension()), blankToNull(p.contactName()));
                desired.put(d.key(), d);
            }
        }
        if (touched.isEmpty()) {
            return;
        }
        UUID tenantId = RegistryContext.tenantId();
        Map<String, CustomerContact> existing = new HashMap<>();
        for (CustomerContact cc : contacts.findByTenantIdAndCustomerId(tenantId, c.getId())) {
            if (!touched.contains(cc.getKind())) {
                continue;
            }
            String key = cc.getKind() + "|" + cc.getValue();
            DesiredContact d = desired.get(key);
            if (d == null) {
                contacts.delete(cc);
            } else {
                existing.put(key, cc);
                if (cc.isPrimaryContact() && !d.primary()) {
                    cc.setPrimaryContact(false);
                    RegistryContext.touch(cc);
                }
            }
        }
        contacts.flush();
        List<CustomerContact> toSave = new ArrayList<>();
        for (DesiredContact d : desired.values()) {
            CustomerContact cc = existing.get(d.key());
            if (cc == null) {
                cc = RegistryContext.forCreate(new CustomerContact());
                cc.setCustomerId(c.getId());
                cc.setKind(d.kind());
                cc.setValue(d.value());
            } else {
                RegistryContext.touch(cc);
            }
            cc.setLabel(d.label());
            cc.setPrimaryContact(d.primary());
            cc.setConsentId(d.consentId());
            cc.setExtension(d.extension());
            cc.setContactName(d.contactName());
            // P1.3: o tipo do telefone vai também para phone_type (label continua sendo o tipo na API).
            if (d.kind() == CustomerContact.Kind.PHONE) {
                cc.setPhoneType(d.label());
            }
            toSave.add(cc);
        }
        contacts.saveAllAndFlush(toSave);
    }

    /**
     * Grava o conjunto de enderecos pedido, na ordem da lista. Lista nula mantem os atuais
     * (mesma semantica dos contatos); lista vazia remove todos. Endereco e composicao do
     * cliente: sai da lista, sai do banco.
     *
     * <p>P1-C (integracao): a ordem importa desde a R2. A tabela propria
     * {@code customer_addresses} referencia {@code addresses} pela FK
     * {@code fk_custaddr_address} — os vinculos tem de sair ANTES das linhas de
     * endereco, senao o DELETE viola a FK (23503) e o PUT volta 409 data-conflict.
     * (O javadoc antigo dizia que nenhum outro agregado referenciava enderecos de
     * cliente; a R2 criou esse vinculo.)
     */
    private void replaceAddresses(Customer c, CustomerInput in) {
        if (in.addresses() == null) {
            return;
        }
        UUID tenantId = RegistryContext.tenantId();
        deleteCustomerAddressLinks(c, tenantId);
        List<Address> existing =
                addresses.findByTenantIdAndEntityTypeAndEntityIdOrderByPositionAsc(tenantId, CUSTOMER_ENTITY_TYPE,
                        c.getId());
        addresses.deleteAll(existing);
        addresses.flush();
        List<Address> toSave = new ArrayList<>();
        for (int i = 0; i < in.addresses().size(); i++) {
            CustomerAddressInput a = in.addresses().get(i);
            Address row = RegistryContext.forCreate(new Address());
            row.setEntityType(CUSTOMER_ENTITY_TYPE);
            row.setEntityId(c.getId());
            boolean correspondence = Boolean.TRUE.equals(a.correspondence());
            row.setType(correspondence ? Address.Type.CORRESPONDENCE : Address.Type.ALTERNATIVE);
            row.setPrimary(correspondence);
            row.setPosition(i);
            var addr = a.address();
            row.setCep(addr.cep() == null ? null : addr.cep().replaceAll("\\D", ""));
            row.setStreet(addr.streetName().trim());
            row.setNumber(blankToNull(addr.number()));
            row.setComplement(blankToNull(addr.complement()));
            row.setNeighborhood(blankToNull(addr.neighborhood()));
            row.setCity(blankToNull(addr.city()));
            row.setState(addr.state() == null ? null : addr.state().toUpperCase(Locale.ROOT));
            row.setCountry("BR");
            // P1.3: município IBGE, referência, precisão, catálogos de logradouro/bairro.
            row.setMunicipalityIbge(blankToNull(addr.municipalityIbge()));
            row.setReferencePoint(blankToNull(addr.referencePoint()));
            row.setGeocodePrecision(blankToNull(addr.geocodePrecision()));
            row.setStreetId(addr.streetId());
            row.setNeighborhoodId(addr.neighborhoodId());
            if (addr.coordinates() != null && addr.coordinates().lat() != null && addr.coordinates().lng() != null) {
                // JTS: X = longitude, Y = latitude.
                row.setCoordinates(GEOMETRY_FACTORY.createPoint(
                        new Coordinate(addr.coordinates().lng(), addr.coordinates().lat())));
            }
            toSave.add(row);
        }
        addresses.saveAllAndFlush(toSave);
        insertCustomerAddressLinks(c, toSave);
    }

    /**
     * P1.3: vinculos novos na tabela propria customer_addresses (a linha polimorfica
     * continua existindo para as linhas legadas). Sem wiring, nada e feito aqui.
     * A exclusao e separada da insercao porque roda ANTES do DELETE das linhas de
     * endereco (ver replaceAddresses): a FK fk_custaddr_address proibe apagar o
     * endereco com o vinculo ainda apontando para ele.
     */
    private void deleteCustomerAddressLinks(Customer c, UUID tenantId) {
        if (customerAddresses == null) {
            return;
        }
        customerAddresses.deleteAll(customerAddresses.findByTenantIdAndCustomerIdOrderByValidFromAsc(tenantId, c.getId()));
        customerAddresses.flush();
    }

    private void insertCustomerAddressLinks(Customer c, List<Address> saved) {
        if (customerAddresses == null) {
            return;
        }
        List<CustomerAddress> toSave = new ArrayList<>();
        for (Address row : saved) {
            CustomerAddress link = RegistryContext.forCreate(new CustomerAddress());
            link.setCustomerId(c.getId());
            link.setAddressId(row.getId());
            link.setValidFrom(java.time.LocalDate.now());
            link.setCorrespondence(row.isPrimary());
            toSave.add(link);
        }
        customerAddresses.saveAllAndFlush(toSave);
    }

    /** Imóveis distintos com vínculo vigente, por cliente. */
    Map<UUID, Long> unitCounts(Collection<UUID> customerIds) {
        Map<UUID, Long> out = new HashMap<>();
        if (customerIds.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createQuery("select uc.customerId, count(distinct uc.unitId) from UnitCustomer uc "
                        + "where uc.tenantId = :t and uc.customerId in :ids and uc.endDate is null group by uc.customerId",
                Object[].class)
                .setParameter("t", RegistryContext.tenantId())
                .setParameter("ids", customerIds)
                .getResultList();
        for (Object[] row : rows) {
            out.put((UUID) row[0], (Long) row[1]);
        }
        return out;
    }

    private CustomerView view(Customer c) {
        List<EmailView> emails = new ArrayList<>();
        List<PhoneView> phones = new ArrayList<>();
        for (CustomerContact cc : contacts.findByTenantIdAndCustomerId(RegistryContext.tenantId(), c.getId())) {
            if (cc.getKind() == CustomerContact.Kind.EMAIL) {
                emails.add(new EmailView(cc.getValue(), cc.isPrimaryContact(), cc.getConsentId()));
            } else {
                phones.add(new PhoneView(cc.getValue(), cc.getLabel(), cc.isPrimaryContact(),
                        cc.getExtension(), cc.getContactName()));
            }
        }
        List<AddressView> addressViews = new ArrayList<>();
        for (Address a : addresses.findByTenantIdAndEntityTypeAndEntityIdOrderByPositionAsc(
                RegistryContext.tenantId(), CUSTOMER_ENTITY_TYPE, c.getId())) {
            CoordinatesView coordinates = a.getCoordinates() == null ? null
                    : new CoordinatesView(a.getCoordinates().getY(), a.getCoordinates().getX());
            addressViews.add(new AddressView(
                    new CustomerDtos.AddressDetailView(a.getCep(), a.getStreet(), a.getNumber(), a.getComplement(),
                            a.getNeighborhood(), a.getCity(), a.getState(), coordinates,
                            a.getMunicipalityIbge(), a.getReferencePoint(), a.getGeocodePrecision()),
                    a.isPrimary()));
        }
        long units = unitCounts(List.of(c.getId())).getOrDefault(c.getId(), 0L);
        return new CustomerView(c.getId(), c.getVersion(), c.getPersonType(), c.getName(), c.getTradeName(),
                catalogRef("customer_types", c.getCustomerTypeId()), c.getDocumentMasked(),
                c.getDocumentExemptionReason(),
                c.getBirthDate(), refOrNull(c.getActivityBranchId()), parentRef(c),
                new Billing(c.getDueDay() == null ? null : (int) c.getDueDay(), c.isNextMonthDue(),
                        c.isAdvanceInvoice(), c.isBlockNegativation()),
                emails, phones, addressViews, units, c.getDeletedAt(), c.getDeletedReason(),
                c.getGovernmentSphere(), catalogRef("document_exemption_reasons", c.getDocumentExemptionReasonId()),
                catalogRef("customer_delete_reasons", c.getDeleteReasonId()));
    }

    /** Ref resolvida no catálogo R2 quando o wiring existe; senão o comportamento P0.16 (id, sem nome). */
    private Ref catalogRef(String table, UUID id) {
        if (id == null) {
            return null;
        }
        if (catalogs == null) {
            return refOrNull(id);
        }
        Ref ref = catalogs.ref(table, id);
        return ref.name() == null ? refOrNull(id) : ref;
    }

    /**
     * Referencia de catalogo: o id e devolvido, mas o nome ainda nao pode ser resolvido — a
     * tabela de catalogo nao existe no backend. O frontend usa o id como rotulo ate a frente
     * de catalogos resolver o nome (refToLookupValue cai para o id quando o nome e null).
     */
    private static Ref refOrNull(UUID id) {
        return id == null ? null : new Ref(id, null, null);
    }

    /** Cliente superior: o nome vem da propria tabela customers, entao e resolvido de verdade. */
    private Ref parentRef(Customer c) {
        if (c.getParentCustomerId() == null) {
            return null;
        }
        return customers.findById(c.getParentCustomerId())
                .filter(p -> RegistryContext.tenantId().equals(p.getTenantId()))
                .map(p -> new Ref(p.getId(), null, p.getName()))
                .orElseGet(() -> new Ref(c.getParentCustomerId(), null, null));
    }
}
