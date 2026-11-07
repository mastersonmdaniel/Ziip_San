package br.com.ziipsan.commercial.billing.social;

import java.time.LocalDate;
import java.util.UUID;

/**
 * ED-57: conferencia e vigencia de cada beneficio da lista de beneficiarios
 * de programa social, sobre a regra da P1.5 - sem duplicar nenhuma regra
 * dela:
 * <ul>
 *   <li>elegibilidade (inclusive CadUnico vencido): {@link SocialEligibility};</li>
 *   <li>classificacao automatica e desempate de uma economia por familia:
 *       {@link SocialClassification} e {@link SocialFamilyTiebreak} - esta
 *       classe recebe o candidato que a classificacao ja produziu;</li>
 *   <li>aviso de perda e graca antes do fim: {@link SocialEnrollmentLifecycle}.</li>
 * </ul>
 *
 * <p>O que esta classe acrescenta e so o que a ED-57 pede e a P1.5 nao tem:
 * a vigencia declarada do beneficio na lista (de/atual) conferida contra a
 * data de referencia, a base legal registrada da lista carregada para a
 * inscricao, e o rascunho de inscricao que o servico aplicador grava em
 * {@code social_tariff_enrollments} (V4_2) com a OS de lote do importador.
 * Nenhum metodo aqui recebe situacao de pagamento: a concessao nao depende
 * de adimplencia (Lei 14.898/2024), como na P1.5.
 *
 * <p>Semantica da data normativa (achado A2 da revisao, sem ambiguidade):
 * o rascunho de inscricao nasce {@code ACTIVE} - ele e a inscricao pronta
 * para o servico aplicador gravar, nao um documento de preparacao. Por
 * isso {@code draftEnrollment} exige a conferencia integralmente
 * aplicavel ({@link Conference#applicable()}): elegivel, dentro da
 * vigencia declarada E sob a regra legal vigente. Antes de 11/12/2026 a
 * regra legal ainda nao se aplica (Lei 14.898/2024; NR 13, art. 26,
 * paragrafo 1o) e o rascunho e RECUSADO, como o inelegivel e o fora da
 * vigencia: a lista pode ser importada, conferida e classificada antes
 * da data (a conferencia responde normalmente), mas inscricao - nem em
 * rascunho - nao nasce antes da vigencia da regra que a cria. Se um dia
 * o dono quiser preparacao pre-vigencia, isso e um modo novo e explicito
 * a decidir, nao um efeito colateral deste metodo.
 */
public final class SocialBenefitConference {

    /** Vigencia declarada do beneficio na lista. {@code validTo} nulo = sem fim declarado. */
    public record BenefitValidity(LocalDate validFrom, LocalDate validTo) {
        public BenefitValidity {
            if (validFrom == null) {
                throw new IllegalArgumentException("Inicio da vigencia do beneficio e obrigatorio");
            }
            if (validTo != null && validTo.isBefore(validFrom)) {
                throw new IllegalArgumentException("Fim da vigencia do beneficio anterior ao inicio");
            }
        }

        /** O beneficio cobre a data (inicio e fim declarados contam como cobertos)? */
        public boolean covers(LocalDate referenceDate) {
            if (referenceDate == null) {
                throw new IllegalArgumentException("Data de referencia e obrigatoria");
            }
            return !referenceDate.isBefore(validFrom) && (validTo == null || !referenceDate.isAfter(validTo));
        }
    }

