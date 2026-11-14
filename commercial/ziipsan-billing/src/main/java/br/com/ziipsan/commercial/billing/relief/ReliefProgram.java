package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Programa de desoneracao de um tenant (DESENHO-PA02 secao 3.2, com o
 * ADENDO-FONTES-PRIMARIAS sobre a NT 40/2026): o contentor auditavel de um
 * acto de desoneracao. Sem programa activo e completo, a guarda de emissao
 * nao tem o que exigir.
 *
 * <p>Semantica dos marcos (P-02-02 RESOLVIDA pela NT, Tabela 2): homologa-se
 * a <b>tabela desonerada</b>; quem homologa e a <b>entidade reguladora
 * infranacional</b> ({@code homologatingRegulator}) — a ANA nao homologa, o
 * acto da Res. 307/2026 e orientativo (F-01 corrigido). Marcos: decisao +
 * publicacao do ato de homologacao ate 15/11/2026
 * ({@code homologationDeadline}); divulgacao aos usuarios ate 02/12/2026
 * ({@code disclosureDeadline}); aplicacao de 01/01/2027
 * ({@code effectiveFrom}). As datas entram pelo cadastro do programa; nao sao
 * default de codigo.
 *
 * <p>Reserva de Fase II (NT 4.7.3/7.3): {@code phaseTwoReserveNote} regista
 * que a medida nao esgota os efeitos da reforma e que a carga tributaria
 * efectiva sera apurada depois (LC 214/2025, arts. 373-377), com compensacao
 * das diferencas. Sem a reserva registada, o programa nao publica — sob pena
 * de a tabela desonerada ser lida como definitiva. Nenhum calculo de Fase II
 * existe neste pacote.
 *
 * <p>Decisoes do dono: Q-D2 = A ({@code provisionalFallbackAllowed} so vale
 * declarado — {@code null} = nao declarado = via desligada); Q-D3 = B com a
 * leitura do adendo de fontes (parametro regista o criterio uniforme do ato;
 * defeito = precisao da tabela vigente); Q-D4 = A (todos os tenants,
 * activacao por cadastro).
 */
public record ReliefProgram(UUID id, String code, String name, String municipalityIbge,
                            String legalBasis, LocalDate effectiveFrom,
                            ReliefProgramStatus status, Boolean provisionalFallbackAllowed,
                            ReliefRoundingPolicy roundingPolicy,
                            LocalDate homologationDeadline, LocalDate disclosureDeadline,
                            String homologatingRegulator, String phaseTwoReserveNote,
                            LocalDate publishedOn, String regulatoryAct) {

    public ReliefProgram {
        Objects.requireNonNull(id, "id do programa e obrigatorio");
        if (code == null || code.isBlank()) {
            throw new TariffConfigurationException("Programa de desoneracao sem codigo");
        }
        if (legalBasis == null || legalBasis.isBlank()) {
            throw new TariffConfigurationException(
                    "Programa de desoneracao sem base legal declarada (programa " + code + ")");
        }
        Objects.requireNonNull(effectiveFrom, "effectiveFrom do programa e obrigatorio");
        Objects.requireNonNull(status, "estado do programa e obrigatorio");
        // roundingPolicy == null e VALIDO e tem semantica propria (correccao
        // R-06 da REVISAO-PA-02): sem politica declarada pelo tenant, o
        // defeito deriva da precisao da tabela de origem no acto da geracao
        // (ReliefRoundingPolicy.tenantDefaultFor) — nunca de constante cega.
        // A constante de 4 casas fica so como recurso quando a origem e
        // indeterminavel.
        if (municipalityIbge != null && !municipalityIbge.matches("\\d{7}")) {
            throw new TariffConfigurationException(
                    "municipalityIbge deve ter 7 digitos: " + municipalityIbge);
        }
    }

    /** Via provisoria (NT 5.3) so com declaracao explicita do tenant (Q-D2 = A). */
    public boolean provisionalFallbackDeclared() {
        return Boolean.TRUE.equals(provisionalFallbackAllowed);
    }

    private ReliefProgram withStatus(ReliefProgramStatus next) {
        return new ReliefProgram(id, code, name, municipalityIbge, legalBasis, effectiveFrom,
                next, provisionalFallbackAllowed, roundingPolicy, homologationDeadline,
                disclosureDeadline, homologatingRegulator, phaseTwoReserveNote,
                publishedOn, regulatoryAct);
    }

    /** Devolve copia do programa no estado seguinte, exigindo transicao valida (secao 7.2). */
    public ReliefProgram transitionTo(ReliefProgramStatus next) {
        status.requireCanTransitionTo(next);
        return withStatus(next);
    }

    /**
     * Publicacao: grava {@code published_on} e o acto declarado. Exige a
     * reserva de Fase II registada (NT 4.7.3) e a reguladora homologante
     * identificada (P-02-02: e a entidade infranacional, nao a ANA).
     */
    public ReliefProgram publish(LocalDate on, String act) {
        status.requireCanTransitionTo(ReliefProgramStatus.PUBLISHED);
        Objects.requireNonNull(on, "data de publicacao e obrigatoria");
        if (act == null || act.isBlank()) {
            throw new TariffConfigurationException(
                    "Publicacao sem acto regulatorio declarado (programa " + code + ")");
        }
        if (phaseTwoReserveNote == null || phaseTwoReserveNote.isBlank()) {
            throw new TariffConfigurationException(
                    "Publicacao sem reserva de Fase II registada (NT 4.7.3): o acto deve "
                            + "consignar que a carga tributaria efectiva sera apurada depois");
        }
        if (homologatingRegulator == null || homologatingRegulator.isBlank()) {
            throw new TariffConfigurationException(
                    "Publicacao sem entidade reguladora infranacional homologante identificada");
        }
        return new ReliefProgram(id, code, name, municipalityIbge, legalBasis, effectiveFrom,
                ReliefProgramStatus.PUBLISHED, provisionalFallbackAllowed, roundingPolicy,
                homologationDeadline, disclosureDeadline, homologatingRegulator,
                phaseTwoReserveNote, on, act);
    }
}
