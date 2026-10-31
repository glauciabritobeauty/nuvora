package com.nuvora.core.data.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionReportTest {
    private val user = "maria123"
    private val password = "S3nh@ forte"
    private val report = ReportBuilder(user, password, "teste")

    @Test
    fun `esconde usuario e senha no endereco e mantem a acao`() {
        val masked = report.maskUrl("http://exemplo.com:8080/player_api.php?username=maria123&password=S3nh%40%20forte&action=get_live_categories")
        assertEquals("http://exemplo.com:8080/player_api.php?username=•••&password=•••&action=get_live_categories", masked)
    }

    @Test
    fun `esconde usuario e senha no caminho do video e codigos desconhecidos`() {
        val masked = report.maskUrl("http://exemplo.com/live/maria123/S3nh%40%20forte/101.m3u8?token=abc123xyz")
        assertEquals("http://exemplo.com/live/•••/•••/101.m3u8?token=•••", masked)
    }

    @Test
    fun `texto do servidor nunca mostra a senha nem o usuario`() {
        val text = report.serverText("Usuario maria123 com senha S3nh@ forte bloqueado")
        assertFalse(text.contains(user))
        assertFalse(text.contains(password))
    }

    @Test
    fun `relatorio completo nao contem nenhum dado de acesso`() {
        report.add("Login", "http://exemplo.com/player_api.php?username=maria123&password=S3nh%40+forte", "HTTP 400 · maria123", false)
        report.problem = ProblemKind.Authentication
        report.conclusion = "recusou S3nh@ forte"
        val text = report.build().asText()
        assertFalse(text.contains(user))
        assertFalse(text.contains(password))
        assertFalse(text.contains("S3nh%40"))
        assertTrue(text.contains("Tipo do problema: Autenticação"))
    }

    @Test
    fun `senha muito curta nao deixa texto livre do servidor aparecer`() {
        val short = ReportBuilder("ab", "12", "teste")
        assertEquals("(texto do servidor oculto por segurança)", short.serverText("qualquer coisa ab 12"))
    }
}
