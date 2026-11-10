package br.com.ziipsan.commercial.collection.cession;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ED-103/E5 (decisao 62) — movimento da conta vinculada, no formato
 * generico DECLARADO deste ciclo. Produtor (execucao ED-103,
 * decisao 62): sessao 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p><b>Leiaute generico declarado (NAO e leiaute oficial):</b> o leiaute
 * real de banco/fundo/registradora e [SEM FONTE] (pendencia
 * P-ED103-INT-02). O formato de importacao aqui definido e texto com uma
 * linha por movimento, campos separados por ';':
 * {@code referencia;data;valor} — referencia = identificador do
 * recebivel, data ISO (AAAA-MM-DD), valor decimal com '.' — sem linha de
 * cabecalho; linhas vazias sao ignoradas; qualquer linha malformada
 * recusa a importacao inteira com erro tipificado (nada e importado
 * pela metade, nada e ajustado em silencio).
 */
public record LinkedAccountMovement(String reference, LocalDate date, BigDecimal amount) {

    public LinkedAccountMovement {
        if (reference == null || reference.isBlank())
            throw new ReceivablesCessionException("MOVEMENT_REFERENCE_REQUIRED", "movimento exige a referencia do recebivel");
        Objects.requireNonNull(date, "movimento exige a data");
        Objects.requireNonNull(amount, "movimento exige o valor");
    }

    /** Importa o ficheiro de movimentos no formato generico declarado. */
    public static List<LinkedAccountMovement> parseDeclaredFormat(String content) {
        if (content == null)
            throw new ReceivablesCessionException("MOVEMENT_FILE_REQUIRED", "ficheiro de movimentos e obrigatorio");
        List<LinkedAccountMovement> out = new ArrayList<>();
        String[] lines = content.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(";", -1);
            if (parts.length != 3)
                throw new ReceivablesCessionException("MOVEMENT_LINE_MALFORMED",
                        "linha " + (i + 1) + " do ficheiro de movimentos fora do formato generico declarado (referencia;data;valor): " + line);
            try {
                out.add(new LinkedAccountMovement(parts[0].trim(),
                        LocalDate.parse(parts[1].trim()), new BigDecimal(parts[2].trim())));
            } catch (RuntimeException e) {
                if (e instanceof ReceivablesCessionException rce) throw rce;
                throw new ReceivablesCessionException("MOVEMENT_LINE_MALFORMED",
                        "linha " + (i + 1) + " do ficheiro de movimentos com data/valor invalidos: " + line);
            }
        }
        return List.copyOf(out);
    }
}
