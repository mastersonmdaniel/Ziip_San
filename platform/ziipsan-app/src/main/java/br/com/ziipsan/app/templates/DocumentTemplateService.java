package br.com.ziipsan.app.templates;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.templates.DocumentTemplate;
import br.com.ziipsan.platform.templates.DocumentTemplateRepository;
import br.com.ziipsan.platform.templates.FactoryTemplates;
import br.com.ziipsan.platform.templates.GeneratedDocument;
import br.com.ziipsan.platform.templates.GeneratedDocumentRepository;
import br.com.ziipsan.platform.templates.TemplateBlock;
import br.com.ziipsan.platform.templates.TemplateBrandProvider;
import br.com.ziipsan.platform.templates.TemplateChannel;
import br.com.ziipsan.platform.templates.TemplateDocumentType;
import br.com.ziipsan.platform.templates.TemplateEngine;
import br.com.ziipsan.platform.templates.TemplateVersion;
import br.com.ziipsan.platform.templates.TemplateVersionRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Motor único de modelos de documento e mensagem (P2.30, tabelas da V1_12).
 *
 * <p>Fluxo de uma versão, sem atalho: cria-se em DRAFT, gera-se a prévia com
 * massa sintética, propõe-se, e outra pessoa aprova — autor e proponente
 * nunca aprovam a própria versão (o envelope de proposta e aprovação da
 * P0.19, com as travas também em CHECKs na V1_12). Rejeição volta a versão
 * para REJECTED com motivo; correção é versão nova, nunca edição.
 *
 * <p>Vigência com os dois extremos inclusivos (como {@code parameter_versions}
 * da V1_5): a sobreposição é recusada pelo banco (EXCLUDE da V1_12) e aqui
 * vira 409 "vigência sobreposta", não 500. Como o EXCLUDE vale para todas
 * as versões, o conflito aparece já na criação da versão — nunca na
 * aprovação.
 *
 * <p>Blocos travados: a conferência é contra a versão de maior número do
 * modelo, qualquer que seja o estado dela — um bloco travado numa versão
 * rejeitada continua travado na seguinte (travado é travado; a saída para
 * um trecho travado por engano é arquivar o modelo e criar outro, nunca
 * editar o bloco por fora).
 *
 * <p>Marca do tenant (P2.17, futura): o construtor aceita um
 * {@link TemplateBrandProvider} opcional — nenhum bean existe nesta frente,
 * então o valor é nulo e referência a {@code brand.*} na geração é recusada
 * (nunca texto vazio silencioso). A P2.17 só precisa declarar o bean ligado
 * ao BrandProvider da P1.19.
 *
 * <p>Documento gerado: o serviço persiste o texto renderizado no instante da
 * geração junto do modelo e da versão usados. Versão nova posterior não
 * re-renderiza nem altera o documento já emitido. Os valores das variáveis
 * NÃO são persistidos (só os nomes referenciados): o valor impresso já
 * está no instantâneo do texto.
 */
@Service
public class DocumentTemplateService {

    public record BlockInput(String id, String kind, String origin, String text) {
    }

    public record VariableInput(String name, String type) {
    }

    public record TemplateInput(
            @NotBlank(message = "Informe o código do modelo.") @Size(max = 100, message = "O código aceita no máximo 100 caracteres.") String code,
            @NotBlank(message = "Informe o título do modelo.") @Size(max = 200, message = "O título aceita no máximo 200 caracteres.") String title,
            @NotBlank(message = "Informe o tipo de documento.") String documentType,
            @NotBlank(message = "Informe o canal.") String channel) {
    }

    public record VersionInput(List<BlockInput> blocks, List<VariableInput> variables,
                               LocalDate validFrom, LocalDate validTo) {
    }

    public record CopyInput(
            @NotBlank(message = "Informe o código do modelo.") @Size(max = 100, message = "O código aceita no máximo 100 caracteres.") String code,
            @NotBlank(message = "Informe o título do modelo.") @Size(max = 200, message = "O título aceita no máximo 200 caracteres.") String title,
            LocalDate validFrom, LocalDate validTo) {
    }

