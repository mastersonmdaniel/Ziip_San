package br.com.ziipsan.commercial.fiscal.homologacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * SHA-256 no formato do dicionário fiscal (64 hex minúsculos) — utilidade única do
 * pacote de homologação (P3.7): hash de XML, hash do lote da exclusão registrada e
 * conferência da restauração. Nenhuma outra classe deste pacote calcula hash por
 * conta própria.
 */
final class Sha256 {

    private Sha256() {
    }

    static String deTexto(String texto) {
        return deBytes(texto.getBytes(StandardCharsets.UTF_8));
    }

    static String deBytes(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é algoritmo obrigatório de toda JRE; ausência é defeito do ambiente.
            throw new IllegalStateException("SHA-256 indisponível nesta JRE", e);
        }
    }

    /**
     * Hash do lote de XMLs de um ciclo: SHA-256 dos hashes individuais ORDENADOS,
     * um por linha, com quebra de linha final — algoritmo fixo e reproduzível fora
     * desta classe (foi assim que os literais dos testes foram calculados).
     * Lote vazio é erro, nunca um hash de conjunto vazio: exclusão registrada sem
     * quantidade não existe.
     */
    static String doLote(List<String> hashesIndividuais) {
        if (hashesIndividuais == null || hashesIndividuais.isEmpty()) {
            throw new IllegalArgumentException("Hash do lote exige ao menos um hash individual");
        }
        for (String h : hashesIndividuais) {
            if (h == null || !h.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Hash individual fora do formato SHA-256 (64 hex minúsculos)");
            }
        }
        List<String> ordenados = hashesIndividuais.stream().sorted().toList();
        return deTexto(String.join("\n", ordenados) + "\n");
    }
}
