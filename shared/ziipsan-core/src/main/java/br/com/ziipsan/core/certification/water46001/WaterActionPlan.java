package br.com.ziipsan.core.certification.water46001;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plano de acao da ISO 46001 (ED-81) LIGADO AOS OBJETIVOS DA P3.31
 * (aceite expresso): cada plano nasce vinculado a um objetivo do
 * registro de governanca da P3.31 ({@code cert_objectives}, V21_3),
 * cuja existencia o servico confere por porta — objetivo
 * inexistente recusa o plano (nenhum objetivo paralelo nasce aqui).
 *
 * <p>Cada acao tem responsavel e prazo declarados. A economia
 * esperada ({@code expectedSavingM3OrNull}) e ESTIMATIVA do tenant:
 * ausente = [A DEFINIR], nunca um numero presumido por esta entrega.
 */
public record WaterActionPlan(Iso46001Activation activation, UUID objectiveId,
                              String installationRef, String title, List<ActionItem> items) {

    public record ActionItem(String description, String owner, LocalDate dueDate,
                             Double expectedSavingM3OrNull) {
        public ActionItem {
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("descricao da acao obrigatoria");
            if (owner == null || owner.isBlank())
                throw new IllegalArgumentException("responsavel da acao obrigatorio");
            Objects.requireNonNull(dueDate, "prazo da acao obrigatorio");
            if (expectedSavingM3OrNull != null
                    && (!Double.isFinite(expectedSavingM3OrNull) || expectedSavingM3OrNull < 0))
                throw new IllegalArgumentException("economia esperada invalida: " + expectedSavingM3OrNull);
            description = description.trim();
            owner = owner.trim();
        }
    }

    public WaterActionPlan {
        Objects.requireNonNull(activation, "ativacao da ISO 46001 obrigatoria");
        activation.requireEnabled();
        activation.requireInstallationInScope(installationRef);
        Objects.requireNonNull(objectiveId,
                "objetivo da P3.31 obrigatorio: plano de acao sem objetivo nao nasce (aceite ED-81)");
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("titulo do plano de acao obrigatorio");
        Objects.requireNonNull(items, "acoes do plano obrigatorias");
        if (items.isEmpty())
            throw new IllegalArgumentException("plano de acao sem nenhuma acao nao nasce");
        title = title.trim();
        installationRef = installationRef.trim();
        items = List.copyOf(items);
    }

    /** Economia esperada total SOMENTE se TODAS as acoes a declararam; senao, ausente. */
    public Double totalExpectedSavingM3OrNull() {
        if (items.stream().anyMatch(i -> i.expectedSavingM3OrNull() == null)) return null;
        return items.stream().mapToDouble(ActionItem::expectedSavingM3OrNull).sum();
    }
}
