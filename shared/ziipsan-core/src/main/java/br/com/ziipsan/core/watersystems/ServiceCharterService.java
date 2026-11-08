package br.com.ziipsan.core.watersystems;

import java.time.Instant;
import java.util.UUID;

/** Servico da Carta de Servicos (ED-79): rascunho livre, publicacao so completa e so pelo portal da P3.20. */
public final class ServiceCharterService {
    private ServiceCharterService() {}

    public static ServiceCharter createDraft(SystemsStore store, ServiceCharter draft) {
        if (draft.status() != ServiceCharter.Status.DRAFT)
            throw new SystemsExceptions.ValidationException("CHARTER_NOT_DRAFT", "a Carta nasce em rascunho");
        boolean versionTaken = store.charters(draft.tenantId()).stream()
                .anyMatch(c -> c.version() == draft.version());
        if (versionTaken)
            throw new SystemsExceptions.ValidationException("CHARTER_VERSION_DUPLICATE",
                    "ja existe Carta na versao " + draft.version() + " neste tenant");
        store.saveCharter(draft);
        return draft;
    }

    /**
     * Publica: entradas incompletas recusam NOMEANDO o servico; a
     * publicacao efetiva e da P3.20 (a referencia dela e gravada);
     * a versao publicada anterior do tenant e substituida.
     */
    public static ServiceCharter publish(SystemsStore store,
                                         SystemsPorts.PortalPublicationPort portal,
                                         UUID tenantId, UUID charterId, Instant at) {
        ServiceCharter charter = store.charter(tenantId, charterId)
                .orElseThrow(() -> new SystemsExceptions.NotFoundException("CHARTER_NOT_FOUND",
                        "Carta inexistente: " + charterId));
        if (charter.status() != ServiceCharter.Status.DRAFT)
            throw new SystemsExceptions.ValidationException("CHARTER_NOT_DRAFT",
                    "so Carta em rascunho publica: " + charterId);
        if (charter.entries().isEmpty())
            throw new SystemsExceptions.ValidationException("CHARTER_WITHOUT_SERVICES",
                    "Carta sem nenhum servico nao publica: " + charterId);
        for (ServiceCharter.CharterEntry entry : charter.entries()) {
            if (!entry.complete())
                throw new SystemsExceptions.ValidationException("CHARTER_ENTRY_INCOMPLETE",
                        "servico incompleto para publicacao (requisitos/etapas/canais/prazo declarado): "
                                + entry.serviceName());
        }
        SystemsPorts.PortalPublicationRef ref = portal.publishServiceCharter(tenantId, charterId, charter.version());
        if (ref.publishedVersion() != charter.version())
            throw new SystemsExceptions.ValidationException("PORTAL_VERSION_MISMATCH",
                    "portal da P3.20 publicou a versao " + ref.publishedVersion()
                            + " mas a Carta e a versao " + charter.version());
        for (ServiceCharter other : store.charters(tenantId)) {
            if (!other.id().equals(charterId) && other.status() == ServiceCharter.Status.PUBLISHED)
                store.saveCharter(other.superseded());
        }
        ServiceCharter published = charter.published(ref.portalRef(), ref.publishedAt());
        store.saveCharter(published);
        return published;
    }
}
