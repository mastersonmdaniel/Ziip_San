package br.com.ziipsan.commercial.billing.tariff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gestão tarifária (P2.29): reajuste, revisão e cópia como <b>propostas de
 * nova versão</b>, com todas as travas do aceite conferidas antes de a
 * proposta existir. Serviço de domínio puro: não persiste (quem grava é a
 * camada de aplicação, nas tabelas da V4_1/V4_9, onde a EXCLUDE volta a
 * impor a vigência sem sobreposição) e não decide vigência sozinho — a
 * proposta nasce com a vigência pedida e é recusada se ela se sobrepõe a
 * qualquer versão conhecida da mesma tarifa.
 *
 * <p>Fronteiras: tarifa com simulação é desta tarefa, não do console da
 * P1.6 (COE-19) — o console só propõe/aprova parâmetros; a estrutura e a
 * simulação vivem aqui. As versões com vigência produzidas aqui são as que
 * a ED-91 usa no mecanismo excepcional de contingência, sem regra própria
 * nesta tarefa. A metodologia da revisão periódica não entra: revisão aqui
 * é por ato da agência, com os preços do ato.
 *
 * <p>Homologação imposta no fluxo (correção pós-revisão): a homologação
 * tácita <b>não</b> é parâmetro livre do chamador. {@code propose*} recebe
 * os dois fatos da norma da agência (a previsão de homologação tácita e o
 * prazo configurado, lidos dos parâmetros do tenant) e confere o pedido
 * com {@link TariffApprovalRule} antes de a proposta existir — do mesmo
 * modo que o piso de 30 dias da publicação é conferido aqui com
 * {@link TariffPublicationRule}. {@code TACIT} sem previsão normativa, sem
 * prazo, com prazo não decorrido ou com data divergente da consumação
 * (publicação + prazo) é recusado no serviço, nunca gravado.
 */
public final class TariffManagementService {

    /** Proposta pronta: a nova versão e o metadado que a acompanha no banco. */
    public record Proposal(TariffVersion version, VersionChangeMetadata metadata) {
    }

    /**
     * Pedido de reajuste (os fatos do ato/índice, já lidos do parâmetro do tenant).
     *
     * @param tacitApprovalAllowed fato da norma da agência:
     *                             {@code bill.tariff_readjust.tacitApprovalAllowed} —
     *                             sem ele, homologação tácita não existe
     * @param tacitApprovalDays    prazo da homologação tácita:
     *                             {@code tariff.tacit_approval_days}; {@code null} =
     *                             não configurado (homologação tácita recusada)
     */
    public record ReadjustmentRequest(LocalDate baseDate, String indexCode, BigDecimal indexPct,
                                      BigDecimal factorXPct, ReadjustFormula formula,
                                      String regulatoryAct, LocalDate publishedOn,
                                      int publicationMinDays,
                                      boolean tacitApprovalAllowed, Integer tacitApprovalDays) {
    }

    private TariffManagementService() {
    }

    // ------------------------------------------------------------------------------------------
    // Reajuste
    // ------------------------------------------------------------------------------------------

    /**
     * Reajuste por índice/fórmula a partir da data-base: aplica o percentual
     * sobre a estrutura de origem e devolve a nova versão com o método de
     * cálculo registrado (transparência, NR 13).
     *
     * @param existingVersions todas as versões conhecidas da tarifa, para a
     *                         conferência de sobreposição (a EXCLUDE da V4_1
     *                         é a última linha de defesa, no banco)
     */
    public static Proposal proposeReadjustment(TariffVersion source, List<TariffVersion> existingVersions,
                                               UUID newVersionId, LocalDate validFrom, LocalDate validTo,
                                               ReadjustmentRequest request,
                                               TariffApprovalRule.ApprovalMode approvalMode, LocalDate approvedOn) {
        if (request == null) {
            throw new TariffConfigurationException("Reajuste sem os fatos do índice/ato");
        }
        if (request.formula() == null) {
            throw new TariffConfigurationException(
                    "Reajuste sem a fórmula (tariff.readjust_formula): a fórmula não se presume");
        }
        TariffPublicationRule.validate(request.publishedOn(), validFrom, request.publicationMinDays());
        LocalDate validatedApprovedOn = validateApproval(approvalMode, approvedOn, request.publishedOn(),
                request.tacitApprovalAllowed(), request.tacitApprovalDays());
        validateNoOverlap(existingVersions, validFrom, validTo);
        BigDecimal appliedPct = request.formula().appliedPct(request.indexPct(), request.factorXPct());
        TariffVersion version = ReadjustmentCalculator.apply(source, newVersionId, validFrom, validTo,
                request.formula(), request.indexPct(), request.factorXPct());
        String method = readjustmentMethod(request, appliedPct);
        VersionChangeMetadata metadata = new VersionChangeMetadata(
                TariffVersionChangeType.READJUSTMENT, source.id(), request.baseDate(), request.indexCode(),
                request.indexPct(), request.factorXPct(), appliedPct, request.formula(),
                request.regulatoryAct(), request.publishedOn(), approvalMode, validatedApprovedOn, method);
        return new Proposal(version, metadata);
    }

