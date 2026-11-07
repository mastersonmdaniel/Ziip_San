package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.platform.console.PlatformConsoleRules;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.ProfileValue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras puras do envelope de ativação de um perfil (P2.31 sobre a P0.19):
 * o que uma proposta precisa conter, o hash de conteúdo que o aceite
 * confere e as travas herdadas de {@link ParameterVersionRule} (quem
 * propõe não aprova; efeito só futuro; aceite com o hash da versão).
 *
 * <p>Nada aqui persiste: o serviço JDBC
 * ({@code RegulatoryProfileActivationService}) aplica estas regras dentro
 * da transação e grava em {@code regulatory_profile_activations},
 * {@code parameter_versions} e {@code parameter_acceptances} da V1_5/V1_13.
 */
public final class RegulatoryActivationRule {

    private RegulatoryActivationRule() {
    }

    /** Uma versão de parâmetro planejada pela ativação (ainda não gravada). */
    public record PlannedVersion(String definitionKey, String valueJson, LocalDate validFrom,
                                 LocalDate validTo, String contentHash, String normCitation) {
    }

    /**
     * Hash do conteúdo canônico da versão (chave + valor + vigência):
     * delega a {@link PlatformConsoleRules#contentHash}, a MESMA função
     * que o {@code TenantParameterService} (P1.6) usa ao propor — o aceite
     * da ativação precisa conferir com o hash que a tela de parâmetros
     * reconhece, e duas fórmulas de hash no mesmo módulo se separariam.
     * O valor é canonizado pela árvore JSON (como no serviço) antes do hash.
     */
    public static String contentHash(String definitionKey, String valueJson,
                                     LocalDate validFrom, LocalDate validTo) {
        String canonicalValue = Json.readTree(valueJson).toString();
        return PlatformConsoleRules.contentHash(definitionKey, canonicalValue, validFrom, validTo);
    }

