package br.com.ziipsan.platform.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.42: composição do /about — registro gravado vence; sem ele, fallbacks literais. */
class AboutInfoTest {

    @Test
    @DisplayName("com registro gravado, versão/commit/ambiente/data são os gravados")
    void recordedRowWins() {
        Deployment row = new Deployment(UUID.randomUUID(), "1.2.3", "abc1234", "prod",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5),
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC));
        AboutInfo info = AboutInfo.resolve(Optional.of(row), "9.9.9", "zzz", new String[]{"local"});
        assertThat(info.version()).isEqualTo("1.2.3");
        assertThat(info.commit()).isEqualTo("abc1234");
        assertThat(info.environment()).isEqualTo("prod");
        assertThat(info.deployedAt()).isEqualTo(row.createdAt());
    }

    @Test
    @DisplayName("sem registro e sem build-info/git.properties: dev/unknown/dev e data nula (nada inventado)")
    void noRowNoBuildInfoIsFallback() {
        AboutInfo info = AboutInfo.resolve(Optional.empty(), null, null, new String[]{"migrate"});
        assertThat(info.version()).isEqualTo("dev");
        assertThat(info.commit()).isEqualTo("unknown");
        assertThat(info.environment()).isEqualTo("dev");
        assertThat(info.deployedAt()).isNull();
    }

    @Test
    @DisplayName("sem registro, mas com build-info/git.properties e perfil: usa os valores presentes")
    void noRowWithBuildInfoUsesIt() {
        AboutInfo info = AboutInfo.resolve(Optional.empty(), "2.0.0", "def5678", new String[]{"local"});
        assertThat(info.version()).isEqualTo("2.0.0");
        assertThat(info.commit()).isEqualTo("def5678");
        assertThat(info.environment()).isEqualTo("local");
        assertThat(info.deployedAt()).isNull();
    }

    @Test
    @DisplayName("registro com commit nulo vira unknown na apresentação, sem inventar commit")
    void recordedNullCommitIsUnknown() {
        Deployment row = new Deployment(UUID.randomUUID(), "dev", null, "dev",
                OffsetDateTime.now(ZoneOffset.UTC), null, OffsetDateTime.now(ZoneOffset.UTC));
        AboutInfo info = AboutInfo.resolve(Optional.of(row), null, null, null);
        assertThat(info.commit()).isEqualTo("unknown");
    }
}
