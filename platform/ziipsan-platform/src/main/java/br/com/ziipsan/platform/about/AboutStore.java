package br.com.ziipsan.platform.about;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P1.24: porta de persistência do Sobre (catálogo, aceites, visualizações).
 *
 * <p>Toda operação recebe tenant e usuário explicitamente: o isolamento
 * nunca depende só da RLS. O repositório JDBC ainda fixa
 * {@code app.current_tenant} na transação (precedente de
 * JdbcDocumentRepository), para a policy com FORCE valer também para a
 * role da aplicação.
 */
public interface AboutStore {

    /** Documentos ativos do catálogo, ordenados pelo código. */
    List<AboutRecords.DocumentCatalog> listDocuments();

    /** Documento do catálogo pelo código, ativo ou não. */
    Optional<AboutRecords.DocumentCatalog> findDocument(String code);

    /**
     * Aceita a versão vigente do documento. Idempotente: se o usuário já
     * aceitou esta versão, devolve o aceite original (não cria outro nem
     * atualiza a data).
     *
     * @throws DocumentNotPublishedException quando o código não existe no
     *                                       catálogo ou está inativo
     */
    AboutRecords.DocumentAcceptance accept(UUID tenantId, UUID userId, String code,
                                           String ipAddress, String authMethod);

    /** Aceites do usuário, ordenados pelo código do documento. */
    List<AboutRecords.DocumentAcceptance> acceptancesFor(UUID tenantId, UUID userId);

    /**
     * Marca a versão como vista pelo usuário. Idempotente: a primeira
     * visualização é a que vale (a data não é atualizada numa segunda
     * marcação).
     */
    AboutRecords.ReleaseNoteView markViewed(UUID tenantId, UUID userId, String version);

    /** Versões já vistas pelo usuário. */
    List<String> viewedVersions(UUID tenantId, UUID userId);
}
