package br.com.sigsan.commercial.collection.returnfile;

import br.com.sigsan.commercial.collection.returnfile.ReturnFileException.Reason;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leitor do arquivo de retorno de arrecadação FEBRABAN de 150 posições (registros A, G e Z).
 *
 * <p>Posições (1-based) conforme o layout FEBRABAN versão 08, seção 06:
 * <pre>
 * A: 1 tipo "A" | 2 remessa (2 = retorno) | 3-22 convênio | 23-42 nome da empresa | 43-45 banco
 *    46-65 nome do banco | 66-73 data AAAAMMDD | 74-79 NSA | 80-81 versão do layout
 *    82-98 "CÓDIGO DE BARRAS" | 99-150 reservado
 * G: 1 tipo "G" | 2-21 agência/conta | 22-29 pagamento AAAAMMDD | 30-37 crédito AAAAMMDD
 *    38-81 código de barras | 82-93 valor recebido 9(10)V99 | 94-100 tarifa 9(5)V99 | 101-108 NSR
 *    109-116 agência arrecadadora | 117 forma de captura | 118-140 autenticação | 141 forma de
 *    pagamento | 142-150 reservado
 * Z: 1 tipo "Z" | 2-7 total de registros | 8-24 valor total 9(15)V99 | 25-150 reservado
 * </pre>
 *
 * <p>O parser só verifica a <b>estrutura</b> (tamanho de linha, tipo de registro, campos numéricos
 * e datas, domínio de remessa, captura e forma de pagamento). Verificações de negócio (NSA,
 * convênio, banco, trailer) ficam em {@link Febraban150Validator}, que recebe o contexto esperado.
 * O texto deve estar em ISO-8859-1 (o registro A carrega "CÓDIGO DE BARRAS" acentuado).
 */
public final class Febraban150ReturnParser {

    public static final int RECORD_LENGTH = 150;
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String CAPTURE_FORMS = "123456789abcdefghil";

    private Febraban150ReturnParser() {
    }

    public static Febraban150File parse(byte[] bytes) {
        return parse(new String(bytes, StandardCharsets.ISO_8859_1));
    }

    public static Febraban150File parse(String content) {
        List<String> lines = new ArrayList<>();
        for (String l : content.split("\r?\n", -1)) {
            lines.add(l);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty()) {
            throw new ReturnFileException(Reason.EMPTY_FILE, 0, "arquivo vazio");
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).length() != RECORD_LENGTH) {
                throw new ReturnFileException(Reason.BAD_LINE_LENGTH, i + 1,
                        "registro com " + lines.get(i).length() + " posições; esperado " + RECORD_LENGTH);
            }
        }
        if (lines.get(0).charAt(0) != 'A') {
            throw new ReturnFileException(Reason.MISSING_HEADER, 1, "o primeiro registro deve ser A (header)");
        }
        if (lines.size() < 2 || lines.get(lines.size() - 1).charAt(0) != 'Z') {
            throw new ReturnFileException(Reason.MISSING_TRAILER, lines.size(), "o último registro deve ser Z (trailer)");
        }

        Febraban150File.Header header = header(lines.get(0));
        List<Febraban150File.Payment> payments = new ArrayList<>();
        for (int i = 1; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            if (line.charAt(0) != 'G') {
                throw new ReturnFileException(Reason.UNKNOWN_RECORD_TYPE, i + 1,
                        "registro '" + line.charAt(0) + "' entre header e trailer; esperado G");
            }
            payments.add(payment(i + 1, line));
        }
        Febraban150File.Trailer trailer = trailer(lines.size(), lines.get(lines.size() - 1));
        return new Febraban150File(header, payments, trailer, lines);
    }

    private static Febraban150File.Header header(String l) {
        int n = 1;
        if (l.charAt(1) != '2') {
            throw new ReturnFileException(Reason.NOT_A_RETURN_FILE, n,
                    "A.02 deve ser 2 (retorno, do banco para a empresa): '" + l.charAt(1) + "'");
        }
        String service = stripAccents(l.substring(81, 98)).trim().toUpperCase(Locale.ROOT);
        if (!service.equals("CODIGO DE BARRAS")) {
            throw new ReturnFileException(Reason.BAD_SERVICE_ID, n,
                    "A.10 deve ser CÓDIGO DE BARRAS: '" + l.substring(81, 98).trim() + "'");
        }
        return new Febraban150File.Header(
                l.substring(2, 22).trim(),
                l.substring(22, 42).trim(),
                digits(l, 42, 45, n, "A.05 código do banco"),
                l.substring(45, 65).trim(),
                date(l, 65, 73, n, "A.07 data de geração"),
                Long.parseLong(digits(l, 73, 79, n, "A.08 NSA")),
                Integer.parseInt(digits(l, 79, 81, n, "A.09 versão do layout")),
                l.substring(81, 98).trim());
    }

    private static Febraban150File.Payment payment(int n, String l) {
        char capture = l.charAt(116);
        if (CAPTURE_FORMS.indexOf(capture) < 0) {
            throw new ReturnFileException(Reason.BAD_CODE_FIELD, n,
                    "G.10 forma de captura inválida: '" + capture + "'");
        }
        int form = Integer.parseInt(digits(l, 140, 141, n, "G.12 forma de pagamento"));
        if (form < 1 || form > 6) {
            throw new ReturnFileException(Reason.BAD_CODE_FIELD, n, "G.12 forma de pagamento fora de 1-6: " + form);
        }
        return new Febraban150File.Payment(
                n,
                l.substring(1, 21).trim(),
                date(l, 21, 29, n, "G.03 data de pagamento"),
                date(l, 29, 37, n, "G.04 data de crédito"),
                l.substring(37, 81).trim(),
                money(l, 81, 93, n, "G.06 valor recebido"),
                money(l, 93, 100, n, "G.07 tarifa"),
                Long.parseLong(digits(l, 100, 108, n, "G.08 NSR")),
                l.substring(108, 116).trim(),
                capture,
                l.substring(117, 140).trim(),
                form,
                l);
    }

    private static Febraban150File.Trailer trailer(int n, String l) {
        return new Febraban150File.Trailer(
                Integer.parseInt(digits(l, 1, 7, n, "Z.02 total de registros")),
                money(l, 7, 24, n, "Z.03 valor total"));
    }

    // ---------------------------------------------------------------- campos

    private static String digits(String l, int from, int to, int line, String field) {
        String s = l.substring(from, to);
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                throw new ReturnFileException(Reason.BAD_NUMERIC_FIELD, line,
                        field + " deve ser numérico: '" + s + "'");
            }
        }
        return s;
    }

    private static BigDecimal money(String l, int from, int to, int line, String field) {
        return new BigDecimal(new java.math.BigInteger(digits(l, from, to, line, field)), 2);
    }

    private static LocalDate date(String l, int from, int to, int line, String field) {
        String s = l.substring(from, to);
        try {
            return LocalDate.parse(s, YYYYMMDD);
        } catch (DateTimeException e) {
            throw new ReturnFileException(Reason.BAD_DATE_FIELD, line, field + " não é uma data AAAAMMDD: '" + s + "'");
        }
    }

    private static String stripAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
