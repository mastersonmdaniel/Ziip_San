package br.com.ziipsan.platform.poc;

import java.util.Objects;

/**
 * P3.23: guardas do tenant de demonstração, como regra pura.
 *
 * <p>O aceite exige um ambiente de demonstração <em>separado</em> da
 * homologação local e uma massa <em>sintética</em>. Estas guardas são a
 * forma executável dessas duas frases — o script
 * {@code scripts/demo/recriar-demo.sh} aplica as mesmas regras antes de
 * semear, e os testes conferem os dois lados com os mesmos casos:
 * <ul>
 *   <li>o tenant de demonstração tem id e schema próprios, diferentes
 *       dos de desenvolvimento/homologação;</li>
 *   <li>{@code ziipsan.fiscal.production-allowed} é falsa (regra de todos
 *       os perfis locais da P0.27);</li>
 *   <li>a massa tem pelo menos 10.000 unidades (piso do aceite);</li>
 *   <li>todos os documentos da massa estão marcados como sintéticos —
 *       a guarda recebe a contagem conferida pelo gerador e recusa
 *       qualquer divergência, em vez de confiar na declaração.</li>
 * </ul>
 * Violação é {@link IllegalArgumentException} com a regra citada:
 * demonstração montada sobre a homologação ou com documento sem marca
 * não é um estado válido que se registre — é um erro que se para.
 */
public final class DemoTenantGuard {

    public static final int MINIMO_UNIDADES = 10_000;

    private DemoTenantGuard() {
    }

    /**
     * @param tenantDemoId        id do tenant de demonstração
     * @param tenantDemoSchema    schema do tenant de demonstração
     * @param tenantDevId         id do tenant de desenvolvimento (pode ser nulo se não houver)
     * @param tenantDevSchema     schema de desenvolvimento (pode ser nulo)
     * @param fiscalProductionAllowed valor de {@code ziipsan.fiscal.production-allowed}
     * @param unidadesSemeadas    unidades geradas para a massa
     * @param documentosTotal     documentos gerados
     * @param documentosMarcadosSinteticos documentos com a marca de sintético
     */
    public static void check(String tenantDemoId, String tenantDemoSchema,
                             String tenantDevId, String tenantDevSchema,
                             boolean fiscalProductionAllowed,
                             long unidadesSemeadas,
                             long documentosTotal, long documentosMarcadosSinteticos) {
        if (tenantDemoId == null || tenantDemoId.isBlank()) {
            throw new IllegalArgumentException("Tenant de demonstração é obrigatório");
        }
        if (tenantDemoSchema == null || tenantDemoSchema.isBlank()) {
            throw new IllegalArgumentException("Schema do tenant de demonstração é obrigatório");
        }
        if (Objects.equals(tenantDemoId, tenantDevId)) {
            throw new IllegalArgumentException(
                    "Tenant de demonstração igual ao de desenvolvimento/homologação: "
                            + "a demonstração é separada da homologação (P3.23/P0.27)");
        }
        if (tenantDevSchema != null && Objects.equals(tenantDemoSchema, tenantDevSchema)) {
            throw new IllegalArgumentException(
                    "Schema de demonstração igual ao de desenvolvimento/homologação: "
                            + "a demonstração é separada da homologação (P3.23/P0.27)");
        }
        if (fiscalProductionAllowed) {
            throw new IllegalArgumentException(
                    "ziipsan.fiscal.production-allowed verdadeira em perfil local: "
                            + "a P0.27 exige falsa em todos os perfis locais");
        }
        if (unidadesSemeadas < MINIMO_UNIDADES) {
            throw new IllegalArgumentException(
                    "Massa de demonstração com " + unidadesSemeadas + " unidades, abaixo do "
                            + "piso de " + MINIMO_UNIDADES + " do aceite da P3.23");
        }
        if (documentosTotal <= 0 || documentosMarcadosSinteticos != documentosTotal) {
            throw new IllegalArgumentException(
                    "Documentos da massa sem a marca de sintético: " + documentosMarcadosSinteticos
                            + " marcados de " + documentosTotal + " gerados (P3.23 exige todos)");
        }
    }
}
