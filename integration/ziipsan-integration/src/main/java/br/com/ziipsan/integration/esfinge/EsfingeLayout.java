package br.com.ziipsan.integration.esfinge;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Leiaute da remessa e-Sfinge como COMPONENTE SUBSTITUIVEL E VERSIONADO
 * (ED-33).
 *
 * <p><b>PENDENCIA DECLARADA — marcador {@value #OFFICIAL_LAYOUT_PENDING_MARKER}:</b>
 * o leiaute vigente do TCE/SC (tabelas, campos e regras de consistencia
 * oficiais) NAO esta nas fontes locais do projeto (fontes da retomada e
 * pacotes vizinhos foram varridos; so ha referencias de periodicidade e da
 * norma na ED-98/PLANO — ver RELATORIO-ED-33). Por isso NENHUMA tabela,
 * campo ou regra oficial e codificada aqui: o leiaute e DADO, carregado na
 * integracao a partir da publicacao oficial do TCE/SC, e registrado com
 * {@code official = true} somente quando vier dessa fonte. Enquanto isso,
 * o motor (EsfingeValidator, EsfingeRemittanceFile) opera sobre qualquer
 * leiaute bem formado, e toda remessa carrega {@code layoutOfficial} para
 * o relatorio de envio nunca apresentar validacao estrutural como
 * validacao oficial.
 *
 * <p>Os TIPOS de regra de consistencia sao mecanica generica de validacao
 * (obrigatoriedade, tipo, escala, unicidade, referencia cruzada, soma de
 * conferencia, data dentro da competencia) — as INSTANCIAS das regras do
 * TCE/SC sao dados do leiaute, nao constantes deste codigo.
 */
public record EsfingeLayout(String version, String normReference, boolean official,
                            LocalDate effectiveFrom, LocalDate effectiveTo,
                            List<LayoutTable> tables, List<ConsistencyRule> rules) {

    /** Marcador textual da pendencia do leiaute oficial, usado em relatorios. */
    public static final String OFFICIAL_LAYOUT_PENDING_MARKER = "PENDENTE_LEIAUTE_OFICIAL_TCE_SC";

    public enum FieldType { TEXT, INTEGER, DECIMAL, DATE }

    /**
     * Campo do leiaute. {@code decimalScale} so se aplica a DECIMAL;
     * {@code maxLength} so a TEXT; {@code nonNegative} e a regra de
     * consistencia "valor nao pode ser negativo" declarada no leiaute.
     */
    public record LayoutField(String code, FieldType type, boolean required,
                              Integer maxLength, Integer decimalScale, boolean nonNegative) {
        public LayoutField {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("campo do leiaute exige codigo");
            Objects.requireNonNull(type, "campo do leiaute exige tipo: " + code);
            if (maxLength != null && maxLength <= 0) throw new IllegalArgumentException("maxLength invalido no campo " + code);
            if (decimalScale != null && decimalScale < 0) throw new IllegalArgumentException("decimalScale invalido no campo " + code);
        }
        public static LayoutField text(String code, boolean required, Integer maxLength) {
            return new LayoutField(code, FieldType.TEXT, required, maxLength, null, false);
        }
        public static LayoutField decimal(String code, boolean required, int scale, boolean nonNegative) {
            return new LayoutField(code, FieldType.DECIMAL, required, null, scale, nonNegative);
        }
        public static LayoutField integer(String code, boolean required, boolean nonNegative) {
            return new LayoutField(code, FieldType.INTEGER, required, null, null, nonNegative);
        }
        public static LayoutField date(String code, boolean required) {
            return new LayoutField(code, FieldType.DATE, required, null, null, false);
        }
    }

    /** Tabela do leiaute; {@code keyFieldCode} (quando ha) e a chave de unicidade/referencia. */
    public record LayoutTable(String code, String keyFieldCode, List<LayoutField> fields) {
        public LayoutTable {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("tabela do leiaute exige codigo");
            if (fields == null || fields.isEmpty()) throw new IllegalArgumentException("tabela " + code + " exige ao menos um campo");
            fields = List.copyOf(fields);
            long distinct = fields.stream().map(LayoutField::code).distinct().count();
            if (distinct != fields.size()) throw new IllegalArgumentException("tabela " + code + " tem campo duplicado");
            if (keyFieldCode != null && fields.stream().noneMatch(f -> f.code().equals(keyFieldCode)))
                throw new IllegalArgumentException("chave '" + keyFieldCode + "' nao e campo da tabela " + code);
        }
        public LayoutField field(String fieldCode) {
            return fields.stream().filter(f -> f.code().equals(fieldCode)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("campo '" + fieldCode + "' nao existe na tabela " + code));
        }
        public boolean hasField(String fieldCode) {
            return fields.stream().anyMatch(f -> f.code().equals(fieldCode));
        }
    }

    public enum RuleKind {
        /** A soma do campo em todos os registros da tabela iguala o campo do registro unico da outra tabela. */
        SUM_EQUALS,
        /** O valor do campo (data) cai dentro do mes de competencia da remessa. */
        DATE_WITHIN_COMPETENCE,
        /** O valor do campo existe como chave (keyFieldCode) de algum registro da outra tabela. */
        REFERENCE_EXISTS
    }

    /**
     * Regra de consistencia entre tabelas (instancia = dado do leiaute).
     * Para SUM_EQUALS: soma de {@code fieldCode} em {@code tableCode} ==
     * {@code otherFieldCode} no registro unico de {@code otherTableCode}.
     * Para DATE_WITHIN_COMPETENCE: {@code otherTableCode/otherFieldCode} nao se aplicam (null).
     * Para REFERENCE_EXISTS: {@code fieldCode} de {@code tableCode} deve existir
     * como chave em {@code otherTableCode}.
     */
    public record ConsistencyRule(RuleKind kind, String tableCode, String fieldCode,
                                  String otherTableCode, String otherFieldCode, String description) {
        public ConsistencyRule {
            Objects.requireNonNull(kind, "regra exige tipo");
            if (tableCode == null || tableCode.isBlank() || fieldCode == null || fieldCode.isBlank())
                throw new IllegalArgumentException("regra exige tabela e campo de origem");
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("regra exige descricao (rastreabilidade da consistencia)");
        }
    }

    public EsfingeLayout {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("leiaute exige versao");
        Objects.requireNonNull(effectiveFrom, "leiaute exige inicio de vigencia");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom))
            throw new IllegalArgumentException("vigencia do leiaute invertida: " + effectiveFrom + " > " + effectiveTo);
        if (tables == null || tables.isEmpty()) throw new IllegalArgumentException("leiaute exige ao menos uma tabela");
        tables = List.copyOf(tables);
        long distinctTables = tables.stream().map(LayoutTable::code).distinct().count();
        if (distinctTables != tables.size()) throw new IllegalArgumentException("leiaute tem tabela duplicada");
        rules = rules == null ? List.of() : List.copyOf(rules);
        // Toda regra tem de apontar para tabelas/campos que existem no leiaute:
        // regra pendurada e erro de carga do leiaute, nao da remessa.
        // (Usa a variavel local: no construtor compacto os campos do record
        // ainda nao foram atribuidos quando esta validacao roda.)
        java.util.Map<String, LayoutTable> byCode = new java.util.HashMap<>();
        for (LayoutTable t : tables) byCode.put(t.code(), t);
        for (ConsistencyRule r : rules) {
            LayoutTable t = byCode.get(r.tableCode());
            if (t == null)
                throw new IllegalArgumentException("regra aponta tabela inexistente: " + r.tableCode());
            if (!t.hasField(r.fieldCode()))
                throw new IllegalArgumentException("regra aponta campo inexistente: " + r.tableCode() + "." + r.fieldCode());
            if (r.kind() != RuleKind.DATE_WITHIN_COMPETENCE) {
                LayoutTable o = byCode.get(r.otherTableCode());
                if (o == null)
                    throw new IllegalArgumentException("regra aponta tabela de conferencia inexistente: " + r.otherTableCode());
                if (r.otherFieldCode() == null || !o.hasField(r.otherFieldCode()))
                    throw new IllegalArgumentException("regra aponta campo de conferencia inexistente: "
                            + r.otherTableCode() + "." + r.otherFieldCode());
            }
        }
    }

    public LayoutTable table(String code) {
        return tables.stream().filter(t -> t.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tabela '" + code + "' nao existe no leiaute " + version));
    }

    public boolean hasTable(String code) { return tables.stream().anyMatch(t -> t.code().equals(code)); }

    /** O leiaute cobre a competencia? (vigencia comparada ao primeiro dia do mes) */
    public boolean covers(java.time.YearMonth competence) {
        Objects.requireNonNull(competence, "competencia e obrigatoria");
        LocalDate first = competence.atDay(1);
        return !first.isBefore(effectiveFrom) && (effectiveTo == null || !first.isAfter(effectiveTo));
    }
}
