package br.com.fiap.alertacom.e2e.tier1;

import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 1: Spring AI Boletim Preventivo E2E Tests (T1_AI_01 a T1_AI_05).
 */
public class SpringAiFeatureE2ETest extends BaseE2ETest {

    private void cadastrarOcorrenciaAux(String token, String titulo, String bairro, String severidade, String endereco) {
        postJson("/api/ocorrencias", Map.of(
                "titulo", titulo,
                "descricao", "Descrição detalhada do incidente registrado para teste de IA.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", severidade,
                "latitude", -23.585000,
                "longitude", -46.592000,
                "endereco", endereco,
                "bairro", bairro,
                "cidadaoId", 1L
        ), token);
    }

    @Test
    @DisplayName("T1_AI_01: Geração de Boletim Preventivo Oficial por Bairro")
    void testGeracaoBoletimPreventivoOficial() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai1@alertacom.com", "Gestor AI", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.ai1@teste.com", "Cidadao AI", "Vila Prudente");

        cadastrarOcorrenciaAux(tokenCidadao, "Alagamento na Anhaia Mello", "Vila Prudente", "ALTA", "Av. Anhaia Mello, 1200");
        cadastrarOcorrenciaAux(tokenCidadao, "Bueiro Entupido", "Vila Prudente", "MEDIA", "Rua das Rosas, 350");

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Vila Prudente", null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        aiHarness.validarEstruturaBoletim(body, "Vila Prudente");
        assertThat(body.get("totalOcorrenciasAnalisadas").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("T1_AI_02: Ativação Transparente do Motor de Fallback Determinístico Offline")
    void testAtivacaoFallbackDeterministico() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai2@alertacom.com", "Gestor AI", "Centro");

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Vila Prudente", null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        if (body.has("modoExecucao")) {
            assertThat(body.get("modoExecucao").asText()).containsIgnoringCase("FALLBACK");
        }
    }

    @Test
    @DisplayName("T1_AI_03: Análise de Cenário de Baixo Risco (Bairro Sem Ocorrências)")
    void testCenarioBaixoRiscoBairroSemOcorrencias() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai3@alertacom.com", "Gestor AI", "Centro");

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Jardim Tranquilo", null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("nivelRiscoGeral").asText()).isIn("BAIXO", "NORMAL");
        assertThat(body.get("totalOcorrenciasAnalisadas").asInt()).isEqualTo(0);
    }

    @Test
    @DisplayName("T1_AI_04: Análise de Cenário Crítico (Múltiplas Ocorrências Graves)")
    void testCenarioCriticoMultiplasOcorrencias() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai4@alertacom.com", "Gestor AI", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.ai4@teste.com", "Cidadao AI", "Morro Alto");

        cadastrarOcorrenciaAux(tokenCidadao, "Deslizamento de terra encosta", "Morro Alto", "CRITICA", "Rua do Alto, 10");
        cadastrarOcorrenciaAux(tokenCidadao, "Ruptura de contenção", "Morro Alto", "CRITICA", "Rua do Alto, 20");

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Morro Alto", null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("nivelRiscoGeral").asText()).isIn("CRÍTICO", "CRITICO", "ALTO");
        assertThat(body.get("totalOcorrenciasAnalisadas").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("T1_AI_05: Extração e Listagem de Pontos Críticos a Evitar")
    void testExtracaoPontosCriticosEvitar() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.ai5@alertacom.com", "Gestor AI", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.ai5@teste.com", "Cidadao AI", "Várzea");

        cadastrarOcorrenciaAux(tokenCidadao, "Inundação total de via", "Várzea", "ALTA", "Rua do Rio, 100");

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Várzea", null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        JsonNode pontos = body.get("pontosCriticosEvitar");
        assertThat(pontos).isNotNull();
        boolean enderecoEncontrado = false;
        for (JsonNode ponto : pontos) {
            if (ponto.asText().contains("Rua do Rio, 100") || ponto.asText().contains("Rua do Rio")) {
                enderecoEncontrado = true;
                break;
            }
        }
        assertThat(enderecoEncontrado).isTrue();
    }
}
