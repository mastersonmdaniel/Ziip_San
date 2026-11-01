package br.com.ziipsan.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do migrador ({@code ziipsan.migrator.*}), SEPARADAS de {@link ZiipsanProperties}.
 *
 * <p>P0.6: este record NÃO é {@code @Validated} de propósito. Ele é registrado pelo
 * {@code @ConfigurationPropertiesScan} em todo processo, mas só é LIDO quando o perfil
 * {@code migrate} cria o {@code migratorDataSource} (que falha explícito se usuário ou
 * senha estiverem em branco). Sem isso, a api e o worker exigiriam a senha do migrador
 * só para subir — e o health agregado chegava a ficar DOWN por causa do indicador de
 * saúde do DataSource de migração (achado da verificação da imagem, P0.5).
 *
 * <p>As variáveis de ambiente continuam as mesmas ({@code ZIIPSAN_DB_MIGRATOR_USER} e
 * {@code ZIIPSAN_DB_MIGRATOR_PASSWORD}, ver application.yml): mudou só o prefixo da
 * propriedade, não o contrato com o ambiente.
 */
@ConfigurationProperties("ziipsan.migrator")
public record MigratorProperties(String user, String password) {
}
