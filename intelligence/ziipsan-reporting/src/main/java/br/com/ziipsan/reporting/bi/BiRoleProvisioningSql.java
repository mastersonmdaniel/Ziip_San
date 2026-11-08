package br.com.ziipsan.reporting.bi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * ED-47: SQL de provisionamento da role de BI de um tenant, executado
 * pela fiação (porta {@link BiRoleProvisioner}) SOBRE A RÉPLICA — nunca
 * no primário e nunca pela migration Flyway: role é objeto de cluster,
 * criada quando o tenant é habilitado ao BI, e a faixa {@code V15_x} do
 * mapa de migrations (§3) não reserva número fechado para a ED-47 (a
 * {@code V15_1} é da P3.22 e a {@code V15_2} é da P3.41). Inventar um
 * número é proibido pela regra da tarefa; o DDL vive aqui, gerado e
 * testado, e a persistência do mapeamento fica atrás da porta
 * {@link BiRoleStore}.
 *
 * <p>Modelo de isolamento (o coração do aceite):
 * <ol>
 *   <li>a role do tenant só tem {@code SELECT} nas views da SUA camada
 *       de acesso ({@code <accessSchema>.bi_rpt_*}); nenhum grant sobre
 *       tabelas-base nem sobre as views {@code rpt_*} — consulta livre
 *       fora da camada é negada pelo banco, não por convenção;</li>
 *   <li>cada view da camada filtra
 *       {@code tenant_id = bi_current_tenant_id()}, função que resolve o
 *       tenant por {@code current_user} no mapeamento
 *       {@code reporting_bi_role_tenant} — a GUC
 *       {@code app.current_tenant} NÃO entra na função, logo
 *       {@code SET app.current_tenant = '<outro>'} não muda o que a
 *       role lê, em schema próprio e em schema compartilhado;</li>
 *   <li>a role nasce {@code NOLOGIN}: quem liga o LOGIN é a fiação, com
 *       a senha vinda do cofre (P0.28/LC.4), por {@code ALTER ROLE}
 *       separado — senha nunca aparece neste SQL nem em log.</li>
 * </ol>
 *
 * <p>Todos os identificadores vêm de tipos validados (role derivada do
 * UUID; schema por padrão fechado; views {@code rpt_*} por padrão
 * fechado) e literais passam por {@link #literal(String)}.
 */
public final class BiRoleProvisioningSql {

    private static final Pattern VIEW_PATTERN = Pattern.compile("rpt_[a-z0-9_]{1,60}");

    private BiRoleProvisioningSql() {
    }

    /** Função de contexto do BI + tabela de mapeamento (uma vez por banco/réplica). */
    public static List<String> sharedObjectsSql() {
        return List.of(
                "CREATE TABLE IF NOT EXISTS public.reporting_bi_role_tenant ("
                        + "role_name TEXT PRIMARY KEY, tenant_id UUID NOT NULL UNIQUE)",
                "CREATE OR REPLACE FUNCTION public.bi_current_tenant_id() RETURNS UUID "
                        + "LANGUAGE SQL STABLE AS $$ SELECT tenant_id FROM public.reporting_bi_role_tenant "
                        + "WHERE role_name = current_user $$");
    }

    /**
     * Provisiona a camada de acesso do tenant: role, mapeamento, schema
     * de acesso, uma view filtrada por view de relatório e os grants
     * mínimos. {@code reportingViews} são os nomes {@code rpt_*} do
     * registro da P3.22 que vivem em {@code mapping.sourceSchema()}.
     */
    public static List<String> provisionSql(TenantBiRole mapping, List<String> reportingViews) {
        Objects.requireNonNull(mapping, "mapping é obrigatório");
        if (reportingViews == null || reportingViews.isEmpty()) {
            throw new IllegalArgumentException("Sem views de relatório não há camada de BI a provisionar");
        }
        String role = mapping.roleName();
        String access = mapping.accessSchema();
        List<String> sql = new ArrayList<>();
        sql.add("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = "
                + literal(role) + ") THEN CREATE ROLE " + role + " NOLOGIN NOSUPERUSER "
                + "NOCREATEDB NOCREATEROLE NOREPLICATION; END IF; END $$");
        sql.add("INSERT INTO public.reporting_bi_role_tenant (role_name, tenant_id) VALUES ("
                + literal(role) + ", " + literal(mapping.tenantId().toString()) + "::uuid) "
                + "ON CONFLICT (role_name) DO UPDATE SET tenant_id = EXCLUDED.tenant_id");
        sql.add("CREATE SCHEMA IF NOT EXISTS " + access);
        for (String view : reportingViews) {
            if (!VIEW_PATTERN.matcher(view).matches()) {
                throw new IllegalArgumentException("View fora do padrão rpt_*: " + view);
            }
            sql.add("CREATE OR REPLACE VIEW " + access + ".bi_" + view + " AS SELECT * FROM "
                    + mapping.sourceSchema() + "." + view
                    + " WHERE tenant_id = public.bi_current_tenant_id()");
            sql.add("GRANT SELECT ON " + access + ".bi_" + view + " TO " + role);
        }
        sql.add("GRANT USAGE ON SCHEMA " + access + " TO " + role);
        return List.copyOf(sql);
    }

    /**
     * Desprovisiona sem apagar histórico: revoga os grants das views,
     * tira o mapeamento (a função passa a resolver nulo e as views
     * devolvem vazio) e desliga o LOGIN. A role e o schema ficam para a
     * auditoria/forense; a exclusão física é decisão operacional à parte.
     */
    public static List<String> deprovisionSql(TenantBiRole mapping, List<String> reportingViews) {
        Objects.requireNonNull(mapping, "mapping é obrigatório");
        String role = mapping.roleName();
        String access = mapping.accessSchema();
        List<String> sql = new ArrayList<>();
        for (String view : reportingViews) {
            if (!VIEW_PATTERN.matcher(view).matches()) {
                throw new IllegalArgumentException("View fora do padrão rpt_*: " + view);
            }
            sql.add("REVOKE SELECT ON " + access + ".bi_" + view + " FROM " + role);
        }
        sql.add("DELETE FROM public.reporting_bi_role_tenant WHERE role_name = " + literal(role));
        sql.add("ALTER ROLE " + role + " NOLOGIN");
        return List.copyOf(sql);
    }

    /** Literal SQL de texto: aspas simples dobradas; nulo é recusado. */
    static String literal(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Literal SQL nulo");
        }
        return "'" + value.replace("'", "''") + "'";
    }
}