    // ------------------------------------------------------------------------------------------
    // Revisão por ato da agência
    // ------------------------------------------------------------------------------------------

    /**
     * Revisão: a agência fixa os preços no ato; a proposta é a estrutura com
     * as regras do ato ({@code revisedRules} e, se o ato mudar o benefício,
     * {@code revisedSocialRule}), sob a vigência do ato. Sem ato não há
     * revisão ({@link VersionChangeMetadata} recusa).
     */
    public static Proposal proposeReview(TariffVersion source, List<TariffVersion> existingVersions,
                                         UUID newVersionId, LocalDate validFrom, LocalDate validTo,
                                         List<CategoryRule> revisedRules, SocialTariffRule revisedSocialRule,
                                         String regulatoryAct, LocalDate publishedOn, int publicationMinDays,
                                         String calculationMethod,
                                         TariffApprovalRule.ApprovalMode approvalMode, LocalDate approvedOn,
                                         boolean tacitApprovalAllowed, Integer tacitApprovalDays) {
        TariffPublicationRule.validate(publishedOn, validFrom, publicationMinDays);
        LocalDate validatedApprovedOn = validateApproval(approvalMode, approvedOn, publishedOn,
                tacitApprovalAllowed, tacitApprovalDays);
        validateNoOverlap(existingVersions, validFrom, validTo);
        if (source == null) {
            throw new TariffConfigurationException("Revisão sem versão de origem");
        }
        TariffVersion version = new TariffVersion(newVersionId, validFrom, validTo, revisedRules,
                revisedSocialRule);
        VersionChangeMetadata metadata = new VersionChangeMetadata(
                TariffVersionChangeType.REVIEW, source.id(), null, null, null, null, null, null,
                regulatoryAct, publishedOn, approvalMode, validatedApprovedOn, calculationMethod);
        return new Proposal(version, metadata);
    }

    // ------------------------------------------------------------------------------------------
    // Cópia
    // ------------------------------------------------------------------------------------------

    /** Cópia da estrutura existente como ponto de partida da nova versão. */
    public static Proposal proposeCopy(TariffVersion source, List<TariffVersion> existingVersions,
                                       UUID newVersionId, LocalDate validFrom, LocalDate validTo) {
        validateNoOverlap(existingVersions, validFrom, validTo);
        TariffVersion version = TariffVersionCopier.copy(source, newVersionId, validFrom, validTo);
        VersionChangeMetadata metadata = new VersionChangeMetadata(
                TariffVersionChangeType.COPY, source.id(), null, null, null, null, null, null,
                null, null, null, null,
                "COPIA da versao " + source.id() + " sem alteracao de precos: ponto de partida da nova versao.");
        return new Proposal(version, metadata);
    }

    // ------------------------------------------------------------------------------------------
    // Regras comuns
    // ------------------------------------------------------------------------------------------

