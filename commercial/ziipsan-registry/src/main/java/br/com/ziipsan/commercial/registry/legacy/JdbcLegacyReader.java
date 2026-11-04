package br.com.ziipsan.commercial.registry.legacy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/**
 * Leitor de banco legado (P1.2): roda a consulta informada contra o
 * {@link DataSource} do legado e devolve uma linha por registro, com o nome
 * da coluna como chave. A consulta é de quem chama (só leitura, por
 * contrato — o motor nunca escreve no legado); valores saem como texto via
 * {@code getString}, e a tipagem acontece na transformação do mapeamento.
 *
 * <p>O DataSource do legado é externo ao do tenant: nunca é o DataSource da
 * aplicação. Em produção ele aponta para uma réplica só-leitura do legado
 * ou para um banco anonimizado; dado real de produção nunca entra no
 * ambiente local (LC.10).
 */
public final class JdbcLegacyReader implements LegacySourceReader {

    private final DataSource dataSource;
    private final String sql;

    public JdbcLegacyReader(DataSource dataSource, String sql) {
        if (dataSource == null || sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("DataSource do legado e consulta são obrigatórios.");
        }
        this.dataSource = dataSource;
        this.sql = sql;
    }

    @Override
    public List<LegacyRow> read() {
        List<LegacyRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= columns; i++) {
                names.add(meta.getColumnLabel(i));
            }
            int rowNo = 0;
            while (rs.next()) {
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < columns; i++) {
                    values.put(names.get(i), rs.getString(i + 1));
                }
                // Map.copyOf rejeita null — e NULL do banco É null por contrato.
                rows.add(new LegacyRow(++rowNo, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler o banco legado: " + e.getMessage(), e);
        }
        return List.copyOf(rows);
    }
}
