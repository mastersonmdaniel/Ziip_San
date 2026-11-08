package br.com.ziipsan.integration.esfinge;

import br.com.ziipsan.integration.esfinge.EsfingeLayout.ConsistencyRule;
import br.com.ziipsan.integration.esfinge.EsfingeLayout.LayoutField;
import br.com.ziipsan.integration.esfinge.EsfingeLayout.LayoutTable;
import br.com.ziipsan.integration.esfinge.EsfingeLayout.RuleKind;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.DatasetRecord;
import br.com.ziipsan.integration.esfinge.EsfingeSourceData.RemittanceSourceData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validacao da remessa contra o leiaute carregado (ED-33): aplica as
 * regras de consistencia DO LEIAUTE (instancias = dados; ver
 * EsfingeLayout) mais as conferencias derivaveis das dependencias:
 *
 * <ol>
 *   <li>estrutural por campo: obrigatoriedade, tipo (inteiro/decimal/data
 *       ISO), escala decimal, comprimento maximo, nao-negatividade
 *       declarada no campo e campo desconhecido para a tabela;</li>
 *   <li>unicidade da chave de cada tabela (keyFieldCode);</li>
 *   <li>regras entre tabelas do leiaute: SUM_EQUALS, DATE_WITHIN_COMPETENCE
 *       e REFERENCE_EXISTS;</li>
 *   <li>partida dobrada da contabilidade (ED-32): total de debitos do
 *       balancete igual ao de creditos;</li>
 *   <li>saldo mensal da divida ativa congelado na ED-31: remessa nao
 *       declara numero de mes ainda aberto.</li>
 * </ol>
 *
 * Nenhum erro e descartado em silencio: o resultado lista TODOS os erros,
 * cada um nomeando tabela, campo/regra e registro. Remessa com erro nao
 * gera arquivo (ver EsfingeRemittanceFile).
 */
public final class EsfingeValidator {

    public record ValidationError(String tableCode, String fieldCode, String message) {}

    public record ValidationResult(List<ValidationError> errors) {
        public ValidationResult { errors = List.copyOf(errors); }
        public boolean valid() { return errors.isEmpty(); }
    }

    private EsfingeValidator() {}

    public static ValidationResult validate(EsfingeLayout layout, YearMonth competence, RemittanceSourceData data) {
        Objects.requireNonNull(layout, "leiaute e obrigatorio");
        Objects.requireNonNull(competence, "competencia e obrigatoria");
        Objects.requireNonNull(data, "dados de origem sao obrigatorios");
        List<ValidationError> errors = new ArrayList<>();

        if (!layout.covers(competence))
            errors.add(new ValidationError(null, null,
                    "competencia " + competence + " fora da vigencia do leiaute " + layout.version()));

        for (DatasetRecord rec : data.records()) {
            if (!layout.hasTable(rec.tableCode())) {
                errors.add(new ValidationError(rec.tableCode(), null,
                        "registro de tabela fora do leiaute " + layout.version() + " (origem " + rec.sourceReference() + ")"));
                continue;
            }
            LayoutTable table = layout.table(rec.tableCode());
            for (String fieldCode : rec.values().keySet())
                if (!table.hasField(fieldCode))
                    errors.add(new ValidationError(rec.tableCode(), fieldCode,
                            "campo fora do leiaute na tabela " + rec.tableCode() + " (origem " + rec.sourceReference() + ")"));
            for (LayoutField field : table.fields())
                validateField(table, field, rec, errors);
        }

        // Unicidade da chave declarada de cada tabela.
        for (LayoutTable table : layout.tables()) {
            if (table.keyFieldCode() == null) continue;
            Set<String> seen = new HashSet<>();
            for (DatasetRecord rec : data.recordsOf(table.code())) {
                String key = rec.value(table.keyFieldCode());
                if (key == null || key.isBlank()) continue; // ausencia ja e erro de obrigatoriedade
                if (!seen.add(key))
                    errors.add(new ValidationError(table.code(), table.keyFieldCode(),
                            "chave duplicada na tabela " + table.code() + ": " + key));
            }
        }

        for (ConsistencyRule rule : layout.rules()) applyRule(layout, rule, competence, data, errors);

        // Conferencia derivavel da ED-32: partida dobrada do balancete do mes.
        if (!data.accounting().balanced())
            errors.add(new ValidationError(null, null,
                    "balancete (ED-32) desequilibrado na competencia " + competence + ": debitos "
                            + data.accounting().totalDebit() + " != creditos " + data.accounting().totalCredit()));
        // Conferencia derivavel da ED-31: saldo do mes congelado.
        if (!data.activeDebt().frozen())
            errors.add(new ValidationError(null, null,
                    "saldo mensal da divida ativa (ED-31) nao congelado na competencia " + competence
                            + ": a remessa so declara mes fechado"));

        return new ValidationResult(errors);
    }

