package br.com.ziipsan.core.certification.quality9001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite da ED-69: mapa de processos com responsável, entradas, saídas, indicador e risco. */
class ProcessMapEntryTest {

    private static Iso9001Activation on() {
        return Iso9001Activation.enabled("2026", Iso9001Activation.EditionUse.TARGET,
                "escopo", UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("processo completo nasce com os cinco elementos do aceite")
    void completeProcessIsDefined() {
        ProcessMapEntry entry = ProcessMapEntry.define(on(), "SYN-PROC-1", "Processo sintético",
                UUID.randomUUID(), List.of("entrada sintética"), List.of("saída sintética"),
                "SYN-IND-1", "risco sintético declarado");
        assertThat(entry.inputs()).containsExactly("entrada sintética");
        assertThat(entry.outputs()).containsExactly("saída sintética");
        assertThat(entry.indicatorCode()).isEqualTo("SYN-IND-1");
    }

    @Test
    @DisplayName("sem responsável, sem entradas, sem saídas, sem indicador ou sem risco: recusado")
    void missingElementIsRefused() {
        UUID owner = UUID.randomUUID();
        assertThatThrownBy(() -> ProcessMapEntry.define(on(), "P1", "proc", null,
                List.of("e"), List.of("s"), "IND", "risco"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProcessMapEntry.define(on(), "P1", "proc", owner,
                List.of(), List.of("s"), "IND", "risco"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcessMapEntry.define(on(), "P1", "proc", owner,
                List.of("e"), List.of(), "IND", "risco"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcessMapEntry.define(on(), "P1", "proc", owner,
                List.of("e"), List.of("s"), " ", "risco"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcessMapEntry.define(on(), "P1", "proc", owner,
                List.of("e"), List.of("s"), "IND", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("entrada ou saída em branco dentro da lista é recusada")
    void blankEntryIsRefused() {
        assertThatThrownBy(() -> ProcessMapEntry.define(on(), "P1", "proc", UUID.randomUUID(),
                List.of("válida", " "), List.of("s"), "IND", "risco"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("com a norma desligada, linha do mapa não nasce")
    void disabledBlocksDefinition() {
        assertThatThrownBy(() -> ProcessMapEntry.define(Iso9001Activation.disabled(), "P1",
                "proc", UUID.randomUUID(), List.of("e"), List.of("s"), "IND", "risco"))
                .isInstanceOf(IllegalStateException.class);
    }
}
