package br.com.ziipsan.compliance.lgpd.privacyiso;

import br.com.ziipsan.core.json.Json;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Uma linha da AUTOAVALIAÇÃO do cliente sobre o mapa de cláusulas e de
 * controles da edição de 2025 (ED-74): cláusula (e, quando houver,
 * controle) por número de referência, audiência (controlador, operador
 * ou comum), situação declarada e evidências ligadas aos registros que
 * o produto já tem — registros de operações e pedidos da P3.40 e
 * incidentes/RIPD da P3.10, por tipo + referência, sem copiar conteúdo
 * (mesmo desenho do {@code cert_evidence_link} da V21_1).
 *
 * <p>O QUE ESTA CLASSE DELIBERADAMENTE NÃO GUARDA: texto da norma.
 * ISO/IEC 27701 é norma protegida; o produto guarda só número da
 * cláusula/controle, a audiência e a avaliação do cliente — título e
 * resumo em palavras próprias são da CURADORIA (ED-76, tabela
 * {@code platform.cert_clause} da V9_8, carregada pela P1.34) e são
 * exibidos a partir do mapa do tenant ({@code cert_clause_map} da
 * V21_1), nunca duplicados aqui. O mapa real da edição de 2025 NÃO foi
 * semeado nesta tarefa: {@code docs/normas/mapa-clausulas.json} está
 * vazio no repo (conferido via MCP) e inventar cláusulas seria fabricar
 * conteúdo de norma — pendência nomeada no relatório da ED-74.
 *
 * <p>Regras de honestidade da autoavaliação: situação CONFORME exige ao
 * menos uma evidência ligada (conformidade sem prova é só opinião);
 * NÃO_APLICÁVEL exige justificativa (cláusula descartada sem motivo não
 * é avaliação); e toda serialização carrega o aviso fixo de que isto é
 * autoavaliação do cliente, nunca parecer nem certificação (ADR-0041,
 * item 8, na mesma linha do aviso do núcleo de certificações).
 *
 * <p>JSON somente pela classe {@link Json} do core (regra do projeto).
 */
public record PrivacyClauseAssessment(String clauseNumber, String controlRef, Audience audience,
                                      Conformity conformity, List<EvidenceRef> evidence,
                                      String justification, UUID assessedBy, LocalDate assessedOn) {

    /** Audiência do item do mapa: a quem o requisito se dirige. */
    public enum Audience {
        /** Requisitos de quem decide o tratamento (controlador). */
        CONTROLLER,
        /** Requisitos de quem trata em nome de outrem (operador). */
        PROCESSOR,
        /** Requisitos comuns aos dois papéis (sistema de gestão). */
        COMMON
    }

    /** Situação declarada pelo cliente na autoavaliação. */
    public enum Conformity {
        CONFORMING, PARTIAL, NON_CONFORMING, NOT_APPLICABLE
    }

    /** Tipo da evidência ligada: sempre um registro que já existe no produto. */
    public enum EvidenceKind {
        /** Registro de operações de tratamento (P3.40, {@code lgpd_processing_records}). */
        PROCESSING_RECORD,
        /** Pedido do titular (P3.40, {@code lgpd_data_subject_request}). */
        DATA_SUBJECT_REQUEST,
        /** RIPD (P3.10, {@code lgpd_dpia}). */
        DPIA,
        /** Incidente com dado pessoal (P3.10, ligado à ocorrência da P2.25). */
        INCIDENT,
        /** Documento do módulo de certificações (P2.25, {@code V21_2}). */
        DOCUMENT,
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

    /** Aviso fixo que acompanha a autoavaliação (ADR-0041, item 8, em palavras do produto). */
    public static final String SELF_ASSESSMENT_NOTICE =
            "Mapa de cláusulas como autoavaliação do cliente: não afirma certificação, "
                    + "que é concedida por organismo de certificação acreditado após auditoria.";

    /** Número de cláusula/controle: grupos alfanuméricos separados por ponto (mesmo formato da ED-76). */
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*");

    /** Teto das referências no SQL pendente da ED-74 (VARCHAR(40)). */
    public static final int REFERENCE_MAX = 40;
    /** Teto da referência de evidência no SQL pendente da ED-74 (VARCHAR(200)). */
    public static final int EVIDENCE_REF_MAX = 200;

    public PrivacyClauseAssessment {
        if (clauseNumber == null || !REFERENCE.matcher(clauseNumber.trim()).matches()
                || clauseNumber.trim().length() > REFERENCE_MAX) {
            throw new IllegalArgumentException("clauseNumber é obrigatório, deve ser só a "
                    + "referência (grupos separados por ponto), sem texto da norma, com no "
                    + "máximo " + REFERENCE_MAX + " caracteres: " + clauseNumber);
        }
        clauseNumber = clauseNumber.trim();
        if (controlRef != null && (!REFERENCE.matcher(controlRef.trim()).matches()
                || controlRef.trim().length() > REFERENCE_MAX)) {
            throw new IllegalArgumentException("controlRef deve ser só a referência do controle "
                    + "(grupos separados por ponto), sem texto da norma, com no máximo "
                    + REFERENCE_MAX + " caracteres: " + controlRef);
        }
        controlRef = controlRef == null ? null : controlRef.trim();
        Objects.requireNonNull(audience, "audience (controlador, operador ou comum) é obrigatória");
        Objects.requireNonNull(conformity, "conformity declarada é obrigatória");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        if (conformity == Conformity.CONFORMING && evidence.isEmpty()) {
            throw new IllegalArgumentException("CONFORMING exige ao menos uma evidência ligada "
                    + "aos registros da P3.40/P3.10: conformidade sem prova não é avaliação");
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
    public static PrivacyClauseAssessment assess(Iso27701Activation activation, String clauseNumber,
                                                 String controlRef, Audience audience,
                                                 Conformity conformity, List<EvidenceRef> evidence,
                                                 String justification, UUID assessedBy,
                                                 LocalDate assessedOn) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new PrivacyClauseAssessment(clauseNumber, controlRef, audience, conformity,
                evidence, justification, assessedBy, assessedOn);
    }

    /**
     * JSON da avaliação, só pela classe {@code Json} do core. As chaves são
     * uma lista fechada de referências e da situação declarada — não há
     * chave capaz de carregar texto da norma.
     */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clauseNumber", clauseNumber);
        map.put("controlRef", controlRef);
        map.put("audience", audience.name());
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
