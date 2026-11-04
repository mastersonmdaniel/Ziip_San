package br.com.ziipsan.platform.about;

import br.com.ziipsan.platform.about.AboutRecords.DocumentAcceptance;
import br.com.ziipsan.platform.about.AboutRecords.DocumentCatalog;
import br.com.ziipsan.platform.about.AboutRecords.ReleaseNoteView;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * P1.24: regras do Sobre sobre {@link AboutStore}. Sem Spring, sem HTTP:
 * o controller traduz as exceções daqui para problem+json.
 *
 * <p>Regras:
 * <ul>
 *   <li>o Sobre mostra, por documento, a versão vigente do catálogo e a
 *       versão aceita MAIS RECENTE do usuário (um usuário pode ter aceito
 *       versões antigas; o que importa é se a vigente já foi aceita);</li>
 *   <li>aceitar é sempre aceitar a versão vigente — não há aceite de
 *       versão antiga pela API (o aceite de versão antiga, se um dia
 *       existir, é outro fluxo, não um parâmetro a mais aqui);</li>
 *   <li>versão de novidade segue o SemVer estrito da P0.41
 *       ({@code X.Y.Z}, sem pré-release): o schema de
 *       content/release-notes/schema.json não aceita outro formato, e a
 *       CHECK da tabela também não.</li>
 * </ul>
 */
@Component
public final class AboutService {

    /** SemVer estrito da P0.41 (o mesmo da CHECK de support.release_note_view). */
    static final Pattern RELEASE_VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");

    /** Um documento do Sobre, com a versão vigente e a aceita do usuário. */
    public record DocumentView(String code, String title, String currentVersion,
                               String acceptedVersion, OffsetDateTime acceptedAt,
                               boolean upToDate) {
    }

    private final AboutStore store;

    @Autowired
    public AboutService(AboutStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store é obrigatório");
        }
        this.store = store;
    }

    /** Documentos ativos com o aceite mais recente do usuário em cada um. */
    public List<DocumentView> documentsFor(UUID tenantId, UUID userId) {
        List<DocumentCatalog> catalog = store.listDocuments();
        Map<String, DocumentAcceptance> latest = new HashMap<>();
        for (DocumentAcceptance acceptance : store.acceptancesFor(tenantId, userId)) {
            latest.merge(acceptance.documentCode(), acceptance, (old, next) ->
                    next.acceptedAt() != null
                            && (old.acceptedAt() == null || next.acceptedAt().isAfter(old.acceptedAt()))
                            ? next : old);
        }
        List<DocumentView> out = new ArrayList<>();
        for (DocumentCatalog doc : catalog) {
            DocumentAcceptance accepted = latest.get(doc.code());
            out.add(new DocumentView(
                    doc.code(),
                    doc.title(),
                    doc.currentVersion(),
                    accepted == null ? null : accepted.documentVersion(),
                    accepted == null ? null : accepted.acceptedAt(),
                    accepted != null && accepted.documentVersion().equals(doc.currentVersion())));
        }
        out.sort(Comparator.comparing(DocumentView::code));
        return out;
    }

    /**
     * Aceita a versão vigente do documento, com a evidência (IP e método de
     * autenticação) que a P3.34 exige sobre este aceite.
     *
     * @throws DocumentNotPublishedException quando o código não está no
     *                                       catálogo ou está inativo
     */
    public DocumentAcceptance accept(UUID tenantId, UUID userId, String code,
                                     String ipAddress, String authMethod) {
        return store.accept(tenantId, userId, code, ipAddress, authMethod);
    }

    /** Versões de novidade já vistas pelo usuário. */
    public List<String> viewedVersions(UUID tenantId, UUID userId) {
        return store.viewedVersions(tenantId, userId);
    }

    /**
     * Marca a versão como vista. Idempotente (a primeira visualização vale).
     *
     * @throws IllegalArgumentException quando a versão não é SemVer X.Y.Z
     */
    public ReleaseNoteView markViewed(UUID tenantId, UUID userId, String version) {
        if (version == null || !RELEASE_VERSION.matcher(version.strip()).matches()) {
            throw new IllegalArgumentException("version deve ser SemVer X.Y.Z: " + version);
        }
        return store.markViewed(tenantId, userId, version.strip());
    }

    /** Conveniência para o controller: a versão existe como nota publicada? */
    public static boolean isReleaseVersion(String version) {
        return version != null && RELEASE_VERSION.matcher(version.strip()).matches();
    }
}
