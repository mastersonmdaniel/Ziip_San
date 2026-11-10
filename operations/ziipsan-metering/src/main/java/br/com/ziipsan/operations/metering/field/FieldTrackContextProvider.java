package br.com.ziipsan.operations.metering.field;

import java.util.UUID;

/**
 * Porta de montagem do contexto da trilha GPS (padrao
 * {@link FieldSyncContextProvider}): a implementacao de producao le a escala
 * vigente do trabalhador, o registo do aviso ao empregado e os parametros
 * {@code field.*} do tenant. A ED-20 define a governanca e esta frente define
 * a porta e as regras que a consomem; a ligacao com as fontes reais (escala,
 * registo do aviso) e a pendencia P-MOB-GPS-02 — nenhuma fonte e presumida
 * aqui.
 */
public interface FieldTrackContextProvider {

    /** Contexto da trilha de um trabalhador (jornada, aviso, trava, limiares, retencao). */
    FieldTrackContext contextFor(UUID tenantId, UUID workerId);

    /**
     * Contexto de uma consulta ao painel de acompanhamento: o papel do
     * chamador e os trabalhadores no ambito da consulta. O papel chega por
     * esta entrada tipada — nenhuma fonte de papeis e inventada nesta frente.
     */
    FieldTrackPanelContext panelContextFor(UUID tenantId);
}
