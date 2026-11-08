package br.com.ziipsan.core.transparency;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dados abertos (ED-99): conjunto publicado em formato LEGIVEL POR
 * MAQUINA (CSV ou JSON) e SEM DADO PESSOAL, sob a trava de
 * {@code pub.open_data_enabled}.
 *
 * <p>Guarda de dado pessoal por construcao: o nome de cada campo e
 * conferido contra a lista de campos pessoais vedados (documentos,
 * identidade e contato de pessoa natural) ANTES de qualquer linha ser
 * aceita — conjunto com campo vedado e recusado inteiro, nunca filtrado
 * em silencio. A conferencia e conservadora por desenho (falha fechada
 * — {@code nome_categoria} tambem e recusado; basta renomear o campo
 * agregado) e cobre variacoes morfologicas triviais do MESMO campo
 * vedado (correcao pos-revisao da ED-99, achado D1):
 * <ul>
 *   <li>NOME INTEIRO normalizado (minusculas, sem acento, sem
 *       separadores) e cada TOKEN (partes separadas por {@code _},
 *       {@code -} ou espaco) iguais a um termo vedado;</li>
 *   <li>forma CANONICA do nome/token — sem digitos finais e sem o
 *       {@code s} final de plural — igual a um termo vedado
 *       ({@code emails}, {@code telefones}, {@code cpfs}, {@code cpf2},
 *       {@code nome2});</li>
 *   <li>nome normalizado CONTENDO um identificador forte vedado como
 *       substring ({@code emailaddress}, {@code phonenumber},
 *       {@code cpfsolicitante}). Termos curtos/ambiguos ({@code rg},
 *       {@code cep}, {@code pis}, {@code nit}, {@code dob}) NAO entram
 *       na substring — so casam por nome/token/canonica, para nao
 *       recusar campos legitimos como {@code recepcao} ou
 *       {@code unidade}.</li>
 * </ul>
 * A lista e fechada e declarada aqui; campo de pessoa que nao esteja
 * nela (e nao seja variacao dos vedados) e responsabilidade da
 * curadoria do tenant na fiacao (ver RELATORIO-ED-99, pendencias).
 * A guarda e sobre NOMES de campo: valores nao sao inspecionados.
 */
public final class OpenDataExport {

    /** Formatos legiveis por maquina suportados. */
    public enum Format { CSV, JSON }

    /**
     * Campos pessoais vedados em dados abertos (nomes normalizados:
     * minusculas, sem acento, sem separadores). Inclui os identificadores
     * e contatos de pessoa natural; CNPJ de pessoa juridica NAO esta na
     * lista (nao e dado pessoal), mas {@code cpf} esta.
     */
    public static final Set<String> FORBIDDEN_PERSONAL_FIELDS = Set.of(
            "cpf", "rg", "cnh", "tituloeleitor", "pis", "nit",
            "nome", "name", "requestername", "nomecompleto", "fullname",
            "requester", "solicitante", "requerente", "applicant",
            "email", "telefone", "phone", "celular", "whatsapp",
            "endereco", "address", "logradouro", "cep",
            "contato", "contact",
            "datanascimento", "nascimento", "datanasc", "nasc",
            "birthdate", "dateofbirth", "dob",
            "mae", "mothername", "sexo", "gender", "genero", "raca", "race",
            "documento", "document", "identidade",
            "senha", "password", "token");

    /**
     * Identificadores fortes vedados: se o nome normalizado (sem
     * separadores) CONTEM um deles como substring, o campo e recusado
     * mesmo concatenado ou flexionado ({@code emailaddress},
     * {@code cpfsolicitante}, {@code emails}). So termos distintivos
     * entram aqui; termos curtos/ambiguos ({@code rg}, {@code cep},
     * {@code pis}, {@code nit}, {@code dob}, {@code name}, {@code raca})
     * ficam fora para nao gerar falso-positivo sobre campos claramente
     * nao pessoais ({@code recepcao}, {@code unidade}, {@code filename}).
     */
    static final Set<String> FORBIDDEN_SUBSTRING_TERMS = Set.of(
            "cpf", "nome", "mae", "nasc", "nascimento",
            "email", "telefone", "phone", "celular", "whatsapp",
            "endereco", "address", "logradouro",
            "solicitante", "requerente", "contato",
            "documento", "genero", "gender", "sexo",
            "senha", "password", "token",
            "datanascimento", "birthdate", "dateofbirth",
            "nomecompleto", "fullname", "requestername", "mothername",
            "tituloeleitor");

    /** Conjunto de dados abertos: nome, campos ordenados e linhas (valores textuais ja agregados/publicos). */
    public record Dataset(String name, List<String> fields, List<Map<String, String>> rows) {
        public Dataset {
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("nome do conjunto de dados abertos obrigatorio");
            if (fields == null || fields.isEmpty())
                throw new IllegalArgumentException("conjunto de dados abertos exige ao menos um campo");
            assertNoPersonalFields(fields);
            fields = List.copyOf(fields);
            rows = rows == null ? List.of() : List.copyOf(rows);
            for (Map<String, String> row : rows) {
                for (String key : row.keySet()) {
                    if (!fields.contains(key)) throw new IllegalArgumentException(
                            "linha com campo fora do conjunto declarado: " + key);
                }
            }
        }
    }

    private OpenDataExport() {}

