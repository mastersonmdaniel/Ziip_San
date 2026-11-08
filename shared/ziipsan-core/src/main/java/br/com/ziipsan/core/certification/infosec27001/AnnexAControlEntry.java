package br.com.ziipsan.core.certification.infosec27001;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Uma linha da Declaração de Aplicabilidade (SoA) da ISO/IEC 27001
 * (ED-73): um controle do Anexo A POR REFERÊNCIA — chave do controle,
 * aplicabilidade, justificativa, estado, evidências, responsável e última
 * revisão.
 *
 * <p><b>FRONTEIRA DE LICENÇA (ponto crítico do aceite da ED-73):</b> os
 * identificadores e títulos literais dos 93 controles do Anexo A só podem
 * ser reproduzidos DEPOIS de decisão do dono sobre licença ou reescrita —
 * e essa decisão NÃO existe. Por isso esta classe, deliberadamente:
 * <ul>
 *   <li>identifica o controle por uma CHAVE DE REFERÊNCIA do tenant
 *       (grupos alfanuméricos separados por ponto, ex.: a chave que a
 *       curadoria vier a atribuir), nunca por um identificador literal
 *       semeado por esta tarefa;</li>
 *   <li>guarda o título como {@link #TITLE_PENDING} ({@code [A DEFINIR]})
 *       enquanto o tenant/curadoria não declarar um título — o campo
 *       existe para ser preenchido após a decisão do dono, e
 *       {@link #titlePending()} expõe a pendência em vez de disfarçá-la;</li>
 *   <li>não semeia controle algum: a estrutura completa dos 93 é montada
 *       por {@link StatementOfApplicability}, que exige as 93 chaves
 *       declaradas por quem monta (tenant/curadoria), sem inventar
 *       nenhuma.</li>
 * </ul>
 *
 * <p>Regras da linha: justificativa é obrigatória SEMPRE (a SoA é um
 * documento de justificativas, não uma lista); controle NÃO_APLICÁVEL é a
 * exclusão justificada que o aceite manda permitir — nasce com a
 * justificativa e sem exigir evidência; controle APLICÁVEL em estado
 * IMPLEMENTADO exige ao menos uma evidência ligada (implementado sem
 * prova é só declaração); controle EXCLUÍDO (NÃO_APLICÁVEL) NÃO pode
 * constar IMPLEMENTADO — exclusão justificada não é implementação, e a
 * combinação ainda furaria a exigência de evidência (correção D5 da
 * revisão cruzada da ED-73).
 *
 * <p><b>Portão de ativação no construtor (correção D1 da revisão):</b> a
 * ativação é COMPONENTE do registro e o construtor canônico exige
 * ativação ligada ({@link Iso27001Activation#requireEnabled()}): não há
 * como construir a linha sem o contexto da norma ligada — o portão não
 * vive só na fábrica {@link #declare}. O que continua sendo da fiação é
 * impedir que um serviço monte uma ativação ligada falsa/fora do núcleo
 * P1.32 e persistir somente registros validados (pendência declarada no
 * relatório da ED-73).
 */
public record AnnexAControlEntry(Iso27001Activation activation, String controlKey, String title,
                                 Applicability applicability, String justification,
                                 ImplementationStatus status, List<EvidenceRef> evidence,
                                 UUID ownerId, LocalDate lastReviewedOn) {

    /** Aplicabilidade declarada do controle na SoA. */
    public enum Applicability {
        APPLICABLE, NOT_APPLICABLE
    }

    /** Estado de implementação declarado (só faz sentido para controle aplicável). */
    public enum ImplementationStatus {
        NOT_STARTED, IN_PROGRESS, IMPLEMENTED
    }

    /** Tipo da evidência ligada: sempre um registro que já existe no produto. */
    public enum EvidenceKind {
        /** Documento controlado do sistema de gestão (P2.25, {@code cert_document}). */
        DOCUMENT,
        /** Ocorrência única (P2.25, {@code cert_occurrence}). */
        OCCURRENCE,
        /** Item do pacote de evidência do fornecedor desta tarefa. */
        SUPPLIER_EVIDENCE,
        /** Outra evidência referenciada por tipo + id, sem cópia de conteúdo. */
        OTHER
    }

    /** Referência de evidência: tipo + identificador, nunca o conteúdo. */
    public record EvidenceRef(EvidenceKind kind, String ref) {
        public EvidenceRef {
            Objects.requireNonNull(kind, "kind da evidência é obrigatório");
            if (ref == null || ref.isBlank() || ref.trim().length() > EVIDENCE_REF_MAX) {
                throw new IllegalArgumentException("ref da evidência é obrigatória "
                        + "(identificador do registro, sem copiar conteúdo) e tem no máximo "
                        + EVIDENCE_REF_MAX + " caracteres");
            }
            ref = ref.trim();
        }
    }

    /**
     * Marcador do título enquanto não há decisão do dono sobre licença ou
     * reescrita dos títulos do Anexo A (pendência nomeada da ED-73).
     */
    public static final String TITLE_PENDING = "[A DEFINIR]";

    /** Chave de referência: grupos alfanuméricos separados por ponto (compartilhado no pacote). */
    static final Pattern KEY = Pattern.compile("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*");

    /** Teto da chave no SQL pendente da ED-73 (VARCHAR(40)). */
    public static final int CONTROL_KEY_MAX = 40;
    /** Teto da referência de evidência no SQL pendente da ED-73 (VARCHAR(200)). */
    public static final int EVIDENCE_REF_MAX = 200;

    public AnnexAControlEntry {
        Objects.requireNonNull(activation, "activation é obrigatória: a linha da SoA só nasce "
                + "no contexto da ativação da norma (correção D1 da revisão da ED-73)");
        activation.requireEnabled();
        if (controlKey == null || !KEY.matcher(controlKey.trim()).matches()
                || controlKey.trim().length() > CONTROL_KEY_MAX) {
            throw new IllegalArgumentException("controlKey é obrigatório, deve ser só a chave de "
                    + "referência (grupos separados por ponto), sem título nem texto da norma, "
                    + "com no máximo " + CONTROL_KEY_MAX + " caracteres: " + controlKey);
        }
        controlKey = controlKey.trim();
        title = (title == null || title.isBlank()) ? TITLE_PENDING : title.trim();
        Objects.requireNonNull(applicability, "applicability declarada é obrigatória");
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("justification é obrigatória em toda linha da SoA: "
                    + "aplicável ou excluído, o controle se justifica — exclusão sem justificativa "
                    + "não é Declaração de Aplicabilidade");
        }
        justification = justification.trim();
        Objects.requireNonNull(status, "status de implementação é obrigatório");
        if (applicability == Applicability.NOT_APPLICABLE
                && status == ImplementationStatus.IMPLEMENTED) {
            throw new IllegalArgumentException("controle excluído (NOT_APPLICABLE) não pode constar "
                    + "IMPLEMENTED: exclusão justificada não é implementação, e a combinação "
                    + "furaria a exigência de evidência do controle implementado (correção D5)");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (applicability == Applicability.APPLICABLE && status == ImplementationStatus.IMPLEMENTED
                && evidence.isEmpty()) {
            throw new IllegalArgumentException("controle aplicável IMPLEMENTED exige ao menos uma "
                    + "evidência ligada: implementado sem prova não é declaração auditável");
        }
        Objects.requireNonNull(ownerId, "ownerId (responsável pelo controle) é obrigatório");
        Objects.requireNonNull(lastReviewedOn, "lastReviewedOn (última revisão) é obrigatório");
    }

    /** Nascimento da linha: só com a norma ligada (porta da ativação). */
    public static AnnexAControlEntry declare(Iso27001Activation activation, String controlKey,
                                             String title, Applicability applicability,
                                             String justification, ImplementationStatus status,
                                             List<EvidenceRef> evidence, UUID ownerId,
                                             LocalDate lastReviewedOn) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new AnnexAControlEntry(activation, controlKey, title, applicability, justification,
                status, evidence, ownerId, lastReviewedOn);
    }

    /** Verdadeiro enquanto o título aguarda a decisão do dono (licença × reescrita). */
    public boolean titlePending() {
        return TITLE_PENDING.equals(title);
    }

    /** Verdadeiro quando a linha é uma exclusão justificada (permitida pelo aceite). */
    public boolean justifiedExclusion() {
        return applicability == Applicability.NOT_APPLICABLE;
    }
}
