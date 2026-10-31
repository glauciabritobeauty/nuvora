"""Servidor de teste para o emulador: serve a pasta "listas" e imita provedores "difíceis".

/lento.m3u            demora 45 s para começar a responder (provedor lento)
/exige-navegador.m3u  recusa (403) quem não se apresenta como navegador
/redireciona.m3u      manda para /teste.m3u (redirecionamento)
/erro-456             responde 456 (código usado por provedores para limite de conexões)
"""
import http.server
import os
import time

PASTA = "listas"


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


http.server.ThreadingHTTPServer(("0.0.0.0", 8000), Provedor).serve_forever()
