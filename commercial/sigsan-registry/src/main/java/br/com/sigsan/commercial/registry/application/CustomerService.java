package br.com.sigsan.commercial.registry.application;

import br.com.sigsan.commercial.registry.application.CustomerDtos.Billing;
import br.com.sigsan.commercial.registry.application.CustomerDtos.CustomerInput;
import br.com.sigsan.commercial.registry.application.CustomerDtos.CustomerSearch;
import br.com.sigsan.commercial.registry.application.CustomerDtos.CustomerSummary;
import br.com.sigsan.commercial.registry.application.CustomerDtos.CustomerView;
import br.com.sigsan.commercial.registry.application.CustomerDtos.EmailInput;
import br.com.sigsan.commercial.registry.application.CustomerDtos.EmailView;
import br.com.sigsan.commercial.registry.application.CustomerDtos.PhoneInput;
import br.com.sigsan.commercial.registry.application.CustomerDtos.PhoneView;
import br.com.sigsan.commercial.registry.application.CustomerDtos.RevealResult;
import br.com.sigsan.commercial.registry.domain.Customer;
import br.com.sigsan.commercial.registry.domain.Customer.PersonType;
import br.com.sigsan.commercial.registry.domain.CustomerContact;
import br.com.sigsan.commercial.registry.repository.CustomerContactRepository;
import br.com.sigsan.commercial.registry.repository.CustomerRepository;
import br.com.sigsan.commercial.registry.service.CustomerDocumentService;
import br.com.sigsan.core.api.ApiException;
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

import static br.com.sigsan.commercial.registry.application.JpqlQuery.LIKE_ESCAPE;
import static br.com.sigsan.commercial.registry.application.JpqlQuery.blankToNull;
import static br.com.sigsan.commercial.registry.application.JpqlQuery.contains;
import static br.com.sigsan.commercial.registry.application.JpqlQuery.startsWith;

