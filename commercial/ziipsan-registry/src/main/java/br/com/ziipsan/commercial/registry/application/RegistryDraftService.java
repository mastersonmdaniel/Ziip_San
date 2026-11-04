package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.DraftInput;
import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.DraftUpdate;
import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.DraftView;
import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.ValidateStepInput;
import br.com.ziipsan.commercial.registry.domain.RegistryDraft;
import br.com.ziipsan.commercial.registry.repository.RegistryDraftRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Rascunhos do assistente de cadastro no servidor (P1.3, V3_12).
 *
 * <p>Contrato do cliente {@code frontend/src/services/registry/drafts.ts}: POST cria,
 * PUT atualiza com versão (409 = mudou), GET retoma, DELETE descarta, POST
 * validate-step valida UMA etapa sem gravar (200 ok; 422 problem+json com errors[]
 * quando há erro bloqueante; só warnings[] pede confirmação com confirmTokens).
 * A etapa é 0-based, como no wizard do frontend (0 = Localização).
 *
 * <p>O payload é a cópia de trabalho do formulário e pode conter o documento em
 * claro antes da cifra — por isso a tabela nasce SEM trigger de auditoria.
 */
@Service
@Transactional(readOnly = true)
public class RegistryDraftService {

    private final RegistryDraftRepository drafts;

    public RegistryDraftService(RegistryDraftRepository drafts) {
        this.drafts = drafts;
    }

    /** Recurso da rota → resource da tabela. Qualquer outro valor é 400. */
    public static String resourceOf(String path) {
        return switch (path) {
            case "customers" -> "CUSTOMER";
            case "consumer-units" -> "CONSUMER_UNIT";
            default -> throw new ApiException.BadRequest("resource",
                    "Recurso de rascunho desconhecido: " + path + ".");
        };
    }

    @Transactional
    public DraftView create(String path, DraftInput in) {
        RegistryDraft draft = RegistryContext.forCreate(new RegistryDraft());
        draft.setResource(resourceOf(path));
        draft.setTargetId(in.targetId());
        draft.setStep(in.step() == null ? 0 : in.step());
        draft.setPayload(in.data() == null ? "{}" : in.data().toString());
        draft.setStatus("OPEN");
        drafts.saveAndFlush(draft);
        return view(draft);
    }

    @Transactional
    public DraftView update(String path, UUID id, DraftUpdate in) {
        RegistryDraft draft = draft(path, id);
        if (!"OPEN".equals(draft.getStatus())) {
            throw new ApiException.Conflict("draft-closed", "Rascunho já encerrado.");
        }
        RegistryContext.checkVersion(draft, in.version());
        draft.setPayload(in.data() == null ? "{}" : in.data().toString());
        if (in.step() != null) {
            draft.setStep(in.step());
        }
        RegistryContext.touch(draft);
        drafts.saveAndFlush(draft);
        return view(draft);
    }

    public DraftView get(String path, UUID id) {
        return view(draft(path, id));
    }

    /** Descartar: o rascunho vira DISCARDED (não é apagado — o ciclo de vida fica rastreável). */
    @Transactional
    public void discard(String path, UUID id) {
        RegistryDraft draft = draft(path, id);
        draft.setStatus("DISCARDED");
        RegistryContext.touch(draft);
        drafts.saveAndFlush(draft);
    }

    private RegistryDraft draft(String path, UUID id) {
        RegistryDraft draft = RegistryContext.require(drafts.findById(id), () -> "Rascunho não encontrado.");
        if (!resourceOf(path).equals(draft.getResource())
                || !RegistryContext.tenantId().equals(draft.getTenantId())) {
            throw new ApiException.NotFound("Rascunho não encontrado.");
        }
        return draft;
    }

    private static DraftView view(RegistryDraft draft) {
        return new DraftView(draft.getId(), draft.getVersion(), draft.getResource(), draft.getTargetId(),
                draft.getStep(), Json.readTree(draft.getPayload()), draft.getStatus());
    }

    /**
     * Valida UMA etapa sem gravar. As regras espelham o wizard do frontend
     * (PropertyWizard, etapas 0–5) e o formulário do cliente; o bean validation
     * do PUT final continua sendo a validação completa.
     */
    public void validateStep(String path, ValidateStepInput in) {
        Checks checks = new Checks(in.confirmTokens());
        JsonNode data = in.data();
        String resource = resourceOf(path);
        if (data == null || !data.isObject()) {
            checks.error("data", "REQUIRED", "Informe os dados da etapa.");
            checks.throwIfAny();
            return;
        }
        if ("CONSUMER_UNIT".equals(resource)) {
            validateUnitStep(in.step(), data, checks);
        } else {
            validateCustomerStep(in.step(), data, checks);
        }
        checks.throwIfAny();
    }

