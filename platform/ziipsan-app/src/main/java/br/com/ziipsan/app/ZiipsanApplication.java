package br.com.ziipsan.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Aplicação ZIIPSAN.
 *
 * <p>O scan é restrito de propósito: core, platform, cadastro e a própria aplicação. Escanear
 * {@code br.com.ziipsan} inteiro subiria serviços que ainda não podem rodar (SCADA, SINIR, LGPD,
 * qualidade), cada um com dependências e premissas próprias. Os módulos entram aqui à medida que
 * ganham serviço de aplicação, API e teste de ponta a ponta.
 *
 * <p>As entidades de todos os módulos continuam mapeadas (PlatformConfig escaneia {@code br.com.ziipsan}
 * para o JPA), porque o schema do tenant é um só.
 *
 * <p>P1.27: o catálogo GIS entra aqui (só o subpacote {@code catalog}: serviço e
 * API de tipos/itens). O resto do módulo GIS (ex.: EPANET) continua fora do
 * contexto até ganhar serviço de aplicação e API próprios.
 *
 * <p>P1.35: o cadastro de ativos entra aqui (serviço e API). A spec OpenAPI
 * é regenerada pela integração; este front só declara as rotas.
 *
 * <p>P2.30: o motor de modelos entra aqui (serviço e API). Sem o pacote no
 * {@code EnableJpaRepositories} abaixo, o contexto subia o controller e o
 * serviço (o scan de componentes cobre {@code br.com.ziipsan.platform})
 * mas nenhum repositório JPA — e o IT falhava com NoSuchBeanDefinition.
 *
 * <p>P2.26: a API pública de integração entra aqui (serviço e API). A
 * cadeia de segurança própria dela vive em {@code SecurityConfig}
 * (ordem 0, só {@code /api/v1/public-api/**}); este scan só sobe os
 * serviços e controllers.
 *
 * <p>II-1 (PDCA GIS Ciclo 2): a OS por clique entra aqui (subpacote
 * {@code clickos}: controller, serviço de aplicação e adaptador da
 * porta de OS sobre o motor da P1.31). A propagação depende da
 * {@code SnapshotPort} da GIS-11, que ainda não tem implementação de
 * produção — sem ela o serviço recusa com a pendência nomeada, sem
 * impedir o arranque (ver o serviço de aplicação do pacote).
 *
 * <p>P-APPDEVICE-01 (R40-3-01): o cadastro de dispositivos de campo entra
 * aqui (serviço e API). Sem o pacote no {@code EnableJpaRepositories}
 * abaixo, o contexto subia o controller e o serviço (o scan de
 * componentes cobre {@code br.com.ziipsan.platform}) mas nenhum
 * repositório JPA — e o IT falhava com NoSuchBeanDefinition (mesmo
 * padrão da P2.30).
 *
 * <p>P-GISVALIDATION-01 (R40-A-01): a validação GIS entra aqui (subpacote
 * {@code validation}: serviço de validação de áreas sujas, serviços de
 * qualidade/componentes e o painel de qualidade). Sem o pacote no scan,
 * o {@code GisDirtyAreaValidationHandler} (em {@code br.com.ziipsan.app.gis})
 * exigia o {@code GisValidationService} no construtor e o contexto falhava
 * com NoSuchBeanDefinition — mesmo padrão da P1.27.
 */
@SpringBootApplication(scanBasePackages = {
        "br.com.ziipsan.app",
        "br.com.ziipsan.core",
        "br.com.ziipsan.platform",
        "br.com.ziipsan.commercial.registry",
        "br.com.ziipsan.intelligence.gis.catalog",
        // P-GISVALIDATION-01 (R40-A-01): validação GIS — serviço de áreas
        // sujas, qualidade/componentes e o painel de qualidade.
        "br.com.ziipsan.intelligence.gis.validation",
        // II-1 (PDCA GIS Ciclo 2): OS por clique — controller, serviço de
        // aplicação e o adaptador da porta de OS sobre o motor da P1.31.
        "br.com.ziipsan.intelligence.gis.clickos",
        "br.com.ziipsan.operations.asset",
        "br.com.ziipsan.integration",
})
@EnableJpaRepositories(basePackages = {
        "br.com.ziipsan.core.audit",
        "br.com.ziipsan.commercial.registry.repository",
        // P0.13: repositórios de usuários do tenant.
        "br.com.ziipsan.platform.user",
        // P1.33: repositórios de visões salvas.
        "br.com.ziipsan.platform.search",
        // P1.27: repositórios do catálogo GIS.
        "br.com.ziipsan.intelligence.gis.catalog.repository",
        // P2.30: repositórios do motor de modelos (document_templates,
        // template_versions, generated_documents).
        "br.com.ziipsan.platform.templates",
        // P-APPDEVICE-01 (R40-3-01): repositório de dispositivos de campo
        // (app_devices).
        "br.com.ziipsan.platform.field",
})
@ConfigurationPropertiesScan("br.com.ziipsan.app")
public class ZiipsanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZiipsanApplication.class, args);
    }
}
