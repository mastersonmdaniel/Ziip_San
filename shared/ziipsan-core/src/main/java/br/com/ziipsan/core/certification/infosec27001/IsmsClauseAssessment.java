package br.com.ziipsan.core.certification.infosec27001;

import br.com.ziipsan.core.json.Json;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Uma linha do MAPA DE CLÁUSULAS como AUTOAVALIAÇÃO do cliente (ED-73):
 * cláusula do sistema de gestão por número de referência, título em
 * PALAVRAS PRÓPRIAS, situação declarada e evidências ligadas aos registros
 * que o produto já tem — mesmo desenho do pacote irmão da ED-69.
 *
 * <p>O QUE ESTA CLASSE DELIBERADAMENTE NÃO GUARDA: texto da norma. O
 * produto guarda só a referência, o título/resumo em palavras próprias
 * (da curadoria da ED-76, tabela {@code platform.cert_clause} da V9_8,
 * exibidos a partir do mapa do tenant {@code cert_clause_map} da V21_1) e
 * a avaliação do cliente. O conteúdo do mapa por edição NÃO é semeado
 * nesta tarefa: inventar cláusulas seria fabricar conteúdo de norma
 * protegida — pendência da curadoria, como nas ED-69/ED-74.
 *
 * <p>Regras de honestidade: CONFORME exige ao menos uma evidência ligada;
 * NÃO_APLICÁVEL exige justificativa; e toda serialização carrega o aviso
 * fixo de que isto é autoavaliação do cliente, nunca parecer nem
 * certificação (ADR-0041 — o módulo não certifica).
 *
 * <p>JSON somente pela classe {@link Json} do core (regra do projeto).
 *
 * <p><b>Portão de ativação no construtor (correção D1 da revisão):</b> a
 * ativação é COMPONENTE do registro e o construtor canônico exige
 * ativação ligada: a avaliação não nasce sem o contexto da norma ligada
 * — o portão não vive só na fábrica {@link #assess}.
 */
public record IsmsClauseAssessment(Iso27001Activation activation, String clauseNumber,
                                   String ownWordsTitle, Conformity conformity,
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
        /** Linha da Declaração de Aplicabilidade desta tarefa. */
        SOA_ENTRY,
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

    /** Aviso fixo que acompanha a autoavaliação (ADR-0041, em palavras do produto). */
    public static final String SELF_ASSESSMENT_NOTICE =
            "Mapa de cláusulas como autoavaliação do cliente: não afirma certificação, "
                    + "que é concedida por organismo de certificação acreditado após auditoria.";

    /** Número de cláusula: grupos alfanuméricos separados por ponto (mesmo formato da ED-76). */
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*");

    /** Teto das referências no SQL pendente da ED-73 (VARCHAR(40)). */
    public static final int REFERENCE_MAX = 40;
    /** Teto da referência de evidência no SQL pendente da ED-73 (VARCHAR(200)). */
    public static final int EVIDENCE_REF_MAX = 200;

    public IsmsClauseAssessment {
        Objects.requireNonNull(activation, "activation é obrigatória: a autoavaliação só nasce "
                + "no contexto da ativação da norma (correção D1 da revisão da ED-73)");
        activation.requireEnabled();
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
        justification = justification == null ? "" : justification.trim();
        Objects.requireNonNull(assessedBy, "assessedBy é obrigatório (quem avaliou)");
        Objects.requireNonNull(assessedOn, "assessedOn é obrigatório (quando avaliou)");
    }

    /** Nascimento da avaliação: só com a norma ligada (porta da ativação). */
    public static IsmsClauseAssessment assess(Iso27001Activation activation, String clauseNumber,
                                              String ownWordsTitle, Conformity conformity,
                                              List<EvidenceRef> evidence, String justification,
                                              UUID assessedBy, LocalDate assessedOn) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new IsmsClauseAssessment(activation, clauseNumber, ownWordsTitle, conformity,
                evidence, justification, assessedBy, assessedOn);
    }

    /** JSON da avaliação, só pela classe {@code Json} do core, com o aviso fixo. */
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
