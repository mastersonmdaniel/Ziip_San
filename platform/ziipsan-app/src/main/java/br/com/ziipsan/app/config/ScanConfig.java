package br.com.ziipsan.app.config;

import br.com.ziipsan.platform.infra.scan.ClamAvScanner;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Varredor de arquivos da P1.20 como bean (P1.19): o mesmo pipeline dos
 * anexos de chamados varre o logo do tenant. A construção não conecta no
 * daemon — a falha de conexão só aparece na varredura, como
 * {@code ScannerUnavailableException}, e o upload cai na quarentena (202).
 */
@Configuration
public class ScanConfig {

    @Bean
    public FileScanner fileScanner(ZiipsanProperties properties) {
        ZiipsanProperties.Clamav clamav = properties.clamav();
        Path databaseDir = clamav.databaseDir() == null ? null : Path.of(clamav.databaseDir());
        return new ClamAvScanner(clamav.host(), clamav.port(), clamav.timeoutMillis(), databaseDir);
    }
}
