package br.com.ziipsan.commercial.billingrules.thermal;

import java.util.Map;

/**
 * Documento pronto para o leiaute termico (ED-19): os valores das variaveis
 * do modelo + os dois elementos graficos da conta — codigo de barras e QR
 * Pix. Barras e QR sao opcionais por documento (comunicado pode nao ter
 * cobranca resolvida), mas quando o chamador os fornece eles sao impressos;
 * quando nao fornece, nada e inventado no lugar.
 *
 * @param values       variavel -> valor ja formatado para impressao
 * @param barcodeValue digitos do codigo de barras (cobranca hibrida, P3.2)
 * @param pixPayload   payload BR Code do QR Pix ({@code PixPayload})
 */
public record ThermalDocument(Map<String, String> values, String barcodeValue, String pixPayload) {

    public ThermalDocument {
        values = values == null ? Map.of() : Map.copyOf(values);
        if (barcodeValue != null && !barcodeValue.matches("[0-9 ]+")) {
            throw new IllegalArgumentException("Codigo de barras termico deve ser numerico: " + barcodeValue);
        }
        if (pixPayload != null && pixPayload.isBlank()) {
            throw new IllegalArgumentException("Payload Pix em branco nao e payload");
        }
    }

    public boolean hasBarcode() {
        return barcodeValue != null;
    }

    public boolean hasPixQr() {
        return pixPayload != null;
    }
}
