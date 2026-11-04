package br.com.ziipsan.commercial.registry.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor em memória (testes e alimentação programática): as linhas já
 * chegam prontas, sem formato de arquivo. É também a porta pela qual um
 * outro importador (no futuro, o genérico da P1.40) pode alimentar o motor
 * de legado sem reescrever o mapeamento — a fronteira é a linha, não o
 * arquivo.
 */
public final class InMemoryLegacyReader implements LegacySourceReader {

    private final List<Map<String, String>> rows;

    public InMemoryLegacyReader(List<Map<String, String>> rows) {
        this.rows = rows == null ? List.of() : List.copyOf(rows);
    }

    @Override
    public List<LegacyRow> read() {
        List<LegacyRow> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            // Map.copyOf rejeita null — e valor ausente É null por contrato.
            out.add(new LegacyRow(i + 1, Collections.unmodifiableMap(new LinkedHashMap<>(rows.get(i)))));
        }
        return List.copyOf(out);
    }
}
