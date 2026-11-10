package br.com.ziipsan.commercial.collection.dunning.thirdparty;

/**
 * Recusa nomeada da ED-68: toda a regra violada sai com um codigo estavel
 * (no padrao das recusas da casa — ex.: GovernanceValidationException da
 * P3.31/ED-37), nunca com efeito silencioso nem valor presumido.
 */
public final class ThirdPartyCollectionException extends RuntimeException {
    private final String code;

    public ThirdPartyCollectionException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
