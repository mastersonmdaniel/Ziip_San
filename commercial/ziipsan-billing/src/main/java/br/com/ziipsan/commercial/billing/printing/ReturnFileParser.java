package br.com.ziipsan.commercial.billing.printing;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Leitor do arquivo de retorno de entrega (P2.8, JOR-35): o arquivo que a
 * grafica (ou a operacao de entrega) devolve com as contas nao entregues do
 * lote. Uma linha por retorno, no mesmo espirito do arquivo de ida:
 *
 * <pre>RET|CONTA=&lt;uuid&gt;|UNIDADE=&lt;uuid&gt;|MOTIVO=&lt;chave do catalogo&gt;|DATA=&lt;yyyy-MM-dd&gt;|ORIGEM=&lt;chave do catalogo&gt;</pre>
 *
 * <p>Linha malformada, motivo fora do catalogo ou data invalida recusam o
 * arquivo INTEIRO nomeando a linha: importar metade dos retornos gravaria
 * uma qualidade cadastral falsa para a P3.32. Linhas em branco sao
 * ignoradas; qualquer outra tag que nao {@code RET} e erro explicito.
 */
public final class ReturnFileParser {

    public static final String RETURN_TAG = "RET";

    private ReturnFileParser() {
    }

    public static List<UndeliveredBill> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("arquivo de retorno vazio: nenhum retorno importado");
        }
        List<UndeliveredBill> returns = new ArrayList<>();
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            int lineNo = i + 1;
            String[] parts = line.split("\\" + PrintLayout.DELIMITER, -1);
            if (!RETURN_TAG.equals(parts[0])) {
                throw new IllegalArgumentException("Linha " + lineNo + " do arquivo de retorno sem a tag "
                        + RETURN_TAG + ": " + parts[0]);
            }
            String bill = null;
            String unit = null;
            String reason = null;
            String date = null;
            String origin = null;
            java.util.Set<String> seenKeys = new java.util.HashSet<>();
            for (int p = 1; p < parts.length; p++) {
                int eq = parts[p].indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException("Linha " + lineNo + " do arquivo de retorno com campo sem '=': " + parts[p]);
                }
                String key = parts[p].substring(0, eq);
                String value = parts[p].substring(eq + 1);
                // [Correcao da revisao cruzada] Campo repetido na mesma linha
                // e linha malformada: sem esta trava o ultimo valor vencia em
                // silencio e um motivo duplicado podia ser gravado errado.
                if (!seenKeys.add(key)) {
                    throw new IllegalArgumentException("Linha " + lineNo
                            + " do arquivo de retorno com campo duplicado: " + key);
                }
                switch (key) {
                    case "CONTA" -> bill = value;
                    case "UNIDADE" -> unit = value;
                    case "MOTIVO" -> reason = value;
                    case "DATA" -> date = value;
                    case "ORIGEM" -> origin = value;
                    default -> throw new IllegalArgumentException(
                            "Linha " + lineNo + " do arquivo de retorno com campo desconhecido: " + key);
                }
            }
            try {
                returns.add(new UndeliveredBill(
                        UUID.fromString(required(bill, "CONTA", lineNo)),
                        UUID.fromString(required(unit, "UNIDADE", lineNo)),
                        UndeliveredReason.fromKey(required(reason, "MOTIVO", lineNo)),
                        LocalDate.parse(required(date, "DATA", lineNo)),
                        ReturnOrigin.fromKey(required(origin, "ORIGEM", lineNo))));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new IllegalArgumentException("Linha " + lineNo + " do arquivo de retorno invalida: "
                        + e.getMessage(), e);
            }
        }
        if (returns.isEmpty()) {
            throw new IllegalArgumentException("arquivo de retorno sem nenhuma linha " + RETURN_TAG);
        }
        return List.copyOf(returns);
    }

    private static String required(String value, String key, int lineNo) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("campo " + key + " ausente na linha " + lineNo);
        }
        return value.strip();
    }
}
