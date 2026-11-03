---
id: pesquisar-imoveis
title: Pesquisar imóveis
module: cadastro
routes:
  - /cadastro/imoveis
roles:
  - tenant-user
audience: tenant
min_version: 1.0.0
keywords:
  - imóvel
  - matrícula
  - inscrição
  - pesquisa
  - endereço
---

# Pesquisar imóveis

Use a busca livre para encontrar um imóvel por matrícula, nome, documento ou
endereço. O servidor reconhece o formato digitado: não é preciso escolher o
tipo de busca antes de digitar.

Se a inscrição completa estiver preenchida, ela tem prioridade sobre a busca
livre. Use a tecla `/` para ir direto ao campo de busca.

Para cadastrar um imóvel novo, use o botão "Cadastrar imóvel" no topo da
tela. Se o cliente ainda não existir, cadastre-o primeiro em
[clientes](/cadastro/clientes) — veja também
[cadastrar cliente](cadastrar-cliente.md).

Os filtros adicionais ficam em "Mais filtros": localidade, setor e outras
características do cadastro. Limpe os filtros para voltar à lista completa.
