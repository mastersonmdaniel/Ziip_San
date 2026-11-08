package br.com.ziipsan.core.certification.quality9001;

import br.com.ziipsan.core.json.Json;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Uma linha da AUTOAVALIAÇÃO do cliente sobre o mapa de cláusulas da
 * ISO 9001 (ED-69): cláusula por número de referência, título em
 * PALAVRAS PRÓPRIAS, situação declarada e evidências ligadas aos
 * registros que o produto já tem — documentos controlados, ocorrências
 * e ações da P2.25 e registros do módulo — por tipo + referência, sem
 * copiar conteúdo (mesmo desenho do {@code cert_evidence_link} da
 * V21_1).
 *
 * <p>O QUE ESTA CLASSE DELIBERADAMENTE NÃO GUARDA: texto da norma.
 * ISO 9001 é norma protegida; o produto guarda só número da cláusula,
 * o título/resumo em palavras próprias (da curadoria da ED-76, tabela
 * {@code platform.cert_clause} da V9_8, exibidos a partir do mapa do
 * tenant {@code cert_clause_map} da V21_1) e a avaliação do cliente.
 * O mapa real por edição NÃO foi semeado nesta tarefa:
 * {@code docs/normas/mapa-clausulas.json} está vazio no repo (conferido
 * via MCP na ED-74/ED-76) e inventar cláusulas seria fabricar conteúdo
 * de norma — pendência nomeada no relatório da ED-69. O
 * {@code ownWordsTitle} desta linha é a paráfrase declarada por quem
 * avalia (curadoria/tenant), nunca uma cópia: o controle sobre o
 * conteúdo é o processo de curadoria humana, como na ED-76.
 *
 * <p>Regras de honestidade da autoavaliação: situação CONFORME exige ao
 * menos uma evidência ligada (conformidade sem prova é só opinião);
 * NÃO_APLICÁVEL exige justificativa; e toda serialização carrega o
 * aviso fixo de que isto é autoavaliação do cliente, nunca parecer nem
 * certificação (ADR-0041 — o módulo não certifica; quem certifica é
 * organismo acreditado, após auditoria).
 *
 * <p>JSON somente pela classe {@link Json} do core (regra do projeto).
 */
public record QualityClauseAssessment(String clauseNumber, String ownWordsTitle,
                                      Conformity conformity, List<EvidenceRef> evidence,
                                      String justification, UUID assessedBy,
                                      LocalDate assessedOn) {

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
        /** Linha do mapa de processos desta tarefa ({@code cert_q9001_processes}). */
        PROCESS_MAP,
        /** Calibração de instrumento desta tarefa ({@code cert_q9001_calibrations}). */
        CALIBRATION,
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
        }
    }

    /** Aviso fixo que acompanha a autoavaliação (ADR-0041, em palavras do produto). */
    public static final String SELF_ASSESSMENT_NOTICE =
            "Mapa de cláusulas como autoavaliação do cliente: não afirma certificação, "
                    + "que é concedida por organismo de certificação acreditado após auditoria.";

    /** Número de cláusula: grupos alfanuméricos separados por ponto (mesmo formato da ED-76). */
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*");

    /** Teto das referências no SQL pendente da ED-69 (VARCHAR(40)). */
    public static final int REFERENCE_MAX = 40;
    /** Teto da referência de evidência no SQL pendente da ED-69 (VARCHAR(200)). */
    public static final int EVIDENCE_REF_MAX = 200;

    public QualityClauseAssessment {
        if (clauseNumber == null || !REFERENCE.matcher(clauseNumber.trim()).matches()
                || clauseNumber.trim().length() > REFERENCE_MAX) {
            throw new IllegalArgumentException("clauseNumber é obrigatório, deve ser só a "
                    + "referência (grupos separados por ponto), sem texto da norma, com no "
                    + "máximo " + REFERENCE_MAX + " caracteres: " + clauseNumber);
        }
        clauseNumber = clauseNumber.trim();
        if (ownWordsTitle == null || ownWordsTitle.isBlank()) {
            throw new IllegalArgumentException("ownWordsTitle é obrigatório: o título da cláusula "
                    + "em palavras próprias (curadoria ED-76/tenant), nunca texto da norma");
        }
        ownWordsTitle = ownWordsTitle.trim();
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
        justification = justification == null ? "" : justification;
        Objects.requireNonNull(assessedBy, "assessedBy é obrigatório (quem avaliou)");
        Objects.requireNonNull(assessedOn, "assessedOn é obrigatório (quando avaliou)");
    }

    /** Nascimento da avaliação: só com a norma ligada (porta da ativação). */
    public static QualityClauseAssessment assess(Iso9001Activation activation, String clauseNumber,
                                                 String ownWordsTitle, Conformity conformity,
                                                 List<EvidenceRef> evidence, String justification,
                                                 UUID assessedBy, LocalDate assessedOn) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new QualityClauseAssessment(clauseNumber, ownWordsTitle, conformity, evidence,
                justification, assessedBy, assessedOn);
    }

    /**
     * JSON da avaliação, só pela classe {@code Json} do core. As chaves são
     * uma lista fechada de referências, do título em palavras próprias e
     * da situação declarada — não há chave capaz de carregar texto da norma.
     */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clauseNumber", clauseNumber);
        map.put("ownWordsTitle", ownWordsTitle);
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