    private static void validateUnitStep(int step, JsonNode data, Checks checks) {
        switch (step) {
            case 0 -> {
                // Localização: lote/sublote só com quadra; sequência numérica quando vem.
                if (has(data, "blockId") && (!has(data, "lot") || !has(data, "sublot"))) {
                    checks.error("lot", "REQUIRED", "Informe o lote e o sublote da quadra.");
                }
                if (!has(data, "blockId") && (has(data, "lot") || has(data, "sublot"))) {
                    checks.error("blockId", "REQUIRED", "Lote e sublote só existem dentro de uma quadra.");
                }
                checkNonNegativeInt(data, "readingSequence", checks);
                checkNonNegativeDecimal(data, "lotFrontageM", checks);
            }
            case 1 -> {
                if (!has(data, "streetName")) {
                    checks.error("address.streetName", "REQUIRED", "Informe o logradouro.");
                }
                if (!has(data, "number")) {
                    checks.error("address.number", "REQUIRED", "Informe o número.");
                }
                if (has(data, "cep") && !text(data, "cep").matches("\\d{5}-?\\d{3}")) {
                    checks.error("address.cep", "INVALID", "CEP com 8 dígitos.");
                }
            }
            case 2 -> {
                if (!data.hasNonNull("customers") || !data.get("customers").isArray()
                        || data.get("customers").isEmpty()) {
                    checks.error("customers", "REQUIRED", "Informe ao menos um cliente no vínculo.");
                } else {
                    for (int i = 0; i < data.get("customers").size(); i++) {
                        JsonNode link = data.get("customers").get(i);
                        if (!has(link, "customerId")) {
                            checks.error("customers[" + i + "].customerId", "REQUIRED", "Informe o cliente.");
                        }
                        if (!has(link, "startDate")) {
                            checks.error("customers[" + i + "].startDate", "REQUIRED", "Informe o início do vínculo.");
                        }
                    }
                }
            }
            case 3 -> {
                if (!data.hasNonNull("subcategories") || !data.get("subcategories").isArray()
                        || data.get("subcategories").isEmpty()) {
                    checks.error("subcategories", "REQUIRED", "Informe ao menos uma subcategoria.");
                } else {
                    for (int i = 0; i < data.get("subcategories").size(); i++) {
                        JsonNode line = data.get("subcategories").get(i);
                        if (!has(line, "subcategoryId")) {
                            checks.error("subcategories[" + i + "].subcategoryId", "REQUIRED", "Informe a subcategoria.");
                        }
                        checkNonNegativeInt(line, "economies", checks);
                    }
                }
            }
            case 4 -> {
                checkNonNegativeDecimal(data, "builtAreaM2", checks);
                if (has(data, "usagePoints")) {
                    checkNonNegativeInt(data, "usagePoints", checks);
                }
            }
            case 5 -> {
                if (has(data, "notes") && text(data, "notes").length() > 750) {
                    checks.error("notes", "TOO_LONG", "Observações com no máximo 750 caracteres.");
                }
                checkNonNegativeInt(data, "usagePoints", checks);
                checkNonNegativeInt(data, "residents", checks);
            }
            default -> throw new ApiException.BadRequest("step", "Etapa desconhecida: " + step + ".");
        }
    }

    private static void validateCustomerStep(int step, JsonNode data, Checks checks) {
        switch (step) {
            case 0 -> {
                if (!has(data, "name")) {
                    checks.error("name", "REQUIRED", "Informe o nome.");
                }
                if (!has(data, "personType") || !(text(data, "personType").equals("PF")
                        || text(data, "personType").equals("PJ"))) {
                    checks.error("personType", "REQUIRED", "Informe o tipo de pessoa (PF ou PJ).");
                }
                if (text(data, "personType").equals("PF") && has(data, "tradeName")) {
                    checks.error("tradeName", "TRADE_NAME_ON_PF", "Pessoa física não tem nome fantasia.");
                }
            }
            case 1 -> {
                if (!has(data, "document") && !has(data, "documentExemptionReason")
                        && !has(data, "documentExemptionReasonId")) {
                    checks.error("document", "REQUIRED", "Informe o documento ou o motivo da dispensa.");
                }
            }
            case 2 -> {
                if (data.hasNonNull("emails") && data.get("emails").isArray()) {
                    for (int i = 0; i < data.get("emails").size(); i++) {
                        JsonNode email = data.get("emails").get(i);
                        String address = has(email, "address") ? text(email, "address") : text(email, "value");
                        if (!address.matches("(?i)^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                            checks.error("emails[" + i + "]", "INVALID", "E-mail inválido.");
                        }
                    }
                }
            }
            default -> throw new ApiException.BadRequest("step", "Etapa desconhecida: " + step + ".");
        }
    }

    private static boolean has(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.get(field).asText("").isBlank();
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText("") : "";
    }

    private static void checkNonNegativeInt(JsonNode node, String field, Checks checks) {
        if (node.hasNonNull(field) && (!node.get(field).isNumber() || node.get(field).asLong() < 0)) {
            checks.error(field, "INVALID", "Informe um número inteiro maior ou igual a zero.");
        }
    }

    private static void checkNonNegativeDecimal(JsonNode node, String field, Checks checks) {
        if (node.hasNonNull(field) && (!node.get(field).isNumber() || node.get(field).asDouble() < 0)) {
            checks.error(field, "INVALID", "Informe um número maior ou igual a zero.");
        }
    }
}
