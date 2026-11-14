package br.com.ziipsan.commercial.fiscal.relief;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Cadastro governado de codigos fiscais CFOP (4 digitos, F-14) e NCM
 * (8 digitos, F-15) — DESENHO-PA02 secao 3.7:
 *
 * <ul>
 *   <li>apagar SO se nunca usado num documento fiscal; usado, apenas
 *       inactivar;</li>
 *   <li>codigo inactivo e recusado na resolucao para emissao;</li>
 *   <li>sem veredicto de falta confirmada de emissao modelo 55 (PA-01 V-48:
 *       AUSENTE como emissao, fora do PA-02), estes cadastros NAO ganham
 *       consumidor de emissao novo neste pacote — sao cadastros governados.</li>
 * </ul>
 *
 * <p>Registo em memoria sobre a tabela da M2; a persistencia JDBC segue o
 * padrao do modulo na integracao.
 */
public final class FiscalCodeRegistry {

    public enum Kind {
        CFOP(4), NCM(8);

        private final int digits;

        Kind(int digits) {
            this.digits = digits;
        }

        public int digits() {
            return digits;
        }
    }

    public record FiscalCode(Kind kind, String code, String description, boolean active) {
        public FiscalCode {
            Objects.requireNonNull(kind, "tipo do codigo e obrigatorio");
            if (code == null || !code.matches("\\d{" + kind.digits() + "}")) {
                throw new IllegalArgumentException(
                        "Codigo " + kind + " deve ter " + kind.digits() + " digitos: " + code);
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("descricao do codigo e obrigatoria");
            }
        }

        FiscalCode withActive(boolean value) {
            return new FiscalCode(kind, code, description, value);
        }
    }

    private final Kind kind;
    private final Map<String, FiscalCode> codes = new LinkedHashMap<>();

    public FiscalCodeRegistry(Kind kind) {
        this.kind = Objects.requireNonNull(kind, "tipo do cadastro e obrigatorio");
    }

    public FiscalCode register(String code, String description) {
        FiscalCode created = new FiscalCode(kind, code, description, true);
        if (codes.putIfAbsent(code, created) != null) {
            throw new IllegalStateException("Codigo " + kind + " ja cadastrado: " + code);
        }
        return created;
    }

    /** Apaga apenas se nunca usado; usado, recusa (o caminho e inactivar). */
    public void delete(String code, Predicate<String> everUsed) {
        Objects.requireNonNull(everUsed, "verificacao de uso e obrigatoria");
        if (everUsed.test(code)) {
            throw new IllegalStateException(
                    "Codigo " + kind + " " + code + " ja usado em documento: nao se apaga, inactiva-se");
        }
        codes.remove(code);
    }

    public void deactivate(String code) {
        FiscalCode current = require(code);
        codes.put(code, current.withActive(false));
    }

    /** Resolucao para uso: codigo inexistente ou inactivo e recusado. */
    public FiscalCode resolveActive(String code) {
        FiscalCode found = require(code);
        if (!found.active()) {
            throw new IllegalStateException("Codigo " + kind + " inactivo recusado: " + code);
        }
        return found;
    }

    private FiscalCode require(String code) {
        FiscalCode found = codes.get(code);
        if (found == null) {
            throw new IllegalStateException("Codigo " + kind + " nao cadastrado: " + code);
        }
        return found;
    }
}
