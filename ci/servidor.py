"""Servidor de teste para o emulador: serve a pasta "listas" e imita provedores "difíceis".

/lento.m3u            demora 45 s para começar a responder (provedor lento)
/exige-navegador.m3u  recusa (403) quem não se apresenta como navegador
/redireciona.m3u      manda para /teste.m3u (redirecionamento)
/erro-456             responde 456 (código usado por provedores para limite de conexões)
/corta.m3u            nas 2 primeiras vezes corta a conexão no meio da lista gigante; na 3ª entrega inteira

Uso: python3 servidor.py [porta] [certificado.pem chave.pem]  (com certificado, vira https)
"""
import http.server
import os
import ssl
import sys
import time

PASTA = "listas"
cortes = {"vezes": 0}


class Provedor(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=PASTA, **kwargs)

    def do_GET(self):
        caminho = self.path.split("?")[0]
        if caminho == "/lento.m3u":
            time.sleep(45)
            return self.enviar_lista()
        if caminho == "/exige-navegador.m3u":
            if "Mozilla" not in self.headers.get("User-Agent", ""):
                self.send_error(403)
                return
            return self.enviar_lista()
        if caminho == "/redireciona.m3u":
            self.send_response(302)
            self.send_header("Location", "/teste.m3u")
            self.end_headers()
            return
        if caminho == "/corta.m3u":
            dados = open(os.path.join(PASTA, "gigante.m3u"), "rb").read()
            self.send_response(200)
            self.send_header("Content-Type", "audio/x-mpegurl")
            self.send_header("Content-Length", str(len(dados)))
            self.end_headers()
            if cortes["vezes"] < 2:
                cortes["vezes"] += 1
                self.wfile.write(dados[: len(dados) // 2])
                self.wfile.flush()
                self.close_connection = True
                self.connection.close()
                return
            self.wfile.write(dados)
            return
        if caminho == "/erro-456":
            self.send_response(456)
            self.end_headers()
            return
        return super().do_GET()

    def enviar_lista(self):
        dados = open(os.path.join(PASTA, "teste.m3u"), "rb").read()
        self.send_response(200)
        self.send_header("Content-Type", "audio/x-mpegurl")
        self.send_header("Content-Length", str(len(dados)))
        self.end_headers()
        self.wfile.write(dados)


porta = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
servidor = http.server.ThreadingHTTPServer(("0.0.0.0", porta), Provedor)
if len(sys.argv) > 3:
    contexto = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    contexto.load_cert_chain(sys.argv[2], sys.argv[3])
    servidor.socket = contexto.wrap_socket(servidor.socket, server_side=True)
servidor.serve_forever()
