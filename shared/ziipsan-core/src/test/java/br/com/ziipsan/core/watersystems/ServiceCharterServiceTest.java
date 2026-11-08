package br.com.ziipsan.core.watersystems;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServiceCharterServiceTest {

    private static final Instant AT = Instant.parse("2026-03-01T12:00:00Z");

    private ServiceCharter.CharterEntry completeEntry(String name) {
        return new ServiceCharter.CharterEntry(name, "descricao declarada de teste",
                List.of("requisito declarado"), List.of("etapa declarada"),
                "prazo declarado de teste", List.of("canal declarado"));
    }

    private SystemsPorts.PortalPublicationPort portalOk() {
        return (tenantId, charterId, version) -> new SystemsPorts.PortalPublicationRef("PORTAL-REF-TESTE", version, AT);
    }

    @Test void publishCompleteCharterRecordsPortalRef() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        ServiceCharter draft = ServiceCharter.draft(UUID.randomUUID(), SystemsTestSupport.TENANT, 1,
                List.of(completeEntry("Servico A")));
        ServiceCharterService.createDraft(store, draft);
        ServiceCharter published = ServiceCharterService.publish(store, portalOk(),
                SystemsTestSupport.TENANT, draft.id(), AT);
        assertEquals(ServiceCharter.Status.PUBLISHED, published.status());
        assertEquals("PORTAL-REF-TESTE", published.portalRef());
        assertEquals(ServiceCharter.LEGAL_BASIS, "Lei 13.460/2017");
    }

    @Test void entryWithoutDeclaredDeadlineBlocksPublication() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        var incomplete = new ServiceCharter.CharterEntry("Servico B", "descricao",
                List.of("requisito"), List.of("etapa"), null, List.of("canal"));
        assertFalse(incomplete.complete());
        ServiceCharter draft = ServiceCharter.draft(UUID.randomUUID(), SystemsTestSupport.TENANT, 1, List.of(incomplete));
        ServiceCharterService.createDraft(store, draft);
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> ServiceCharterService.publish(store, portalOk(), SystemsTestSupport.TENANT, draft.id(), AT));
        assertEquals("CHARTER_ENTRY_INCOMPLETE", ex.code());
        assertTrue(ex.getMessage().contains("Servico B"));
        assertEquals(ServiceCharter.Status.DRAFT,
                store.charter(SystemsTestSupport.TENANT, draft.id()).orElseThrow().status());
    }

    @Test void entryMissingRequirementsStepsOrChannelsIsIncomplete() {
        assertFalse(new ServiceCharter.CharterEntry("S", "d", List.of(), List.of("e"), "prazo", List.of("c")).complete());
        assertFalse(new ServiceCharter.CharterEntry("S", "d", List.of("r"), List.of(), "prazo", List.of("c")).complete());
        assertFalse(new ServiceCharter.CharterEntry("S", "d", List.of("r"), List.of("e"), "prazo", List.of()).complete());
        assertFalse(new ServiceCharter.CharterEntry("S", "d", List.of("r"), List.of("e"), " ", List.of("c")).complete());
        assertTrue(completeEntry("S").complete());
    }

    @Test void emptyCharterCannotPublish() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        ServiceCharter draft = ServiceCharter.draft(UUID.randomUUID(), SystemsTestSupport.TENANT, 1, List.of());
        ServiceCharterService.createDraft(store, draft);
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> ServiceCharterService.publish(store, portalOk(), SystemsTestSupport.TENANT, draft.id(), AT));
        assertEquals("CHARTER_WITHOUT_SERVICES", ex.code());
    }

    @Test void publishedCharterCannotExistWithoutPortalRef() {
        // Correcao D1 (sonda A8): nao ha construtor publico — PUBLISHED com
        // portalRef falso nao nasce por construcao direta (ver Ed79SealingTest);
        // a transicao selada continua recusando referencia em branco.
        assertThrows(IllegalArgumentException.class, () -> ServiceCharter.draft(UUID.randomUUID(),
                SystemsTestSupport.TENANT, 1, List.of(completeEntry("S"))).published(" ", AT));
        assertThrows(IllegalArgumentException.class, () -> ServiceCharter.draft(UUID.randomUUID(),
                SystemsTestSupport.TENANT, 1, List.of()).published("PORTAL-REF", AT));
    }

    @Test void publishingNewVersionSupersedesPrevious() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        ServiceCharter v1 = ServiceCharter.draft(UUID.randomUUID(), SystemsTestSupport.TENANT, 1, List.of(completeEntry("Servico A")));
        ServiceCharterService.createDraft(store, v1);
        ServiceCharterService.publish(store, portalOk(), SystemsTestSupport.TENANT, v1.id(), AT);
        ServiceCharter v2 = ServiceCharter.draft(UUID.randomUUID(), SystemsTestSupport.TENANT, 2, List.of(completeEntry("Servico A2")));
        ServiceCharterService.createDraft(store, v2);
        ServiceCharterService.publish(store, portalOk(), SystemsTestSupport.TENANT, v2.id(), AT);
        assertEquals(ServiceCharter.Status.SUPERSEDED,
                store.charter(SystemsTestSupport.TENANT, v1.id()).orElseThrow().status());
        assertEquals(ServiceCharter.Status.PUBLISHED,
                store.charter(SystemsTestSupport.TENANT, v2.id()).orElseThrow().status());
    }

    @Test void duplicateVersionRejected() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        ServiceCharterService.createDraft(store, ServiceCharter.draft(
                UUID.randomUUID(), SystemsTestSupport.TENANT, 1, List.of(completeEntry("S"))));
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> ServiceCharterService.createDraft(store, ServiceCharter.draft(
                        UUID.randomUUID(), SystemsTestSupport.TENANT, 1, List.of(completeEntry("S")))));
        assertEquals("CHARTER_VERSION_DUPLICATE", ex.code());
    }

    @Test void portalVersionMismatchRejected() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        ServiceCharter draft = ServiceCharter.draft(UUID.randomUUID(), SystemsTestSupport.TENANT, 3, List.of(completeEntry("S")));
        ServiceCharterService.createDraft(store, draft);
        SystemsPorts.PortalPublicationPort wrongVersion =
                (t, id, v) -> new SystemsPorts.PortalPublicationRef("REF", v + 1, AT);
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> ServiceCharterService.publish(store, wrongVersion, SystemsTestSupport.TENANT, draft.id(), AT));
        assertEquals("PORTAL_VERSION_MISMATCH", ex.code());
    }
}
