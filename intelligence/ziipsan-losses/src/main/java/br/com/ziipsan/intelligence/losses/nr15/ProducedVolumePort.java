package br.com.ziipsan.intelligence.losses.nr15;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta do volume produzido lido da P4.2 (ED-78; item literal do
 * aceite: "volume produzido lido da P4.2").
 *
 * <p>Na P4.2 (SINISA assistido, GES-16), a variavel e
 * {@code AGUA.VOLUME_PRODUZIDO} da semente do mapa
 * ({@code sinisa-catalog-seed.json}): "Volume de agua produzido no
 * ano-base", unidade m3, origem METERING - "volume de entrada do
 * sistema (system input) do ano-base; insumo do WaterBalance da
 * P4.2", lida pela porta {@code SinisaSourceData} da P4.2. A fiacao
 * desta porta (pendencia P-ED78-05) chama exatamente essa leitura;
 * esta entrega nao depende do modulo integration (precedente ED-48:
 * ligacao por chave, sem dependencia nova).
 *
 * <p>Vazio = a origem da P4.2 nao tem o dado do ano-base: a ED-78
 * RECUSA montar a declaracao (nunca substitui por zero nem pela
 * soma da ED-85 em silencio - a divergencia entre os dois e dado da
 * reconciliacao, ver {@link Nr15BalanceAssembler}).
 */
public interface ProducedVolumePort {

    /** Chave da variavel na semente do mapa SINISA da P4.2. */
    String SINISA_VARIABLE_KEY = "AGUA.VOLUME_PRODUZIDO";

    Optional<Double> producedVolumeM3(UUID tenantId, int baseYear);
}
