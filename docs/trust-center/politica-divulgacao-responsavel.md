# Política de divulgação responsável de vulnerabilidade — Ziip SAN

> **Estado: ESTRUTURA APROVADA PARA PUBLICAÇÃO, COM DOIS CAMPOS PENDENTES
> DE DECISÃO DO DONO.** O canal oficial de relato e o prazo de
> confirmação de recebimento não constam da especificação do projeto
> (P3.38, ADR-0020/0021) e **não foram inventados**: aparecem abaixo como
> `[PENDENTE — decisão do dono]`. Esta política só deve ser publicada
> quando os dois campos estiverem preenchidos e o
> `/.well-known/security.txt` correspondente tiver sido gerado (ver
> `README.md` deste diretório).

## 1. Compromisso

O Ziip SAN recebe relatos de vulnerabilidade de pesquisadores de
segurança de boa-fé e se compromete a tratá-los pelo fluxo registrado de
recebimento, triagem e resposta descrito nesta política e operado sobre
`support.vulnerability_report` (core `V10_6`). Relatar uma
vulnerabilidade por este canal é uma colaboração bem-vinda, não uma
ameaça.

## 2. Canal

- Canal oficial de relato: **[PENDENTE — decisão do dono]** (será o
  campo `Contact` do `/.well-known/security.txt`, RFC 9116, publicado
  nesta mesma hospedagem).
- Ao relatar, informe: descrição da vulnerabilidade, ativo/serviço
  afetado, passos para reproduzir e um canal de resposta. Você recebe
  uma referência de protocolo do relato (formato `VULN-AAAA-NNNN`), que
  acompanha toda a comunicação.
- Não envie dado pessoal de titulares, dado de tenant nem credencial de
  terceiros no relato; se a prova exigir um exemplo, use dado sintético
  ou mínimo e avise no próprio relato.

## 3. Prazo de resposta

- Confirmação de recebimento em até **[PENDENTE — decisão do dono]** a
  contar do recebimento. O relógio é medido pelo registro do fluxo: sem
  prazo publicado, o sistema declara `NO_TARGET_DEFINED` em vez de
  fingir pontualidade (ver `VulnerabilityReportFlow`).
- Depois da triagem, você recebe a classificação (aceito ou rejeitado,
  com o motivo) e, se aceito, é mantido informado até a correção e a
  divulgação coordenada.

## 4. Salvaguarda legal ao pesquisador (safe harbor)

Para o pesquisador que atua de boa-fé e dentro desta política, o Ziip
SAN se compromete a:

- **não** iniciar nem apoiar ação legal, cível ou criminal, pelo relato
  nem pelo acesso estritamente necessário para demonstrá-lo;
- considerar a pesquisa autorizada para os fins dos testes descritos no
  escopo (item 5), desde que respeitados os limites do item 6;
- não tratar o relato como violação de termos de uso quando ele segue
  esta política.

Esta salvaguarda cobre a conduta de boa-fé descrita aqui; ela não
autoriza acesso a dado de tenant ou de titular além do mínimo para
demonstrar a vulnerabilidade, nem atos fora do escopo.

## 5. Escopo

- Plataforma Ziip SAN: aplicação, APIs públicas e autenticadas, portal
  do consumidor e páginas públicas (status e Trust Center).
- Fora de escopo: infraestrutura de tenants e de terceiros (inclusive
  provedores listados no Trust Center), engenharia social, ataques
  físicos e testes contra contas que não sejam do próprio pesquisador.

## 6. Limites da pesquisa de boa-fé

- Sem negação de serviço, spam, degradação de desempenho ou destruição
  de dados.
- Sem acesso, alteração ou exfiltração de dado de terceiros; achou dado
  alheio, pare, não copie além do mínimo para provar e relate.
- Sem exigir pagamento ou vantagem como condição para não divulgar.

## 7. Divulgação coordenada

A divulgação pública do relato é coordenada: acontece depois da
correção (ou de mitigação registrada) e é combinada com o pesquisador,
que é creditado na divulgação se assim desejar. O resumo publicado não
contém detalhe explorável além do que a própria divulgação já torna
público, nem dado pessoal.

## 8. Vulnerabilidade com dado pessoal

Se o relato envolver dado pessoal, ele é ligado ao procedimento de
resposta a incidente da P3.10 (o Ziip SAN, operador, informa o tenant
controlador sem demora injustificada e dentro do prazo contratual
`lgpd.operator_notice_hours`), sem que isso atrase a resposta ao
pesquisador.
