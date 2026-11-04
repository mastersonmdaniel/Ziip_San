package br.com.ziipsan.commercial.registry.importer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P1.40: o esquema de importação que um cadastro declara, e o aplicador que
 * grava (e desfaz) as suas linhas aceitas.
 *
 * <p>Fronteira (plano v3.0, P1.40): esta frente é dona do framework genérico.
 * A P1.3 é dona da declaração do esquema CSV do cadastro e a P1.2, do motor
 * de migração de legado. Um esquema novo entra como um bean {@code ImportSchema}
 * (ver {@link ImportSchemaRegistry}): nenhum importador novo, nenhuma rota nova.
 *
 * <p>Valores são sempre texto cru da planilha ({@code Map<coluna, valor>});
 * o aplicador faz o parse final na sua tabela. A validação genérica já
 * garantiu tipo, domínio, FK, duplicidade no arquivo e no banco antes de o
 * aplicador ver a linha — o aplicador só grava, e grava uma vez (a linha
 * aplicada tem {@code applied=true} e não é reaplicada).
 */
public interface ImportSchema {

    /** Código do esquema, ex.: {@code registry.categories}. Nunca texto livre na rota. */
    String code();

    /** Rótulo exibido no menu Importações e no modelo. */
    String label();

    /** Colunas, na ordem do modelo. O cabeçalho do arquivo tem de ser exatamente este. */
    List<ImportColumn> columns();

    /** Quem grava e desfaz as linhas aceitas deste esquema. */
    ImportApplier applier();

    /**
     * Quem grava uma linha aceita e desfaz o que gravou. O inverso é a correção
     * da P1.40 (ADR-0039 item 5: sem reversão, outra OS com o efeito inverso):
     * nos catálogos, desativar o que foi inserido — catálogo não tem DELETE.
     */
    interface ImportApplier {

        /** Linha já aplicada no banco por este esquema, para a recusa DUPLICATE_IN_DATABASE. */
        boolean existsByKey(Connection connection, String schema, UUID tenantId,
                            Map<String, String> values) throws SQLException;

        /** Grava a linha aceita. Devolve o alvo aplicado (tabela, id, chave) para o livro-razão. */
        Applied apply(Connection connection, String schema, UUID tenantId, UUID actorId,
                       Map<String, String> values) throws SQLException;

        /** Desfaz o que {@link #apply} gravou (efeito inverso da OS de correção). */
        void reverse(Connection connection, String schema, UUID tenantId, UUID actorId,
                      Applied applied) throws SQLException;

        /** Alvo aplicado: o que a linha virou no banco, para auditoria e para o inverso. */
        record Applied(String targetTable, UUID targetId, String targetKey) {
            public Applied {
                if (targetTable == null || targetTable.isBlank()) {
                    throw new IllegalArgumentException("tabela do alvo aplicado e obrigatoria");
                }
                if (targetId == null) {
                    throw new IllegalArgumentException("id do alvo aplicado e obrigatorio");
                }
            }
        }
    }
}
