package br.com.ziipsan.intelligence.losses.nr15;

import java.util.List;
import java.util.UUID;

/**
 * Porta de leitura dos balancos por DMC da ED-85 (ED-78): devolve as
 * fotos {@link DmcBalanceSnapshot} de um tenant num ano civil (a
 * fiacao le {@code loss_monthly_balances} da ED-85; pendencia
 * P-ED78-05). Lista vazia significa "sem balanco da ED-85 no ano" -
 * nunca e convertida em zeros pela ED-78.
 */
public interface DmcBalancePort {

    List<DmcBalanceSnapshot> balancesOfYear(UUID tenantId, int baseYear);
}
