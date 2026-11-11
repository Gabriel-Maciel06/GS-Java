package br.com.fiap.alertacom.e2e.tier1;

import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 1: Protocolo Defesa Civil E2E Tests (T1_PROT_01 a T1_PROT_05).
 */
public class ProtocoloFeatureE2ETest extends BaseE2ETest {

    private Long cadastrarOcorrenciaAux(String tokenCidadao, Long cidadaoId) {
        ResponseEntity<JsonNode> resp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Risco de Enxurrada na Viela",
                "descricao", "Drenagem entupida com risco às moradias vizinhas.",
                "tipoRisco", "ENXURRADA",
                "severidade", "ALTA",
                "latitude", -23.587000,
                "longitude", -46.591000,
                "endereco", "Viela São Pedro, 40",
                "bairro", "Vila Prudente",
                "cidadaoId", cidadaoId
        ), tokenCidadao);
        return resp.getBody().get("id").asLong();
    }

    @Test
    @DisplayName("T1_PROT_01: Emissão Oficial de Protocolo da Defesa Civil")
    void testEmissaoOficialProtocolo() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.prot1@alertacom.com", "Gestor Prot", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.prot1@teste.com", "Cidadao Prot", "Vila Prudente");

        Long ocorrenciaId = cadastrarOcorrenciaAux(tokenCidadao, 1L);

        Map<String, Object> req = new HashMap<>();
        req.put("ocorrenciaId", ocorrenciaId);
        req.put("equipeResponsavel", "Equipe Tática Alfa - Zona Sudeste");
        req.put("parecerTecnico", "Risco iminente de colapso de galeria pluvial. Vistoria presencial agendada.");
        req.put("dataPrevisaoAtendimento", LocalDateTime.now().plusDays(1).toString());

        ResponseEntity<JsonNode> response = postJson("/api/protocolos", req, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.has("id")).isTrue();
        assertThat(body.has("numeroProtocolo")).isTrue();
        assertThat(body.get("numeroProtocolo").asText()).matches("^DEF-\\d{4}-\\d{5}$");
        assertThat(body.get("equipeResponsavel").asText()).isEqualTo("Equipe Tática Alfa - Zona Sudeste");
        if (body.has("ocorrenciaStatus")) {
            assertThat(body.get("ocorrenciaStatus").asText()).isEqualTo("ENCAMINHADA_DEFESA_CIVIL");
        }
    }

    @Test
    @DisplayName("T1_PROT_02: Consulta Pública de Protocolo pelo Número Oficial")
    void testConsultaPublicaProtocolo() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.prot2@alertacom.com", "Gestor Prot", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.prot2@teste.com", "Cidadao Prot", "Vila Prudente");

        Long ocorrenciaId = cadastrarOcorrenciaAux(tokenCidadao, 1L);

        ResponseEntity<JsonNode> postResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocorrenciaId,
                "equipeResponsavel", "Equipe Sul",
                "parecerTecnico", "Inspeção inicial realizada com êxito.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(2).toString()
        ), tokenGestor);
        String numeroProtocolo = postResp.getBody().get("numeroProtocolo").asText();

        // Consulta pública anônima (sem header Authorization)
        ResponseEntity<JsonNode> response = postAnonimoJson("/api/protocolos/numero/" + numeroProtocolo, null);
        if (response.getStatusCode() == HttpStatus.METHOD_NOT_ALLOWED) {
            // Se o controller implementa GET
            HttpHeaders headers = jwtHelper.criarHeadersAnonimo();
            response = restTemplate.exchange(url("/api/protocolos/numero/" + numeroProtocolo),
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers),
                    JsonNode.class);
        }

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("numeroProtocolo").asText()).isEqualTo(numeroProtocolo);
        assertThat(body.get("equipeResponsavel").asText()).isEqualTo("Equipe Sul");
    }

    @Test
    @DisplayName("T1_PROT_03: Listagem Paginada de Protocolos Emitidos")
    void testListagemPaginadaProtocolos() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.prot3@alertacom.com", "Gestor Prot", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.prot3@teste.com", "Cidadao Prot", "Vila Prudente");

        Long ocId = cadastrarOcorrenciaAux(tokenCidadao, 1L);
        postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Norte 01",
                "parecerTecnico", "Vistoria preliminar em andamento.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(3).toString()
        ), tokenGestor);

        ResponseEntity<JsonNode> response = getJson("/api/protocolos?page=0&size=10", tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        JsonNode content = body.get("content");
        assertThat(content).isNotNull();
        assertThat(content.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("T1_PROT_04: Consulta de Protocolo por ID Primário")
    void testConsultaProtocoloPorId() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.prot4@alertacom.com", "Gestor Prot", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.prot4@teste.com", "Cidadao Prot", "Vila Prudente");

        Long ocId = cadastrarOcorrenciaAux(tokenCidadao, 1L);
        ResponseEntity<JsonNode> postResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Leste 05",
                "parecerTecnico", "Avaliação de fundações de muro.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(2).toString()
        ), tokenGestor);
        Long protocoloId = postResp.getBody().get("id").asLong();

        ResponseEntity<JsonNode> response = getJson("/api/protocolos/" + protocoloId, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("id").asLong()).isEqualTo(protocoloId);
        assertThat(body.get("numeroProtocolo").asText()).matches("^DEF-\\d{4}-\\d{5}$");
    }

    @Test
    @DisplayName("T1_PROT_05: Conclusão de Protocolo e Resolução da Ocorrência")
    void testConclusaoProtocoloEResolucaoOcorrencia() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.prot5@alertacom.com", "Gestor Prot", "Centro");
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.prot5@teste.com", "Cidadao Prot", "Vila Prudente");

        Long ocId = cadastrarOcorrenciaAux(tokenCidadao, 1L);
        ResponseEntity<JsonNode> postResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Desobstrução",
                "parecerTecnico", "Início de atendimento de campo.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(1).toString()
        ), tokenGestor);
        Long protocoloId = postResp.getBody().get("id").asLong();

        Map<String, String> conclusaoReq = Map.of(
                "parecerTecnico", "Equipe realizou a desobstrução completa da tubulação pluvial e contenção de terra. Área segura."
        );

        ResponseEntity<JsonNode> patchResp = patchJson("/api/protocolos/" + protocoloId + "/concluir", conclusaoReq, tokenGestor);

        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = patchResp.getBody();
        assertThat(body.has("dataConclusao")).isTrue();
        if (body.has("ocorrenciaStatus")) {
            assertThat(body.get("ocorrenciaStatus").asText()).isEqualTo("RESOLVIDA");
        }

        // Verificação na ocorrência: status RESOLVIDA
        ResponseEntity<JsonNode> ocResp = getJson("/api/ocorrencias/" + ocId, tokenGestor);
        assertThat(ocResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ocResp.getBody().get("status").asText()).isEqualTo("RESOLVIDA");
    }
}
