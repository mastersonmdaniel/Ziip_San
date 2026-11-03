# Roteiro de anonimizacao na origem — piloto (P0.45, ADR-0033)

Documento/script de processo. NENHUM dado real acompanha este roteiro: os exemplos
abaixo usam valores sinteticos marcados. A carga que violar este roteiro e recusada
e descartada, com registro (ver `SyntheticDataScanTest`: documento formatado sem a
marca `SINTETICO` em fonte de banco local falha o build).

## Regras (sem excecao, fora as excecoes do ADR-0033)

1. **Sem nome, documento, endereco nem contato.** Nenhuma carga de homologacao
   local traz nome de pessoa, CPF/CNPJ de cliente, endereco, telefone ou e-mail.
2. **Codigo substituto no lugar da matricula.** A matricula do legado vira um
   codigo substituto (ex.: `SUBST-000001` — sintetico). A correspondencia
   codigo-substituto <-> matricula real fica SO no piloto, fora do ambiente
   local, sob controle do piloto.
3. **Cadastro tecnico real, pessoal anonimizado.** Redes, pecas e estruturas
   podem vir reais (sao cadastro tecnico, nao dado pessoal). Ligacoes, ramais,
   cavaletes e imoveis: NUNCA com a coordenada do lote de origem.
4. **Coordenada deslocada ou agregada, com metodo registrado por camada.**
   Para cada camada (ligacoes, ramais, cavaletes, imoveis), registrar o metodo
   usado, em campo proprio da carga:
   - `DESLOCADA_QUADRA`: coordenada deslocada dentro da quadra, sem coincidir
     com o lote de origem (deslocamento aleatorio dentro da quadra, com semente
     registrada, e checagem de nao-coincidencia com o lote);
   - `CENTROIDE_QUADRA`: coordenada agregada no centroide da quadra.
   Camada pessoal sem o metodo registrado e recusada (ver P3.26).
5. **Nenhuma camada da rede real em ambiente de demonstracao.** Demonstracao
   local usa so massa sintetica.
6. **Excecoes do ADR-0033 (unicas toleradas):** o e-CNPJ da propria Ziip/TGP no
   cofre local, fora do banco, com acesso registrado e consentimento escrito do
   responsavel; e XML assinados da homologacao fiscal so na area separada da base
   local, apagados ao fim de cada ciclo, com varredura que recusa XML remanescente
   (P3.7). Nenhum certificado de cliente no ambiente local.

## Passos da carga (ordem)

1. Extrair do legado, no piloto, so as camadas autorizadas pelo termo de
   cooperacao (P0.30): redes, pecas, estruturas; ligacoes e imoveis apenas nas
   formas anonimizadas acima.
2. Substituir matriculas por codigos substitutos; guardar a correspondencia so
   no piloto (nunca enviar junto com a carga).
3. Aplicar o deslocamento/agregacao de coordenadas por camada; registrar o
   metodo por camada; verificar: nenhuma coordenada resultante coincide com um
   lote de origem.
4. Varrer a carga: nenhum nome, documento formatado, endereco ou contato; nenhum
   arquivo fora da lista autorizada. Falha na varredura = carga recusada e
   descartada, com registro — nunca "corrigida" no ambiente local.
5. Carregar no ambiente local; rodar `SyntheticDataScanTest` e os testes de
   integracao; registrar origem, metodo por camada e data da carga.

## O que este roteiro NAO faz

- Nao define o piloto (P0.30), nao autoriza a carga (termo de cooperacao), nao
  substitui o DPA (LC.9) nem o KMS/R1 de producao (LC.4).
- Nao cria dado: quem executa a carga no piloto segue este roteiro com os dados
  do piloto; este documento nao contem — nem pode conter — dado real.
