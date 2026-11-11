package br.com.fiap.alertacom.e2e.tier4;

import br.com.fiap.alertacom.e2e.base.AlertaCriticoEventDto;
import br.com.fiap.alertacom.e2e.base.BaseE2ETest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tier 4: Real-World Disaster Scenarios E2E Tests (T4-SC01 a T4-SC05).
 * Alinhado aos ODS 11 (Cidades Sustentáveis) e ODS 13 (Ação Climática).
 */
public class RealWorldDisasterScenariosE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("T4-SC01: Megatempestade de Verão & Alagamento Crítico na Vila Prudente (Bacia do Tamanduateí)")
    void testCenario01_MegatempestadeVilaPrudente() {
        rabbitHarness.resetInteractions();

        // Fase 1: Cadastro e Login do Cidadão
        postAnonimoJson("/api/auth/cadastro", Map.of(
                "nome", "Carlos Silva",
                "email", "carlos.prudente@alerta.com.br",
                "telefone", "(11) 98765-4321",
                "bairro", "Vila Prudente",
                "senha", "SenhaVilaPrudente@2026",
                "role", "ROLE_CIDADAO"
        ));
        ResponseEntity<JsonNode> loginResp = postAnonimoJson("/api/auth/login", Map.of(
                "email", "carlos.prudente@alerta.com.br",
                "senha", "SenhaVilaPrudente@2026"
        ));
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tokenCidadao = loginResp.getBody().get("token").asText();

        // Fase 2: Reporte de Emergência Crítica
        Map<String, Object> ocReq = Map.of(
                "titulo", "Transbordamento violento do Córrego da Mooca na Av. Anhaia Mello",
                "descricao", "Água subiu 1,5 metro em 15 minutos. Carros submersos sob o monotrilho, passageiros refugiados sobre tetos de veículos.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "CRITICA",
                "latitude", -23.584100,
                "longitude", -46.587200,
                "endereco", "Av. Professor Luiz Ignácio Anhaia Mello, 1200",
                "bairro", "Vila Prudente",
                "cidadaoId", 1L
        );
        ResponseEntity<JsonNode> ocResp = postJson("/api/ocorrencias", ocReq, tokenCidadao);
        assertThat(ocResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ocorrenciaId = ocResp.getBody().get("id").asLong();
        assertThat(ocResp.getBody().get("status").asText()).isEqualTo("ABERTA");

        // Fase 3: Disparo e Captura RabbitMQ em Milissegundos
        rabbitHarness.validarEventoCritico(ocorrenciaId, "CRITICA", "Vila Prudente");
        AlertaCriticoEventDto evento = rabbitHarness.capturarEventoPublicado();
        assertThat(evento.tipoRisco()).isEqualToIgnoringCase("ALAGAMENTO");

        // Fase 4: Despacho Oficial do Protocolo Defesa Civil pelo Comandante Souza
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "defesa.leste@sp.gov.br", "Comandante Souza", "Sé");

        Map<String, Object> protReq = Map.of(
                "ocorrenciaId", ocorrenciaId,
                "equipeResponsavel", "Batalhão de Salvamento Tático Leste - Viatura B-12",
                "parecerTecnico", "Despacho prioritário de botes infláveis de resgate e equipe de mergulho para resgate de ocupantes de veículos ilhados.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusHours(1).toString()
        );
        ResponseEntity<JsonNode> protResp = postJson("/api/protocolos", protReq, tokenGestor);
        assertThat(protResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long protocoloId = protResp.getBody().get("id").asLong();
        assertThat(protResp.getBody().get("numeroProtocolo").asText()).matches("^DEF-\\d{4}-\\d{5}$");

        // Validação de transição atômica para ENCAMINHADA_DEFESA_CIVIL
        ResponseEntity<JsonNode> ocEnc = getJson("/api/ocorrencias/" + ocorrenciaId, tokenGestor);
        assertThat(ocEnc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ocEnc.getBody().get("status").asText()).isEqualTo("ENCAMINHADA_DEFESA_CIVIL");

        // Fase 5: Operação em Campo (EM_ATENDIMENTO)
        patchJson("/api/ocorrencias/" + ocorrenciaId + "/status", Map.of(
                "novoStatus", "EM_ATENDIMENTO",
                "justificativa", "Botes na água, 8 pessoas resgatadas e conduzidas a local seco seguro."
        ), tokenGestor);

        // Fase 6: Conclusão Técnica e Encerramento
        ResponseEntity<JsonNode> conclusaoResp = patchJson("/api/protocolos/" + protocoloId + "/concluir", Map.of(
                "parecerTecnico", "Operação de resgate finalizada sem vítimas fatais. Vias desobstruídas pelo guincho da CET. Limpeza da pista executada pela subprefeitura."
        ), tokenGestor);
        assertThat(conclusaoResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verificação final: status RESOLVIDA
        ResponseEntity<JsonNode> ocFinal = getJson("/api/ocorrencias/" + ocorrenciaId, tokenGestor);
        assertThat(ocFinal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ocFinal.getBody().get("status").asText()).isEqualTo("RESOLVIDA");
    }

    @Test
    @DisplayName("T4-SC02: Saturação de Solo & Risco Iminente de Deslizamento no Jardim São Luís (Zona Sul)")
    void testCenario02_DeslizamentoJardimSaoLuis() {
        rabbitHarness.resetInteractions();

        String tokenCidadao = jwtHelper.gerarTokenCidadao(2L, "mariana.saoluis@alerta.com.br", "Mariana Costa", "Jardim São Luís");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "helena.geotecnia@sp.gov.br", "Dra. Helena Mendes", "Centro");

        // Fase 1: Notificação de Alerta Geológico
        ResponseEntity<JsonNode> ocResp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Trincas profundas no solo e inclinação de postes de energia",
                "descricao", "Fendas no asfalto aumentando a cada hora, muro de arrimo com fissura diagonal e solo instável após 3 dias de chuva ininterrupta.",
                "tipoRisco", "DESLIZAMENTO",
                "severidade", "CRITICA",
                "latitude", -23.662100,
                "longitude", -46.738900,
                "endereco", "Rua das Encostas, 340 - Morro da Esperança",
                "bairro", "Jardim São Luís",
                "cidadaoId", 2L
        ), tokenCidadao);
        assertThat(ocResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ocorrenciaId = ocResp.getBody().get("id").asLong();

        // Fase 2: Gatilho RabbitMQ
        rabbitHarness.validarEventoCritico(ocorrenciaId, "CRITICA", "Jardim São Luís");

        // Fase 3: Emissão de Boletim Preventivo de Contingência via Spring AI
        ResponseEntity<JsonNode> boletimResp = postJson("/api/relatorios/boletim-bairro?bairro=Jardim São Luís", null, tokenGestor);
        assertThat(boletimResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        aiHarness.validarEstruturaBoletim(boletimResp.getBody(), "Jardim São Luís");

        // Fase 4: Despacho do Protocolo de Interdição
        ResponseEntity<JsonNode> protResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocorrenciaId,
                "equipeResponsavel", "Núcleo de Geotecnia da Defesa Civil - Geo 04",
                "parecerTecnico", "Determinação de evacuação imediata de 6 residências no raio da fratura do talude e cobertura com lona vinílica.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusHours(2).toString()
        ), tokenGestor);
        assertThat(protResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long protocoloId = protResp.getBody().get("id").asLong();

        // Fase 5: Atendimento em Campo
        patchJson("/api/ocorrencias/" + ocorrenciaId + "/status", Map.of(
                "novoStatus", "EM_ATENDIMENTO",
                "justificativa", "Lonas plásticas instaladas e famílias abrigadas no ginásio municipal."
        ), tokenGestor);

        // Fase 6: Conclusão Técnica e Prevenção de Tragédia
        patchJson("/api/protocolos/" + protocoloId + "/concluir", Map.of(
                "parecerTecnico", "Área devidamente interditada e isolada com fita zebrada. Talude protegido contra infiltração pluvial. Risco estabilizado temporariamente."
        ), tokenGestor);

        ResponseEntity<JsonNode> ocFinal = getJson("/api/ocorrencias/" + ocorrenciaId, tokenGestor);
        assertThat(ocFinal.getBody().get("status").asText()).isEqualTo("RESOLVIDA");
    }

    @Test
    @DisplayName("T4-SC03: Vendaval com Queda de Árvore Bloqueando Rota de Ambulâncias em Pinheiros")
    void testCenario03_QuedaArvoreCorredorHospitalarPinheiros() {
        rabbitHarness.resetInteractions();

        String tokenSamu = jwtHelper.gerarTokenCidadao(3L, "marcos.samu@alerta.com.br", "Dr. Marcos Valente", "Pinheiros");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "renato.subprefeitura@pinheiros.sp.gov.br", "Dr. Renato Siqueira", "Pinheiros");

        // Fase 1: Abertura do Chamado de Alta Severidade
        ResponseEntity<JsonNode> ocResp = postJson("/api/ocorrencias", Map.of(
                "titulo", "Queda de árvore de grande porte bloqueando totalmente via de emergência médica",
                "descricao", "Árvore centenária caída transversalmente na pista, fiação de alta tensão rompida faiscando no chão. Trânsito paralisado em direção ao HC.",
                "tipoRisco", "QUEDA_ARVORE",
                "severidade", "ALTA",
                "latitude", -23.567000,
                "longitude", -46.689000,
                "endereco", "Rua dos Pinheiros, 750",
                "bairro", "Pinheiros",
                "cidadaoId", 3L
        ), tokenSamu);
        assertThat(ocResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ocId = ocResp.getBody().get("id").asLong();

        // Fase 2: Mensageria Integrada
        rabbitHarness.validarEventoCritico(ocId, "ALTA", "Pinheiros");

        // Fase 3: Triagem Técnica pelo Gestor
        patchJson("/api/ocorrencias/" + ocId + "/status", Map.of(
                "novoStatus", "EM_ANALISE",
                "justificativa", "Triagem técnica com despacho conjunto Enel e Poda."
        ), tokenGestor);

        // Fase 4: Emissão do Protocolo Multidisciplinar
        ResponseEntity<JsonNode> protResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId,
                "equipeResponsavel", "Força-Tarefa Conjunta: Poda Subprefeitura Pinheiros & Enel Emergência",
                "parecerTecnico", "Prioridade máxima de desobstrução devido ao corredor hospitalar. Desligamento do circuito 13.8kV necessário antes do corte dos galhos.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusHours(1).toString()
        ), tokenGestor);
        assertThat(protResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long protocoloId = protResp.getBody().get("id").asLong();

        // Fase 5: Execução em Campo
        patchJson("/api/ocorrencias/" + ocId + "/status", Map.of(
                "novoStatus", "EM_ATENDIMENTO",
                "justificativa", "Circuito desenergizado pela Enel e motosserristas em ação."
        ), tokenGestor);

        // Fase 6: Desobstrução e Resolução
        patchJson("/api/protocolos/" + protocoloId + "/concluir", Map.of(
                "parecerTecnico", "Fiação refeita e religada. Tronco totalmente picotado e recolhido em caminhão basculante. Pista lavada e liberada para ambulâncias do SAMU."
        ), tokenGestor);

        ResponseEntity<JsonNode> ocFinal = getJson("/api/ocorrencias/" + ocId, tokenGestor);
        assertThat(ocFinal.getBody().get("status").asText()).isEqualTo("RESOLVIDA");
    }

    @Test
    @DisplayName("T4-SC04: Varredura Preventiva Pré-Tempestade e Zeladoria Urbana na Mooca")
    void testCenario04_VarreduraPreventivaMooca() {
        rabbitHarness.resetInteractions();

        String tokenCidadao1 = jwtHelper.gerarTokenCidadao(4L, "roberto.rossi@mooca.com.br", "Roberto Rossi", "Mooca");
        String tokenCidadao2 = jwtHelper.gerarTokenCidadao(5L, "lucia.bianchi@mooca.com.br", "Lucia Bianchi", "Mooca");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "fagundes.defesa@mooca.sp.gov.br", "Capitão Fagundes", "Mooca");

        // Fase 1: Relatos preventivos de BAIXA e MEDIA
        ResponseEntity<JsonNode> oc1 = postJson("/api/ocorrencias", Map.of(
                "titulo", "Boca de lobo entupida com lixo e garrafas PET",
                "descricao", "Acúmulo de detritos impedindo vazão das águas pluviais.",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "MEDIA",
                "latitude", -23.570000,
                "longitude", -46.580000,
                "endereco", "Rua da Mooca, 1400",
                "bairro", "Mooca",
                "cidadaoId", 4L
        ), tokenCidadao1);
        Long ocId1 = oc1.getBody().get("id").asLong();

        postJson("/api/ocorrencias", Map.of(
                "titulo", "Erosão inicial em sarjeta acumulando sedimentos",
                "descricao", "Areia acumulando na guia e sarjeta.",
                "tipoRisco", "EROSAO_SOLO",
                "severidade", "BAIXA",
                "latitude", -23.571000,
                "longitude", -46.581000,
                "endereco", "Rua Taquari, 300",
                "bairro", "Mooca",
                "cidadaoId", 5L
        ), tokenCidadao2);

        // Invariante AMQP: Nenhuma publicação para ocorrências MEDIA ou BAIXA
        rabbitHarness.assertNenhumaMensagemPublicada();

        // Fase 2: IA acionada para síntese preventiva
        ResponseEntity<JsonNode> boletimResp = postJson("/api/relatorios/boletim-bairro?bairro=Mooca", null, tokenGestor);
        assertThat(boletimResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        aiHarness.validarEstruturaBoletim(boletimResp.getBody(), "Mooca");

        // Fase 3: Despacho da operação preventiva
        ResponseEntity<JsonNode> protResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId1,
                "equipeResponsavel", "Equipe de Drenagem Subprefeitura Mooca",
                "parecerTecnico", "Desobstrução preventiva de 12 bueiros antes da chuva prevista.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusHours(3).toString()
        ), tokenGestor);
        Long protId = protResp.getBody().get("id").asLong();

        // Fase 4 & 5: Intervenção e Conclusão
        patchJson("/api/protocolos/" + protId + "/concluir", Map.of(
                "parecerTecnico", "Desobstrução preventiva realizada com sucesso. Removidas 2 toneladas de resíduos sólidos das galerias pluviais."
        ), tokenGestor);

        // Fase 6: Ocorrência resolvida
        ResponseEntity<JsonNode> ocFinal = getJson("/api/ocorrencias/" + ocId1, tokenGestor);
        assertThat(ocFinal.getBody().get("status").asText()).isEqualTo("RESOLVIDA");
    }

    @Test
    @DisplayName("T4-SC05: Tempestade Simultânea Multi-Cidadão em Itaquera (Deduplicação, Triagem & Conclusão Paginada)")
    void testCenario05_TempestadeSimultaneaItaquera() {
        rabbitHarness.resetInteractions();

        String token1 = jwtHelper.gerarTokenCidadao(11L, "juliana.itaquera@alerta.com.br", "Juliana Alves", "Itaquera");
        String token2 = jwtHelper.gerarTokenCidadao(12L, "tiago.itaquera@alerta.com.br", "Tiago Santos", "Itaquera");
        String token3 = jwtHelper.gerarTokenCidadao(13L, "amanda.itaquera@alerta.com.br", "Amanda Rocha", "Itaquera");
        String tokenGestor = jwtHelper.gerarTokenGestor(99L, "moreira.transito@itaquera.sp.gov.br", "Tenente Moreira", "Itaquera");

        // Fase 1: Submissão concorrente
        ResponseEntity<JsonNode> oc1 = postJson("/api/ocorrencias", Map.of(
                "titulo", "Alagamento intransitável na Radial Leste altura do viaduto de Itaquera",
                "descricao", "Pistas centrais da Radial Leste alagadas com água na altura do capô.",
                "tipoRisco", "ALAGAMENTO",
                "severidade", "CRITICA",
                "latitude", -23.541200,
                "longitude", -46.471500,
                "endereco", "Radial Leste, Km 23",
                "bairro", "Itaquera",
                "cidadaoId", 11L
        ), token1);
        Long ocId1 = oc1.getBody().get("id").asLong();

        ResponseEntity<JsonNode> oc2 = postJson("/api/ocorrencias", Map.of(
                "titulo", "Enxurrada forte arrastando caçambas de lixo e bloqueando acesso ao metrô",
                "descricao", "Correnteza violenta descendo a ladeira em direção à passarela de pedestres.",
                "tipoRisco", "ENXURRADA",
                "severidade", "ALTA",
                "latitude", -23.542000,
                "longitude", -46.472100,
                "endereco", "Rua do Metrô Itaquera, 100",
                "bairro", "Itaquera",
                "cidadaoId", 12L
        ), token2);
        Long ocId2 = oc2.getBody().get("id").asLong();

        ResponseEntity<JsonNode> oc3 = postJson("/api/ocorrencias", Map.of(
                "titulo", "Bueiro estourado por pressão de água na Praça da Estação",
                "descricao", "Tampa do bueiro deslocada e água minando pelo asfalto.",
                "tipoRisco", "BUEIRO_OBSTRUIDO",
                "severidade", "MEDIA",
                "latitude", -23.543100,
                "longitude", -46.470800,
                "endereco", "Praça da Estação, 1",
                "bairro", "Itaquera",
                "cidadaoId", 13L
        ), token3);
        Long ocId3 = oc3.getBody().get("id").asLong();

        // Fase 2: Validação Seletiva RabbitMQ (Exatamente 2 mensagens: CRITICA e ALTA)
        assertThat(rabbitHarness.getQuantidadeMensagensPublicadas()).isEqualTo(2);

        // Fase 3: Consulta Paginada do Gestor
        ResponseEntity<JsonNode> pageResp = getJson("/api/ocorrencias?bairro=Itaquera&page=0&size=10", tokenGestor);
        assertThat(pageResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pageResp.getBody().get("totalElements").asInt()).isEqualTo(3);

        // Fase 4: Protocolo Prioritário para ocorrência crítica 1
        ResponseEntity<JsonNode> protResp = postJson("/api/protocolos", Map.of(
                "ocorrenciaId", ocId1,
                "equipeResponsavel", "Força Tática de Trânsito e Defesa Civil Leste",
                "parecerTecnico", "Desvio do fluxo da Radial Leste para a Av. Jacu Pêssego e acionamento de bomba móvel de sucção.",
                "dataPrevisaoAtendimento", LocalDateTime.now().plusHours(1).toString()
        ), tokenGestor);
        Long protId = protResp.getBody().get("id").asLong();

        // Fase 5 & 6: Conclusão em Cascata
        patchJson("/api/protocolos/" + protId + "/concluir", Map.of(
                "parecerTecnico", "Radial Leste totalmente liberada para veículos leves e pesados. Fluxo normalizado."
        ), tokenGestor);

        patchJson("/api/ocorrencias/" + ocId2 + "/status", Map.of(
                "novoStatus", "RESOLVIDA",
                "justificativa", "Caçambas removidas e via liberada."
        ), tokenGestor);

        patchJson("/api/ocorrencias/" + ocId3 + "/status", Map.of(
                "novoStatus", "RESOLVIDA",
                "justificativa", "Tampa de bueiro recolocada e sinalização removida."
        ), tokenGestor);

        // Validação Final de Consulta Paginada de Ocorrências Resolvidas
        ResponseEntity<JsonNode> finalPage = getJson("/api/ocorrencias?bairro=Itaquera&status=RESOLVIDA", tokenGestor);
        assertThat(finalPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalPage.getBody().get("totalElements").asInt()).isEqualTo(3);
    }
}
