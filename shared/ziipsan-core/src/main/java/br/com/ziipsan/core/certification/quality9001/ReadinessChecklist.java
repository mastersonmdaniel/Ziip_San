package br.com.ziipsan.core.certification.quality9001;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Checklist de prontidão POR EDIÇÃO da ISO 9001 (ED-69): para a edição
 * em uso, a lista de itens de prontidão por cláusula (referência +
 * pergunta em palavras próprias), cada um PRONTO, PENDENTE ou
 * NÃO_APLICÁVEL justificado. A prontidão é a fração de itens aplicáveis
 * prontos — item não aplicável sem justificativa não sai do denominador
 * por truque: ele é recusado no nascimento do item.
 *
 * <p>Troca de edição: {@link #compareEditions} compara o checklist da
 * edição anterior com o da nova (mesma régua da comparação de cláusulas
 * da ED-76, {@code ClauseMapRevision}: a identidade é o número da
 * cláusula) e devolve incluídos, removidos, alterados (pergunta em
 * palavras próprias ou situação mudaram) e inalterados — é a lista que
 * diz ao tenant o que reavaliar quando migra de 2015 para 2026.
 * Comparar checklists da MESMA edição é recusado: sem troca de edição
 * não há comparação.
 *
 * <p>Conteúdo real (quais itens por edição): o checklist nasce dos itens
 * declarados a partir do mapa de cláusulas curado (ED-76); esta classe é
 * o mecanismo. Nenhum item de norma foi inventado aqui — os testes usam
 * itens sintéticos mínimos assumidamente fictícios.
 *
 * <p>Só com a norma ligada, e a edição do checklist tem de ser a edição
 * da ativação: checklist da edição errada não mede a prontidão certa.
 */
public record ReadinessChecklist(String edition, Iso9001Activation.EditionUse editionUse,
                                 List<ChecklistItem> items) {

    /** Situação de um item do checklist. */
    public enum ItemStatus {
        READY, PENDING, NOT_APPLICABLE
    }

    /** Um item de prontidão: cláusula, pergunta em palavras próprias e situação. */
    public record ChecklistItem(String clauseNumber, String promptOwnWords, ItemStatus status,
                                String justification) {

        private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*");

        public ChecklistItem {
            if (clauseNumber == null || !REFERENCE.matcher(clauseNumber.trim()).matches()
                    || clauseNumber.trim().length() > QualityClauseAssessment.REFERENCE_MAX) {
                throw new IllegalArgumentException("clauseNumber é obrigatório, deve ser só a "
                        + "referência (grupos separados por ponto), sem texto da norma: "
                        + clauseNumber);
            }
            clauseNumber = clauseNumber.trim();
            if (promptOwnWords == null || promptOwnWords.isBlank()) {
                throw new IllegalArgumentException("promptOwnWords é obrigatório: a pergunta de "
                        + "prontidão em palavras próprias, nunca texto da norma");
            }
            promptOwnWords = promptOwnWords.trim();
            Objects.requireNonNull(status, "status do item é obrigatório");
            if (status == ItemStatus.NOT_APPLICABLE
                    && (justification == null || justification.isBlank())) {
                throw new IllegalArgumentException("item NOT_APPLICABLE exige justificativa: "
                        + "item descartado sem motivo continua pendência, não sai da conta");
            }
            justification = justification == null ? "" : justification;
        }
    }

    /** Resultado da comparação entre edições: as quatro listas são disjuntas. */
    public record EditionDiff(String fromEdition, String toEdition,
                              List<ChecklistItem> added, List<ChecklistItem> removed,
                              List<ChecklistItem> changed, List<ChecklistItem> unchanged) {
        public EditionDiff {
            if (added == null || removed == null || changed == null || unchanged == null) {
                throw new IllegalArgumentException("as listas do diff são obrigatórias");
            }
            added = List.copyOf(added);
            removed = List.copyOf(removed);
            changed = List.copyOf(changed);
            unchanged = List.copyOf(unchanged);
        }

        /** Verdadeiro quando a nova edição muda alguma coisa no checklist. */
        public boolean hasChanges() {
            return !added.isEmpty() || !removed.isEmpty() || !changed.isEmpty();
        }
    }

    public ReadinessChecklist {
        Objects.requireNonNull(editionUse, "editionUse é obrigatório");
        if (!Iso9001Activation.TARGET_EDITION.equals(edition)
                && !Iso9001Activation.TRANSITION_EDITION.equals(edition)) {
            throw new IllegalArgumentException("edição do checklist deve ser 2026 (alvo) ou 2015 "
                    + "(transição), conforme " + Iso9001Activation.EDITION_PARAM + ": " + edition);
        }
        if (Iso9001Activation.TARGET_EDITION.equals(edition)
                && editionUse != Iso9001Activation.EditionUse.TARGET) {
            throw new IllegalArgumentException("a edição 2026 é a edição-alvo: checklist dela "
                    + "não é de transição");
        }
        if (Iso9001Activation.TRANSITION_EDITION.equals(edition)
                && editionUse != Iso9001Activation.EditionUse.TRANSITION) {
            throw new IllegalArgumentException("a edição 2015 só aparece em transição para a 2026");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("checklist sem itens não mede prontidão nenhuma");
        }
        items = List.copyOf(items);
        Map<String, ChecklistItem> seen = new LinkedHashMap<>();
        for (ChecklistItem item : items) {
            if (seen.put(item.clauseNumber(), item) != null) {
                throw new IllegalArgumentException("cláusula duplicada no checklist: "
                        + item.clauseNumber());
            }
        }
    }

    /**
     * Nascimento do checklist: só com a norma ligada, e a edição tem de
     * ser exatamente a edição (e o uso) da ativação do tenant.
     */
    public static ReadinessChecklist build(Iso9001Activation activation,
                                           List<ChecklistItem> items) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new ReadinessChecklist(activation.edition(), activation.editionUse(), items);
    }

    /** Item por número de cláusula, ou {@code null} se o checklist não o tem. */
    public ChecklistItem item(String clauseNumber) {
        for (ChecklistItem item : items) {
            if (item.clauseNumber().equals(clauseNumber)) {
                return item;
            }
        }
        return null;
    }

    /** Cláusulas ainda pendentes (aplicáveis e não prontas), na ordem do checklist. */
    public List<String> pendingClauses() {
        return items.stream()
                .filter(i -> i.status() == ItemStatus.PENDING)
                .map(ChecklistItem::clauseNumber)
                .toList();
    }

    /**
     * Prontidão em percentual inteiro (0–100): itens READY sobre os
     * aplicáveis (READY + PENDING). NOT_APPLICABLE justificado fica fora
     * da conta; checklist só de não-aplicáveis devolve 100 (nada aplicável
     * pendente) — leitura declarada, não truque.
     */
    public int readinessPercent() {
        long ready = items.stream().filter(i -> i.status() == ItemStatus.READY).count();
        long applicable = items.stream()
                .filter(i -> i.status() != ItemStatus.NOT_APPLICABLE).count();
        if (applicable == 0) {
            return 100;
        }
        return (int) Math.round(100.0 * ready / applicable);
    }

    /** Pronto para a edição: nenhum item aplicável pendente. */
    public boolean ready() {
        return pendingClauses().isEmpty();
    }

    /**
     * Comparação de cláusulas na troca de edição (aceite da ED-69): o que
     * a edição nova inclui, remove ou altera em relação à anterior. Item
     * presente nas duas com pergunta ou situação diferentes é ALTERADO
     * (a situação pode mudar porque a exigência mudou de sentido na nova
     * edição — reavaliar é o ponto da comparação).
     */
    public static EditionDiff compareEditions(ReadinessChecklist before,
                                              ReadinessChecklist after) {
        Objects.requireNonNull(before, "checklist anterior é obrigatório");
        Objects.requireNonNull(after, "checklist novo é obrigatório");
        if (before.edition().equals(after.edition())) {
            throw new IllegalArgumentException("comparação de cláusulas exige troca de edição: "
                    + before.edition() + " × " + after.edition() + " é a mesma edição");
        }
        List<ChecklistItem> added = new ArrayList<>();
        List<ChecklistItem> removed = new ArrayList<>();
        List<ChecklistItem> changed = new ArrayList<>();
        List<ChecklistItem> unchanged = new ArrayList<>();
        for (ChecklistItem item : after.items()) {
            ChecklistItem previous = before.item(item.clauseNumber());
            if (previous == null) {
                added.add(item);
            } else if (!previous.promptOwnWords().equals(item.promptOwnWords())
                    || previous.status() != item.status()) {
                changed.add(item);
            } else {
                unchanged.add(item);
            }
        }
        for (ChecklistItem item : before.items()) {
            if (after.item(item.clauseNumber()) == null) {
                removed.add(item);
            }
        }
        return new EditionDiff(before.edition(), after.edition(), added, removed, changed,
                unchanged);
    }
}
