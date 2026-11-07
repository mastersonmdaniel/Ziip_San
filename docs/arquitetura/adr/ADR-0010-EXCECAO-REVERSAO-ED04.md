# ADR-0010 — Exceção da reversão (ED-04): linhas para a tabela de Exceções

> **Forma de registro:** este arquivo é o adendo da ED-04 para incorporação à seção
> **Exceções** do ADR-0010 (`docs/arquitetura/adr/ADR-0010-*.md`). Ele contém as
> **linhas exatas** a inserir na tabela daquela seção, no formato real dela —
> `Componente | Caso concreto | Forma | Substituição | Origem` — sob a regra do
> próprio ADR-0010: "componente novo só entra pelo item 3 da decisão, registrado
> aqui com o ADR de origem". A incorporação é a inserção das duas linhas abaixo
> na tabela, sem alteração de sentido.

## Linhas para a tabela da seção Exceções do ADR-0010

| Componente | Caso concreto | Forma | Substituição | Origem |
|---|---|---|---|---|
| Cofre próprio (pacote de reversão) | Segredos de execução e chaves do tenant depois da reversão, no servidor do órgão, onde o KMS e o Secrets Manager da hospedagem não existem (ADR-0024, item 3) | Só no pacote de reversão — nunca no SaaS nem no ambiente local de desenvolvimento; contêiner no servidor do órgão, porta em 127.0.0.1, credenciais fora do repositório | AWS KMS e Secrets Manager enquanto o tenant está no SaaS (ADR-0011), atrás das mesmas interfaces | ADR-0024 (item 4) |
| Armazenamento de objetos compatível com S3 (pacote de reversão) | Arquivos e objetos do tenant depois da reversão, no servidor do órgão, onde o S3 da hospedagem não existe (ADR-0024, item 3) | Só no pacote de reversão — nunca no SaaS nem no ambiente local de desenvolvimento; contêiner no servidor do órgão, porta em 127.0.0.1, credenciais fora do repositório | S3 com Object Lock enquanto o tenant está no SaaS (ADR-0011), atrás da mesma interface | ADR-0024 (item 4) |

## Notas

1. **Distinção das linhas já existentes com origem ADR-0033.** A tabela do
   ADR-0010 já tem linhas de cofre local e de emulador compatível com S3 com
   origem ADR-0033: são o caso do **ambiente local de desenvolvimento**, até o
   lançamento comercial. As duas linhas acima são **outro caso**: o **pacote de
   reversão no servidor do órgão**, com origem ADR-0024 (item 4). As linhas não
   se substituem nem se fundem.
2. **Fora do escopo desta exceção:** a instância dedicada na AWS e o acesso
   administrativo do órgão são da **LC.15**; o perfil externo do regulador e do
   titular é da **ED-75**. Nada disso entra por estas linhas.
3. **Homologação Windows única não é desta exceção.** A execução do pacote em
   Windows Server 2022 ou superior, sobre Docker numa distribuição Linux do
   WSL2, e a sua homologação única na construção do pacote são decisão do
   **ADR-0024** (ajuste de 12/10/2026), não desta exceção ao ADR-0010.