    /**
     * Planeja as versões da ativação: um {@link PlannedVersion} por valor
     * confirmado do perfil e por valor que o tenant informou para as chaves
     * que exigem valor dele. Ausência confirmada
     * ({@code CONFIRMED_ABSENT}) não gera versão: ela é o registro de que a
     * regra não existe naquela ERI, e consta do perfil aplicado.
     *
     * <p>Contrato dos valores do tenant (correção pós-revisão — antes,
     * valor para chave confirmada era ignorado EM SILÊNCIO):
     * <ul>
     *   <li>chave {@code REQUIRES_TENANT_VALUE}: o valor do tenant é o
     *       valor da versão (exigido; faltando, a ativação é recusada
     *       nomeando as chaves);</li>
     *   <li>chave {@code CONFIRMED} de {@code RegulatoryLoadGuard.TENANT_COMPLETED_KEYS}
     *       (hoje só {@code bill.due_date_options}): o perfil confirma só o
     *       requisito (o mínimo de datas); a lista concreta de dias é do
     *       tenant, é exigida, é validada contra o mínimo confirmado e é
     *       ELA que vira versão — no formato {@code LIST_INT} que o
     *       consumidor da P2.5 lê. O {@code {"minCount":N}} do perfil
     *       nunca vira versão;</li>
     *   <li>qualquer outra chave {@code CONFIRMED}, e qualquer chave
     *       {@code CONFIRMED_ABSENT}: valor do tenant é RECUSADO
     *       explicitamente, nomeando a chave — o valor (ou a ausência)
     *       vem do perfil confirmado, e divergir dele é outro fluxo
     *       (proposta avulsa de parâmetro, com o envelope próprio),
     *       nunca um desvio silencioso da ativação do perfil.</li>
     * </ul>
     *
     * @param tenantValues valores informados pelo tenant: as chaves
     *                     {@code REQUIRES_TENANT_VALUE} do perfil e a
     *                     lista concreta de dias de
     *                     {@code bill.due_date_options} (JSON por chave)
     * @throws IllegalArgumentException se faltar valor do tenant, se vier
     *                                  chave fora do perfil, se o tenant
     *                                  tentar valor para chave confirmada
     *                                  ou de ausência confirmada, ou se um
     *                                  valor (do perfil ou do tenant)
     *                                  violar a guarda de carga
     */
    public static List<PlannedVersion> planVersions(RegulatoryProfileDef profile,
                                                    Map<String, String> tenantValues,
                                                    LocalDate validFrom, LocalDate validTo) {
        if (profile == null || validFrom == null) {
            throw new IllegalArgumentException("profile e validFrom são obrigatórios");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("valid_to anterior a valid_from na ativação do perfil "
                    + profile.profileKey());
        }
        RegulatoryLoadGuard.validateProfile(profile);
        Map<String, String> supplied = tenantValues == null ? Map.of() : tenantValues;

        for (Map.Entry<String, String> entry : supplied.entrySet()) {
            String key = entry.getKey();
            ProfileValue profileValue = profile.value(key);
            if (profileValue == null) {
                throw new IllegalArgumentException("Valor do tenant para a chave " + key
                        + ", que não pertence ao perfil " + profile.profileKey());
            }
            // Recusa explícita (nada de ignorar em silêncio): valor do
            // tenant só entra onde o perfil pede — REQUIRES_TENANT_VALUE
            // ou o completamento das chaves de TENANT_COMPLETED_KEYS.
            switch (profileValue.confirmation()) {
                case CONFIRMED -> {
                    if (!RegulatoryLoadGuard.TENANT_COMPLETED_KEYS.contains(key)) {
                        throw new IllegalArgumentException("Ativação do perfil "
                                + profile.profileKey() + " recusada: o tenant informou valor "
                                + "para a chave " + key + ", confirmada pelo perfil — na "
                                + "ativação o valor vem do perfil, não do tenant (para "
                                + "divergir, use a proposta avulsa de parâmetro, com o "
                                + "envelope próprio da P0.19)");
                    }
                }
                case CONFIRMED_ABSENT -> throw new IllegalArgumentException("Ativação do perfil "
                        + profile.profileKey() + " recusada: o tenant informou valor para a "
                        + "chave " + key + ", cuja AUSÊNCIA foi confirmada no texto da ERI "
                        + "— a ativação do perfil não cria valor para regra que a ERI não "
                        + "tem (para criar, use a proposta avulsa de parâmetro, com o "
                        + "envelope próprio da P0.19)");
                case REQUIRES_TENANT_VALUE -> {
                    // Caminho normal: o valor do tenant é o valor da versão.
                }
            }
        }

        List<PlannedVersion> planned = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (ProfileValue value : profile.values()) {
            switch (value.confirmation()) {
                case CONFIRMED -> {
                    if (RegulatoryLoadGuard.TENANT_COMPLETED_KEYS.contains(value.definitionKey())) {
                        // O requisito (minCount) é do perfil; a versão é a
                        // lista concreta de dias do tenant, validada contra
                        // o mínimo confirmado — legível pelo billing.
                        String tenantValue = supplied.get(value.definitionKey());
                        if (tenantValue == null || tenantValue.isBlank()) {
                            int minCount = RegulatoryLoadGuard.dueDateMinCount(
                                    profile.profileKey(), value.valueJson());
                            missing.add(value.definitionKey() + " (dias concretos de "
                                    + "vencimento: o perfil confirma o mínimo de " + minCount
                                    + " datas, mas os dias são escolha do tenant)");
                        } else {
                            List<Integer> days = RegulatoryLoadGuard.dueDateDays(tenantValue);
                            int minCount = RegulatoryLoadGuard.dueDateMinCount(
                                    profile.profileKey(), value.valueJson());
                            if (days.size() < minCount) {
                                throw new IllegalArgumentException("Ativação do perfil "
                                        + profile.profileKey() + " recusada: "
                                        + "bill.due_date_options com " + days.size()
                                        + " dias abaixo do mínimo de " + minCount
                                        + " datas confirmado pelo perfil");
                            }
                            planned.add(planned(profile, value.definitionKey(), tenantValue,
                                    value, validFrom, validTo));
                        }
                    } else {
                        planned.add(planned(profile, value.definitionKey(),
                                value.valueJson(), value, validFrom, validTo));
                    }
                }
                case CONFIRMED_ABSENT -> {
                    // Sem versão: a ausência é o dado (ver javadoc da classe).
                }
                case REQUIRES_TENANT_VALUE -> {
                    String tenantValue = supplied.get(value.definitionKey());
                    if (tenantValue == null || tenantValue.isBlank()) {
                        missing.add(value.definitionKey());
                    } else {
                        RegulatoryLoadGuard.assertValueLoadable(value.definitionKey(), tenantValue);
                        planned.add(planned(profile, value.definitionKey(), tenantValue, value,
                                validFrom, validTo));
                    }
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Ativação do perfil " + profile.profileKey()
                    + " recusada: faltam os valores que o tenant precisa informar para "
                    + String.join(", ", missing)
                    + " (o texto da ERI não os confirmou; não há valor de fábrica)");
        }
        return List.copyOf(planned);
    }

    private static PlannedVersion planned(RegulatoryProfileDef profile, String definitionKey,
                                          String valueJson, ProfileValue origin,
                                          LocalDate validFrom, LocalDate validTo) {
        // Valor canonizado pela arvore JSON, como o TenantParameterService
        // faz ao propor: o que se grava e o que o hash cobre sao o mesmo texto.
        String canonical = Json.readTree(valueJson).toString();
        String citation = origin.sourceAct() == null ? null
                : origin.sourceArticle() == null
                        ? origin.sourceAct()
                        : origin.sourceAct() + ", " + origin.sourceArticle();
        return new PlannedVersion(definitionKey, canonical, validFrom, validTo,
                contentHash(definitionKey, canonical, validFrom, validTo), citation);
    }

    /** Quem propõe não aprova; efeito só futuro (regra da P0.19, sem duplicar a trava). */
    public static void validateApproval(UUID proposedBy, UUID approvedBy,
                                        LocalDate approvedOn, LocalDate validFrom) {
        ParameterVersionRule.validateApproval(proposedBy, approvedBy, approvedOn, validFrom);
    }

    /** O aceite só vale para a versão cujo hash ele registra (regra da P0.19). */
    public static void validateAcceptanceHash(String versionContentHash, String acceptanceContentHash) {
        ParameterVersionRule.validateAcceptanceHash(versionContentHash, acceptanceContentHash);
    }
}
