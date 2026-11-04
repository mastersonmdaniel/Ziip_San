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
 */
@SpringBootApplication(scanBasePackages = {
        "br.com.ziipsan.app",
        "br.com.ziipsan.core",
        "br.com.ziipsan.platform",
        "br.com.ziipsan.commercial.registry",
        "br.com.ziipsan.intelligence.gis.catalog",
        "br.com.ziipsan.operations.asset",
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
})
@ConfigurationPropertiesScan("br.com.ziipsan.app")
public class ZiipsanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZiipsanApplication.class, args);
    }
}
