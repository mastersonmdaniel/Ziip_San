package br.com.ziipsan.core.slaos;

import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Exigencias por tipo no encerramento + codificacao obrigatoria (II-4).
 *
 * <p>Porta/extensao de encerramento: este portao e avaliado ANTES de o
 * chamador delegar o fecho ao motor da P1.31
 * ({@code ServiceOrderService.closeExecuted}); a maquina de estados da
 * P1.31 NAO e alterada — o portao so recusa ou libera a delegacao.
 * Como no motor, so uma OS {@code EXECUTED} pode fechar como executada.
 *
 * <p>Exigencias por tipo (fotos/checklist/documentos) sao configuracao:
 * tipo sem exigencias declaradas fecha sem elas. Fotos contam apenas as
 * LIBERADAS pela varredura (ADR-0020, como em {@code OrderAttachment} da
 * ED-36): a evidencia traz a contagem ja filtrada por CLEARED.
 *
 * <p>Toda a recusa e TIPIFICADA ({@link RefusalKind}); o encerramento ou
 * passa inteiro ou devolve a lista completa das recusas — nunca uma
 * recusa generica.
 */
public final class CloseGate {

    /** Exigencias declaradas para um tipo de OS (configuracao; conjuntos podem ser vazios). */
    public record Requirements(int minClearedPhotos, Set<String> requiredChecklistItems,
                               Set<String> requiredDocumentKinds) {
        public Requirements {
            if (minClearedPhotos < 0) throw new IllegalArgumentException("minimo de fotos nao pode ser negativo");
            requiredChecklistItems = Set.copyOf(Objects.requireNonNull(requiredChecklistItems, "checklist exigido e obrigatorio (pode ser vazio)"));
            requiredDocumentKinds = Set.copyOf(Objects.requireNonNull(requiredDocumentKinds, "documentos exigidos sao obrigatorios (pode ser vazio)"));
        }
        public static Requirements none() { return new Requirements(0, Set.of(), Set.of()); }
    }

    /** Evidencia apresentada no encerramento (fotos ja filtradas por varredura CLEARED). */
    public record Evidence(int clearedPhotoCount, Set<String> completedChecklistItems,
                           Set<String> presentDocumentKinds) {
        public Evidence {
            if (clearedPhotoCount < 0) throw new IllegalArgumentException("contagem de fotos nao pode ser negativa");
            completedChecklistItems = Set.copyOf(Objects.requireNonNull(completedChecklistItems, "checklist cumprido e obrigatorio (pode ser vazio)"));
            presentDocumentKinds = Set.copyOf(Objects.requireNonNull(presentDocumentKinds, "documentos presentes sao obrigatorios (pode ser vazio)"));
        }
    }

    public enum RefusalKind {
        ORDER_NOT_EXECUTED,
        MISSING_PHOTOS,
        MISSING_CHECKLIST_ITEM,
        MISSING_DOCUMENT,
        MISSING_PROBLEM_CAUSE_REMEDY,
        INVALID_PROBLEM_CAUSE_REMEDY
    }

    public record Refusal(RefusalKind kind, String detail) {
        public Refusal {
            Objects.requireNonNull(kind, "tipo da recusa e obrigatorio");
            Objects.requireNonNull(detail, "detalhe da recusa e obrigatorio");
        }
    }

    /** Recusa tipificada do encerramento: carrega TODAS as recusas encontradas. */
    public static final class CloseBlockedException extends RuntimeException {
        private final List<Refusal> refusals;
        public CloseBlockedException(List<Refusal> refusals) {
            super("encerramento bloqueado: " + refusals);
            this.refusals = List.copyOf(refusals);
        }
        public List<Refusal> refusals() { return refusals; }
    }

    private CloseGate() {}

    /** Avaliacao pura: lista vazia = encerramento liberado. */
    public static List<Refusal> evaluate(ServiceOrderStatus status, Requirements requirements,
                                         Evidence evidence, ProblemCauseRemedy codingOrNull,
                                         ProblemCauseRemedy.PcrCatalog catalog) {
        Objects.requireNonNull(status, "estado da OS e obrigatorio");
        Objects.requireNonNull(requirements, "exigencias do tipo sao obrigatorias");
        Objects.requireNonNull(evidence, "evidencia do encerramento e obrigatoria");
        Objects.requireNonNull(catalog, "catalogo problema-causa-remedio e obrigatorio");
        List<Refusal> out = new ArrayList<>();
        if (status != ServiceOrderStatus.EXECUTED) {
            out.add(new Refusal(RefusalKind.ORDER_NOT_EXECUTED,
                    "so EXECUTED fecha como executada (a ordem esta " + status + ")"));
            return List.copyOf(out);
        }
        if (evidence.clearedPhotoCount() < requirements.minClearedPhotos()) {
            out.add(new Refusal(RefusalKind.MISSING_PHOTOS,
                    "fotos liberadas " + evidence.clearedPhotoCount() + " < exigidas " + requirements.minClearedPhotos()));
        }
        for (String item : requirements.requiredChecklistItems().stream().sorted().toList()) {
            if (!evidence.completedChecklistItems().contains(item)) {
                out.add(new Refusal(RefusalKind.MISSING_CHECKLIST_ITEM, item));
            }
        }
        for (String doc : requirements.requiredDocumentKinds().stream().sorted().toList()) {
            if (!evidence.presentDocumentKinds().contains(doc)) {
                out.add(new Refusal(RefusalKind.MISSING_DOCUMENT, doc));
            }
        }
        if (codingOrNull == null) {
            out.add(new Refusal(RefusalKind.MISSING_PROBLEM_CAUSE_REMEDY,
                    "codificacao problema-causa-remedio e obrigatoria no encerramento"));
        } else {
            catalog.validate(codingOrNull).ifPresent(reason ->
                    out.add(new Refusal(RefusalKind.INVALID_PROBLEM_CAUSE_REMEDY, reason.name())));
        }
        return List.copyOf(out);
    }

    /** Forma imperativa: lanca {@link CloseBlockedException} se houver qualquer recusa. */
    public static void requireClosable(ServiceOrderStatus status, Requirements requirements,
                                       Evidence evidence, ProblemCauseRemedy codingOrNull,
                                       ProblemCauseRemedy.PcrCatalog catalog) {
        List<Refusal> refusals = evaluate(status, requirements, evidence, codingOrNull, catalog);
        if (!refusals.isEmpty()) throw new CloseBlockedException(refusals);
    }
}
