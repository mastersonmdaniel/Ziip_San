package br.com.ziipsan.app.web;

import br.com.ziipsan.app.about.ReleaseNotesLoader;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.about.AboutRecords.ReleaseNoteView;
import br.com.ziipsan.platform.about.AboutService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * P1.24: novidades por usuário (ADR-0021 decisão 8; AJUDA §6).
 *
 * <p>Na allowlist do {@code RoutePermissionTest} (sem {@code @PreAuthorize}):
 * o GET devolve notas globais do produto mais o estado de visto DO PRÓPRIO
 * usuário, e o POST só escreve o visto DO PRÓPRIO usuário (tenant e usuário
 * vêm do token) — como {@code /me}, nenhum dado de terceiros. As rotas
 * continuam exigindo autenticação e tenant.
 *
 * <p>"Uma vez por versão por usuário" é estado no servidor
 * ({@code support.release_note_view}), não flag local: outro dispositivo
 * do mesmo usuário não reexibe o que já foi visto. O POST é idempotente
 * (a primeira visualização é a que vale) e responde 404 para versão não
 * publicada — marcar como vista uma versão que não existe criaria estado
 * órfão. O filtro por tenant ({@code affects}) é P2.16 e não entra aqui.
 */
@RestController
public class ReleaseNotesController {

    public record Entry(String type, String title, List<String> commits, String issue) {
    }

    public record Note(String version, String date, List<Entry> entries, boolean viewed) {
    }

    public record Viewed(String version, boolean viewed, OffsetDateTime viewedAt) {
    }

    private final ReleaseNotesLoader loader;
    private final AboutService aboutService;

    public ReleaseNotesController(AboutService aboutService) {
        this(new ReleaseNotesLoader(), aboutService);
    }

    ReleaseNotesController(ReleaseNotesLoader loader, AboutService aboutService) {
        this.loader = loader;
        this.aboutService = aboutService;
    }

    @GetMapping("/api/v1/release-notes")
    public List<Note> notes() {
        Set<String> viewed = Set.copyOf(aboutService.viewedVersions(
                TenantContextHolder.getTenantId(), TenantContextHolder.getUserId()));
        return loader.notes().stream()
                .map(n -> new Note(n.version(), n.date(),
                        n.entries().stream()
                                .map(e -> new Entry(e.type(), e.title(), e.commits(), e.issue()))
                                .toList(),
                        viewed.contains(n.version())))
                .toList();
    }

    @PostMapping("/api/v1/release-notes/{version}/view")
    public Viewed view(@PathVariable String version) {
        if (!AboutService.isReleaseVersion(version)) {
            throw new ApiException.BadRequest("invalid-version", "Versão deve ser SemVer X.Y.Z: " + version);
        }
        if (loader.note(version).isEmpty()) {
            throw new ApiException.NotFound("Nota de versão não publicada: " + version);
        }
        ReleaseNoteView marked = aboutService.markViewed(
                TenantContextHolder.getTenantId(), TenantContextHolder.getUserId(), version);
        return new Viewed(marked.version(), true, marked.viewedAt());
    }
}
