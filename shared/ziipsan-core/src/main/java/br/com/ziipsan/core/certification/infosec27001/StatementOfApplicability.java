package br.com.ziipsan.core.certification.infosec27001;

import br.com.ziipsan.core.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Declaração de Aplicabilidade (SoA) completa da ISO/IEC 27001:2022
 * (ED-73): exatamente {@value #EXPECTED_CONTROLS} controles do Anexo A,
 * um por chave de referência, exportável.
 *
 * <p>Esta classe NÃO conhece nenhum controle: as 93 chaves são declaradas
 * por quem monta a declaração (tenant/curadoria, após a decisão do dono
 * sobre licença × reescrita dos identificadores e títulos — pendência
 * nomeada da ED-73). O que a régua garante é a ESTRUTURA do aceite:
 * contagem exata, chave única, exportação determinística pela classe
 * {@code Json} do core e o retrato honesto das pendências (quantos
 * títulos ainda estão {@code [A DEFINIR]}, quantas exclusões justificadas,
 * quantos controles aplicáveis ainda sem implementação concluída).
 *
 * <p>JSON somente pela classe {@link Json} do core (regra do projeto).
 *
 * <p><b>Portão de ativação no construtor (correção D1 da revisão):</b> a
 * ativação é componente do registro e o construtor canônico a exige
 * ligada — como cada linha já nasce amarrada a uma ativação ligada, a
 * declaração inteira fica inalcançável com a norma desligada, e não só
 * pela fábrica {@link #build}.
 */
public record StatementOfApplicability(Iso27001Activation activation,
                                       List<AnnexAControlEntry> entries) {

    /** Número de controles do Anexo A da edição 2022 fixado no aceite da ED-73. */
    public static final int EXPECTED_CONTROLS = 93;

    /** Aviso fixo que acompanha a exportação (ADR-0041, em palavras do produto). */
    public static final String SELF_ASSESSMENT_NOTICE =
            "Declaração de Aplicabilidade como registro do cliente no módulo de certificação: "
                    + "não afirma certificação, que é concedida por organismo de certificação "
                    + "acreditado após auditoria.";

    /** Pendência nomeada dos títulos/identificadores, citada em toda exportação. */
    public static final String TITLES_PENDING_NOTE =
            "Títulos e identificadores do Anexo A aguardam decisão do dono (licença × reescrita): "
                    + "linhas com título [A DEFINIR] são pendência declarada, não conteúdo da norma.";

    public StatementOfApplicability {
        Objects.requireNonNull(activation, "activation é obrigatória: a SoA só nasce no contexto "
                + "da ativação da norma (correção D1 da revisão da ED-73)");
        activation.requireEnabled();
        Objects.requireNonNull(entries, "entries é obrigatório");
        entries = List.copyOf(entries);
        for (AnnexAControlEntry entry : entries) {
            // Defesa em profundidade: a linha já exige ativação ligada no
            // próprio construtor; a SoA reconfere o contexto de cada linha.
            Objects.requireNonNull(entry.activation(), "linha da SoA sem contexto de ativação");
            entry.activation().requireEnabled();
        }
        if (entries.size() != EXPECTED_CONTROLS) {
            throw new IllegalArgumentException("a Declaração de Aplicabilidade da edição 2022 tem "
                    + "exatamente " + EXPECTED_CONTROLS + " controles do Anexo A; recebidos "
                    + entries.size() + " — declaração incompleta ou excedente não é SoA");
        }
        long distinct = entries.stream().map(AnnexAControlEntry::controlKey).distinct().count();
        if (distinct != entries.size()) {
            throw new IllegalArgumentException("chave de controle duplicada na SoA: cada controle "
                    + "do Anexo A aparece uma única vez, por chave de referência");
        }
    }

    /** Montagem da SoA: só com a norma ligada (porta da ativação). */
    public static StatementOfApplicability build(Iso27001Activation activation,
                                                 List<AnnexAControlEntry> entries) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new StatementOfApplicability(activation, entries);
    }

    /** Verdadeiro quando a chave pertence à declaração (base da conferência da matriz, D3). */
    public boolean containsControl(String controlKey) {
        return controlKey != null
                && entries.stream().anyMatch(e -> e.controlKey().equals(controlKey.trim()));
    }

    /** Quantas linhas ainda aguardam o título (decisão do dono pendente). */
    public long titlesPending() {
        return entries.stream().filter(AnnexAControlEntry::titlePending).count();
    }

    /** Quantas linhas são exclusões justificadas (permitidas pelo aceite). */
    public long justifiedExclusions() {
        return entries.stream().filter(AnnexAControlEntry::justifiedExclusion).count();
    }

    /** Quantos controles aplicáveis ainda não concluíram a implementação. */
    public long applicableNotImplemented() {
        return entries.stream()
                .filter(e -> e.applicability() == AnnexAControlEntry.Applicability.APPLICABLE)
                .filter(e -> e.status() != AnnexAControlEntry.ImplementationStatus.IMPLEMENTED)
                .count();
    }

    /**
     * Exportação da SoA em JSON, só pela classe {@code Json} do core:
     * chaves fechadas por linha (referência, título declarado ou marcador,
     * aplicabilidade, justificativa, estado, evidências por tipo + ref,
     * responsável, última revisão), com o aviso de autoavaliação e a nota
     * da pendência dos títulos. Nenhuma chave carrega texto da norma.
     */
    public String exportJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("standard", "ISO/IEC 27001");
        map.put("edition", Iso27001Activation.TARGET_EDITION);
        map.put("amendment", Iso27001Activation.TARGET_AMENDMENT);
        map.put("expectedControls", EXPECTED_CONTROLS);
        map.put("titlesPending", titlesPending());
        map.put("justifiedExclusions", justifiedExclusions());
        map.put("applicableNotImplemented", applicableNotImplemented());
        map.put("controls", entries.stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("controlKey", e.controlKey());
            row.put("title", e.title());
            row.put("titlePending", e.titlePending());
            row.put("applicability", e.applicability().name());
            row.put("justification", e.justification());
            row.put("status", e.status().name());
            row.put("evidence", e.evidence().stream()
                    .map(r -> Map.of("kind", r.kind().name(), "ref", r.ref())).toList());
            row.put("ownerId", e.ownerId().toString());
            row.put("lastReviewedOn", e.lastReviewedOn().toString());
            return row;
        }).toList());
        map.put("notice", SELF_ASSESSMENT_NOTICE);
        map.put("titlesPendingNote", TITLES_PENDING_NOTE);
        return Json.write(map);
    }
}
