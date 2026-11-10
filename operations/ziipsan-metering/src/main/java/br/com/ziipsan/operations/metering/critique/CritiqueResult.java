package br.com.ziipsan.operations.metering.critique;

import java.util.List;
import java.util.UUID;
import java.time.YearMonth;

/**
 * Resultado da critica de uma leitura (II-2). {@code valid} = nenhum
 * disparo. Os disparos vem na ordem das regras da configuracao
 * (deterministico: mesma submissao + mesma configuracao = mesmo resultado).
 */
public record CritiqueResult(UUID unitId, YearMonth reference, List<RuleFinding> findings) {

    public CritiqueResult {
        findings = List.copyOf(findings);
    }

    public boolean valid() {
        return findings.isEmpty();
    }
}
