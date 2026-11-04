package br.com.ziipsan.commercial.registry.importer;

import java.util.List;

/**
 * P1.40: uma coluna do esquema de importação declarado por um cadastro.
 *
 * <p>O esquema é o contrato entre o cadastro e o importador genérico: o modelo
 * para baixar, a validação por linha e a aplicação são todos gerados daqui, e
 * nenhum cadastro precisa de um importador próprio. O que o esquema NÃO faz:
 * conhecer regra de negócio do cadastro além da forma (tipo, domínio, chave
 * estrangeira, unicidade) — a regra vive no aplicador do cadastro.
 *
 * <p>Convenções: nome da coluna é o cabeçalho exato do modelo CSV; tipo é
 * validado por parse (nunca por máscara); domínio é lista fechada de valores
 * aceitos (comparação exata, após trim); chave estrangeira é resolvida por
 * (tabela, coluna) no schema do tenant — a tabela vem do esquema declarado em
 * código, nunca do arquivo, então não há interpolação de entrada do usuário;
 * chave única ({@code uniqueKey}) marca a coluna que identifica a linha no
 * arquivo e no banco (duplicidade nos dois lados é recusa).
 */
public record ImportColumn(
        String name,
        ColumnType type,
        boolean required,
        List<String> domain,
        String example,
        ForeignKey foreignKey,
        boolean uniqueKey) {

    public enum ColumnType {
        TEXT, INTEGER, DECIMAL, BOOLEAN, DATE
    }

    /** Chave estrangeira declarada: tabela e coluna de destino no schema do tenant. */
    public record ForeignKey(String table, String column) {
    }

    public ImportColumn {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome da coluna e obrigatorio");
        }
        if (type == null) {
            throw new IllegalArgumentException("tipo da coluna e obrigatorio");
        }
        domain = domain == null ? List.of() : List.copyOf(domain);
        if (example == null) {
            example = "";
        }
    }

    /** Atalho para coluna de texto simples, sem domínio, sem FK, sem unicidade. */
    public static ImportColumn text(String name, boolean required, String example) {
        return new ImportColumn(name, ColumnType.TEXT, required, List.of(), example, null, false);
    }

    /** Atalho para a coluna-chave do esquema (texto, obrigatória, única). */
    public static ImportColumn key(String name, String example) {
        return new ImportColumn(name, ColumnType.TEXT, true, List.of(), example, null, true);
    }
}
