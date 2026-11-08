package br.com.ziipsan.platform.clientapp;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta dos parametros do app do cliente (ED-39), no padrao das portas de
 * parametros do projeto (P3.34/ED-38): o adaptador le os valores vigentes
 * do catalogo (P0.19); esta tarefa NAO presume fabrica onde o plano nao
 * a da.
 *
 * <ul>
 *   <li>{@code app.client_android_store_account} — conta de organizacao
 *       do Google Play do prestador (definicao criada na core V11_1,
 *       SEM fabrica: ato do dono);</li>
 *   <li>{@code app.client_ios_store_account} — conta do Apple Developer
 *       Program (organizacao) do prestador (idem);</li>
 *   <li>visibilidade da curva horaria ao consumidor — a MESMA regra da
 *       ED-17 ({@code HourlyProfileAccessRule}): o app so mostra
 *       consumo quase em tempo real com o parametro do tenant ligado;
 *       a curva continua sendo prova na revisao sempre (fora do app).</li>
 * </ul>
 */
public interface ClientAppParameters {

    /** Valor de {@code app.client_android_store_account}, se registrado. */
    Optional<String> androidStoreAccount();

    /** Valor de {@code app.client_ios_store_account}, se registrado. */
    Optional<String> iosStoreAccount();

    /** A curva horaria e visivel ao consumidor deste tenant? (regra da ED-17) */
    boolean hourlyProfileVisibleToCustomer(UUID tenantId);
}
