package br.com.ziipsan.grs.mtr.pgrs;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E4.
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteClassification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * PGRS electrónico (ED-104, E4). Cada linha do plano é validada pelo E5: código da Lista
 * Brasileira com formato de 6 dígitos ou, nos esquemas próprios, grupo de RSS (A–E) / classe de
 * RCC (A–D). Plano sem linhas, sem responsável técnico declarado ou com vigência invertida é
 * recusado. Activar exige vigência completa. Nada aqui decide se o tenant é obrigado a ter
 * PGRS — enquadramento jurídico é a pendência P-ED104-04.
 */
@Service
@RequiredArgsConstructor
public class PgrsService {

    private final WastePgrsRepository pgrsRepository;

    /** Linha do plano: resíduo codificado + destinação pretendida declarada. */
    public record PgrsLine(String scheme, String code, String description, String intendedDestination) {
        public PgrsLine {
            if (scheme == null || code == null || description == null || description.isBlank()) {
                throw new IllegalArgumentException("linha do PGRS exige esquema, código e descrição");
            }
            switch (scheme) {
                case "NBR_10004" -> WasteClassification.requireIbamaCode(code);
                case "RSS" -> WasteClassification.requireRssGroup(code);
                case "RCC" -> WasteClassification.requireRccClass(code);
                default -> throw new IllegalArgumentException("esquema de classificação desconhecido: " + scheme);
            }
        }
    }

    public record CreatePgrsRequest(UUID generatorId, String technicianName, String technicianRegistry,
                                    LocalDate validFrom, LocalDate validUntil, LocalDate revisionDue,
                                    List<PgrsLine> lines) {
    }

    @Transactional
    public WastePgrs createPgrs(CreatePgrsRequest request) {
        UUID tenantId = requireTenant();
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("PGRS sem linhas de resíduo não é plano");
        }
        if (request.technicianName() == null || request.technicianName().isBlank()
                || request.technicianRegistry() == null || request.technicianRegistry().isBlank()) {
            throw new IllegalArgumentException("PGRS exige responsável técnico declarado (nome e registro)");
        }
        if (request.validFrom() != null && request.validUntil() != null
                && request.validUntil().isBefore(request.validFrom())) {
            throw new IllegalArgumentException("vigência do PGRS invertida");
        }
        WastePgrs pgrs = new WastePgrs();
        pgrs.setTenantId(tenantId);
        pgrs.setGeneratorId(request.generatorId());
        pgrs.setTechnicianName(request.technicianName());
        pgrs.setTechnicianRegistry(request.technicianRegistry());
        pgrs.setValidFrom(request.validFrom());
        pgrs.setValidUntil(request.validUntil());
        pgrs.setRevisionDue(request.revisionDue());
        pgrs.setLines(Json.write(request.lines()));
        pgrs.setStatus(WastePgrs.PgrsStatus.DRAFT);
        return pgrsRepository.save(pgrs);
    }

    @Transactional
    public WastePgrs activatePgrs(UUID pgrsId) {
        UUID tenantId = requireTenant();
        WastePgrs pgrs = pgrsRepository.findByIdAndTenantId(pgrsId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("PGRS not found"));
        if (pgrs.getStatus() != WastePgrs.PgrsStatus.DRAFT) {
            throw new IllegalStateException("só um PGRS em DRAFT pode ser activado; estado: " + pgrs.getStatus());
        }
        if (pgrs.getValidFrom() == null || pgrs.getValidUntil() == null) {
            throw new IllegalStateException("activação exige vigência completa (início e fim)");
        }
        pgrs.setStatus(WastePgrs.PgrsStatus.ACTIVE);
        return pgrsRepository.save(pgrs);
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }
}
