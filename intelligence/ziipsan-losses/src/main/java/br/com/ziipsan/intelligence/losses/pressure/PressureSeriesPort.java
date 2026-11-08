package br.com.ziipsan.intelligence.losses.pressure;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Porta de leitura da serie de pressao da ED-17 (ED-86): o ponto de
 * controle de pressao NAO recadastra medidor — ele referencia o ponto
 * de medicao da ED-17 ({@code MeasuringPoint}) pelo codigo estavel do
 * tenant, e a serie de pressao por ponto vem da ED-17 (amostras do
 * supervisório / canais de telemetria, conforme a fiacao do tenant).
 *
 * <p>Modo fail-closed, no padrao da correcao R1 da ED-85: vinculo com
 * codigo inexistente na ED-17 ou porta ausente RECUSAM. A fiacao real
 * (adaptador sobre o cadastro e as series da ED-17, incluindo qual
 * tag/canal carrega a pressao em mca) e da integracao — pendencia
 * nomeada no relatorio; nenhuma tag de pressao e inventada aqui.
 */
public interface PressureSeriesPort {

    /** Uma amostra de pressao (mca) num instante, vinda da serie da ED-17. */
    record PressureSample(LocalDateTime measuredAt, double pressureMca) {
        public PressureSample {
            Objects.requireNonNull(measuredAt, "instante da amostra de pressao obrigatorio");
            if (pressureMca < 0 || Double.isNaN(pressureMca))
                throw new IllegalArgumentException("pressao da amostra invalida (mca >= 0): " + pressureMca);
        }
    }

    /** Verdadeiro somente se existe ponto de medicao da ED-17 com este codigo no tenant. */
    boolean exists(UUID tenantId, String measuringPointCode);

    /** Serie de pressao do ponto no intervalo [from, to] (vazia quando nao ha amostras). */
    List<PressureSample> series(UUID tenantId, String measuringPointCode,
                                 LocalDateTime from, LocalDateTime to);
}
