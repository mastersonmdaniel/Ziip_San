package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.secrets.SecretStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P0.45: suite de contrato de {@link SecretStore} (local hoje, gerenciado na LC.4). */
public abstract class SecretStoreContractTest {

    protected abstract SecretStore store();

    @Test
    @DisplayName("put + get devolve os mesmos bytes; delete apaga; inexistente e vazio")
    void putGetDelete() {
        var store = store();
        store.put("kek/current", new byte[]{1, 2, 3});
        assertThat(store.exists("kek/current")).isTrue();
        assertThat(store.get("kek/current")).isPresent().get().isEqualTo(new byte[]{1, 2, 3});
        store.delete("kek/current");
        assertThat(store.exists("kek/current")).isFalse();
        assertThat(store.get("kek/current")).isEmpty();
        store.delete("kek/current");
    }

    @Test
    @DisplayName("chave com .. e recusada; valor nulo e recusado")
    void traversalAndNullRefused() {
        var store = store();
        assertThatThrownBy(() -> store.put("../fora", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("ok", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
