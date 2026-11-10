package br.com.ziipsan.commercial.collection.persistence;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas textuais da V104 PENDENTE (tempo contract do desenho
 * expand-contract, item 46), sem banco. A V104 vive em db/pendente/,
 * fora de db/migration/tenant/: nao e migration deste ciclo e nao
 * entra na lista de publicadas. A assercao da obrigatoriedade fisica
 * da chave, que saiu da V103 no expand, vive AQUI com a mesma
 * substancia: pre-checagem com contagem de nulos antes do passo que
 * torna a coluna obrigatoria.
 */
class LegacyDebitRemittanceKeyV104PendenteConsistencyTest {

    private String sql() throws Exception {
        try (InputStream in = getClass()
                .getResourceAsStream("/db/pendente/V104__legacy_debit_remittance_key_not_null.sql")) {
            assertThat(in).as("ficheiro pendente da V104 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v104PendenteTemPreChecagemComContagemAntesDoSetNotNull() throws Exception {
        String sql = sql();
        assertThat(sql).contains("COUNT(*)");
        assertThat(sql).contains("WHERE remittance_key IS NULL");
        assertThat(sql).contains("RAISE EXCEPTION");
        assertThat(sql).contains("P-V103-01");
        // A obrigatoriedade fisica realojada da V103 (substancia igual):
        assertThat(sql).contains(
                "ALTER TABLE legacy_debit_entries ALTER COLUMN remittance_key SET NOT NULL");
        int preChecagem = sql.indexOf("WHERE remittance_key IS NULL");
        int excecao = sql.indexOf("RAISE EXCEPTION");
        int setNotNull = sql.indexOf(
                "ALTER TABLE legacy_debit_entries ALTER COLUMN remittance_key SET NOT NULL");
        assertThat(preChecagem).isGreaterThanOrEqualTo(0);
        assertThat(excecao).isGreaterThan(preChecagem);
        assertThat(setNotNull).isGreaterThan(excecao);
    }

    @Test
    void v104PendenteNaoContemBackfillNemFormulaDuplicada() throws Exception {
        // Preencher e trabalho do adaptador/da operacao pre-contract:
        // o contract nao repete o backfill nem a expressao da formula.
        String sql = sql();
        assertThat(sql).doesNotContain("UPDATE legacy_debit_entries");
        assertThat(sql).doesNotContain("rtrim(rtrim(amount::text");
        assertThat(sql).doesNotContain("stripTrailingZeros");
    }

    @Test
    void v104PendenteMarcadoComoPendenteENaoAplicarNesteCiclo() throws Exception {
        String sql = sql();
        assertThat(sql).contains("PENDENTE");
        assertThat(sql).contains("NAO APLICAR");
        assertThat(sql).contains("item 46");
        assertThat(sql).contains("adaptador JPA");
    }
}
