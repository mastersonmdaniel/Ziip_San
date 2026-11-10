package br.com.ziipsan.operations.metering.critique;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fila de excepcoes em memoria (II-2, E3). Ordenacao deterministica:
 * referencia, depois UUID da unidade, depois codigo da primeira regra.
 * A persistencia e a SQL em {@code db/pendente/} (prefixo II-2, sem numero
 * Flyway); esta classe e o nucleo de comportamento que a fiacao usa.
 */
public final class ExceptionQueue {

    private static final Comparator<CritiqueException> ORDER = Comparator
            .comparing(CritiqueException::reference)
            .thenComparing(e -> e.unitId().toString())
            .thenComparing(CritiqueException::firstRuleCode);

    private final List<CritiqueException> entries = new ArrayList<>();

    /** Enfileira o resultado criticado; resultado valido (sem disparos) NAO entra. */
    public CritiqueException enqueue(CritiqueResult result) {
        if (result.valid()) {
            throw new IllegalArgumentException("resultado valido nao gera excepcao");
        }
        CritiqueException exception = CritiqueException.from(result);
        entries.add(exception);
        return exception;
    }

    /** Todas as excepcoes, na ordem deterministica da fila. */
    public List<CritiqueException> ordered() {
        List<CritiqueException> copy = new ArrayList<>(entries);
        copy.sort(ORDER);
        return List.copyOf(copy);
    }

    /** So as pendentes de decisao (PENDING ou a aguardar releitura), na ordem da fila. */
    public List<CritiqueException> open() {
        return ordered().stream()
                .filter(e -> e.status() == ExceptionStatus.PENDING
                        || e.status() == ExceptionStatus.REREADING_REQUESTED)
                .toList();
    }
}
