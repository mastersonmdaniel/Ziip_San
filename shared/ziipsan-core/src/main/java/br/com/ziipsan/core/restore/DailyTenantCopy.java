package br.com.ziipsan.core.restore;

import br.com.ziipsan.core.json.Json;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Copia logica diaria do schema de cada tenant (P3.33): o manifesto de
 * UMA copia, com a retencao do RNF-43.
 *
 * <p>A copia e o {@code pg_dump -n <schema>} do tenant (modelo SCHEMA do
 * ADR-0005; no tenant SHARED/P4.6, a extracao e por {@code tenant_id} —
 * ver o roteiro), gravada em armazenamento com retencao imutavel. Ela
 * alimenta tres consumidores declarados no aceite/plano:
 * <ul>
 *   <li>a restauracao seletiva desta tarefa (fonte quando o PITR nao
 *       alcanca ou nao e o caminho escolhido);</li>
 *   <li>a exportacao de fim de contrato da P3.16;</li>
 *   <li>o pacote de reversao da ED-04 (o {@code importar-tenant.sh} da
 *       ED-04 ja espera manifesto + SHA256SUMS + dump neste formato).</li>
 * </ul>
 *
 * <p>Retencao (RNF-43): "backups expiram em ate 35 dias" — a expiracao
 * gravada e {@code capturedAt + 35 dias}, o teto do RNF, nem um dia a
 * mais; copia vencida NAO e fonte de restauracao
 * ({@link #isUsableAsRestoreSource(Instant)}).
 *
 * <p>Crypto-shredding: escopo da LC.5, NAO implementado aqui. O que
 * esta classe faz e PREPARAR: o manifesto carrega a referencia da chave
 * do tenant ({@code tenantKeyId}, a linha de {@code public.tenant_key}
 * da P0.28/core {@code V6}) para que a destruicao da DEK na LC.5
 * alcance tambem estas copias, e o campo
 * {@code cryptoShredding} do manifesto registra o escopo pendente em
 * vez de fingir o efeito.
 *
 * <p>JSON somente pela classe {@code Json} do core (regra do projeto).
 */
public final class DailyTenantCopy {

    /** Retencao maxima das copias: RNF-43 (expiracao em ate 35 dias). */
    public static final Duration RETENTION = Duration.ofDays(35);

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SCHEMA_NAME = Pattern.compile("[a-z_][a-z0-9_]*");

    private DailyTenantCopy() {
    }

    /** Manifesto de uma copia logica diaria. */
    public record CopyManifest(
            UUID tenantId,
            String schemaName,
            UUID tenantKeyId,
            Instant capturedAt,
            String storageKey,
            String sha256,
            long sizeBytes) {

        public CopyManifest {
            Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
            if (schemaName == null || !SCHEMA_NAME.matcher(schemaName).matches()) {
                throw new IllegalArgumentException(
                        "schemaName invalido (identificador simples em minusculas): " + schemaName);
            }
            Objects.requireNonNull(tenantKeyId,
                    "tenantKeyId e obrigatorio: sem a referencia da chave da P0.28, o crypto-shredding da LC.5 nao alcanca a copia");
            Objects.requireNonNull(capturedAt, "capturedAt e obrigatorio");
            if (storageKey == null || storageKey.isBlank()) {
                throw new IllegalArgumentException("storageKey e obrigatorio");
            }
            if (sha256 == null || !SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("sha256 invalido (esperado 64 hex minusculos)");
            }
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("sizeBytes nao pode ser negativo");
            }
        }

        /** Expiracao pelo RNF-43: captura + 35 dias (o teto, nao uma folga). */
        public Instant expiresAt() {
            return capturedAt.plus(RETENTION);
        }

        /** Vencida em {@code now} (no instante exato da expiracao, ja venceu). */
        public boolean isExpiredAt(Instant now) {
            Objects.requireNonNull(now, "now e obrigatorio");
            return !now.isBefore(expiresAt());
        }

        /** Fonte de restauracao/exportacao so enquanto nao vencida. */
        public boolean isUsableAsRestoreSource(Instant now) {
            return !isExpiredAt(now);
        }

        /** Manifesto JSON (o mesmo arquivo que a ED-04 e a P3.16 consomem), via {@code Json}. */
        public String manifestJson() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tenantId", tenantId.toString());
            map.put("schemaName", schemaName);
            map.put("tenantKeyId", tenantKeyId.toString());
            map.put("capturedAt", capturedAt.toString());
            map.put("expiresAt", expiresAt().toString());
            map.put("retentionRule", "RNF-43 (expiracao em ate 35 dias)");
            map.put("storageKey", storageKey);
            map.put("sha256", sha256);
            map.put("sizeBytes", sizeBytes);
            map.put("format", "pg_dump -n <schema> (formato custom)");
            map.put("consumers", java.util.List.of("P3.33-restauracao", "P3.16-exportacao", "ED-04-reversao"));
            map.put("cryptoShredding", "PENDENTE-LC.5 (destruicao da DEK alcanca esta copia por tenantKeyId)");
            return Json.write(map);
        }
    }
}
