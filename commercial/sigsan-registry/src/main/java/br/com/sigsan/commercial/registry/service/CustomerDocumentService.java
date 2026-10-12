package br.com.sigsan.commercial.registry.service;

import br.com.sigsan.commercial.registry.domain.Customer;
import br.com.sigsan.commercial.registry.domain.Customer.PersonType;
import br.com.sigsan.commercial.registry.repository.CustomerRepository;
import br.com.sigsan.core.audit.AuditLog;
import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.core.document.DocumentNumber;
import br.com.sigsan.core.json.Json;
import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.security.crypto.FieldProtector;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * CPF/CNPJ do cliente: o único caminho para gravar, buscar e revelar o documento.
 *
 * <p>O documento nunca é persistido em claro: vai cifrado (AES-256-GCM, chave do tenant), com
 * índice cego (HMAC) para busca e unicidade e uma máscara para exibição. Antes desta classe as
 * colunas existiam, mas nada chamava a cifra: a decisão de "documento só cifrado" estava registrada
 * como tomada, não como implementada (PDCA de 12/10/2026).
 *
 * <p>O tenant vem do contexto da requisição ({@link TenantContextHolder}); sem ele, erro.
 */
public class CustomerDocumentService {

    /** Contexto da cifra e do índice: um valor protegido aqui não decifra nem casa em outro campo. */
    public static final String FIELD = "customers.document";

    private final FieldProtector protector;
    private final CustomerRepository customers;
    private final AuditLogRepository auditLog;

    public CustomerDocumentService(FieldProtector protector, CustomerRepository customers, AuditLogRepository auditLog) {
        this.protector = protector;
        this.customers = customers;
        this.auditLog = auditLog;
    }

    /** Documento já usado por outro cliente ativo do mesmo tenant. */
    public static class DuplicateDocumentException extends IllegalStateException {
        public DuplicateDocumentException() {
            super("já existe cliente ativo com este documento");
        }
    }

    /**
     * Valida, normaliza e grava o documento no cliente (cifra + índice + máscara). Não salva o cliente.
     *
     * @throws IllegalArgumentException documento inválido ou incompatível com o tipo de pessoa (CPF para
     *                                  PF, CNPJ para PJ)
     * @throws DuplicateDocumentException outro cliente ativo do tenant já tem o documento
     */
    public void assign(Customer customer, String rawDocument) {
        UUID tenantId = requireTenant();
        if (customer.getTenantId() != null && !customer.getTenantId().equals(tenantId)) {
            throw new IllegalStateException("cliente de outro tenant");
        }
        DocumentNumber.Kind kind = DocumentNumber.kind(rawDocument);
        if (kind == DocumentNumber.Kind.INVALID) {
            throw new IllegalArgumentException("documento inválido");
        }
        PersonType expected = kind == DocumentNumber.Kind.CPF ? PersonType.PF : PersonType.PJ;
        if (customer.getPersonType() != null && customer.getPersonType() != expected) {
            throw new IllegalArgumentException(
                    "documento " + kind + " incompatível com pessoa " + customer.getPersonType());
        }

        String normalized = DocumentNumber.normalize(rawDocument);
        var protectedValue = protector.protect(tenantId, FIELD, normalized, DocumentNumber.mask(normalized));

        customers.findByTenantIdAndDocumentHashAndDeletedAtIsNull(tenantId, protectedValue.hash())
                .filter(other -> !other.getId().equals(customer.getId()))
                .ifPresent(other -> {
                    throw new DuplicateDocumentException();
                });

        customer.setDocumentCiphertext(protectedValue.ciphertext());
        customer.setDocumentHash(protectedValue.hash());
        customer.setDocumentMasked(protectedValue.masked());
    }

    /** Cliente ativo com o documento, pelo índice cego (nada é decifrado). Documento inválido: vazio. */
    public Optional<Customer> findActiveByDocument(String rawDocument) {
        UUID tenantId = requireTenant();
        if (DocumentNumber.kind(rawDocument) == DocumentNumber.Kind.INVALID) {
            return Optional.empty();
        }
        String hash = protector.indexOf(tenantId, FIELD, DocumentNumber.normalize(rawDocument));
        return customers.findByTenantIdAndDocumentHashAndDeletedAtIsNull(tenantId, hash);
    }

    /**
     * Índice cego de um documento válido, para buscas que precisam de mais filtros (incluir excluídos,
     * imóveis do titular). Documento inválido: vazio. Nada é decifrado.
     */
    public Optional<String> searchIndex(String rawDocument) {
        UUID tenantId = requireTenant();
        if (DocumentNumber.kind(rawDocument) == DocumentNumber.Kind.INVALID) {
            return Optional.empty();
        }
        return Optional.of(protector.indexOf(tenantId, FIELD, DocumentNumber.normalize(rawDocument)));
    }

    /**
     * Documento em claro, formatado. Cada acesso fica registrado na trilha de auditoria (quem, quando,
     * qual cliente, com que finalidade), sem o valor.
     */
    public String reveal(Customer customer, String purpose) {
        UUID tenantId = requireTenant();
        if (customer.getDocumentCiphertext() == null) {
            throw new IllegalStateException("cliente sem documento");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("finalidade do acesso ao documento é obrigatória");
        }
        String plain = protector.reveal(tenantId, FIELD, customer.getDocumentCiphertext());

        AuditLog access = new AuditLog();
        access.setTenantId(tenantId);
        access.setEntityType("customers");
        access.setEntityId(customer.getId());
        access.setAction(AuditLog.AuditAction.READ);
        access.setUserId(TenantContextHolder.getUserId());
        access.setMetadata(Json.write(Map.of("field", FIELD, "purpose", purpose)));
        auditLog.save(access);

        return DocumentNumber.format(plain);
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }
}
