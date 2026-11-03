package br.com.ziipsan.commercial.fiscal.rules;

import br.com.ziipsan.core.document.DocumentNumber;

/**
 * Id da DPS da NFS-e Nacional, 42 posições (FISCAL-PORTE-JAVA §1):
 * IBGE do município (7) + tipo de inscrição (1) + inscrição (14) + série (5) + número (15).
 *
 * <p>Inscrição: o campo tem 14 posições. CNPJ alfanumérico entra como está (12 alfanuméricas +
 * 2 dígitos verificadores, validados por {@link DocumentNumber}); CPF (11 dígitos) entra
 * completado com zeros à esquerda até 14 posições, que é o preenchimento do campo de 14
 * posições do leiaute. O preenchimento do CPF é a única regra desta classe que não está
 * escrita no documento de porte: ela está marcada para confirmação contra o XSD/manual da
 * NFS-e na P1.8, antes de qualquer homologação. O tipo é um dígito só, sem semântica
 * afirmada aqui: os testes usam {@code '1'} para CPF e {@code '2'} para CNPJ, os valores
 * usados nos testes do C#, a confirmar no manual.
 */
public final class DpsId {

    /** Tipo usado nos testes do C# para CPF (a confirmar no manual). */
    public static final char TYPE_CPF = '1';

    /** Tipo usado nos testes do C# para CNPJ (a confirmar no manual). */
    public static final char TYPE_CNPJ = '2';

    private DpsId() {
    }

    /** Monta o Id a partir dos campos já no tamanho do leiaute; valida cada campo. */
    public static String of(String ibge, char type, String inscription, String series, String number) {
        if (ibge == null || !ibge.matches("\\d{7}")) {
            throw new IllegalArgumentException("O IBGE do municipio deve ter 7 digitos");
        }
        if (!Character.isDigit(type)) {
            throw new IllegalArgumentException("O tipo de inscricao deve ser um digito");
        }
        String normalized = DocumentNumber.normalize(inscription);
        if (normalized.length() != 14 || !normalized.chars().allMatch(c -> isAlnum((char) c))) {
            throw new IllegalArgumentException("A inscricao deve ter 14 posicoes alfanumericas");
        }
        if (series == null || !series.matches("\\d{5}")) {
            throw new IllegalArgumentException("A serie deve ter 5 digitos");
        }
        if (number == null || !number.matches("\\d{15}")) {
            throw new IllegalArgumentException("O numero deve ter 15 digitos");
        }
        return ibge + type + normalized + series + number;
    }

    /** Monta o Id para um emitente CPF: o CPF de 11 dígitos é completado com zeros à esquerda. */
    public static String ofCpf(String ibge, String cpf, String series, String number) {
        String normalized = DocumentNumber.normalize(cpf);
        if (!DocumentNumber.isValidCpf(normalized)) {
            throw new IllegalArgumentException("O CPF do emitente e invalido");
        }
        return of(ibge, TYPE_CPF, "000" + normalized, series, number);
    }

    /** Monta o Id para um emitente CNPJ (inclusive alfanumérico). */
    public static String ofCnpj(String ibge, String cnpj, String series, String number) {
        String normalized = DocumentNumber.normalize(cnpj);
        if (!DocumentNumber.isValidCnpj(normalized)) {
            throw new IllegalArgumentException("O CNPJ do emitente e invalido");
        }
        return of(ibge, TYPE_CNPJ, normalized, series, number);
    }

    /** Id de evento de cancelamento e101101: {@code PRE} + chave de acesso (50) + tipo (6). */
    public static String eventId(String accessKey, String eventType) {
        if (accessKey == null || accessKey.length() != 50) {
            throw new IllegalArgumentException("A chave de acesso do evento deve ter 50 posicoes");
        }
        if (eventType == null || eventType.length() != 6) {
            throw new IllegalArgumentException("O tipo do evento deve ter 6 posicoes");
        }
        return "PRE" + accessKey + eventType;
    }

    private static boolean isAlnum(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z');
    }
}
