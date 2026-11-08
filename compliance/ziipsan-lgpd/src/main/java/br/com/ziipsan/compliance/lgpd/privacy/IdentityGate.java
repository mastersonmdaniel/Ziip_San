package br.com.ziipsan.compliance.lgpd.privacy;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Portão de identidade do titular (aceite da P3.40): NENHUMA entrega — resposta, arquivo
 * de acesso ou de portabilidade — acontece antes da identidade verificada. A verificação
 * é a que já existe no pedido ({@code lgpd_data_subject_request.verification_method},
 * {@code verified_at}, {@code verified_by} da {@code V2_5}); este portão só recusa o que
 * não estiver verificado, nunca presume verificação.
 *
 * <p>Pedido por representante usa a representação da P1.41 ({@code customer_representations}):
 * representante diferente do representado, tipo e PODERES por catálogo — o pedido do titular
 * exige o poder {@code REQUEST} —, documento comprobatório OBRIGATÓRIO
 * ({@code proof_document_id}, {@code nullable = false} na entidade real da P1.41), STATUS
 * e vigência. Na P1.41 a representação nasce {@code PENDING_APPROVAL} e só vale depois da
 * aprovação por outra pessoa ({@code ACTIVE}); representação pendente, rejeitada,
 * encerrada ou vencida NÃO entrega dado nenhum. O acesso delegado é registado no
 * {@code personal_data_access_log} pela entrega ({@link AccessDelivery}), como manda a P3.3.
 *
 * <p>ENTREGA É DO TITULAR DO PEDIDO: além da verificação gravada no pedido, a entrega
 * confere o CHAMADOR (o {@code subjectId} da sessão do portal, cabeçalho
 * {@code X-Portal-Subject}) contra o titular DO PEDIDO — o mesmo vínculo que a abertura
 * e a revogação já têm. Quem souber um {@code requestId} alheio não dispara entrega:
 * ou o chamador é o próprio titular do pedido ({@link #requireCallerMatchesSubject}),
 * ou é o representante de uma representação válida daquele titular
 * ({@link #requireCallerIsRepresentative}).
 */
public final class IdentityGate {

    private IdentityGate() {
    }

    /** Poderes da representação da P1.41 (catálogo dela; o pedido exige {@code REQUEST}). */
    public enum RepresentationPower {
        VIEW, REQUEST, PAY, CHANGE_BILLING, OWNERSHIP
    }

    /**
     * Status da representação na P1.41 (coluna {@code status} de
     * {@code customer_representations}): nasce {@code PENDING_APPROVAL} e só vira
     * {@code ACTIVE} na aprovação por outra pessoa; {@code REJECTED} é a recusa da
     * aprovação e {@code EXPIRED} o encerramento explícito pelo backoffice. Só
     * {@code ACTIVE} vale no portão.
     */
    public enum RepresentationStatus {
        PENDING_APPROVAL, ACTIVE, REJECTED, EXPIRED
    }

    /**
     * Representação conferida a partir do registro da P1.41.
     *
     * @param representativeId representante (cliente cadastrado, diferente do titular)
     * @param subjectId        titular representado
     * @param powers           poderes concedidos (ao menos um; o pedido exige REQUEST)
     * @param validFrom        início da vigência
     * @param validTo          fim da vigência; {@code null} = sem fim declarado
     * @param status           status do registro na P1.41 (o portão só aceita ACTIVE)
     * @param proofDocumentId  documento comprobatório ({@code proof_document_id} da
     *                         P1.41, obrigatório — {@code nullable = false}); sem prova,
     *                         a representação nem se constrói neste contrato
     */
    public record Representation(java.util.UUID representativeId, java.util.UUID subjectId,
                                 Set<RepresentationPower> powers,
                                 LocalDate validFrom, LocalDate validTo,
                                 RepresentationStatus status,
                                 java.util.UUID proofDocumentId) {

        public Representation {
            Objects.requireNonNull(representativeId, "representativeId é obrigatório");
            Objects.requireNonNull(subjectId, "subjectId é obrigatório");
            if (representativeId.equals(subjectId)) {
                throw new IllegalArgumentException(
                        "representante deve ser diferente do representado (P1.41)");
            }
            if (powers == null || powers.isEmpty()) {
                throw new IllegalArgumentException("a representação exige ao menos um poder (P1.41)");
            }
            powers = Set.copyOf(powers);
            Objects.requireNonNull(validFrom, "validFrom é obrigatório (vigência da representação)");
            if (validTo != null && validTo.isBefore(validFrom)) {
                throw new IllegalArgumentException("validTo não pode ser anterior a validFrom");
            }
            Objects.requireNonNull(status, "status é obrigatório (P1.41: a representação "
                    + "nasce PENDING_APPROVAL e só vale ACTIVE)");
            if (proofDocumentId == null) {
                throw new IllegalArgumentException("documento comprobatório é obrigatório "
                        + "(proof_document_id da P1.41, nullable = false): representação "
                        + "sem prova não vale para entrega");
            }
        }

        /** Vigente na data: dentro de [validFrom, validTo] (fim inclusivo; sem fim = vigente). */
        public boolean isCurrentOn(LocalDate date) {
            Objects.requireNonNull(date, "date é obrigatória");
            return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
        }
    }

    /**
     * Exige a identidade verificada para uma entrega ao próprio titular.
     *
     * @param verifiedAt instante da verificação gravado no pedido; {@code null} = não verificado
     * @throws IllegalStateException se não houver verificação registrada
     */
    public static void requireVerified(java.time.Instant verifiedAt) {
        if (verifiedAt == null) {
            throw new IllegalStateException("entrega recusada: identidade do titular não "
                    + "verificada (verified_at vazio no pedido) — nenhuma resposta, arquivo "
                    + "de acesso ou portabilidade sai antes da verificação");
        }
    }

    /**
     * Exige que o chamador da entrega seja o próprio titular DO PEDIDO (vínculo
     * chamador × titular, o mesmo da abertura e da revogação): conhecer o
     * {@code requestId} de outro titular não autoriza entrega nenhuma.
     *
     * @param requestSubjectId titular gravado no pedido que se quer entregar
     * @param callerSubjectId  titular da sessão do portal ({@code X-Portal-Subject})
     * @throws IllegalStateException se o chamador não for o titular do pedido
     */
    public static void requireCallerMatchesSubject(java.util.UUID requestSubjectId,
                                                   java.util.UUID callerSubjectId) {
        Objects.requireNonNull(requestSubjectId, "requestSubjectId é obrigatório (titular do pedido)");
        Objects.requireNonNull(callerSubjectId, "callerSubjectId é obrigatório (titular da sessão)");
        if (!requestSubjectId.equals(callerSubjectId)) {
            throw new IllegalStateException("entrega recusada: o pedido é de outro titular — "
                    + "quem chama não é o titular do pedido nem representante válido dele; "
                    + "a entrega só procede para o próprio titular verificado ou por "
                    + "representação válida (P1.41)");
        }
    }

    /**
     * Exige que o chamador seja o representante DESTA representação e que a
     * representação seja SOBRE o titular do pedido: representação válida de um
     * titular não entrega o pedido de outro, e terceiro que não é o representante
     * registado não se vale dela.
     *
     * @throws IllegalStateException se a representação for de outro titular ou o
     *                               chamador não for o representante registado
     */
    public static void requireCallerIsRepresentative(Representation representation,
                                                     java.util.UUID requestSubjectId,
                                                     java.util.UUID callerId) {
        Objects.requireNonNull(representation, "representation é obrigatória");
        Objects.requireNonNull(requestSubjectId, "requestSubjectId é obrigatório (titular do pedido)");
        Objects.requireNonNull(callerId, "callerId é obrigatório (quem chama a entrega)");
        if (!representation.subjectId().equals(requestSubjectId)) {
            throw new IllegalStateException("entrega recusada: a representação é sobre outro "
                    + "titular, não sobre o titular deste pedido");
        }
        if (!representation.representativeId().equals(callerId)) {
            throw new IllegalStateException("entrega recusada: quem chama não é o "
                    + "representante registado nesta representação (P1.41)");
        }
    }

    /**
     * Exige representação válida para uma entrega feita a representante: verificada a
     * identidade de quem pede, representação APROVADA ({@code ACTIVE} na P1.41, com o
     * documento comprobatório presente), vigente na data e com o poder REQUEST.
     *
     * @throws IllegalStateException se não estiver ACTIVE, sem prova, vencida ou sem
     *                               o poder de pedir pelo titular
     */
    public static void requireRepresentative(Representation representation,
                                             java.time.Instant verifiedAt, LocalDate today) {
        Objects.requireNonNull(representation, "representation é obrigatória em pedido por representante");
        requireVerified(verifiedAt);
        if (representation.status() != RepresentationStatus.ACTIVE) {
            throw new IllegalStateException("entrega recusada: a representação não está "
                    + "ACTIVE (status atual: " + representation.status() + ") — na P1.41 ela "
                    + "nasce PENDING_APPROVAL e só vale após aprovação por outra pessoa");
        }
        if (representation.proofDocumentId() == null) {
            throw new IllegalStateException("entrega recusada: representação sem documento "
                    + "comprobatório (proof_document_id da P1.41)");
        }
        if (!representation.isCurrentOn(today)) {
            throw new IllegalStateException("entrega recusada: representação vencida ou ainda "
                    + "não vigente na data — representação vencida deixa de valer (P1.41)");
        }
        if (!representation.powers().contains(RepresentationPower.REQUEST)) {
            throw new IllegalStateException("entrega recusada: a representação não concede o "
                    + "poder REQUEST (pedir pelo titular) — poderes atuais: "
                    + representation.powers());
        }
    }
}
