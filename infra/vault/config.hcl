# P0.27: cofre local (servidor Vault). Backend em arquivo no volume vault-data.
# TLS desligado AQUI porque o listener so e publicado em 127.0.0.1 pelo compose;
# fora da maquina local, TLS e obrigatorio (LC.4). Nunca usar este modo fora do local.
storage "file" {
  path = "/vault/data"
}
listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1
}
disable_mlock = true
ui = false