    /**
     * Homologação conferida dentro do fluxo de proposta, com
     * {@link TariffApprovalRule} — a data de homologação não é aceita como
     * fato livre do chamador:
     * <ul>
     *   <li>{@code TACIT}: a regra exige os dois fatos (previsão na norma da
     *       agência e prazo configurado) e o prazo decorrido; a data
     *       informada ainda precisa ser <b>exatamente</b> a data da
     *       consumação devolvida pela regra (publicação + prazo) — qualquer
     *       outra data é homologação fabricada e é recusada;</li>
     *   <li>{@code EXPRESS}: quando a data do ato é informada, a regra a
     *       valida (não pode anteceder a publicação). Proposta expressa
     *       ainda sem ato ({@code approvedOn == null}) continua
     *       representável: é o estágio de proposta, sem homologação
     *       alegada — decisão registrada na revisão da P2.29;</li>
     *   <li>sem modo ({@code approvalMode == null}) não pode haver data:
     *       data sem modo é homologação sem fato e é recusada.</li>
     * </ul>
     *
     * @return a data de homologação validada (a da consumação, no caso tácito)
     */
    private static LocalDate validateApproval(TariffApprovalRule.ApprovalMode approvalMode, LocalDate approvedOn,
                                              LocalDate publishedOn,
                                              boolean tacitApprovalAllowed, Integer tacitApprovalDays) {
        if (approvalMode == null) {
            if (approvedOn != null) {
                throw new TariffConfigurationException(
                        "Data de homologação (" + approvedOn + ") sem o modo de homologação: "
                                + "homologação sem fato não se registra");
            }
            return null;
        }
        return switch (approvalMode) {
            case EXPRESS -> approvedOn == null ? null
                    : TariffApprovalRule.expressApprovalDate(publishedOn, approvedOn);
            case TACIT -> {
                // approvedOn entra como a data da consulta: a regra recusa sem
                // previsão normativa, sem prazo e com o prazo não decorrido.
                LocalDate consummatedOn = TariffApprovalRule.tacitApprovalDate(
                        tacitApprovalAllowed, publishedOn, tacitApprovalDays, approvedOn);
                if (!consummatedOn.equals(approvedOn)) {
                    throw new TariffConfigurationException(
                            "Homologação tácita fabricada recusada: a data informada (" + approvedOn
                                    + ") diverge da data da consumação (" + consummatedOn
                                    + " = publicação " + publishedOn + " + prazo de " + tacitApprovalDays
                                    + " dias)");
                }
                yield consummatedOn;
            }
        };
    }

    /**
     * Sem sobreposição de vigência, espelho da EXCLUDE da V4_1
     * ({@code excl_tariff_versions_no_overlap}): intervalos fechados
     * {@code [validFrom, validTo]} da mesma tarifa não se tocam; fim aberto
     * colide com qualquer início posterior.
     */
    public static void validateNoOverlap(List<TariffVersion> existingVersions,
                                         LocalDate validFrom, LocalDate validTo) {
        if (validFrom == null || (validTo != null && validTo.isBefore(validFrom))) {
            throw new TariffConfigurationException("Vigência proposta inválida: " + validFrom + " a " + validTo);
        }
        LocalDate newEnd = validTo == null ? LocalDate.MAX : validTo;
        for (TariffVersion existing : existingVersions == null ? List.<TariffVersion>of() : existingVersions) {
            LocalDate existingEnd = existing.validTo() == null ? LocalDate.MAX : existing.validTo();
            if (!validFrom.isAfter(existingEnd) && !existing.validFrom().isAfter(newEnd)) {
                throw new TariffConfigurationException(
                        "Vigência proposta " + validFrom + " a " + validTo + " se sobrepõe à versão "
                                + existing.id() + " (" + existing.validFrom() + " a " + existing.validTo()
                                + "): feche a vigência anterior antes de abrir a nova (EXCLUDE da V4_1)");
            }
        }
    }

    /** Método de cálculo do reajuste, determinístico, mantido com a versão (NR 13, arts. 61 e 62). */
    static String readjustmentMethod(ReadjustmentRequest request, BigDecimal appliedPct) {
        StringBuilder sb = new StringBuilder("REAJUSTE ");
        sb.append(request.formula() == ReadjustFormula.INDEX_ONLY
                ? "POR INDICE" : "PARAMETRICO (INDICE - FATOR X)");
        sb.append(": preco_novo = preco_base x (1 + percentual_aplicado/100), arredondamento HALF_UP a 4 casas;");
        sb.append(" indice ").append(request.indexCode())
                .append(" = ").append(request.indexPct()).append("%")
                .append(" acumulado desde a data-base ").append(request.baseDate()).append(";");
        if (request.formula() == ReadjustFormula.INDEX_MINUS_FACTOR_X) {
            sb.append(" fator X = ").append(request.factorXPct()).append("%;");
        }
        sb.append(" percentual aplicado = ").append(appliedPct).append("%;");
        sb.append(" percentuais da tarifa social e consumo minimo nao sao reajustados (nao sao precos).");
        if (request.regulatoryAct() != null && !request.regulatoryAct().isBlank()) {
            sb.append(" Ato: ").append(request.regulatoryAct()).append(".");
        }
        return sb.toString();
    }
}
