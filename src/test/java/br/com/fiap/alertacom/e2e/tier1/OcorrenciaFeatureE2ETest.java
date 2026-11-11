package br.com.fiap.alertacom.e2e.tier1;

import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 1: Ocorrência CRUD & Estados E2E Tests (T1_OCO_01 a T1_OCO_05).
 */
public class OcorrenciaFeatureE2ETest extends BaseE2ETest {

    private Long criarCidadao(String email, String nome, String bairro) {
        ResponseEntity<JsonNode> resp = postAnonimoJson("/api/cidadaos", Map.of(
                "nome", nome,
                "email", email,
                "telefone", "(11) 98765-4321",
                "bairro", bairro,
                "senha", "SenhaForte@123"
        ));
        return resp.getBody().get("id").asLong();
    }

    @Test
    @DisplayName("T1_OCO_01: Cadastro de Ocorrência Georreferenciada com Sucesso")
    void testCadastroOcorrenciaComSucesso() {
        Long cidadaoId = criarCidadao("cidadao.oco1@teste.com", "Cidadao Oco", "Vila Prudente");
        String token = jwtHelper.gerarTokenCidadao(cidadaoId, "cidadao.oco1@teste.com", "Cidadao Oco", "Vila Prudente");

        Map<String, Object> req = new HashMap<>();
        req.put("titulo", "Bueiro transbordando na Rua das Rosas");
        req.put("descricao", "Acúmulo intenso de lixo impedindo a drenagem das chuvas e alagando a calçada.");
        req.put("tipoRisco", "BUEIRO_OBSTRUIDO");
        req.put("severidade", "MEDIA");
        req.put("latitude", -23.587214);
        req.put("longitude", -46.591245);
        req.put("endereco", "Rua das Rosas, 350");
        req.put("bairro", "Vila Prudente");
        req.put("cidadaoId", cidadaoId);

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", req, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("id")).isTrue();
        assertThat(body.get("titulo").asText()).isEqualTo("Bueiro transbordando na Rua das Rosas");
        assertThat(body.get("status").asText()).isEqualTo("ABERTA");
        assertThat(body.get("tipoRisco").asText()).isEqualTo("BUEIRO_OBSTRUIDO");
        assertThat(body.get("severidade").asText()).isEqualTo("MEDIA");
    }