    /**
     * Recusa qualquer campo pessoal vedado: nome inteiro normalizado,
     * qualquer token dele, a forma canonica (sem digitos finais, sem
     * plural) de ambos, ou nome contendo identificador forte vedado.
     */
    public static void assertNoPersonalFields(Collection<String> fields) {
        for (String field : fields) {
            if (field == null || field.isBlank())
                throw new IllegalArgumentException("campo de dados abertos em branco");
            boolean forbidden = isForbiddenName(normalize(field));
            if (!forbidden) {
                for (String token : field.split("[^\\p{L}\\p{N}]+")) {
                    if (!token.isBlank() && isForbiddenName(normalize(token))) {
                        forbidden = true;
                        break;
                    }
                }
            }
            if (forbidden) throw new IllegalArgumentException(
                    "dados abertos sem dado pessoal: campo vedado '" + field + "'");
        }
    }

    /**
     * Um nome normalizado (minusculas, sem acento, sem separadores) e
     * vedado quando: (a) e igual a um termo da lista; (b) sua forma
     * canonica — digitos finais e {@code s} de plural removidos — e
     * igual a um termo da lista ({@code cpf2 -> cpf},
     * {@code emails -> email}); ou (c) contem um identificador forte
     * da lista como substring ({@code emailaddress},
     * {@code cpfsolicitante}).
     */
    static boolean isForbiddenName(String normalized) {
        if (normalized.isEmpty()) return false;
        if (FORBIDDEN_PERSONAL_FIELDS.contains(normalized)) return true;
        String canonical = canonicalForm(normalized);
        if (!canonical.equals(normalized) && FORBIDDEN_PERSONAL_FIELDS.contains(canonical)) return true;
        for (String term : FORBIDDEN_SUBSTRING_TERMS) {
            if (normalized.contains(term)) return true;
        }
        return false;
    }

    /**
     * Forma canonica para comparacao: remove digitos finais e o
     * {@code s} final (plural pt/en), repetidamente, sem reduzir o
     * termo a menos de 2 caracteres. Ex.: {@code cpfs -> cpf},
     * {@code cpf2 -> cpf}, {@code telefones -> telefone},
     * {@code addresses -> addresse} (a recusa de {@code addresses}
     * vem da substring {@code address}, nao da canonica).
     */
    static String canonicalForm(String normalized) {
        String s = normalized;
        boolean changed = true;
        while (changed && s.length() > 2) {
            changed = false;
            if (Character.isDigit(s.charAt(s.length() - 1))) {
                s = s.substring(0, s.length() - 1);
                changed = true;
            } else if (s.charAt(s.length() - 1) == 's') {
                s = s.substring(0, s.length() - 1);
                changed = true;
            }
        }
        return s;
    }

    static String normalize(String field) {
        String noAccents = Normalizer.normalize(field, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Exporta o conjunto. Sem {@code pub.open_data_enabled} (ou com o
     * modulo desligado) a exportacao e RECUSADA — dado aberto nao vaza
     * por caminho lateral.
     */
    public static String export(Dataset dataset, Format format, boolean moduleEnabled, Boolean openDataRaw) {
        Objects.requireNonNull(dataset, "conjunto obrigatorio");
        Objects.requireNonNull(format, "formato obrigatorio");
        if (!moduleEnabled) throw new IllegalStateException(
                "dados abertos exigem o modulo de transparencia ligado (pub.lai_mode)");
        if (!TransparencyParameters.openDataEnabled(openDataRaw)) throw new IllegalStateException(
                "dados abertos desligados para o tenant (" + TransparencyParameters.OPEN_DATA_ENABLED + ")");
        return format == Format.CSV ? toCsv(dataset) : toJson(dataset);
    }

    /** CSV legivel por maquina: cabecalho + linhas, separador ';' (padrao pt-BR das exportacoes do produto). */
    public static String toCsv(Dataset dataset) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(";", dataset.fields())).append('\n');
        for (Map<String, String> row : dataset.rows()) {
            List<String> cells = new ArrayList<>();
            for (String field : dataset.fields()) cells.add(csvCell(row.get(field)));
            sb.append(String.join(";", cells)).append('\n');
        }
        return sb.toString();
    }

    /** JSON legivel por maquina: array de objetos com os campos declarados, na ordem. */
    public static String toJson(Dataset dataset) {
        StringBuilder sb = new StringBuilder("[");
        boolean firstRow = true;
        for (Map<String, String> row : dataset.rows()) {
            if (!firstRow) sb.append(',');
            firstRow = false;
            sb.append('{');
            boolean firstField = true;
            for (String field : dataset.fields()) {
                if (!firstField) sb.append(',');
                firstField = false;
                sb.append('"').append(jsonEscape(field)).append("\":");
                String value = row.get(field);
                if (value == null) sb.append("null");
                else sb.append('"').append(jsonEscape(value)).append('"');
            }
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    private static String csvCell(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String jsonEscape(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /** Mapa ordenado campo->valor de uma linha (util para quem monta o conjunto). */
    public static Map<String, String> row(Object... fieldValues) {
        if (fieldValues.length % 2 != 0) throw new IllegalArgumentException("linha exige pares campo/valor");
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < fieldValues.length; i += 2) {
            row.put(String.valueOf(fieldValues[i]),
                    fieldValues[i + 1] == null ? null : String.valueOf(fieldValues[i + 1]));
        }
        return row;
    }
}
