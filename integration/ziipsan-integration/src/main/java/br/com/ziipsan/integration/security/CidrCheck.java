package br.com.ziipsan.integration.security;

import java.net.InetAddress;
import java.util.List;

/**
 * Pertinência de um IP a uma allowlist de IPs/CIDRs (P2.26).
 *
 * <p>Entrada vazia significa sem restrição (a restrição, quando existe,
 * vem do cliente ou, na falta dele, do parâmetro {@code api.ip_allowlist}
 * do tenant). IPv4 e IPv6, com prefixo; sem prefixo, igualdade exata.
 * Comparação por bytes mascarados — sem DNS reverso, sem nome.
 */
public final class CidrCheck {

    private CidrCheck() {
    }

    public static boolean allowed(String remoteIp, List<String> allowlist) {
        if (allowlist == null || allowlist.isEmpty()) {
            return true;
        }
        if (remoteIp == null) {
            return false;
        }
        InetAddress remote;
        try {
            remote = InetAddress.getByName(remoteIp);
        } catch (Exception e) {
            return false;
        }
        for (String entry : allowlist) {
            if (matches(remote, entry)) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(InetAddress remote, String entry) {
        try {
            String host = entry;
            int prefix = -1;
            if (entry.contains("/")) {
                host = entry.substring(0, entry.indexOf('/'));
                prefix = Integer.parseInt(entry.substring(entry.indexOf('/') + 1));
            }
            InetAddress network = InetAddress.getByName(host);
            byte[] remoteBytes = remote.getAddress();
            byte[] networkBytes = network.getAddress();
            if (remoteBytes.length != networkBytes.length) {
                return false;
            }
            if (prefix < 0) {
                return java.util.Arrays.equals(remoteBytes, networkBytes);
            }
            int bits = prefix;
            for (int i = 0; i < remoteBytes.length && bits > 0; i++) {
                int mask = bits >= 8 ? 0xFF : (0xFF << (8 - bits)) & 0xFF;
                if ((remoteBytes[i] & mask) != (networkBytes[i] & mask)) {
                    return false;
                }
                bits -= 8;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
