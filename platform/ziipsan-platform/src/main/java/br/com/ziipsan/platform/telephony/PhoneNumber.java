package br.com.ziipsan.platform.telephony;

import java.util.Objects;

/**
 * Normalizacao do numero chamador (ED-44, Telefonia minima).
 * A identificacao usa os dados de contato que as fontes ja tem (cadastro
 * da unidade consumidora / contatos do atendimento) — NENHUM cadastro
 * paralelo de telefones nasce aqui: esta classe so normaliza a chave de
 * comparacao (digitos, com DDI 55 removido quando presente) para o numero
 * vindo do PABX e o numero guardado na fonte serem comparaveis.
 */
public final class PhoneNumber {
    private PhoneNumber() {}
    public static String normalize(String raw) {
        Objects.requireNonNull(raw, "numero obrigatorio");
        String d = raw.replaceAll("\\D", "");
        if (d.startsWith("55") && d.length() > 11) d = d.substring(2);
        if (d.length() < 8 || d.length() > 11) throw new IllegalArgumentException("numero chamador invalido: digitos=" + d.length());
        return d;
    }
    public static boolean matches(String storedRaw, String incomingRaw) {
        try { return normalize(storedRaw).equals(normalize(incomingRaw)); }
        catch (RuntimeException e) { return false; }
    }
}
