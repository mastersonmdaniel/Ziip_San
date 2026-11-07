package br.com.ziipsan.commercial.registry.service;

import br.com.ziipsan.core.audit.AuditLog;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.security.crypto.FieldProtector;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ED-12: RG e filiação do cliente (pessoa física) protegidos do mesmo jeito
 * que o CPF/CNPJ da P0.11/P1.3 — cifrados por tenant (AES-256-GCM), com
 * índice cego para o RG e máscara para exibição — e com a FINALIDADE do
 * tratamento registrada (LGPD, art. 6º, I e art. 37): nenhum dos dois campos
 * é gravado nem revelado sem uma finalidade declarada do registro fechado
 * abaixo, e cada gravação e cada revelação grava a finalidade na trilha de
 * auditoria, sem o valor.
 *
 * <p>Por que um serviço separado do {@code CustomerDocumentService}: o
 * contexto da cifra é por campo ({@code customers.rg},
 * {@code customers.filiation}); um valor protegido num campo não decifra nem
 * casa no outro, nem no documento. A filiação é cifrada como UM valor (o
 * conjunto dos nomes, em JSON pela classe {@code Json} do core): revelar um
 * nome só não é uma operação que exista.
 *
 * <p>Validação do RG: o RG não tem dígito verificador nacional único (cada
 * UF emite com formato próprio), então a validação aqui é de FORMA —
 * normalizado (sem pontuação, maiúsculas), de 5 a 20 caracteres
 * alfanuméricos. Declarar um DV que não existe seria inventar regra; a
 * conferência contra o documento físico é ato do atendimento, fora do
 * código. A filiação aceita 1 ou 2 nomes (a certidão pode trazer um só).
 *
 * <p>Persistência: este serviço é agnóstico de colunas, como o
 * {@code FieldProtector} — ele devolve as formas protegidas para o chamador
 * gravar. As colunas em {@code customers} dependem de migration nova, e o
 * mapa Flyway do plano (§3) NÃO reserva versão para a ED-12: a fiação na
 * entidade {@code Customer} fica declarada no relatório da entrega, para
 * entrar com a versão que o plano reservar. Sem migration, nada aqui finge
 * gravar no banco.
 */
public class CustomerIdentityService {

    /** Contexto da cifra e do índice do RG. */
    public static final String RG_FIELD = "customers.rg";
    /** Contexto da cifra da filiação (sem índice cego: filiação não é chave de busca). */
    public static final String FILIATION_FIELD = "customers.filiation";

    /**
     * Finalidade registrada de um tratamento (LGPD): código estável, base
     * legal e descrição. Código fora deste registro é recusado — finalidade
     * em texto livre não é finalidade registrada.
     */
    public record TreatmentPurpose(String code, String legalBasis, String description) {
    }

    /** Identificação civil do titular no cadastro comercial (o RG é o documento de identidade). */
    public static final TreatmentPurpose PURPOSE_IDENTIFICATION = new TreatmentPurpose(
            "CUSTOMER_IDENTIFICATION", "LGPD art. 7, V (execucao de contrato)",
            "Identificacao civil do titular no cadastro comercial e no atendimento.");
    /** Conferência de identidade em ato sensível (troca de titularidade, contestação). */
    public static final TreatmentPurpose PURPOSE_IDENTITY_CHECK = new TreatmentPurpose(
            "IDENTITY_VERIFICATION", "LGPD art. 7, II e V",
            "Conferencia de identidade do titular em ato cadastral ou de atendimento.");
    /** Prevenção a fraude cadastral (fins legítimos do controlador, com minimização). */
    public static final TreatmentPurpose PURPOSE_FRAUD_PREVENTION = new TreatmentPurpose(
            "FRAUD_PREVENTION", "LGPD art. 7, IX (interesse legitimo)",
            "Prevencao a fraude cadastral, com acesso restrito e registrado.");

    /** Registro fechado das finalidades aceitas para RG e filiação. */
    public static final List<TreatmentPurpose> PURPOSES = List.of(
            PURPOSE_IDENTIFICATION, PURPOSE_IDENTITY_CHECK, PURPOSE_FRAUD_PREVENTION);

    /** Formas persistidas do RG: cifra, índice cego (busca/unicidade) e máscara. */
    public record ProtectedRg(String ciphertext, String hash, String masked, String purposeCode) {
    }

    /** Formas persistidas da filiação: cifra e máscara (sem índice: não é chave de busca). */
    public record ProtectedFiliation(String ciphertext, String masked, String purposeCode) {
    }

    private final FieldProtector protector;
    private final AuditLogRepository auditLog;

    public CustomerIdentityService(FieldProtector protector, AuditLogRepository auditLog) {
        this.protector = protector;
        this.auditLog = auditLog;
    }

    // ------------------------------------------------------------------ RG

