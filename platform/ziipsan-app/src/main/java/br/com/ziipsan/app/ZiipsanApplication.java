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
 */
@SpringBootApplication(scanBasePackages = {
        "br.com.ziipsan.app",
        "br.com.ziipsan.core",
        "br.com.ziipsan.platform",
        "br.com.ziipsan.commercial.registry",
})
@EnableJpaRepositories(basePackages = {
        "br.com.ziipsan.core.audit",
        "br.com.ziipsan.commercial.registry.repository",
})
@ConfigurationPropertiesScan("br.com.ziipsan.app")
public class ZiipsanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZiipsanApplication.class, args);
    }
}
