"""Cria as listas de teste servidas para o emulador (o emulador enxerga este computador em 10.0.2.2).

- teste.m3u: 4 itens com os vídeos públicos de teste, em 3 categorias, com logos.
- gigante.m3u: 20.000 itens em 50 categorias, para medir tempo e memória com lista grande.
"""
import os
import struct
import zlib

PASTA = "listas"
BASE = "http://10.0.2.2:8000"
os.makedirs(PASTA, exist_ok=True)


def png(nome, cor):
    """Logo simples de uma cor só (64x64), sem precisar de biblioteca de imagem."""
    largura = altura = 64
    bruto = b"".join(b"\x00" + bytes(cor) * largura for _ in range(altura))

    def bloco(tipo, dados):
        return struct.pack(">I", len(dados)) + tipo + dados + struct.pack(">I", zlib.crc32(tipo + dados) & 0xFFFFFFFF)

    with open(os.path.join(PASTA, nome), "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(bloco(b"IHDR", struct.pack(">IIBBBBB", largura, altura, 8, 2, 0, 0, 0)))
        f.write(bloco(b"IDAT", zlib.compress(bruto)))
        f.write(bloco(b"IEND", b""))


png("logo1.png", (47, 107, 255))
png("logo2.png", (139, 92, 246))
png("logo3.png", (34, 211, 238))

HLS = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
DASH = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd"
MP4 = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"

itens = [
    ("Filme HLS de teste", HLS, "Filmes de teste", "logo1.png"),
    ("Filme DASH de teste", DASH, "Filmes de teste", "logo2.png"),
    ("Clipe MP4 de teste", MP4, "Clipes de teste", "logo3.png"),
    ("Canal de teste", HLS, "Canais de teste", "logo1.png"),
]
with open(os.path.join(PASTA, "teste.m3u"), "w", encoding="utf-8") as f:
    f.write("#EXTM3U\n")
    for titulo, url, grupo, logo in itens:
        f.write(f'#EXTINF:-1 tvg-logo="{BASE}/{logo}" group-title="{grupo}",{titulo}\n{url}\n')

with open(os.path.join(PASTA, "gigante.m3u"), "w", encoding="utf-8") as f:
    f.write("#EXTM3U\n")
    for i in range(20_000):
        f.write(f'#EXTINF:-1 tvg-logo="{BASE}/logo{i % 3 + 1}.png" group-title="Grupo {i % 50 + 1}",Item {i}\n{MP4}\n')

print("listas criadas:", sorted(os.listdir(PASTA)))
