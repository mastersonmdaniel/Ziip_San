package br.com.ziipsan.core.certification.governance;

public class GovernanceAccessDeniedException extends RuntimeException {
    public GovernanceAccessDeniedException(String op,String what){super("acesso negado: "+op+" exige EDITOR/APPROVER ("+what+")");}
    /** Mensagem com o papel exigido exato (correcao da observacao da revisao: recordReview exige so APPROVER). */
    public GovernanceAccessDeniedException(String op,String required,String what){super("acesso negado: "+op+" exige "+required+" ("+what+")");}
}
