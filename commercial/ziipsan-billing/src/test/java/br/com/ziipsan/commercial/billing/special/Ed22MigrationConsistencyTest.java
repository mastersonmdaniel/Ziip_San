package br.com.ziipsan.commercial.billing.special;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ED-22: consistencia estatica do SQL pendente - FK/CHECK do consumo fixo,
 * codigo unico do agrupador e tabelas da tarefa. O numero Flyway sai na
 * integracao; este teste le o arquivo como recurso de db/pendente/.
 */
class Ed22MigrationConsistencyTest {

    @Test
    @DisplayName("SQL pendente da ED-22 contem FK+CHECK, codigo unico, isencao com base legal e register_tenant_table")
    void pendenteSql() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(
                "/db/pendente/ED-22__special_situations_central_large_user.sql")) {
            assertThat(in).as("SQL pendente da ED-22 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql).contains("special_situations")
                .contains("fk_consumption_special_situation")
                .contains("chk_consumption_special_situation_link")
                .contains("SPECIAL_SITUATION_FIXED")
                .contains("idx_grouped_bills_code")
                .contains("large_user_contracts")
                .contains("central_billing_links")
                .contains("register_tenant_table('special_situations')")
                // D2: FIXED_VOLUME com os dois volumes nulos e recusado no SQL.
                .contains("type_code <> 'FIXED_VOLUME' OR fixed_water_volume IS NOT NULL"
                        + " OR fixed_sewer_volume IS NOT NULL")
                // D5: bill_id do membro com FK para bills e unicidade por conta.
                .contains("fk_grouped_member_bill")
                .contains("REFERENCES bills(tenant_id, id)")
                .contains("idx_grouped_bill_members_bill")
                // D7: a chave nomeada do percentual entra no catalogo comentado.
                .contains("quality.noncompliance_compensation_percent");
    }
}
