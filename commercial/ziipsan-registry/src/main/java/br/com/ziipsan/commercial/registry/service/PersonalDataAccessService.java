package br.com.ziipsan.commercial.registry.service;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.audit.PersonalDataAccessLog;
import br.com.ziipsan.core.audit.PersonalDataAccessLogRepository;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Busca por documento com papel, registro e cota; leituras de dado pessoal registradas
 * (P0.11, ADR-0008).
 *
 * <p>Regras, na ordem (a negação não vaza estado da cota para quem não tem permissão):
 * <ol>
 *   <li>sem a permissão própria {@code registry:search-document}, a busca por documento é
 *       403 e o evento fica registrado como DENIED;</li>
 *   <li>com a permissão, a cota por usuário por hora é conferida sobre as buscas concluídas
 *       em {@code personal_data_access_log}; excedida, a resposta é 429 e o estouro fica
 *       registrado como QUOTA_EXCEEDED — esse registro é o gancho da detecção de anomalia
 *       da P3.10, que lê os estouros recentes em vez de definir critério próprio aqui;</li>
 *   <li>buscas concluídas, leituras de cadastro e revelações são registradas com usuário,
 *       tenant, entidade e resultado, sem o documento em claro (só o índice cego).</li>
 * </ol>
 *
 * <p>Cota: tentativas negadas e estouros não consomem cota, para que um usuário sem
 * permissão não esgote a cota de outro. O valor-padrão (60 buscas por usuário por hora) é
 * provisório e ajustável por {@code ziipsan.document-search.quota-per-hour}: o plano não
 * fixa o número, e a confirmação do valor pelo dono do produto está pendente (ver relatório
 * da frente). Busca por nome continua com o papel de leitura, sem cota deste serviço.
 *
 * <p>Cada escrita abre transação própria (REQUIRES_NEW): as buscas e leituras que registram
 * aqui rodam em transações read-only, e um log escrito nelas nunca chegaria ao banco
 * (flush suprimido). O registro de acesso precisa persistir independentemente da
 * transação da consulta.
 */