    /** Metadados da lista recebida: origem (PLT-36), base legal registrada e recebimento. */
    public record ListMetadata(SocialProgramListPolicy.ListSource listSource, String legalBasis,
                               String layoutVersion, LocalDate receivedOn) {
        public ListMetadata {
            if (listSource == null) {
                throw new IllegalArgumentException("Origem da lista e obrigatoria");
            }
            if (legalBasis == null || legalBasis.isBlank()) {
                throw new IllegalArgumentException("Base legal da lista e obrigatoria (lista sem base"
                        + " legal registrada nao entra)");
            }
            if (legalBasis.length() > 200) {
                throw new IllegalArgumentException("Base legal acima de 200 caracteres (limite da"
                        + " coluna legal_basis da inscricao, V4_2)");
            }
            if (layoutVersion == null || layoutVersion.isBlank()) {
                throw new IllegalArgumentException("Versao do leiaute da lista e obrigatoria");
            }
            if (receivedOn == null) {
                throw new IllegalArgumentException("Data de recebimento da lista e obrigatoria");
            }
        }
    }

    /** Resultado da conferencia de um beneficio na data de referencia. */
    public record Conference(boolean eligible, boolean withinValidity, boolean legalRuleApplicable) {

        /** Beneficio conferido e aplicavel: elegivel, dentro da vigencia e sob a regra legal vigente. */
        public boolean applicable() {
            return eligible && withinValidity && legalRuleApplicable;
        }
    }

    /**
     * Rascunho da inscricao a gravar em {@code social_tariff_enrollments} a
     * partir de um candidato da classificacao da P1.5. {@code status} inicial
     * ACTIVE e {@code enrollmentSource} OFFICIAL_LIST sao os dominios da V4_2
     * para a classificacao automatica pela lista; {@code socialEconomies} e a
     * economia beneficiada da familia na unidade (uma por familia, ja
     * desempatada pela classificacao). A gravacao em si, com a OS de lote do
     * importador em {@code serviceOrderId} (NOT NULL desde a V4_13), e do
     * servico aplicador, fora deste dominio puro.
     */
    public record EnrollmentDraft(UUID unitId, UUID holderCustomerId, String familyCodeHash,
                                  SocialEligibility.Basis eligibilityBasis, String enrollmentSource,
                                  LocalDate cadunicoUpdatedOn, LocalDate validFrom, LocalDate validTo,
                                  String status, String legalBasis, int socialEconomies) {
    }

    private SocialBenefitConference() {
    }

    /**
     * Conferencia de um beneficio: elegibilidade pela regra da P1.5 (sem
     * reimplementar - delega a {@link SocialEligibility}), vigencia
     * declarada e vigencia da regra legal na data.
     */
    public static Conference check(SocialClassification.BaseRecord record, BenefitValidity validity,
            LocalDate referenceDate, int cadunicoMaxAgeMonths) {
        if (record == null || validity == null || referenceDate == null) {
            throw new IllegalArgumentException("Registro da lista, vigencia do beneficio e data de"
                    + " referencia sao obrigatorios");
        }
        boolean eligible = SocialEligibility.isEligible(record.basis(), record.cadunicoUpdatedOn(),
                referenceDate, cadunicoMaxAgeMonths);
        return new Conference(eligible, validity.covers(referenceDate),
                SocialProgramListPolicy.legalRuleAppliesOn(referenceDate));
    }

    /**
     * O fim de um beneficio conferido so vale respeitando o aviso de perda
     * da P1.5: delega a {@link SocialEnrollmentLifecycle}, nao recalcula.
     */
    public static boolean endRespectsLossNotice(LocalDate lossNoticeStartedOn, LocalDate endDate,
            int lossNoticeMonths) {
        return SocialEnrollmentLifecycle.lossNoticePeriodSatisfied(lossNoticeStartedOn, endDate,
                lossNoticeMonths);
    }

