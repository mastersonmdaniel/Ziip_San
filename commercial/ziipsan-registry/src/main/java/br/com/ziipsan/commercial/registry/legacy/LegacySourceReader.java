package br.com.ziipsan.commercial.registry.legacy;

import java.util.List;
import java.util.Map;

/**
 * Leitor de fonte do legado (P1.2): dump SQL, CSV e banco legado falam a
 * mesma língua para o motor — uma lista de linhas, cada linha um mapa
 * coluna → valor bruto (null quando ausente). O motor nunca vê o formato
 * da fonte; um leitor novo não muda o motor.
 *
 * <p>Os valores são sempre texto: a tipagem acontece na transformação do
 * mapeamento, não na leitura. O número da linha ({@code rowNo}, a partir
 * de 1, contando o cabeçalho do CSV e cada INSERT do dump) é o que o
 * relatório de recusas usa para apontar a origem.
 */
public interface LegacySourceReader {

    /** Uma linha da fonte, com o número da linha de origem para o relatório. */
    record LegacyRow(int rowNo, Map<String, String> values) {
        /** Valor bruto da coluna (case-insensitive no nome, como o cabeçalho do CSV). */
        public String get(String column) {
            if (column == null) {
                return null;
            }
            String direct = values.get(column);
            if (direct != null) {
                return direct;
            }
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(column)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }

    List<LegacyRow> read();
}