/**
 * Cadastro de clientes. O documento passa SEMPRE pelo {@link CustomerDocumentService} (cifra, índice
 * cego e máscara); esta classe nunca lê nem grava as colunas do documento por conta própria.
 *
 * <p>A busca livre aceita nome ou CPF/CNPJ: documento válido vira índice cego e casa por igualdade
 * (com ou sem pontuação dá o mesmo índice); o resto é busca por nome em {@code lower(name)}, que é o
 * índice de V3_1 (não há índice trigram: "contém" faz varredura no tenant, aceitável no volume do R0).
 */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    /** Tempo sugerido para a tela mostrar o documento revelado (o frontend usa o menor entre este e o seu). */
    static final int REVEAL_SECONDS = 15;

    private static final Map<String, List<String>> SORT = Map.of(
            "name", List.of("c.name"), "createdAt", List.of("c.createdAt"));

    @PersistenceContext
    private EntityManager em;

    private final CustomerRepository customers;
    private final CustomerContactRepository contacts;
    private final CustomerDocumentService documents;

    public CustomerService(CustomerRepository customers, CustomerContactRepository contacts,
                           CustomerDocumentService documents) {
        this.customers = customers;
        this.contacts = contacts;
        this.documents = documents;
    }

    // ------------------------------------------------------------------ leitura

    /** Lista por GET: sem busca livre. Nome e documento são dado pessoal e não vão na URL (C5). */
    public PageResult<CustomerSummary> list(String q, PersonType personType, Boolean includeDeleted, PageSpec spec) {
        if (blankToNull(q) != null) {
            throw new ApiException.BadRequest("busca-na-url",
                    "A busca de clientes por nome ou documento vai no corpo de POST /api/registry/customers/search, "
                            + "nunca na URL.");
        }
        return find(null, null, personType, includeDeleted, spec);
    }

    public PageResult<CustomerSummary> search(CustomerSearch s) {
        if (s.customerTypeId() != null) {
            throw new ApiException.BadRequest("filtro-indisponivel",
                    "O cadastro ainda não tem tipo de cliente: o filtro customerTypeId não é suportado.");
        }
        if (blankToNull(s.city()) != null) {
            throw new ApiException.BadRequest("filtro-indisponivel",
                    "O endereço do cliente ainda não é gravado pela API: o filtro city não é suportado.");
        }
        return find(s.q(), s.nameMatch(), s.personType(), s.includeDeleted(), PageSpec.of(s.page(), s.size(), s.sort()));
    }

    private PageResult<CustomerSummary> find(String q, String nameMatch, PersonType personType, Boolean includeDeleted,
                                             PageSpec spec) {
        JpqlQuery query = new JpqlQuery("c", "Customer c")
                .where("c.tenantId = :t", "t", RegistryContext.tenantId())
                .whereIfPresent("c.personType = :personType", "personType", personType);
        if (!Boolean.TRUE.equals(includeDeleted)) {
            query.where("c.deletedAt is null");
        }
        String text = blankToNull(q);
        if (text != null) {
            Optional<String> hash = documents.searchIndex(text);
            if (hash.isPresent()) {
                query.where("c.documentHash = :hash", "hash", hash.get());
            } else {
                String match = nameMatch == null ? "contains" : nameMatch;
                if (!match.equals("contains") && !match.equals("startsWith")) {
                    throw new ApiException.BadRequest("filtro-invalido", "nameMatch aceita contains ou startsWith.");
                }
                query.where("lower(c.name) like :name" + LIKE_ESCAPE, "name",
                        match.equals("startsWith") ? startsWith(text) : contains(text));
            }
        }
        PageResult<Customer> page = query.page(em, Customer.class, spec, spec.orderBy(SORT, "name", "c.id"));
        Map<UUID, Long> counts = unitCounts(page.content().stream().map(Customer::getId).toList());
        return page.map(c -> new CustomerSummary(c.getId(), c.getName(), c.getPersonType(), c.getDocumentMasked(),
                counts.getOrDefault(c.getId(), 0L), c.getDeletedAt() != null));
    }

    public CustomerView get(UUID id) {
        return view(customer(id));
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
        checks.throwIfAny();
        apply(c, in);
        customers.saveAndFlush(c);
        replaceContacts(c, in);
        return view(c);
    }

    @Transactional
    public CustomerView update(UUID id, CustomerInput in) {
        Customer c = customer(id);
        if (c.getDeletedAt() != null) {
            throw new ApiException.Conflict("registro-excluido", "Cliente excluído não pode ser alterado.");
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
        checks.throwIfAny();
        apply(c, in);
        RegistryContext.touch(c);
        // Versão nova a cada PUT, mesmo quando só os contatos mudam: a versão protege o cadastro inteiro.
        c.setUpdatedAt(OffsetDateTime.now());
        customers.saveAndFlush(c);
        replaceContacts(c, in);
        return view(c);
    }

    /** Exclusão lógica. Cliente com vínculo vigente com imóvel não sai: a conta ficaria sem titular. */
    @Transactional
    public void delete(UUID id, String reason) {
        Customer c = customer(id);
        if (c.getDeletedAt() != null) {
            throw new ApiException.Conflict("registro-ja-excluido", "O cliente já está excluído.");
        }
        if (unitCounts(List.of(id)).getOrDefault(id, 0L) > 0) {
            throw new ApiException.Conflict("cliente-com-vinculo-vigente",
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
            throw new ApiException.Conflict("cliente-sem-documento", "O cliente não tem documento cadastrado.");
        }
        return new RevealResult(documents.reveal(c, purpose.trim()), REVEAL_SECONDS);
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
        c.setBirthDate(in.birthDate());
        Integer dueDay = in.billing() == null ? null : in.billing().dueDay();
        c.setDueDay(dueDay == null ? null : dueDay.shortValue());
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

    private record DesiredContact(CustomerContact.Kind kind, String value, String label, boolean primary, UUID consentId) {
        String key() {
            return kind + "|" + value;
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
                        null, first, e.consentId());
                desired.put(d.key(), d);
                first = false;
            }
        }
        if (in.phones() != null) {
            touched.add(CustomerContact.Kind.PHONE);
            for (PhoneInput p : in.phones()) {
                DesiredContact d = new DesiredContact(CustomerContact.Kind.PHONE, p.number().trim(),
                        blankToNull(p.type()), Boolean.TRUE.equals(p.primary()), null);
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
            toSave.add(cc);
        }
        contacts.saveAllAndFlush(toSave);
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
                phones.add(new PhoneView(cc.getValue(), cc.getLabel(), cc.isPrimaryContact()));
            }
        }
        long units = unitCounts(List.of(c.getId())).getOrDefault(c.getId(), 0L);
        return new CustomerView(c.getId(), c.getVersion(), c.getPersonType(), c.getName(), c.getTradeName(),
                c.getDocumentMasked(), c.getBirthDate(), new Billing(c.getDueDay() == null ? null : (int) c.getDueDay()),
                emails, phones, units, c.getDeletedAt(), c.getDeletedReason());
    }
}
