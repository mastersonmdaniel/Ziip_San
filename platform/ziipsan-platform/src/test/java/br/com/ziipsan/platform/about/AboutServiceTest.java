package br.com.ziipsan.platform.about;

import br.com.ziipsan.platform.about.AboutRecords.DocumentAcceptance;
import br.com.ziipsan.platform.about.AboutRecords.DocumentCatalog;
import br.com.ziipsan.platform.about.AboutRecords.ReleaseNoteView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.24: regras do Sobre sem banco — repositório em memória.
 *
 * <p>O que se prova aqui é a JUNÇÃO (vigente x aceita mais recente) e a
 * validação de versão; a persistência real (RLS, grants, idempotência no
 * banco) é do IT da aplicação (AboutP124IT).
 */
class AboutServiceTest {

    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = OffsetDateTime.of(2026, 11, 2, 10, 0, 0, 0, ZoneOffset.UTC);

    private static final class MemoryStore implements AboutStore {
        final List<DocumentCatalog> docs = new ArrayList<>();
        final List<DocumentAcceptance> acceptances = new ArrayList<>();
        final Map<String, ReleaseNoteView> views = new HashMap<>();

        @Override
        public List<DocumentCatalog> listDocuments() {
            return List.copyOf(docs);
        }

        @Override
        public Optional<DocumentCatalog> findDocument(String code) {
            return docs.stream().filter(d -> d.code().equals(code)).findFirst();
        }

        @Override
        public DocumentAcceptance accept(UUID tenantId, UUID userId, String code,
                                         String ipAddress, String authMethod) {
            DocumentCatalog doc = findDocument(code).filter(DocumentCatalog::active)
                    .orElseThrow(() -> new DocumentNotPublishedException(code));
            return acceptances.stream()
                    .filter(a -> a.tenantId().equals(tenantId) && a.userId().equals(userId)
                            && a.documentCode().equals(code)
                            && a.documentVersion().equals(doc.currentVersion()))
                    .findFirst()
                    .orElseGet(() -> {
                        DocumentAcceptance created = new DocumentAcceptance(
                                UUID.randomUUID(), tenantId, userId, code, doc.currentVersion(),
                                doc.currentContentHash(), T2, ipAddress, authMethod);
                        acceptances.add(created);
                        return created;
                    });
        }

        @Override
        public List<DocumentAcceptance> acceptancesFor(UUID tenantId, UUID userId) {
            return acceptances.stream()
                    .filter(a -> a.tenantId().equals(tenantId) && a.userId().equals(userId))
                    .toList();
        }

        @Override
        public ReleaseNoteView markViewed(UUID tenantId, UUID userId, String version) {
            return views.computeIfAbsent(tenantId + "|" + userId + "|" + version,
                    k -> new ReleaseNoteView(UUID.randomUUID(), tenantId, userId, version, T1));
        }

        @Override
        public List<String> viewedVersions(UUID tenantId, UUID userId) {
            return views.values().stream()
                    .filter(v -> v.tenantId().equals(tenantId) && v.userId().equals(userId))
                    .map(ReleaseNoteView::version)
                    .toList();
        }
    }

    private static DocumentCatalog doc(String code, String version) {
        return new DocumentCatalog(code, "Documento " + code, version,
                "a".repeat(64), T1, true);
    }

    @Test
    @DisplayName("Sobre junta vigente com a aceita mais recente; upToDate só quando a vigente foi aceita")
    void documentsJoinCurrentWithLatestAcceptance() {
        MemoryStore store = new MemoryStore();
        store.docs.add(doc("terms", "2.0.0"));
        store.docs.add(doc("privacy", "1.0.0"));
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        // Aceites antigos de terms (versões 1.0.0 e 1.1.0) + vigente de privacy.
        store.acceptances.add(new DocumentAcceptance(UUID.randomUUID(), tenant, user, "terms",
                "1.0.0", "b".repeat(64), T1, "127.0.0.1", "jwt"));
        store.acceptances.add(new DocumentAcceptance(UUID.randomUUID(), tenant, user, "terms",
                "1.1.0", "c".repeat(64), T2, "127.0.0.1", "jwt"));
        store.acceptances.add(new DocumentAcceptance(UUID.randomUUID(), tenant, user, "privacy",
                "1.0.0", "d".repeat(64), T1, "127.0.0.1", "jwt"));
        // Aceite de OUTRO usuário não pode vazar para este.
        store.acceptances.add(new DocumentAcceptance(UUID.randomUUID(), tenant, UUID.randomUUID(), "terms",
                "2.0.0", "e".repeat(64), T2, "127.0.0.1", "jwt"));

        List<AboutService.DocumentView> views = new AboutService(store).documentsFor(tenant, user);

        assertThat(views).extracting(AboutService.DocumentView::code).containsExactly("privacy", "terms");
        AboutService.DocumentView terms = views.get(1);
        assertThat(terms.currentVersion()).isEqualTo("2.0.0");
        assertThat(terms.acceptedVersion()).isEqualTo("1.1.0");
        assertThat(terms.upToDate()).isFalse();
        AboutService.DocumentView privacy = views.get(0);
        assertThat(privacy.acceptedVersion()).isEqualTo("1.0.0");
        assertThat(privacy.upToDate()).isTrue();
    }

    @Test
    @DisplayName("aceitar documento inativo ou inexistente é DocumentNotPublishedException")
    void acceptUnknownOrInactiveIsNotPublished() {
        MemoryStore store = new MemoryStore();
        store.docs.add(new DocumentCatalog("dpa", "DPA", "1.0.0", "a".repeat(64), T1, false));
        AboutService service = new AboutService(store);
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> service.accept(tenant, user, "dpa", "127.0.0.1", "jwt"))
                .isInstanceOf(DocumentNotPublishedException.class);
        assertThatThrownBy(() -> service.accept(tenant, user, "terms", "127.0.0.1", "jwt"))
                .isInstanceOf(DocumentNotPublishedException.class);
    }

    @Test
    @DisplayName("marcar vista exige SemVer X.Y.Z; segunda marcação mantém a primeira data")
    void markViewedRequiresSemverAndIsIdempotent() {
        MemoryStore store = new MemoryStore();
        AboutService service = new AboutService(store);
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> service.markViewed(tenant, user, "1.2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markViewed(tenant, user, "abc"))
                .isInstanceOf(IllegalArgumentException.class);
        ReleaseNoteView first = service.markViewed(tenant, user, "1.2.3");
        ReleaseNoteView second = service.markViewed(tenant, user, "1.2.3");
        assertThat(second.viewedAt()).isEqualTo(first.viewedAt());
        assertThat(service.viewedVersions(tenant, user)).containsExactly("1.2.3");
    }
}