    @Test
    @DisplayName("T1_OCO_02: Listagem Paginada de Ocorrências com Metadados")
    void testListagemPaginadaOcorrencias() {
        Long cidadaoId = criarCidadao("cidadao.oco2@teste.com", "Cidadao Page", "Mooca");
        String token = jwtHelper.gerarTokenCidadao(cidadaoId, "cidadao.oco2@teste.com", "Cidadao Page", "Mooca");

        for (int i = 1; i <= 4; i++) {
            postJson("/api/ocorrencias", Map.of(
                    "titulo", "Ocorrência de Teste número " + i,
                    "descricao", "Descrição detalhada do incidente número " + i,
                    "tipoRisco", "ALAGAMENTO",
                    "severidade", "BAIXA",
                    "latitude", -23.580000,
                    "longitude", -46.590000,
                    "endereco", "Rua Teste, " + i,
                    "bairro", "Mooca",
                    "cidadaoId", cidadaoId
            ), token);
        }

        ResponseEntity<JsonNode> response = getJson("/api/ocorrencias?page=0&size=3&sort=dataCriacao,desc", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        JsonNode content = body.get("content");
        assertThat(content).isNotNull();
        assertThat(content.size()).isEqualTo(3);
        assertThat(body.get("totalElements").asInt()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("T1_OCO_03: Filtragem Dinâmica por Bairro e Severidade")
    void testFiltragemDinamicaBairroESeveridade() {
        Long cidadaoId = criarCidadao("cidadao.oco3@teste.com", "Cidadao Filtro", "Mooca");
        String token = jwtHelper.gerarTokenCidadao(cidadaoId, "cidadao.oco3@teste.com", "Cidadao Filtro", "Mooca");

        // Cria ocorrência alvo: Mooca + CRITICA
        postJson("/api/ocorrencias", Map.of(
                "titulo", "Alagamento Crítico na Mooca",
                "descricao", "Inundação severa da pista com correnteza forte.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "CRITICA",
                "latitude", -23.570000,
                "longitude", -46.580000,
                "endereco", "Rua da Mooca, 100",
                "bairro", "Mooca",
                "cidadaoId", cidadaoId
        ), token);

        // Cria ocorrência dispersão: Vila Prudente + BAIXA
        postJson("/api/ocorrencias", Map.of(
                "titulo", "Lixo na calçada Vila Prudente",
                "descricao", "Pequeno acúmulo de sacolas sem risco iminente.",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "BAIXA",
                "latitude", -23.585000,
                "longitude", -46.592000,
                "endereco", "Rua das Flores, 50",
                "bairro", "Vila Prudente",
                "cidadaoId", cidadaoId
        ), token);

        ResponseEntity<JsonNode> response = getJson("/api/ocorrencias?bairro=Mooca&severidade=CRITICA", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        JsonNode content = body.get("content");
        assertThat(content).isNotNull();
        assertThat(content.size()).isGreaterThanOrEqualTo(1);

        for (JsonNode item : content) {
            assertThat(item.get("bairro").asText()).isEqualToIgnoringCase("Mooca");
            assertThat(item.get("severidade").asText()).isEqualToIgnoringCase("CRITICA");
        }
    }

    @Test
    @DisplayName("T1_OCO_04: Consulta de Ocorrência por ID com Dados do Cidadão")
    void testConsultaOcorrenciaPorId() {
        Long cidadaoId = criarCidadao("cidadao.oco4@teste.com", "Cidadao Detalhes", "Pinheiros");
        String token = jwtHelper.gerarTokenCidadao(cidadaoId, "cidadao.oco4@teste.com", "Cidadao Detalhes", "Pinheiros");

        ResponseEntity<JsonNode> postResp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Erosão na Margem do Rio Pinheiros",
                "descricao", "Solo cedendo lentamente na calçada da ciclovia.",
                "tipoRisco", "EROSAO_SOLO",
                "severidade", "MEDIA",
                "latitude", -23.560000,
                "longitude", -46.690000,
                "endereco", "Av. Nações Unidas, 2000",
                "bairro", "Pinheiros",
                "cidadaoId", cidadaoId
        ), token);
        Long id = postResp.getBody().get("id").asLong();

        ResponseEntity<JsonNode> response = getJson("/api/ocorrencias/" + id, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("id").asLong()).isEqualTo(id);
        assertThat(body.get("titulo").asText()).isEqualTo("Erosão na Margem do Rio Pinheiros");
        assertThat(body.get("bairro").asText()).isEqualTo("Pinheiros");
    }

    @Test
    @DisplayName("T1_OCO_05: Transição Válida de Status: ABERTA para EM_ANALISE")
    void testTransicaoValidaStatus() {
        Long cidadaoId = criarCidadao("cidadao.oco5@teste.com", "Cidadao Status", "Vila Prudente");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(cidadaoId, "cidadao.oco5@teste.com", "Cidadao Status", "Vila Prudente");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.status@alertacom.com", "Gestor Triagem", "Centro");

        ResponseEntity<JsonNode> postResp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Queda de Galho em Fiação Elétrica",
                "descricao", "Galho médio tocando na rede secundária sem faíscas.",
                "tipoRisco", "QUEDA_ARVORE",
                "severidade", "MEDIA",
                "latitude", -23.587000,
                "longitude", -46.591000,
                "endereco", "Rua do Ipê, 12",
                "bairro", "Vila Prudente",
                "cidadaoId", cidadaoId
        ), tokenCidadao);
        Long id = postResp.getBody().get("id").asLong();

        Map<String, String> statusReq = Map.of(
                "novoStatus", "EM_ANALISE",
                "justificativa", "Triagem técnica iniciada pela equipe de monitoramento."
        );

        ResponseEntity<JsonNode> patchResp = patchJson("/api/ocorrencias/" + id + "/status", statusReq, tokenGestor);

        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = patchResp.getBody();
        assertThat(body.get("id").asLong()).isEqualTo(id);
        assertThat(body.get("status").asText()).isEqualTo("EM_ANALISE");
    }
}
