package br.com.sigsan.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Aplicação SIGSAN.
 *
 * <p>O scan é restrito de propósito: core, platform, cadastro e a própria aplicação. Escanear
 * {@code br.com.sigsan} inteiro subiria serviços que ainda não podem rodar (SCADA, SINIR, LGPD,
 * qualidade), cada um com dependências e premissas próprias. Os módulos entram aqui à medida que
 * ganham serviço de aplicação, API e teste de ponta a ponta.
 *
 * <p>As entidades de todos os módulos continuam mapeadas (PlatformConfig escaneia {@code br.com.sigsan}
 * para o JPA), porque o schema do tenant é um só.
 */
@SpringBootApplication(scanBasePackages = {
        "br.com.sigsan.app",
        "br.com.sigsan.core",
        "br.com.sigsan.platform",
        "br.com.sigsan.commercial.registry",
})
@EnableJpaRepositories(basePackages = {
        "br.com.sigsan.core.audit",
        "br.com.sigsan.commercial.registry.repository",
})
@ConfigurationPropertiesScan("br.com.sigsan.app")
public class SigsanApplication {

    public static void main(String[] args) {
        SpringApplication.run(SigsanApplication.class, args);
    }
}
