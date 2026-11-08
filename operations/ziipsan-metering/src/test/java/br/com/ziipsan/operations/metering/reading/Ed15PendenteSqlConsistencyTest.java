package br.com.ziipsan.operations.metering.reading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O SQL pendente da ED-15 declara exatamente as três chaves nomeadas no
 * aceite — e nenhuma outra — sem valor de fábrica inventado.
 */
class Ed15PendenteSqlConsistencyTest {

    @Test
    @DisplayName("SQL pendente contém as 3 chaves read.* do aceite e nenhum factory_default com valor")
    void pendenteSqlDeclaresOnlyTheNamedKeys() throws Exception {
        String sql;
        try (InputStream in = getClass()
                .getResourceAsStream("/db/pendente/ED-15__reading_cycle_parameters.sql")) {
            assertThat(in).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql).contains(ReadingCycleParameters.RESAMPLE_PCT_KEY);
        assertThat(sql).contains(ReadingCycleParameters.PHOTO_REQUIRED_ON_KEY);
        assertThat(sql).contains(ReadingCycleParameters.IMPEDIMENT_ATTEMPTS_KEY);
        // exatamente 3 linhas de definição (chaves entre aspas simples)
        assertThat(sql.split("'read\\.").length - 1).isEqualTo(3);
    }
}
