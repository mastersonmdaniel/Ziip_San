package br.com.ziipsan.app.migrate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Saída do job {@code migrate} (P0.6).
 *
 * <p>Quando o perfil {@code migrate} está ativo, o {@code DatabaseMigrator} já rodou
 * na inicialização do contexto (é um {@code InitializingBean}). Este runner, que roda
 * depois do contexto pronto, só encerra o processo com código 0 — sem ele, o job
 * ficaria no ar como se fosse a api. Desligável por
 * {@code ziipsan.migrate.exit-after=false}, que os testes de perfil usam para provar
 * a migração sem matar a JVM do Surefire (um {@code System.exit} no teste mataria o
 * fork inteiro). O padrão é sair: um job que não sai não é um job.
 */
@Slf4j
@Component
@Profile("migrate")
@RequiredArgsConstructor
public class MigrateExitRunner implements ApplicationRunner {

    private final ApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        boolean exitAfter = Boolean.parseBoolean(
                context.getEnvironment().getProperty("ziipsan.migrate.exit-after", "true"));
        if (!exitAfter) {
            log.info("Job migrate: exit-after=false, processo fica no ar (modo teste)");
            return;
        }
        int code = SpringApplication.exit(context, () -> 0);
        log.info("Job migrate concluido, saindo com codigo {}", code);
        System.exit(code);
    }
}
