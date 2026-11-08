package br.com.ziipsan.platform.portal.selfcare;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class Ed38TestSupport {
    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID BILL = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    static final class Params implements SelfCareParameters {
        boolean govbr; String minLevel; boolean peer; Integer peerMin; boolean passkey;
        public boolean govbrEnabled(UUID t) { return govbr; }
        public Optional<String> govbrMinLevelRaw(UUID t) { return Optional.ofNullable(minLevel); }
        public boolean peerComparisonEnabled(UUID t) { return peer; }
        public Optional<Integer> peerMinUnits(UUID t) { return Optional.ofNullable(peerMin); }
        public boolean passkeyEnabled(UUID t) { return passkey; }
    }

    /** Codigo verificador com a MESMA formula da ED-34 (fixture de teste do adaptador). */
    static String ed34Code(String type, String number, String issuedIso) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((type + "|" + number + "|" + issuedIso).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h).substring(0, 20).toUpperCase();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    static AuthenticDocument.AuthenticityPort authenticity() {
        Map<String, String> levels = new HashMap<>();
        levels.put("ANNUAL_SETTLEMENT_CERTIFICATE", "ADVANCED");
        return new AuthenticDocument.AuthenticityPort() {
            public String signatureLevelFor(String documentType) { return levels.get(documentType); }
            public String verificationCode(String t, String n, String d) { return ed34Code(t, n, d); }
            public String publicValidationBaseUrl(UUID tenantId) { return "https://portal.exemplo.gov.br"; }
        };
    }
}