    /**
     * Normaliza o RG: sem pontuação nem espaços, maiúsculas. Só a forma é
     * validada (ver o javadoc da classe); RG fora da forma é
     * {@link IllegalArgumentException}, nunca gravado.
     */
    public static String normalizeRg(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("RG vazio");
        }
        String normalized = raw.replaceAll("[^0-9A-Za-z]", "").toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() < 5 || normalized.length() > 20
                || !normalized.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("RG fora da forma esperada (5 a 20 caracteres alfanumericos)");
        }
        return normalized;
    }

    /** Máscara do RG: só os 2 últimos caracteres à mostra (teto de exibição, ADR-0008 por analogia). */
    public static String maskRg(String normalizedRg) {
        if (normalizedRg == null || normalizedRg.length() < 5) {
            throw new IllegalArgumentException("RG vazio");
        }
        return "***" + normalizedRg.substring(normalizedRg.length() - 2);
    }

    /**
     * Protege o RG para gravação e registra a finalidade na auditoria
     * (entidade {@code customers}, campo, finalidade — sem o valor).
     */
    public ProtectedRg protectRg(UUID customerId, String rawRg, String purposeCode) {
        UUID tenantId = requireTenant();
        TreatmentPurpose purpose = requirePurpose(purposeCode);
        String normalized = normalizeRg(rawRg);
        var value = protector.protect(tenantId, RG_FIELD, normalized, maskRg(normalized));
        recordAccess(customerId, RG_FIELD, purpose, AuditLog.AuditAction.CREATE);
        return new ProtectedRg(value.ciphertext(), value.hash(), value.masked(), purpose.code());
    }

    /** Índice cego do RG para busca sem decifrar nada. RG fora da forma: erro explícito. */
    public String rgIndex(String rawRg) {
        UUID tenantId = requireTenant();
        return protector.indexOf(tenantId, RG_FIELD, normalizeRg(rawRg));
    }

    /**
     * RG em claro (normalizado). Exige finalidade do registro e grava o
     * acesso na auditoria antes de devolver, como o reveal do documento.
     */
    public String revealRg(UUID customerId, String ciphertext, String purposeCode) {
        UUID tenantId = requireTenant();
        TreatmentPurpose purpose = requirePurpose(purposeCode);
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalStateException("cliente sem RG cadastrado");
        }
        String plain = protector.reveal(tenantId, RG_FIELD, ciphertext);
        recordAccess(customerId, RG_FIELD, purpose, AuditLog.AuditAction.READ);
        return plain;
    }

    // ------------------------------------------------------------------ filiação

    /**
     * Normaliza os nomes da filiação: 1 ou 2 nomes, sem espaços duplicados,
     * cada um de 2 a 200 caracteres. Lista fora disso é erro explícito.
     */
    public static List<String> normalizeFiliation(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty() || rawNames.size() > 2) {
            throw new IllegalArgumentException("filiacao deve ter 1 ou 2 nomes");
        }
        List<String> names = rawNames.stream()
                .map(n -> n == null ? "" : n.trim().replaceAll("\\s+", " "))
                .toList();
        for (String name : names) {
            if (name.length() < 2 || name.length() > 200) {
                throw new IllegalArgumentException("nome da filiacao fora da forma esperada (2 a 200 caracteres)");
            }
        }
        return names;
    }

    /** Máscara da filiação: primeiro nome de cada genitor, resto oculto. Ex.: "Maria ***; Jose ***". */
    public static String maskFiliation(List<String> normalizedNames) {
        return String.join("; ", normalizedNames.stream().map(name -> {
            int space = name.indexOf(' ');
            return (space < 0 ? name : name.substring(0, space)) + " ***";
        }).toList());
    }

    /**
     * Protege a filiação como um valor só (JSON pela classe {@code Json} do
     * core, cifrado no contexto próprio) e registra a finalidade.
     */
    public ProtectedFiliation protectFiliation(UUID customerId, List<String> rawNames, String purposeCode) {
        UUID tenantId = requireTenant();
        TreatmentPurpose purpose = requirePurpose(purposeCode);
        List<String> names = normalizeFiliation(rawNames);
        String payload = Json.write(Map.of("names", names));
        var value = protector.protect(tenantId, FILIATION_FIELD, payload, maskFiliation(names));
        recordAccess(customerId, FILIATION_FIELD, purpose, AuditLog.AuditAction.CREATE);
        return new ProtectedFiliation(value.ciphertext(), value.masked(), purpose.code());
    }

    /** Filiação em claro (os nomes, na ordem gravada). Exige finalidade e registra o acesso. */
    public List<String> revealFiliation(UUID customerId, String ciphertext, String purposeCode) {
        UUID tenantId = requireTenant();
        TreatmentPurpose purpose = requirePurpose(purposeCode);
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalStateException("cliente sem filiacao cadastrada");
        }
        String payload = protector.reveal(tenantId, FILIATION_FIELD, ciphertext);
        recordAccess(customerId, FILIATION_FIELD, purpose, AuditLog.AuditAction.READ);
        Object names = Json.readMap(payload).get("names");
        if (!(names instanceof List<?> list)) {
            throw new IllegalStateException("filiacao gravada em formato inesperado");
        }
        return list.stream().map(String::valueOf).toList();
    }

    // ------------------------------------------------------------------ comuns

    /** Finalidade do registro fechado, pelo código. Fora do registro: erro explícito. */
    public static TreatmentPurpose requirePurpose(String purposeCode) {
        if (purposeCode != null) {
            for (TreatmentPurpose purpose : PURPOSES) {
                if (purpose.code().equals(purposeCode.trim())) {
                    return purpose;
                }
            }
        }
        throw new IllegalArgumentException("finalidade nao registrada para RG/filiacao: " + purposeCode);
    }

    private void recordAccess(UUID customerId, String field, TreatmentPurpose purpose,
                              AuditLog.AuditAction action) {
        AuditLog access = new AuditLog();
        access.setTenantId(requireTenant());
        access.setEntityType("customers");
        access.setEntityId(customerId);
        access.setAction(action);
        access.setUserId(TenantContextHolder.getUserId());
        access.setMetadata(Json.write(Map.of(
                "field", field, "purpose", purpose.code(), "legalBasis", purpose.legalBasis())));
        auditLog.save(access);
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }
}
