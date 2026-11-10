package br.com.ziipsan.operations.metering.selfreading;

import java.time.Instant;
import java.util.Objects;

/**
 * A prova geografica capturada no acto da autoleitura (II-7): a posicao
 * onde a leitura foi submetida, a precisao declarada do GPS em metros e
 * o instante da captura. O instante e ENTRADA (quem captura informa) --
 * o nucleo nao le relogio, para ser deterministico.
 *
 * @param position        posicao capturada; obrigatoria quando ha prova
 * @param accuracyMetres  precisao do GPS em metros (&gt;= 0); menor = melhor
 * @param capturedAt      instante da captura informado por quem submete
 */
public record GeoProof(GeoPoint position, double accuracyMetres, Instant capturedAt) {

    public GeoProof {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (!Double.isFinite(accuracyMetres) || accuracyMetres < 0) {
            throw new IllegalArgumentException(
                    "precisao do GPS precisa ser finita e >= 0 metros; recebido " + accuracyMetres);
        }
    }
}
