package br.com.ziipsan.core.certification.env14001;

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
 * Uma linha da AUTOAVALIACAO do cliente sobre o mapa de clausulas da
 * ISO 14001 (aceite da ED-70: "mapa de clausulas como autoavaliacao
 * do cliente"): clausula por numero de referencia, situacao declarada
 * e evidencias ligadas aos registros que o produto ja tem, por tipo +
 * referencia, sem copiar conteudo. Desenho dos pacotes irmaos
 * (ED-69/ED-71/ED-73).
 *
 * <p>LICENCA (regra dura, padrao dos pacotes irmaos): textos e
 * TITULOS LITERAIS das clausulas da ISO 14001 NAO sao reproduzidos
 * neste pacote — norma protegida e nenhuma licenca consta. Por isso o
 * titulo literal nasce SEMPRE como o marcador {@value #TITLE_PENDING}
 * ({@code [A DEFINIR]}): nao ha construtor nem fabrica que aceite um
 * titulo literal. O que o cliente escreve e o RESUMO EM PALAVRAS
 * PROPRIAS ({@code ownWordsSummary}, opcional, da curadoria do
 * tenant/ED-76) e a sua autoavaliacao. Quando a decisao do dono sobre
 * licenca x reescrita existir, a curadoria preenche os titulos reais
 * FORA desta regua (pendencia nomeada no relatorio da ED-70).</p>
 *
 * <p>NOVA EDICAO: quando uma nova edicao for publicada e incorporada
 * ao parametro {@code cert.iso14001.edition}, a incorporacao se faz
 * pela COMPARACAO DE CLAUSULAS {@link #compareEditions} (incluidas,
 * removidas, mantidas) sobre as referencias — e o caminho da
 * transicao 2015 -&gt; 2026 declarada na ativacao.</p>
 *
 * <p>Regras de honestidade: CONFORME exige evidencia; NAO_APLICAVEL
 * exige justificativa; toda serializacao carrega o aviso fixo de
 * autoavaliacao (o modulo nao certifica — ADR-0041). Registro da
 * norma: a ativacao e COMPONENTE e o construtor canonico exige a
 * norma ligada.</p>
 */
public record EmsClauseAssessment(Iso14001Activation activation, String clauseNumber,
                                  String ownWordsSummary, Conformity conformity,
                                  List<EvidenceRef> evidence, String justification,
                                  UUID assessedBy, LocalDate assessedOn) {

    /** Situacao declarada pelo cliente na autoavaliacao. */
    public enum Conformity {
        CONFORMING, PARTIAL, NON_CONFORMING, NOT_APPLICABLE
    }

    /** Tipo da evidencia ligada: sempre um registro que ja existe no produto. */
    public enum EvidenceKind {
        /** Documento controlado do sistema de gestao (P2.25, {@code cert_document}). */
        DOCUMENT,
        /** Ocorrencia unica (P2.25, {@code cert_occurrence}). */
        OCCURRENCE,
        /** Aspecto ambiental desta tarefa ({@code cert_e14001_aspects}). */
        ASPECT,
        /** Avaliacao de significancia desta tarefa ({@code cert_e14001_significance_evaluations}). */
        SIGNIFICANCE_EVALUATION,
        /** Licenca ambiental desta tarefa ({@code cert_e14001_licenses}). */
        LICENSE,
        /** Cenario de emergencia desta tarefa ({@code cert_e14001_emergency_scenarios}). */
        EMERGENCY_SCENARIO,
        /** Outra evidencia referenciada por tipo + id, sem copia de conteudo. */
        OTHER
    }

    /** Referencia de evidencia: tipo + identificador, nunca o conteudo. */
    public record EvidenceRef(EvidenceKind kind, String ref) {
        public EvidenceRef {
            Objects.requireNonNull(kind, "kind da evidencia e obrigatorio");
            if (ref == null || ref.isBlank() || ref.length() > EVIDENCE_REF_MAX) {
                throw new IllegalArgumentException("ref da evidencia e obrigatoria "
                        + "(identificador do registro, sem copiar conteudo) e tem no maximo "
                        + EVIDENCE_REF_MAX + " caracteres");
            }
            ref = ref.trim();
        }
    }

    /** Comparacao de clausulas entre edicoes: referencias incluidas, removidas e mantidas. */
    public record EditionComparison(List<String> included, List<String> removed,
                                    List<String> retained) {
        public EditionComparison {
            included = included == null ? List.of() : List.copyOf(included);
            removed = removed == null ? List.of() : List.copyOf(removed);
            retained = retained == null ? List.of() : List.copyOf(retained);
        }
    }

    /** Marcador do titulo literal pendente de licenca/reescrita (padrao dos pacotes irmaos). */
    public static final String TITLE_PENDING = "[A DEFINIR]";

    /** Aviso fixo que acompanha a autoavaliacao (ADR-0041, em palavras do produto). */
    public static final String SELF_ASSESSMENT_NOTICE =
            "Mapa de clausulas como autoavaliacao do cliente: nao afirma certificacao, "
                    + "que e concedida por organismo de certificacao acreditado apos auditoria.";

    /** Numero de clausula: grupos alfanumericos separados por ponto (mesmo formato da ED-76). */
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*");

    /** Teto das referencias no SQL pendente da ED-70 (VARCHAR(40)). */
    public static final int REFERENCE_MAX = 40;
    /** Teto da referencia de evidencia no SQL pendente da ED-70 (VARCHAR(200)). */
    public static final int EVIDENCE_REF_MAX = 200;

    public EmsClauseAssessment {
        Objects.requireNonNull(activation, "activation e obrigatoria (componente do registro)");
        activation.requireEnabled();
        if (clauseNumber == null || !REFERENCE.matcher(clauseNumber.trim()).matches()
                || clauseNumber.trim().length() > REFERENCE_MAX) {
            throw new IllegalArgumentException("clauseNumber e obrigatorio, deve ser so a "
                    + "referencia (grupos separados por ponto), sem texto da norma, com no "
                    + "maximo " + REFERENCE_MAX + " caracteres: " + clauseNumber);
        }
        clauseNumber = clauseNumber.trim();
        ownWordsSummary = ownWordsSummary == null ? "" : ownWordsSummary.trim();
        Objects.requireNonNull(conformity, "conformity declarada e obrigatoria");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (conformity == Conformity.CONFORMING && evidence.isEmpty()) {
            throw new IllegalArgumentException("CONFORMING exige ao menos uma evidencia ligada "
                    + "aos registros do modulo: conformidade sem prova nao e avaliacao");
        }
        if (conformity == Conformity.NOT_APPLICABLE
                && (justification == null || justification.isBlank())) {
            throw new IllegalArgumentException("NOT_APPLICABLE exige justificativa: clausula "
                    + "descartada sem motivo nao e avaliacao");
        }
        justification = justification == null ? "" : justification.trim();
        Objects.requireNonNull(assessedBy, "assessedBy e obrigatorio (quem avaliou)");
        Objects.requireNonNull(assessedOn, "assessedOn e obrigatorio (quando avaliou)");
    }

    /** Nascimento da avaliacao: so com a norma ligada (a ativacao e componente e reconfere). */
    public static EmsClauseAssessment assess(Iso14001Activation activation, String clauseNumber,
                                             String ownWordsSummary, Conformity conformity,
                                             List<EvidenceRef> evidence, String justification,
                                             UUID assessedBy, LocalDate assessedOn) {
        return new EmsClauseAssessment(activation, clauseNumber, ownWordsSummary, conformity,
                evidence, justification, assessedBy, assessedOn);
    }

    /** O titulo literal e SEMPRE o marcador pendente — nao ha caminho que o preencha aqui. */
    public String literalTitle() {
        return TITLE_PENDING;
    }

    /** Verdadeiro enquanto o titulo literal aguarda a decisao de licenca x reescrita. */
    public boolean titlePending() {
        return true;
    }

    /**
     * Comparacao de clausulas na incorporacao de uma nova edicao
     * (ex.: a transicao 2015 -&gt; 2026): referencias da edicao nova x
     * referencias da edicao corrente.
     */
    public static EditionComparison compareEditions(List<String> currentClauseRefs,
                                                    List<String> newClauseRefs) {
        Set<String> current = new TreeSet<>(Objects.requireNonNull(currentClauseRefs,
                "currentClauseRefs e obrigatorio"));
        Set<String> next = new TreeSet<>(Objects.requireNonNull(newClauseRefs,
                "newClauseRefs e obrigatorio"));
        List<String> included = next.stream().filter(r -> !current.contains(r)).toList();
        List<String> removed = current.stream().filter(r -> !next.contains(r)).toList();
        List<String> retained = next.stream().filter(current::contains).toList();
        return new EditionComparison(included, removed, retained);
    }

    /** JSON da avaliacao, so pela classe {@code Json} do core. */
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
