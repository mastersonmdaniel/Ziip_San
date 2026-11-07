package br.com.ziipsan.commercial.fiscal.arquivo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 no formato do dicionário fiscal (64 hex minúsculos) — utilidade
 * única do pacote de arquivamento (P2.18), no mesmo desenho do utilitário do
 * pacote de homologação da P3.7: nenhuma outra classe deste pacote calcula
 * hash por conta própria.
 */
final class Sha256 {

    private Sha256() {
    }

    static String deBytes(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é algoritmo obrigatório de toda JRE; ausência é defeito do ambiente.
            throw new IllegalStateException("SHA-256 indisponível nesta JRE", e);
        }
    }

    static boolean formatoValido(String sha256) {
        return sha256 != null && sha256.matches("[0-9a-f]{64}");
    }
}
