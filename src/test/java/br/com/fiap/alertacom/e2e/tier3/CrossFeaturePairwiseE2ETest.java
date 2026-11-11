package br.com.fiap.alertacom.e2e.tier3;

import br.com.fiap.alertacom.e2e.base.AlertaCriticoEventDto;
import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Tier 3: Cross-Feature Pairwise Integration E2E Tests (T3-J01 a T3-J09).
 */
public class CrossFeaturePairwiseE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("T3-J01: Autenticação de Cidadão -> Abertura de Ocorrência Crítica -> Disparo Assíncrono RabbitMQ")
    void testJ01_AutenticacaoAberturaCriticaRabbitMQ() {
        rabbitHarness.resetInteractions();

        // 1. Cadastro e Login
        postAnonimoJson("/api/auth/cadastro", Map.of(
                "nome", "Carlos Silva",
                "email", "carlos.j01@email.com",
                "telefone", "(11) 98765-4321",
                "bairro", "Vila Prudente",
                "senha", "senha123",
                "role", "ROLE_CIDADAO"
        ));
        ResponseEntity<JsonNode> loginResp = postAnonimoJson("/api/auth/login", Map.of(
                "email", "carlos.j01@email.com",
                "senha", "senha123"
        ));
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResp.getBody().get("token").asText();

        // 2. POST /api/ocorrencias
        Map<String, Object> req = Map.of(
                "titulo", "Transbordamento Iminente do Córrego da Mooca",
                "descricao", "Água subindo rapidamente, invadindo pistas da Anhaia Mello e ameaçando veículos.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "CRITICA",
                "latitude", -23.584100,
                "longitude", -46.587200,
                "endereco", "Av. Professor Luiz Ignácio Anhaia Mello, 1200",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        );
        ResponseEntity<JsonNode> ocResp = postJson("/api/ocorrencias", req, token);
        assertThat(ocResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ocorrenciaId = ocResp.getBody().get("id").asLong();
        assertThat(ocResp.getBody().get("status").asText()).isEqualTo("ABERTA");

        // 3. Efeito Colateral Assíncrono (RabbitMQ)
        rabbitHarness.validarEventoCritico(ocorrenciaId, "CRITICA", "Vila Prudente");
        AlertaCriticoEventDto evento = rabbitHarness.capturarEventoPublicado();
        assertThat(evento.tipoRisco()).isEqualToIgnoringCase("ALAGAMENTO");
    }

    @Test
    @DisplayName("T3-J02: Relato de Cidadão -> Triagem pelo Gestor -> Emissão de Protocolo Defesa Civil -> Transição de Status")
    void testJ02_RelatoTriagemProtocoloTransicao() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.j02@email.com", "Cidadao J02", "Jardim São Luís");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.souza@defesacivil.sp.gov.br", "Gestor Souza", "Centro");

        // 1. Cidadão envia ocorrência
        ResponseEntity<JsonNode> ocResp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Risco de Deslizamento de Encosta Sul",
                "descricao", "Fissuras no solo após forte tempestade contínua na região montanhosa.",
                "tipoRisco", "DESLIZAMENTO",
                "severidade", "ALTA",
                "latitude", -23.662100,
                "longitude", -46.738900,
                "endereco", "Rua das Encostas, 340",
                "bairro", "Jardim São Luís",
                "cidadaoId", 1L
        ), tokenCidadao);
        assertThat(ocResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ocId = ocResp.getBody().get("id").asLong();

        // 2. Gestor consulta a ocorrência
        ResponseEntity<JsonNode> getResp = getJson("/api/ocorrencias/" + ocId, tokenGestor);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Gestor emite protocolo
        ResponseEntity<JsonNode> protResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Geotécnica Sul 02",
                "parecerTecnico", "Inspeção de talude solicitada com urgência devido a fissuras no solo.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(1).toString()
        ), tokenGestor);
        assertThat(protResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(protResp.getBody().get("numeroProtocolo").asText()).matches("^DEF-\\d{4}-\\d{5}$");

        // 4. Invariante: Ocorrência transiciona para ENCAMINHADA_DEFESA_CIVIL
        ResponseEntity<JsonNode> checkOc = getJson("/api/ocorrencias/" + ocId, tokenGestor);
        assertThat(checkOc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(checkOc.getBody().get("status").asText()).isEqualTo("ENCAMINHADA_DEFESA_CIVIL");
    }

    @Test
    @DisplayName("T3-J03: Tentativa de Escalação de Privilégio por Cidadão -> Bloqueio Spring Security -> RFC 7807")
    void testJ03_EscalacaoPrivilegioBloqueioRbac() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.j03@email.com", "Cidadao J03", "Centro");

        // Tentativa de alterar status diretamente
        ResponseEntity<JsonNode> patchResp = patchJson("/api/ocorrencias/200/status", Map.of(
                "novoStatus", "RESOLVIDA",
                "justificativa", "Tentativa de autofechamento pelo cidadão"
        ), tokenCidadao);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Tentativa de emitir protocolo
        ResponseEntity<JsonNode> postResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", 200L,
                "equipeResponsavel", "Equipe Falsa",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(1).toString()
        ), tokenCidadao);
        assertThat(postResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("T3-J04: Ciclo Completo de Atendimento -> Parecer Técnico -> Conclusão Protocolo -> Resolução")
    void testJ04_CicloCompletoAtendimentoConclusaoResolucao() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.j04@email.com", "Cidadao J04", "Vila Prudente");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.j04@alertacom.com", "Gestor J04", "Centro");

        // 1. Cadastra ocorrência
        ResponseEntity<JsonNode> ocResp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Bueiro entupido com transbordamento",
                "descricao", "Água pluvial inundando a via e impedindo passagem de pedestres.",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "MEDIA",
                "latitude", -23.587000,
                "longitude", -46.591000,
                "endereco", "Rua das Rosas, 350",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        ), tokenCidadao);
        Long ocId = ocResp.getBody().get("id").asLong();

        // 2. Emite protocolo
        ResponseEntity<JsonNode> protResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Hidrojato 01",
                "parecerTecnico", "Vistoria inicial e agendamento de hidrojateamento.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(1).toString()
        ), tokenGestor);
        Long protId = protResp.getBody().get("id").asLong();

        // 3. Atualiza ocorrência para EM_ATENDIMENTO
        patchJson("/api/ocorrencias/" + ocId + "/status", Map.of(
                "novoStatus", "EM_ATENDIMENTO",
                "justificativa", "Caminhão hidrojato no local"
        ), tokenGestor);

        // 4. Conclui o protocolo
        ResponseEntity<JsonNode> conclusaoResp = patchJson("/api/protocolos/" + protId + "/concluir", Map.of(
                "parecerTecnico", "Intervenção concluída com sucesso. Bueiro desobstruído com caminhão hidrojato e grelha substituída. Via liberada para tráfego seguro."
        ), tokenGestor);
        assertThat(conclusaoResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(conclusaoResp.getBody().has("dataConclusao")).isTrue();

        // 5. Invariante: Ocorrência vinculada transiciona para RESOLVIDA
        ResponseEntity<JsonNode> ocFinal = getJson("/api/ocorrencias/" + ocId, tokenGestor);
        assertThat(ocFinal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ocFinal.getBody().get("status").asText()).isEqualTo("RESOLVIDA");

        // 6. Guard: Não permite reabertura de ocorrência resolvida
        ResponseEntity<JsonNode> reabrirResp = patchJson("/api/ocorrencias/" + ocId + "/status", Map.of(
                "novoStatus", "ABERTA",
                "justificativa", "Tentativa ilegal de reabertura"
        ), tokenGestor);
        assertThat(reabrirResp.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T3-J05: Multi-Ocorrências em Bairro -> Disparo do Boletim IA -> Síntese Dual-Engine")
    void testJ05_MultiOcorrenciasBoletimIa() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.j05@email.com", "Cidadao J05", "Mooca");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.j05@alertacom.com", "Gestor J05", "Centro");

        // 3 ocorrências no bairro Mooca
        postJson("/api/ocorrencias", Map.of(
                "titulo", "Alagamento na Rua da Mooca",
                "descricao", "Drenagem insuficiente alagando calçadas.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "MEDIA",
                "latitude", -23.570000,
                "longitude", -46.580000,
                "endereco", "Rua da Mooca, 1400",
                "bairro", "Mooca",
                "cidadaoId", 1L
        ), tokenCidadao);

        postJson("/api/ocorrencias", Map.of(
                "titulo", "Bueiro Obstruído com Entulho",
                "descricao", "Restos de construção jogados no bueiro.",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "ALTA",
                "latitude", -23.571000,
                "longitude", -46.581000,
                "endereco", "Rua Taquari, 500",
                "bairro", "Mooca",
                "cidadaoId", 1L
        ), tokenCidadao);

        postJson("/api/ocorrencias", Map.of(
                "titulo", "Galho de Árvore Caído",
                "descricao", "Galho obstruindo calçada de pedestres.",
                "tipoRisco", "QUEDA_ARVORE",
                "severidade", "BAIXA",
                "latitude", -23.572000,
                "longitude", -46.582000,
                "endereco", "Rua Juventus, 210",
                "bairro", "Mooca",
                "cidadaoId", 1L
        ), tokenCidadao);

        // Disparo do boletim de IA
        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Mooca", null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        aiHarness.validarEstruturaBoletim(body, "Mooca");
        assertThat(body.get("totalOcorrenciasAnalisadas").asInt()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("T3-J06: Resiliência contra Queda do Broker RabbitMQ -> Transação JPA Preservada")
    void testJ06_ResilienciaRabbitMqTransacaoPreservada() {
        rabbitHarness.resetInteractions();
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.j06@email.com", "Cidadao J06", "Vila Prudente");

        doThrow(new AmqpConnectException(new RuntimeException("RabbitMQ indisponível")))
                .when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        ResponseEntity<JsonNode> response = postJson("/api/ocorrencias", Map.of(
                "titulo", "Enxurrada violenta com arraste de carros",
                "descricao", "Rua totalmente inundada e correnteza forte.",
                "tipoRisco", "ENXURRADA",
                "severidade", "CRITICA",
                "latitude", -23.585000,
                "longitude", -46.592000,
                "endereco", "Rua do Orfanato, 800",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        ), tokenCidadao);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = response.getBody().get("id").asLong();

        // Ocorrência salva no banco relacional
        ResponseEntity<JsonNode> getResp = getJson("/api/ocorrencias/" + id, tokenCidadao);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("id").asLong()).isEqualTo(id);
    }

    @Test
    @DisplayName("T3-J07: Protocolo Inválido -> Rejeição Bean Validation @Future -> Conflito de Chave Única")
    void testJ07_ProtocoloInvalidoValidacaoEConflito() {
        String tokenCidadao = jwtHelper.gerarTokenCidadao(1L, "cidadao.j07@email.com", "Cidadao J07", "Centro");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.j07@alertacom.com", "Gestor J07", "Centro");

        ResponseEntity<JsonNode> ocResp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Ocorrência para Teste de Validação Temporal",
                "descricao", "Descrição detalhada do incidente registrado para teste J07.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "MEDIA",
                "latitude", -23.580000,
                "longitude", -46.590000,
                "endereco", "Rua Teste, 500",
                "bairro", "Centro",
                "cidadaoId", 1L
        ), tokenCidadao);
        Long ocId = ocResp.getBody().get("id").asLong();

        // 1. Data no passado -> 400 Bad Request
        ResponseEntity<JsonNode> respPassado = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Delta",
                "parecerTecnico", "Parecer com data inválida no passado.",
                "dataPrevisaoAtendimento", "2020-01-01T10:00:00"
        ), tokenGestor);
        assertThat(respPassado.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 2. Data futura válida -> 201 Created
        ResponseEntity<JsonNode> respValido = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Delta",
                "parecerTecnico", "Parecer com data futura válida.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(2).toString()
        ), tokenGestor);
        assertThat(respValido.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 3. Segundo protocolo para mesma ocorrência -> 422 ou 409
        ResponseEntity<JsonNode> respDuplicado = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Equipe Epsilon",
                "parecerTecnico", "Tentativa de duplicidade de protocolo.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusDays(3).toString()
        ), tokenGestor);
        assertThat(respDuplicado.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.CONFLICT, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T3-J08: Paginação Dinâmica, Ordenação, Filtros Multi-Atributo & Isolamento RBAC")
    void testJ08_PaginacaoOrdenacaoFiltrosEIsolamento() {
        String tokenCidA = jwtHelper.gerarTokenCidadao(101L, "cidadao.a@email.com", "Cidadao A", "Mooca");
        String tokenCidB = jwtHelper.gerarTokenCidadao(102L, "cidadao.b@email.com", "Cidadao B", "Pinheiros");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.j08@alertacom.com", "Gestor J08", "Centro");

        // Cidadão A cria 3 ocorrências na Mooca
        for (int i = 1; i <= 3; i++) {
            postJson("/api/ocorrencias", Map.of(
                    "titulo", "Ocorrência Cidadão A " + i,
                    "descricao", "Descrição detalhada do incidente registrado pelo Cidadão A.",
                    "tipoRisco", "ALAGAMENTO",
                    "severidade", "MEDIA",
                    "latitude", -23.570000,
                    "longitude", -46.580000,
                    "endereco", "Rua da Mooca, " + i,
                    "bairro", "Mooca",
                    "cidadaoId", 101L
            ), tokenCidA);
        }

        // Cidadão B cria 2 ocorrências em Pinheiros
        for (int i = 1; i <= 2; i++) {
            postJson("/api/ocorrencias", Map.of(
                    "titulo", "Ocorrência Cidadão B " + i,
                    "descricao", "Descrição detalhada do incidente registrado pelo Cidadão B.",
                    "tipoRisco", "QUEDA_ARVORE",
                    "severidade", "BAIXA",
                    "latitude", -23.560000,
                    "longitude", -46.690000,
                    "endereco", "Rua Pinheiros, " + i,
                    "bairro", "Pinheiros",
                    "cidadaoId", 102L
            ), tokenCidB);
        }

        // 1. Cidadão A consulta suas ocorrências
        ResponseEntity<JsonNode> minhasA = getJson("/api/ocorrencias/minhas", tokenCidA);
        assertThat(minhasA.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2. Gestor consulta paginado por bairro Mooca
        ResponseEntity<JsonNode> pageMooca = getJson("/api/ocorrencias?bairro=Mooca&page=0&size=2&sort=dataCriacao,desc", tokenGestor);
        assertThat(pageMooca.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = pageMooca.getBody();
        assertThat(body.get("content").size()).isEqualTo(2);
        assertThat(body.get("totalElements").asInt()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("T3-J09: Consulta de Boletim IA para Bairro sem Incidentes -> Normalidade sem Alucinação")
    void testJ09_BoletimIaBairroSemIncidentes() {
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "gestor.j09@alertacom.com", "Gestor J09", "Centro");

        ResponseEntity<JsonNode> response = postJson("/api/relatorios/boletim-bairro?bairro=Jardim Europa", null, tokenGestor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body.get("bairro").asText()).isEqualToIgnoringCase("Jardim Europa");
        assertThat(body.get("totalOcorrenciasAnalisadas").asInt()).isEqualTo(0);
        assertThat(body.get("nivelRiscoGeral").asText()).isIn("BAIXO", "NORMAL");
    }
}
