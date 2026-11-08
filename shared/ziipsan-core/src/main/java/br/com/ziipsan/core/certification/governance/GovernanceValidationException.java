package br.com.ziipsan.core.certification.governance;

public class GovernanceValidationException extends RuntimeException {
    private final String code;
    public GovernanceValidationException(String code, String message){super(message);this.code=code;}
    public String code(){return code;}
}
