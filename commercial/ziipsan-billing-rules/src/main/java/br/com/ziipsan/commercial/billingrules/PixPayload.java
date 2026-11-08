package br.com.ziipsan.commercial.billingrules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Payload Pix "copia e cola" (BR Code / EMV QRCPS) para o QR impresso na
 * conta de campo (ED-19), conforme o Manual de Padroes para Iniciacao do
 * Pix do BCB: GUI {@code br.gov.bcb.pix}, moeda 986 (BRL), CRC16-CCITT
 * (polinomio 0x1021, valor inicial 0xFFFF) ao final.
 *
 * <p>Chave, nome, cidade e txid sao ENTRADAS de quem emite (resolvidos da
 * cobranca da P3.2 / cadastro do tenant): nada aqui inventa chave Pix nem
 * beneficiario. Nome e cidade sao normalizados para ASCII e truncados nos
 * limites do padrao (25 e 15 caracteres).
 */
public final class PixPayload {

    private static final String GUI = "br.gov.bcb.pix";

    private PixPayload() {
    }

    /**
     * @param pixKey chave Pix do recebedor (da cobranca, P3.2)
     * @param amount valor da conta; {@code null} = QR sem valor fechado
     * @param receiverName nome do recebedor (max. 25 no payload)
     * @param receiverCity cidade do recebedor (max. 15 no payload)
     * @param txid identificador da transacao (alfanumerico, max. 25)
     */
    public static String build(String pixKey, BigDecimal amount, String receiverName,
                               String receiverCity, String txid) {
        if (pixKey == null || pixKey.isBlank()) {
            throw new IllegalArgumentException("Chave Pix e obrigatoria para montar o QR");
        }
        if (receiverName == null || receiverName.isBlank()) {
            throw new IllegalArgumentException("Nome do recebedor e obrigatorio para o QR Pix");
        }
        if (receiverCity == null || receiverCity.isBlank()) {
            throw new IllegalArgumentException("Cidade do recebedor e obrigatoria para o QR Pix");
        }
        if (txid == null || !txid.matches("[A-Za-z0-9]{1,25}")) {
            throw new IllegalArgumentException("txid do QR Pix deve ser alfanumerico de 1 a 25: " + txid);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(field("00", "01"));
        String merchantAccount = field("00", GUI) + field("01", pixKey);
        sb.append(field("26", merchantAccount));
        sb.append(field("52", "0000"));
        sb.append(field("53", "986"));
        if (amount != null) {
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Valor do QR Pix negativo: " + amount);
            }
            sb.append(field("54", amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }
        sb.append(field("58", "BR"));
        sb.append(field("59", truncate(normalize(receiverName), 25)));
        sb.append(field("60", truncate(normalize(receiverCity), 15)));
        sb.append(field("62", field("05", txid)));
        sb.append("6304");
        sb.append(crc16(sb.toString()));
        return sb.toString();
    }

    /** CRC16-CCITT (0xFFFF, pol. 0x1021) em hexadecimal maiusculo de 4 digitos. */
    public static String crc16(String data) {
        Objects.requireNonNull(data, "dados do CRC sao obrigatorios");
        int crc = 0xFFFF;
        for (int i = 0; i < data.length(); i++) {
            crc ^= (data.charAt(i) & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format(Locale.ROOT, "%04X", crc);
    }

    private static String field(String id, String value) {
        if (value.length() > 99) {
            throw new IllegalArgumentException("Campo " + id + " do QR Pix excede 99 caracteres");
        }
        return id + String.format(Locale.ROOT, "%02d", value.length()) + value;
    }

    private static String normalize(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ]", " ")
                .trim()
                .replaceAll(" +", " ");
        return ascii.toUpperCase(Locale.ROOT);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