    public record GenerateInput(UUID versionId, LocalDate date, Map<String, String> values) {
    }

    public record TemplateView(UUID id, String code, String title, String documentType, String channel,
                               String factoryKey, String status) {
    }

    public record VersionView(UUID id, UUID templateId, int versionNumber, List<BlockInput> blocks,
                              List<VariableInput> variables, LocalDate validFrom, LocalDate validTo,
                              String status, UUID proposedBy, UUID approvedBy, String previewRendered,
                              String rejectionReason) {
    }

    public record GeneratedView(UUID id, UUID templateId, UUID templateVersionId, String renderedBody,
                                List<String> variablesUsed, OffsetDateTime generatedAt) {
    }

    public record FactoryView(String key, String documentType, String channel, String title, String body,
                              List<VariableInput> variables) {
    }

    private final DocumentTemplateRepository templates;
    private final TemplateVersionRepository versions;
    private final GeneratedDocumentRepository generated;
    private final TemplateBrandProvider brandProvider;

    public DocumentTemplateService(DocumentTemplateRepository templates, TemplateVersionRepository versions,
                                   GeneratedDocumentRepository generated,
                                   @Autowired(required = false) TemplateBrandProvider brandProvider) {
        this.templates = templates;
        this.versions = versions;
        this.generated = generated;
        this.brandProvider = brandProvider;
    }