    /**
     * Monta o rascunho de inscricao de um candidato da classificacao.
     * Recusa: lista cuja origem a natureza juridica nao permite (trava da
     * {@link SocialProgramListPolicy}); registro da lista de outra familia
     * ou com base de elegibilidade divergente da do candidato; beneficio
     * inelegivel na referencia (pela {@link SocialEligibility}, via
     * registro da base); beneficio fora da vigencia declarada; beneficio
     * em data em que a regra legal ainda nao se aplica (antes de
     * 11/12/2026 - ver o Javadoc da classe: o rascunho nasce ACTIVE e
     * exige {@link Conference#applicable()} integral); economias abaixo
     * de 1.
     *
     * @param candidate       candidato vencedor do desempate (P1.5)
     * @param baseRecord      registro da lista da familia vencedora (base e CadUnico)
     * @param holderCustomerId titular da unidade, resolvido pelo cadastro (o
     *                         cruzamento da P1.5 casa por indices cegos; o id
     *                         do titular vem do vinculo vigente da unidade)
     * @param list            metadados da lista (origem e base legal registrada)
     * @param legalNature     natureza juridica do prestador ({@code reg.legal_nature})
     * @param validity        vigencia declarada do beneficio na lista
     * @param socialEconomies economias sociais da unidade (uma familia = uma inscricao)
     */
    public static EnrollmentDraft draftEnrollment(SocialClassification.EnrollmentCandidate candidate,
            SocialClassification.BaseRecord baseRecord, UUID holderCustomerId, ListMetadata list,
            SocialProgramListPolicy.LegalNature legalNature, BenefitValidity validity,
            int socialEconomies, LocalDate referenceDate, int cadunicoMaxAgeMonths) {
        if (candidate == null || baseRecord == null || holderCustomerId == null || list == null
                || legalNature == null || validity == null || referenceDate == null) {
            throw new IllegalArgumentException("Candidato, registro da lista, titular, metadados da"
                    + " lista, natureza juridica, vigencia e data de referencia sao obrigatorios");
        }
        SocialProgramListPolicy.requireAllowed(list.listSource(), legalNature);
        if (!candidate.familyCodeHash().equals(baseRecord.familyCodeHash())) {
            throw new IllegalArgumentException("Registro da lista nao pertence a familia do candidato"
                    + " da classificacao");
        }
        if (candidate.basis() != baseRecord.basis()) {
            // Achado A4 da revisao: a elegibilidade e conferida sobre a base do
            // registro, mas a inscricao carrega a base do candidato - sem esta
            // checagem, uma divergencia gravaria uma base nao conferida.
            throw new IllegalArgumentException("Base de elegibilidade do candidato da classificacao"
                    + " diverge da base do registro da lista: nao gera inscricao");
        }
        if (socialEconomies < 1) {
            throw new IllegalArgumentException("A inscricao precisa de ao menos uma economia social");
        }
        Conference conference = check(baseRecord, validity, referenceDate, cadunicoMaxAgeMonths);
        if (!conference.eligible()) {
            throw new IllegalArgumentException("Beneficio inelegivel na data de referencia pela regra"
                    + " da P1.5 (base ou CadUnico vencido): nao gera inscricao");
        }
        if (!conference.withinValidity()) {
            throw new IllegalArgumentException("Beneficio fora da vigencia declarada na lista na data"
                    + " de referencia: nao gera inscricao");
        }
        if (!conference.legalRuleApplicable()) {
            // Achado A2 da revisao: o rascunho nasce ACTIVE e exige a conferencia
            // integralmente aplicavel - antes de 11/12/2026 a regra legal nao
            // vigora e nao ha inscricao, nem em rascunho (ver Javadoc da classe).
            throw new IllegalArgumentException("Regra legal da tarifa social ainda nao aplicavel"
                    + " na data de referencia: ela vigora a partir de 11/12/2026 (Lei 14.898/2024;"
                    + " NR 13, art. 26, paragrafo 1o) e antes disso nao gera inscricao");
        }
        return new EnrollmentDraft(candidate.unitId(), holderCustomerId, candidate.familyCodeHash(),
                candidate.basis(), "OFFICIAL_LIST", baseRecord.cadunicoUpdatedOn(),
                validity.validFrom(), validity.validTo(), "ACTIVE", list.legalBasis(), socialEconomies);
    }
}