    private static void validateField(LayoutTable table, LayoutField field, DatasetRecord rec,
                                      List<ValidationError> errors) {
        String raw = rec.value(field.code());
        String where = table.code() + "." + field.code() + " (origem " + rec.sourceReference() + ")";
        if (raw == null || raw.isBlank()) {
            if (field.required())
                errors.add(new ValidationError(table.code(), field.code(), "campo obrigatorio ausente: " + where));
            return;
        }
        switch (field.type()) {
            case TEXT -> {
                if (field.maxLength() != null && raw.length() > field.maxLength())
                    errors.add(new ValidationError(table.code(), field.code(),
                            "texto excede " + field.maxLength() + " caracteres: " + where));
            }
            case INTEGER -> {
                try {
                    long v = Long.parseLong(raw.trim());
                    if (field.nonNegative() && v < 0)
                        errors.add(new ValidationError(table.code(), field.code(), "inteiro negativo em campo nao-negativo: " + where));
                } catch (NumberFormatException e) {
                    errors.add(new ValidationError(table.code(), field.code(), "valor nao e inteiro: " + where + " = '" + raw + "'"));
                }
            }
            case DECIMAL -> {
                try {
                    BigDecimal v = new BigDecimal(raw.trim());
                    if (field.decimalScale() != null && v.scale() > field.decimalScale())
                        errors.add(new ValidationError(table.code(), field.code(),
                                "decimal com escala " + v.scale() + " maior que a do leiaute (" + field.decimalScale() + "): " + where));
                    if (field.nonNegative() && v.signum() < 0)
                        errors.add(new ValidationError(table.code(), field.code(), "decimal negativo em campo nao-negativo: " + where));
                } catch (NumberFormatException e) {
                    errors.add(new ValidationError(table.code(), field.code(), "valor nao e decimal: " + where + " = '" + raw + "'"));
                }
            }
            case DATE -> {
                try {
                    LocalDate.parse(raw.trim());
                } catch (DateTimeParseException e) {
                    errors.add(new ValidationError(table.code(), field.code(), "valor nao e data ISO (aaaa-mm-dd): " + where + " = '" + raw + "'"));
                }
            }
        }
    }

    private static void applyRule(EsfingeLayout layout, ConsistencyRule rule, YearMonth competence,
                                  RemittanceSourceData data, List<ValidationError> errors) {
        switch (rule.kind()) {
            case SUM_EQUALS -> {
                BigDecimal sum = BigDecimal.ZERO;
                for (DatasetRecord rec : data.recordsOf(rule.tableCode())) {
                    String raw = rec.value(rule.fieldCode());
                    if (raw == null || raw.isBlank()) continue; // ausencia ja reportada
                    try { sum = sum.add(new BigDecimal(raw.trim())); }
                    catch (NumberFormatException ignored) { /* tipo ja reportado */ }
                }
                List<DatasetRecord> others = data.recordsOf(rule.otherTableCode());
                if (others.size() != 1) {
                    errors.add(new ValidationError(rule.otherTableCode(), rule.otherFieldCode(),
                            "regra '" + rule.description() + "': esperava exatamente 1 registro em "
                                    + rule.otherTableCode() + ", vieram " + others.size()));
                    return;
                }
                String rawTotal = others.get(0).value(rule.otherFieldCode());
                if (rawTotal == null || rawTotal.isBlank()) return; // ausencia ja reportada
                try {
                    if (sum.compareTo(new BigDecimal(rawTotal.trim())) != 0)
                        errors.add(new ValidationError(rule.tableCode(), rule.fieldCode(),
                                "regra '" + rule.description() + "': soma de " + rule.tableCode() + "." + rule.fieldCode()
                                        + " (" + sum + ") difere de " + rule.otherTableCode() + "." + rule.otherFieldCode()
                                        + " (" + rawTotal + ")"));
                } catch (NumberFormatException ignored) { /* tipo ja reportado */ }
            }
            case DATE_WITHIN_COMPETENCE -> {
                for (DatasetRecord rec : data.recordsOf(rule.tableCode())) {
                    String raw = rec.value(rule.fieldCode());
                    if (raw == null || raw.isBlank()) continue;
                    try {
                        LocalDate d = LocalDate.parse(raw.trim());
                        if (!YearMonth.from(d).equals(competence))
                            errors.add(new ValidationError(rule.tableCode(), rule.fieldCode(),
                                    "regra '" + rule.description() + "': data " + d + " fora da competencia " + competence
                                            + " (origem " + rec.sourceReference() + ")"));
                    } catch (DateTimeParseException ignored) { /* tipo ja reportado */ }
                }
            }
            case REFERENCE_EXISTS -> {
                LayoutTable target = layout.table(rule.otherTableCode());
                if (target.keyFieldCode() == null) {
                    errors.add(new ValidationError(rule.tableCode(), rule.fieldCode(),
                            "regra '" + rule.description() + "': tabela de destino " + rule.otherTableCode()
                                    + " nao declara chave (keyFieldCode) no leiaute " + layout.version()));
                    return;
                }
                Set<String> keys = new HashSet<>();
                for (DatasetRecord rec : data.recordsOf(rule.otherTableCode())) {
                    String k = rec.value(target.keyFieldCode());
                    if (k != null && !k.isBlank()) keys.add(k);
                }
                for (DatasetRecord rec : data.recordsOf(rule.tableCode())) {
                    String v = rec.value(rule.fieldCode());
                    if (v == null || v.isBlank()) continue; // ausencia ja reportada
                    if (!keys.contains(v))
                        errors.add(new ValidationError(rule.tableCode(), rule.fieldCode(),
                                "regra '" + rule.description() + "': referencia '" + v + "' nao existe em "
                                        + rule.otherTableCode() + " (origem " + rec.sourceReference() + ")"));
                }
            }
        }
    }
}