    // -- fábricas ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FactoryView> listFactory() {
        return FactoryTemplates.ALL.stream()
                .map(f -> new FactoryView(f.key(), f.documentType().name(), f.channel().name(), f.title(),
                        f.body(), f.variables().stream()
                                .map(v -> new VariableInput(v.name(), v.type().name())).toList()))
                .toList();
    }

    /**
     * Copia uma fábrica para um modelo do tenant, em DRAFT, versão 1. A
     * cópia pode ser ajustada (versões novas) sem tocar na fábrica — a
     * fábrica continua em código, imutável.
     */
    @Transactional
    public TemplateView copyFactory(String key, CopyInput input) {
        FactoryTemplates.FactoryTemplate factory = FactoryTemplates.byKey(key)
                .orElseThrow(() -> new ApiException.NotFound("Modelo de fábrica não encontrado."));
        UUID tenantId = requireTenant();
        UUID actor = requireUser();
        DocumentTemplate template = new DocumentTemplate();
        template.setTenantId(tenantId);
        template.setTenantSchema(TenantContextHolder.getTenant());
        template.setCode(input.code().trim());
        template.setTitle(input.title().trim());
        template.setDocumentType(factory.documentType().name());
        template.setChannel(factory.channel().name());
        template.setFactoryKey(factory.key());
        template.setStatus("ACTIVE");
        template.setCreatedBy(actor);
        template.setUpdatedBy(actor);
        DocumentTemplate saved = saveTemplate(template);
        TemplateVersion version = new TemplateVersion();
        version.setTenantId(tenantId);
        version.setTenantSchema(TenantContextHolder.getTenant());
        version.setTemplateId(saved.getId());
        version.setVersionNumber(1);
        version.setBlocks(Json.write(List.of(Map.of("id", "main", "kind", "FREE", "origin", "TENANT",
                "text", factory.body()))));
        version.setVariables(Json.write(factory.variables().stream()
                .map(v -> Map.of("name", v.name(), "type", v.type().name())).toList()));
        version.setValidFrom(input.validFrom() == null ? LocalDate.now(ZoneOffset.UTC) : input.validFrom());
        version.setValidTo(input.validTo());
        version.setStatus("DRAFT");
        version.setCreatedBy(actor);
        version.setUpdatedBy(actor);
        saveVersion(version);
        return toView(saved);
    }

    // -- modelos ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TemplateView> list() {
        return templates.findAllByTenantIdOrderByCodeAsc(requireTenant()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public TemplateView get(UUID id) {
        return toView(requireTemplate(id));
    }

    @Transactional
    public TemplateView create(TemplateInput input) {
        UUID tenantId = requireTenant();
        UUID actor = requireUser();
        TemplateDocumentType type = parseDocumentType(input.documentType());
        TemplateChannel channel = parseChannel(input.channel());
        DocumentTemplate template = new DocumentTemplate();
        template.setTenantId(tenantId);
        template.setTenantSchema(TenantContextHolder.getTenant());
        template.setCode(input.code().trim());
        template.setTitle(input.title().trim());
        template.setDocumentType(type.name());
        template.setChannel(channel.name());
        template.setStatus("ACTIVE");
        template.setCreatedBy(actor);
        template.setUpdatedBy(actor);
        return toView(saveTemplate(template));
    }

    // -- versões ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<VersionView> listVersions(UUID templateId) {
        requireTemplate(templateId);
        return versions.findAllByTenantIdAndTemplateIdOrderByVersionNumberAsc(requireTenant(), templateId)
                .stream().map(this::toView).toList();
    }

    @Transactional
    public VersionView createVersion(UUID templateId, VersionInput input) {
        UUID tenantId = requireTenant();
        UUID actor = requireUser();
        DocumentTemplate template = requireTemplate(templateId);
        if (!"ACTIVE".equals(template.getStatus())) {
            throw new ApiException.Conflict("state-conflict", "Modelo arquivado não aceita versão nova.");
        }
        TemplateDocumentType type = TemplateDocumentType.valueOf(template.getDocumentType());
        List<TemplateBlock> blocks = parseBlocks(input.blocks());
        List<TemplateEngine.DeclaredVariable> declared = parseVariables(input.variables());
        if (input.validFrom() == null) {
            throw validation("validFrom", "Informe o início da vigência.");
        }
        if (input.validTo() != null && input.validTo().isBefore(input.validFrom())) {
            throw validation("validTo", "O fim da vigência não pode ser anterior ao início.");
        }
        List<String> problems = new ArrayList<>(TemplateEngine.validationProblems(type, blocks, declared));
        versions.findTopByTenantIdAndTemplateIdOrderByVersionNumberDesc(tenantId, templateId)
                .ifPresent(latest -> problems.addAll(
                        TemplateEngine.lockedBlockProblems(parseBlocksJson(latest.getBlocks()), blocks)));
        if (!problems.isEmpty()) {
            throw new ApiException.Validation(String.join(" ", problems), List.of(), List.of());
        }
        int number = versions.findTopByTenantIdAndTemplateIdOrderByVersionNumberDesc(tenantId, templateId)
                .map(v -> v.getVersionNumber() + 1).orElse(1);
        TemplateVersion version = new TemplateVersion();
        version.setTenantId(tenantId);
        version.setTenantSchema(TenantContextHolder.getTenant());
        version.setTemplateId(templateId);
        version.setVersionNumber(number);
        version.setBlocks(Json.write(blocks.stream().map(DocumentTemplateService::blockToMap).toList()));
        version.setVariables(Json.write(declared.stream()
                .map(v -> Map.of("name", v.name(), "type", v.type().name())).toList()));
        version.setValidFrom(input.validFrom());
        version.setValidTo(input.validTo());
        version.setStatus("DRAFT");
        version.setCreatedBy(actor);
        version.setUpdatedBy(actor);
        return toView(saveVersion(version));
    }

    /** Prévia com massa sintética; grava o instante e o texto aprovado depois. */
    @Transactional
    public String preview(UUID templateId, UUID versionId) {
        DocumentTemplate template = requireTemplate(templateId);
        TemplateVersion version = requireVersion(templateId, versionId);
        if (!"DRAFT".equals(version.getStatus()) && !"PROPOSED".equals(version.getStatus())) {
            throw new ApiException.Conflict("state-conflict",
                    "Só versão em rascunho ou proposta aceita prévia.");
        }
        TemplateDocumentType type = TemplateDocumentType.valueOf(template.getDocumentType());
        String rendered;
        try {
            rendered = TemplateEngine.preview(type, parseBlocksJson(version.getBlocks()));
        } catch (IllegalArgumentException e) {
            throw new ApiException.Validation(e.getMessage(), List.of(), List.of());
        }
        version.setPreviewedAt(OffsetDateTime.now(ZoneOffset.UTC));
        version.setPreviewRendered(rendered);
        version.setUpdatedBy(requireUser());
        versions.save(version);
        return rendered;
    }

    @Transactional
    public VersionView propose(UUID templateId, UUID versionId) {
        TemplateVersion version = requireVersion(templateId, versionId);
        if (!"DRAFT".equals(version.getStatus())) {
            throw new ApiException.Conflict("state-conflict", "Só versão em rascunho pode ser proposta.");
        }
        version.setStatus("PROPOSED");
        version.setProposedBy(requireUser());
        version.setUpdatedBy(requireUser());
        return toView(versions.save(version));
    }

    @Transactional
    public VersionView approve(UUID templateId, UUID versionId) {
        UUID actor = requireUser();
        TemplateVersion version = requireVersion(templateId, versionId);
        if (!"PROPOSED".equals(version.getStatus())) {
            throw new ApiException.Conflict("state-conflict", "Só versão proposta pode ser aprovada.");
        }
        if (actor.equals(version.getProposedBy()) || actor.equals(version.getCreatedBy())) {
            throw new ApiException.Forbidden("Quem propôs ou criou não aprova o próprio modelo.");
        }
        if (version.getPreviewedAt() == null) {
            throw new ApiException.Conflict("state-conflict",
                    "A prévia com massa sintética é obrigatória antes da aprovação.");
        }
        version.setStatus("APPROVED");
        version.setApprovedBy(actor);
        version.setApprovedAt(OffsetDateTime.now(ZoneOffset.UTC));
        version.setUpdatedBy(actor);
        return toView(saveVersion(version));
    }

    @Transactional
    public VersionView reject(UUID templateId, UUID versionId, String reason) {
        UUID actor = requireUser();
        TemplateVersion version = requireVersion(templateId, versionId);
        if (!"PROPOSED".equals(version.getStatus())) {
            throw new ApiException.Conflict("state-conflict", "Só versão proposta pode ser rejeitada.");
        }
        // Rejeitar é ato de quem aprovaria: a mesma separação da aprovação.
        if (actor.equals(version.getProposedBy()) || actor.equals(version.getCreatedBy())) {
            throw new ApiException.Forbidden("Quem propôs ou criou não rejeita o próprio modelo.");
        }
        version.setStatus("REJECTED");
        version.setRejectionReason(reason);
        version.setUpdatedBy(actor);
        return toView(versions.save(version));
    }

    // -- geração ----------------------------------------------------------

    /**
     * Gera um documento: versão explícita (aprovada) ou a versão aprovada
     * vigente na data. O texto renderizado é persistido como instantâneo.
     */
    @Transactional
    public GeneratedView generate(UUID templateId, GenerateInput input) {
        UUID tenantId = requireTenant();
        UUID actor = requireUser();
        DocumentTemplate template = requireTemplate(templateId);
        TemplateDocumentType type = TemplateDocumentType.valueOf(template.getDocumentType());
        TemplateVersion version;
        if (input.versionId() != null) {
            version = requireVersion(templateId, input.versionId());
            if (!"APPROVED".equals(version.getStatus())) {
                throw new ApiException.Conflict("state-conflict",
                        "Só versão aprovada gera documento.");
            }
        } else {
            LocalDate date = input.date() == null ? LocalDate.now(ZoneOffset.UTC) : input.date();
            List<TemplateVersion> effective = versions.findApprovedEffectiveOn(tenantId, templateId, date);
            if (effective.isEmpty()) {
                throw new ApiException.NotFound("Nenhuma versão aprovada vigente para este modelo.");
            }
            version = effective.get(0);
        }
        Map<String, String> values = input.values() == null ? Map.of() : input.values();
        String rendered;
        try {
            rendered = TemplateEngine.render(type, parseBlocksJson(version.getBlocks()), values,
                    brandProvider, tenantId);
        } catch (IllegalArgumentException e) {
            throw new ApiException.Validation(e.getMessage(), List.of(), List.of());
        }
        GeneratedDocument doc = new GeneratedDocument();
        doc.setTenantId(tenantId);
        doc.setTenantSchema(TenantContextHolder.getTenant());
        doc.setTemplateId(templateId);
        doc.setTemplateVersionId(version.getId());
        doc.setRenderedBody(rendered);
        doc.setVariablesUsed(Json.write(TemplateEngine.references(TemplateEngine.bodyText(
                parseBlocksJson(version.getBlocks())))));
        doc.setGeneratedBy(actor);
        doc.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
        doc.setCreatedBy(actor);
        doc.setUpdatedBy(actor);
        GeneratedDocument saved = generated.save(doc);
        return new GeneratedView(saved.getId(), saved.getTemplateId(), saved.getTemplateVersionId(),
                saved.getRenderedBody(), parseStringList(saved.getVariablesUsed()), saved.getGeneratedAt());
    }

    @Transactional(readOnly = true)
    public List<GeneratedView> listGenerated(UUID templateId) {
        requireTemplate(templateId);
        return generated.findAllByTenantIdAndTemplateIdOrderByGeneratedAtDesc(requireTenant(), templateId)
                .stream().map(d -> new GeneratedView(d.getId(), d.getTemplateId(), d.getTemplateVersionId(),
                        d.getRenderedBody(), parseStringList(d.getVariablesUsed()), d.getGeneratedAt()))
                .toList();
    }

    // -- apoio ------------------------------------------------------------

    private DocumentTemplate requireTemplate(UUID id) {
        return templates.findByIdAndTenantId(id, requireTenant())
                .orElseThrow(() -> new ApiException.NotFound("Modelo não encontrado."));
    }

    private TemplateVersion requireVersion(UUID templateId, UUID versionId) {
        TemplateVersion version = versions.findByIdAndTenantId(versionId, requireTenant())
                .orElseThrow(() -> new ApiException.NotFound("Versão não encontrada."));
        if (!version.getTemplateId().equals(templateId)) {
            throw new ApiException.NotFound("Versão não encontrada para este modelo.");
        }
        return version;
    }

    private DocumentTemplate saveTemplate(DocumentTemplate template) {
        try {
            // saveAndFlush, não save: o JPA só executa o INSERT no flush, e a
            // violação de unicidade tem de ser mapeada aqui, não no commit.
            return templates.saveAndFlush(template);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException.Conflict("data-conflict", "Já existe um modelo com este código.");
        }
    }

    private TemplateVersion saveVersion(TemplateVersion version) {
        try {
            // saveAndFlush, pelo mesmo motivo: a violação do EXCLUDE de
            // vigência (V1_12) tem de virar 409 aqui, não 500 no commit.
            return versions.saveAndFlush(version);
        } catch (DataIntegrityViolationException e) {
            // Violação do EXCLUDE de vigência (V1_12) ou do número da versão:
            // a tela mostra 409 "vigência sobreposta", não 500 genérico.
            throw new ApiException.Conflict("data-conflict",
                    "A vigência se sobrepõe a outra versão do mesmo modelo.");
        }
    }

    private static TemplateDocumentType parseDocumentType(String raw) {
        try {
            return TemplateDocumentType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw validation("documentType", "Tipo de documento desconhecido: " + raw + ".");
        }
    }

    private static TemplateChannel parseChannel(String raw) {
        try {
            return TemplateChannel.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw validation("channel", "Canal desconhecido: " + raw + ".");
        }
    }

    private static List<TemplateBlock> parseBlocks(List<BlockInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw validation("blocks", "Informe ao menos um bloco de conteúdo.");
        }
        List<TemplateBlock> blocks = new ArrayList<>();
        for (BlockInput input : inputs) {
            if (input.id() == null || input.id().isBlank() || input.text() == null) {
                throw validation("blocks", "Todo bloco precisa de id e texto.");
            }
            TemplateBlock.Kind kind;
            TemplateBlock.Origin origin;
            try {
                kind = TemplateBlock.Kind.valueOf(input.kind() == null ? "FREE" : input.kind());
                origin = TemplateBlock.Origin.valueOf(input.origin() == null ? "TENANT" : input.origin());
            } catch (IllegalArgumentException e) {
                throw validation("blocks", "Bloco com kind ou origin desconhecido.");
            }
            blocks.add(new TemplateBlock(input.id(), kind, origin, input.text()));
        }
        return blocks;
    }

    private static List<TemplateBlock> parseBlocksJson(String json) {
        List<TemplateBlock> blocks = new ArrayList<>();
        JsonNode tree = Json.readTree(json);
        if (tree.isArray()) {
            for (JsonNode node : tree) {
                blocks.add(new TemplateBlock(node.get("id").asString(),
                        TemplateBlock.Kind.valueOf(node.get("kind").asString()),
                        TemplateBlock.Origin.valueOf(node.get("origin").asString()),
                        node.get("text").asString()));
            }
        }
        return blocks;
    }

    private static List<TemplateEngine.DeclaredVariable> parseVariables(List<VariableInput> inputs) {
        List<TemplateEngine.DeclaredVariable> declared = new ArrayList<>();
        if (inputs == null) {
            return declared;
        }
        for (VariableInput input : inputs) {
            if (input.name() == null || input.name().isBlank()) {
                throw validation("variables", "Toda variável declarada precisa de nome.");
            }
            try {
                declared.add(new TemplateEngine.DeclaredVariable(input.name(),
                        br.com.ziipsan.platform.templates.TemplateVariableType.valueOf(input.type())));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw validation("variables", "Variável com tipo desconhecido: " + input.name() + ".");
            }
        }
        return declared;
    }

    private static List<String> parseStringList(String json) {
        List<String> names = new ArrayList<>();
        JsonNode tree = Json.readTree(json);
        if (tree.isArray()) {
            for (JsonNode node : tree) {
                names.add(node.asString());
            }
        }
        return names;
    }

    private static Map<String, String> blockToMap(TemplateBlock block) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", block.id());
        map.put("kind", block.kind().name());
        map.put("origin", block.origin().name());
        map.put("text", block.text());
        return map;
    }

    private TemplateView toView(DocumentTemplate template) {
        return new TemplateView(template.getId(), template.getCode(), template.getTitle(),
                template.getDocumentType(), template.getChannel(), template.getFactoryKey(),
                template.getStatus());
    }

    private VersionView toView(TemplateVersion version) {
        return new VersionView(version.getId(), version.getTemplateId(), version.getVersionNumber(),
                parseBlocksJson(version.getBlocks()).stream()
                        .map(b -> new BlockInput(b.id(), b.kind().name(), b.origin().name(), b.text()))
                        .toList(),
                parseVariablesJson(version.getVariables()), version.getValidFrom(), version.getValidTo(),
                version.getStatus(), version.getProposedBy(), version.getApprovedBy(),
                version.getPreviewRendered(), version.getRejectionReason());
    }

    private static List<VariableInput> parseVariablesJson(String json) {
        List<VariableInput> variables = new ArrayList<>();
        JsonNode tree = Json.readTree(json);
        if (tree.isArray()) {
            for (JsonNode node : tree) {
                variables.add(new VariableInput(node.get("name").asString(), node.get("type").asString()));
            }
        }
        return variables;
    }

    private static ApiException.Validation validation(String field, String message) {
        return ApiException.Validation.of(new ApiException.FieldError(field, "INVALID", message));
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.NotFound("Modelo não encontrado.");
        }
        return tenantId;
    }

    private static UUID requireUser() {
        UUID userId = TenantContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("Usuário autenticado ausente no contexto.");
        }
        return userId;
    }
}
