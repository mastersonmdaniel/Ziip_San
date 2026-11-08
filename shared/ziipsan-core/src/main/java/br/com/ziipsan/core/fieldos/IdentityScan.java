package br.com.ziipsan.core.fieldos;

import java.util.Objects;
import java.util.UUID;

/**
 * Confirmacao de identidade e posicao por leitura de codigo (ED-20): QR,
 * codigo de barras ou NFC do HIDROMETRO, do LACRE e da PLACA DO ATIVO,
 * lidos no app de leitura (ED-18) e no app de OS ANTES de registrar leitura
 * ou intervencao. Codigo de OUTRA unidade nao confirma: a leitura e
 * recusada com o motivo (o teste do aceite).
 */
public final class IdentityScan {

    public enum Channel { QR, BARCODE, NFC }

    public enum Target { METER, SEAL, ASSET_PLATE }

    /** Identidade esperada, vinda do pacote da tarefa (unidade/OS). */
    public record Expected(UUID unitId, Target target, String code) {
        public Expected {
            Objects.requireNonNull(unitId, "unidade esperada e obrigatoria");
            Objects.requireNonNull(target, "alvo da leitura e obrigatorio");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo esperado e obrigatorio");
        }
    }

    public record Scanned(UUID unitId, Target target, String code, Channel channel) {
        public Scanned {
            Objects.requireNonNull(target, "alvo da leitura e obrigatorio");
            Objects.requireNonNull(channel, "canal da leitura (QR/barras/NFC) e obrigatorio");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo lido e obrigatorio");
        }
    }

    private IdentityScan() {}

    /** Confirma ou recusa: so confirma com unidade, alvo e codigo iguais aos esperados. */
    public static void requireMatch(Expected expected, Scanned scanned) {
        Objects.requireNonNull(expected, "identidade esperada e obrigatoria");
        Objects.requireNonNull(scanned, "leitura e obrigatoria");
        if (scanned.target() != expected.target()) throw new IllegalStateException(
                "leitura de " + scanned.target() + " nao confirma " + expected.target());
        if (!expected.code().equals(scanned.code())) throw new IllegalStateException(
                "codigo lido nao e o codigo esperado de " + expected.target() + " desta unidade: leitura recusada");
        if (scanned.unitId() != null && !scanned.unitId().equals(expected.unitId())) throw new IllegalStateException(
                "codigo pertence a OUTRA unidade (" + scanned.unitId() + "), nao a " + expected.unitId() + ": leitura recusada");
    }
}
