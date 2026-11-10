package br.com.ziipsan.platform.tenant;

import java.util.Locale;

/**
 * P4.6 (ADR-0005): modo de isolamento de um tenant, tal como registado em
 * {@code public.tenant_catalog.isolation_mode} (core V7, P0.21 — lista fechada
 * SCHEMA, SHARED, DEDICATED, com CHECK no banco).
 *
 * <p>Este enum e o vocabulario unico do codigo para a coluna: ate a P4.6 o modo
 * viajava como String solta (o provisionamento gravava o texto do pedido e o
 * CHECK do banco era a unica validacao). Espelhar a lista fechada em enum nao
 * muda a coluna nem o CHECK — so faz o codigo recusar modo inventado antes de
 * chegar ao banco, com mensagem clara.
 */
public enum IsolationMode {

    /** Schema proprio por tenant (padrao do ADR-0005, como hoje). */
    SCHEMA,

    /** Schema compartilhado: varios tenants no mesmo schema, isolados por tenant_id e RLS. */
    SHARED,

    /** Banco dedicado: cluster proprio, workers e janela de lote proprios. */
    DEDICATED;

    /** Texto da coluna em branco/ausente vale o padrao SCHEMA (mesma regra do provisionamento). */
    public static IsolationMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return SCHEMA;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "isolation_mode invalido: '" + raw + "' (valores da core V7: SCHEMA, SHARED, DEDICATED)", e);
        }
    }

    /** O texto exacto gravado em tenant_catalog.isolation_mode. */
    public String columnValue() {
        return name();
    }

    /** Neste modo o schema fisico e dividido com outros tenants (isolamento so por tenant_id + RLS). */
    public boolean sharesSchema() {
        return this == SHARED;
    }

    /** Neste modo o tenant vive num cluster (banco) so dele. */
    public boolean dedicatedDatabase() {
        return this == DEDICATED;
    }
}
