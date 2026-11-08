package br.com.ziipsan.core.certification.health45001;

import br.com.ziipsan.core.json.Json;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Uma linha da AUTOAVALIAÇÃO do cliente sobre o mapa de cláusulas da
 * ISO 45001 (ED-71): cláusula por número de referência, situação
 * declarada e evidências ligadas aos registros que o produto já tem,
 * por tipo + referência, sem copiar conteúdo.
 *
 * <p>LICENÇA (regra dura do brief, padrão da ED-73): textos e TÍTULOS
 * LITERAIS das cláusulas da ISO 45001 NÃO são reproduzidos neste pacote
 * — norma protegida e nenhuma licença consta. Por isso o título literal
 * nasce SEMPRE como o marcador {@value #TITLE_PENDING} ({@code [A DEFINIR]}):
 * não há construtor nem fábrica que aceite um título literal. O que o
 * cliente escreve é o RESUMO EM PALAVRAS PRÓPRIAS
 * ({@code ownWordsSummary}, opcional, da curadoria do tenant/ED-76) e a
 * sua autoavaliação. Quando a decisão do dono sobre licença × reescrita
 * existir, a curadoria preenche os títulos reais FORA desta régua
 * (pendência nomeada no relatório da ED-71).
 *
 * <p>NOVA EDIÇÃO: quando uma nova edição for publicada e incorporada ao
 * parâmetro {@code cert.iso45001.edition}, a incorporação se faz pela
 * COMPARAÇÃO DE CLÁUSULAS {@link #compareEditions} (incluídas,
 * removidas, mantidas) sobre as referências — o mesmo desenho da
 * comparação da ED-76/ED-69.
 *
 * <p>Regras de honestidade: CONFORME exige evidência; NÃO_APLICÁVEL
 * exige justificativa; toda serialização carrega o aviso fixo de
 * autoavaliação (o módulo não certifica — ADR-0041).
 */
public record OhsClauseAssessment(Iso45001Activation activation, String clauseNumber,
                                  String ownWordsSummary, Conformity conformity,
                                  List<EvidenceRef> evidence, String justification,
                                  UUID assessedBy, LocalDate assessedOn) {

    /** Situação declarada pelo cliente na autoavaliação. */
    public enum Conformity {
        CONFORMING, PARTIAL, NON_CONFORMING, NOT_APPLICABLE
    }

    /** Tipo da evidência ligada: sempre um registro que já existe no produto. */
    public enum EvidenceKind {
        /** Documento controlado do sistema de gestão (P2.25, {@code cert_document}). */
        DOCUMENT,
        /** Ocorrência única (P2.25, {@code cert_occurrence}). */
        OCCURRENCE,
        /** Linha do inventário de perigos desta tarefa ({@code cert_h45001_hazards}). */
        HAZARD,
        /** Avaliação de risco/permissão de OS desta tarefa ({@code cert_h45001_os_assessments}). */
        OS_SAFETY,
        /** Entrega de EPI desta tarefa ({@code cert_h45001_ppe_deliveries}). */
        PPE_DELIVERY,
        /** Investigação de acidente desta tarefa ({@code cert_h45001_accident_investigations}). */
        ACCIDENT_INVESTIGATION,
        /** Outra evidência referenciada por tipo + id, sem cópia de conteúdo. */
        OTHER
    }

    /** Referência de evidência: tipo + identificador, nunca o conteúdo. */
    public record EvidenceRef(EvidenceKind kind, String ref) {
        public EvidenceRef {
            Objects.requireNonNull(kind, "kind da evidência é obrigatório");
            if (ref == null || ref.isBlank() || ref.length() > EVIDENCE_REF_MAX) {
                throw new IllegalArgumentException("ref da evidência é obrigatória "
                        + "(identificador do registro, sem copiar conteúdo) e tem no máximo "
                        + EVIDENCE_REF_MAX + " caracteres");
            }
            ref = ref.trim();
        }
    }

    /** Comparação de cláusulas entre edições: referências incluídas, removidas e mantidas. */
    public record EditionComparison(List<String> included, List<String> removed,
                                    List<String> retained) {
        public EditionComparison {
            included = included == null ? List.of() : List.copyOf(included);
            removed = removed == null ? List.of() : List.copyOf(removed);
            retained = retained == null ? List.of() : List.copyOf(retained);
        }
    }

    /** Marcador do título literal pendente de licença/reescrita (padrão da ED-73). */
    public static final String TITLE_PENDING = "[A DEFINIR]";

    /** Aviso fixo que acompanha a autoavaliação (ADR-0041, em palavras do produto). */
    public static final String SELF_ASSESSMENT_NOTICE =
            "Mapa de cláusulas como autoavaliação do cliente: não afirma certificação, "
                    + "que é concedida por organismo de certificação acreditado após auditoria.";

    /** Número de cláusula: grupos alfanuméricos separados por ponto (mesmo formato da ED-76). */
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*");

    /** Teto das referências no SQL pendente da ED-71 (VARCHAR(40)). */
    public static final int REFERENCE_MAX = 40;
    /** Teto da referência de evidência no SQL pendente da ED-71 (VARCHAR(200)). */
    public static final int EVIDENCE_REF_MAX = 200;

    public OhsClauseAssessment {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        if (clauseNumber == null || !REFERENCE.matcher(clauseNumber.trim()).matches()
                || clauseNumber.trim().length() > REFERENCE_MAX) {
            throw new IllegalArgumentException("clauseNumber é obrigatório, deve ser só a "
                    + "referência (grupos separados por ponto), sem texto da norma, com no "
                    + "máximo " + REFERENCE_MAX + " caracteres: " + clauseNumber);
        }
        clauseNumber = clauseNumber.trim();
        ownWordsSummary = ownWordsSummary == null ? "" : ownWordsSummary.trim();
        Objects.requireNonNull(conformity, "conformity declarada é obrigatória");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (conformity == Conformity.CONFORMING && evidence.isEmpty()) {
            throw new IllegalArgumentException("CONFORMING exige ao menos uma evidência ligada "
                    + "aos registros do módulo: conformidade sem prova não é avaliação");
        }
        if (conformity == Conformity.NOT_APPLICABLE
                && (justification == null || justification.isBlank())) {
            throw new IllegalArgumentException("NOT_APPLICABLE exige justificativa: cláusula "
                    + "descartada sem motivo não é avaliação");
        }
        justification = justification == null ? "" : justification.trim();
        Objects.requireNonNull(assessedBy, "assessedBy é obrigatório (quem avaliou)");
        Objects.requireNonNull(assessedOn, "assessedOn é obrigatório (quando avaliou)");
    }

    /** Nascimento da avaliação: só com a norma ligada (a ativação é componente e reconfere). */
    public static OhsClauseAssessment assess(Iso45001Activation activation, String clauseNumber,
                                             String ownWordsSummary, Conformity conformity,
                                             List<EvidenceRef> evidence, String justification,
                                             UUID assessedBy, LocalDate assessedOn) {
        return new OhsClauseAssessment(activation, clauseNumber, ownWordsSummary, conformity,
                evidence, justification, assessedBy, assessedOn);
    }

    /** O título literal é SEMPRE o marcador pendente — não há caminho que o preencha aqui. */
    public String literalTitle() {
        return TITLE_PENDING;
    }

    /** Verdadeiro enquanto o título literal aguarda a decisão de licença × reescrita. */
    public boolean titlePending() {
        return true;
    }

    /**
     * Comparação de cláusulas na incorporação de uma nova edição:
     * referências da edição nova × referências da edição corrente.
     * Comparar a mesma edição (conjuntos idênticos de referências
     * repetidas são deduplicados; a régua não presume conteúdo).
     */
    public static EditionComparison compareEditions(List<String> currentClauseRefs,
                                                    List<String> newClauseRefs) {
        Set<String> current = new TreeSet<>(Objects.requireNonNull(currentClauseRefs,
                "currentClauseRefs é obrigatório"));
        Set<String> next = new TreeSet<>(Objects.requireNonNull(newClauseRefs,
                "newClauseRefs é obrigatório"));
        List<String> included = next.stream().filter(r -> !current.contains(r)).toList();
        List<String> removed = current.stream().filter(r -> !next.contains(r)).toList();
        List<String> retained = next.stream().filter(current::contains).toList();
        return new EditionComparison(included, removed, retained);
    }

    /** JSON da avaliação, só pela classe {@code Json} do core. */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clauseNumber", clauseNumber);
        map.put("literalTitle", TITLE_PENDING);
        map.put("ownWordsSummary", ownWordsSummary);
        map.put("conformity", conformity.name());
        map.put("evidence", evidence.stream()
                .map(e -> Map.of("kind", e.kind().name(), "ref", e.ref())).toList());
        map.put("justification", justification);
        map.put("assessedBy", assessedBy.toString());
        map.put("assessedOn", assessedOn.toString());
        map.put("notice", SELF_ASSESSMENT_NOTICE);
        return Json.write(map);
    }
}
