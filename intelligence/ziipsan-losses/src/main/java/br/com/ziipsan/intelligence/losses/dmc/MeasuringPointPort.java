package br.com.ziipsan.intelligence.losses.dmc;

import java.util.UUID;

/**
 * Porta de consulta aos pontos de medicao da ED-17 (ED-85, correcao
 * R1 da revisao independente): o vinculo de macromedidor de um DMC so
 * pode apontar para um {@code MeasuringPoint} que EXISTE no tenant —
 * a existencia e conferida por esta porta no ato do vinculo
 * ({@link DmcDistrict#linkMacroMeter}), em modo fail-closed: porta
 * ausente ou codigo inexistente RECUSAM o vinculo.
 *
 * <p>A ED-17 continua dona do cadastro do ponto de medicao; esta
 * porta e somente leitura de existencia pelo codigo estavel. A
 * fiacao real (adaptador sobre o cadastro da ED-17) e da integracao
 * — ver pendencia P-ED85-01 no relatorio da ED-85.
 */
public interface MeasuringPointPort {

    /** Verdadeiro somente se existe ponto de medicao da ED-17 com este codigo no tenant. */
    boolean exists(UUID tenantId, String measuringPointCode);
}