@Service
public class PersonalDataAccessService {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataAccessService.class);

    /** Permissão própria da busca por documento, do catálogo da P0.13 (criada na P0.11). */
    public static final String SEARCH_DOCUMENT_PERMISSION = "registry:search-document";

    /** Cota provisória por usuário por hora, pendente de confirmação do dono (ver javadoc). */
    public static final int DEFAULT_DOCUMENT_SEARCH_QUOTA_PER_HOUR = 60;

    private final PersonalDataAccessLogRepository logRepository;
    private final int quotaPerHour;

    public PersonalDataAccessService(PersonalDataAccessLogRepository logRepository,
                                     @Value("${ziipsan.document-search.quota-per-hour:"
                                             + DEFAULT_DOCUMENT_SEARCH_QUOTA_PER_HOUR + "}") int quotaPerHour) {
        this.logRepository = logRepository;
        this.quotaPerHour = quotaPerHour;
    }

    /**
     * Autoriza a busca por documento: permissão própria e cota. Não registra a busca
     * concluída — quem chamou registra o resultado em {@link #recordDocumentSearch}.
     *
     * <p>O registro da negação/estouro precisa sobreviver à exceção lançada em seguida:
     * sem {@code noRollbackFor}, a transação REQUIRES_NEW faria rollback e o evento
     * DENIED/QUOTA_EXCEEDED se perderia (o registro de acesso é a prova, não pode
     * depender do sucesso da operação).
     *
     * @throws ApiException.Forbidden sem a permissão própria (403, evento DENIED registrado)
     * @throws ApiException.TooManyRequests cota excedida (429, evento QUOTA_EXCEEDED registrado)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = {ApiException.Forbidden.class, ApiException.TooManyRequests.class})
    public void authorizeDocumentSearch(String documentHash) {
        UUID tenantId = requireTenant();
        if (!hasSearchDocumentPermission()) {
            save(tenantId, PersonalDataAccessLog.AccessType.DOCUMENT_SEARCH, null, null,
                    documentHash, null, PersonalDataAccessLog.Result.DENIED);
            throw new ApiException.Forbidden("A busca por documento exige a permissão registry:search-document.");
        }
        long used = logRepository.countDocumentSearchesInWindow(tenantId, TenantContextHolder.getUserId(),
                Instant.now().minus(Duration.ofHours(1)));
        if (used >= quotaPerHour) {
            save(tenantId, PersonalDataAccessLog.AccessType.DOCUMENT_SEARCH, null, null,
                    documentHash, null, PersonalDataAccessLog.Result.QUOTA_EXCEEDED);
            log.warn("Cota de busca por documento excedida: tenant={} user={} buscas_na_hora={}",
                    tenantId, TenantContextHolder.getUserId(), used);
            throw new ApiException.TooManyRequests(
                    "Cota de busca por documento por hora excedida. Tente novamente mais tarde.");
        }
    }

    /** Busca por documento concluída: FOUND se retornou ao menos um registro. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDocumentSearch(String documentHash, boolean found) {
        save(requireTenant(), PersonalDataAccessLog.AccessType.DOCUMENT_SEARCH, null, null,
                documentHash, null,
                found ? PersonalDataAccessLog.Result.FOUND : PersonalDataAccessLog.Result.NOT_FOUND);
    }

    /** Leitura de um cadastro (ex.: GET do cliente): dado pessoal lido, sem documento em claro. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPersonalRead(String entityType, UUID entityId) {
        save(requireTenant(), PersonalDataAccessLog.AccessType.PERSONAL_READ, entityType, entityId,
                null, null, PersonalDataAccessLog.Result.FOUND);
    }

    /** Permissão própria da exportação de listas (P1.12, regra única `*:export` por recurso). */
    public static final String EXPORT_PERMISSION = "registry:export";

    /**
     * P1.12: autoriza a exportação, no mesmo modelo do gate de busca por documento:
     * sem {@code registry:export}, a negação fica registrada (DENIED) e a resposta é
     * 403 — e a negação não vaza estado de mais nada. O serviço de exportação chama
     * isto ANTES de qualquer consulta, para que uma exportação negada não conte
     * linhas nem toque nos dados.
     *
     * @throws ApiException.Forbidden sem a permissão própria (403, evento DENIED registrado)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = {ApiException.Forbidden.class})
    public void authorizeExport(String resource) {
        UUID tenantId = requireTenant();
        if (!hasExportPermission()) {
            save(tenantId, PersonalDataAccessLog.AccessType.EXPORT, resource, null,
                    null, "export:" + resource, PersonalDataAccessLog.Result.DENIED);
            throw new ApiException.Forbidden("A exportação de listas exige a permissão registry:export.");
        }
    }

    /**
     * P1.12: cada exportação concluída ou enfileirada deixa um registro EXPORT, com o
     * recurso e a quantidade (ou {@code queued}, quando acima do limite síncrono e o
     * arquivo ainda vai ser gerado pelo worker). O registro é feito uma vez, no pedido;
     * a conclusão assíncrona não registra de novo. Sem documento em claro: o arquivo
     * já sai mascarado, e o log não guarda nem a máscara.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExport(String resource, String outcome) {
        save(requireTenant(), PersonalDataAccessLog.AccessType.EXPORT, resource, null,
                null, "export:" + resource + " " + outcome, PersonalDataAccessLog.Result.FOUND);
    }

    /** Revelação de documento em claro (além do registro já feito na trilha de auditoria). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDocumentReveal(String entityType, UUID entityId, String documentHash, String purpose) {
        save(requireTenant(), PersonalDataAccessLog.AccessType.DOCUMENT_REVEAL, entityType, entityId,
                documentHash, purpose, PersonalDataAccessLog.Result.FOUND);
    }

    /**
     * Gancho para a detecção de anomalia da P3.10: quantos estouros de cota o tenant teve
     * desde o instante dado. A detecção completa (critério, incidente, RIPD) é da P3.10.
     */
    @Transactional(readOnly = true)
    public long quotaExceededSince(UUID tenantId, Instant since) {
        return logRepository.countQuotaExceeded(tenantId, since);
    }

    private void save(UUID tenantId, PersonalDataAccessLog.AccessType type, String entityType, UUID entityId,
                      String documentHash, String purpose, PersonalDataAccessLog.Result result) {
        PersonalDataAccessLog entry = new PersonalDataAccessLog();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(tenantId);
        entry.setUserId(TenantContextHolder.getUserId());
        entry.setAccessedAt(Instant.now());
        entry.setAccessType(type.name());
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDocumentHash(documentHash);
        entry.setPurpose(purpose);
        entry.setResult(result.name());
        logRepository.save(entry);
    }

    private static boolean hasSearchDocumentPermission() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> SEARCH_DOCUMENT_PERMISSION.equals(a.getAuthority()));
    }

    private static boolean hasExportPermission() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> EXPORT_PERMISSION.equals(a.getAuthority()));
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }
}
