package br.com.fiap.alertacom.domain;

import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.ProtocoloDefesaCivil;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.domain.repository.ProtocoloDefesaCivilRepository;
import br.com.fiap.alertacom.dto.request.CidadaoRequest;
import br.com.fiap.alertacom.dto.request.ConcluirProtocoloRequest;
import br.com.fiap.alertacom.dto.request.OcorrenciaRequest;
import br.com.fiap.alertacom.dto.request.ProtocoloRequest;
import br.com.fiap.alertacom.dto.response.OcorrenciaResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Adversarial Challenger Test Suite for Milestone 2 (M2).
 * Verifies Bean Validation rules, extreme boundary conditions, JSON aliases,
 * and RFC 7807 ProblemDetail error structure with empirical precision.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "gestor@alertacom.com.br", roles = {"GESTOR", "CIDADAO"})
@ActiveProfiles("test")
@Transactional
class AdversarialMilestone2ValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private ProtocoloDefesaCivilRepository protocoloRepository;

    private Cidadao cidadaoBase;
    private Ocorrencia ocorrenciaBase;
    private ProtocoloDefesaCivil protocoloBase;

    @BeforeEach
    void setupFixtures() {
        cidadaoBase = cidadaoRepository.save(new Cidadao(
                "Cidadão Teste Adversarial",
                "adversarial." + System.nanoTime() + "@fiap.com.br",
                "11988887777",
                "Mooca",
                "$2a$10$wK1F5N8K7kHnZkExemploHashSenhaCriptografada",
                "ROLE_CIDADAO"
        ));

        ocorrenciaBase = ocorrenciaRepository.save(new Ocorrencia(
                "Ocorrência Base Teste",
                "Descrição detalhada para teste de fronteira adversarial",
                CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.MEDIA,
                -23.5505,
                -46.6333,
                "Rua dos Trilhos, 1200",
                "Mooca",
                cidadaoBase
        ));

        protocoloBase = protocoloRepository.save(new ProtocoloDefesaCivil(
                "DEF-2026-99" + (System.nanoTime() % 1000),
                "Equipe Defesa Leste",
                "Atendimento preliminar em curso",
                LocalDateTime.now().plusDays(1),
                ocorrenciaBase
        ));
    }

    // =========================================================================
    // 1. COORDINATE BOUNDARIES (LATITUDE: -90.0 to 90.0; LONGITUDE: -180.0 to 180.0)
    // =========================================================================

    @Nested
    @DisplayName("1. Geographic Coordinates Boundary Edge Cases")
    class CoordinateBoundaryTests {

        @Test
        @DisplayName("ADV-GEO-1: Latitude no limite inferior exato (-90.0) deve ser aceita")
        void deveAceitarLatitudeLimiteInferiorExato() throws Exception {
            OcorrenciaRequest req = criarRequestOcorrenciaValido(-90.0, 0.0);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.latitude").value(-90.0));
        }

        @Test
        @DisplayName("ADV-GEO-2: Latitude no limite superior exato (90.0) deve ser aceita")
        void deveAceitarLatitudeLimiteSuperiorExato() throws Exception {
            OcorrenciaRequest req = criarRequestOcorrenciaValido(90.0, 0.0);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.latitude").value(90.0));
        }

        @Test
        @DisplayName("ADV-GEO-3: Latitude logo abaixo do limite inferior (-90.0001) deve retornar 400 Bad Request")
        void deveRejeitarLatitudeAbaixoDoLimiteInferior() throws Exception {
            OcorrenciaRequest req = criarRequestOcorrenciaValido(-90.0001, 0.0);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.type").value("https://alertacom.fiap.com.br/errors/bad-request"))
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("latitude")));
        }

        @Test
        @DisplayName("ADV-GEO-4: Latitude logo acima do limite superior (90.0001) deve retornar 400 Bad Request")
        void deveRejeitarLatitudeAcimaDoLimiteSuperior() throws Exception {
            OcorrenciaRequest req = criarRequestOcorrenciaValido(90.0001, 0.0);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.type").value("https://alertacom.fiap.com.br/errors/bad-request"))
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("latitude")));
        }

        @Test
        @DisplayName("ADV-GEO-5: Longitude no limite inferior exato (-180.0) deve ser aceita")
        void deveAceitarLongitudeLimiteInferiorExato() throws Exception {
            OcorrenciaRequest req = criarRequestOcorrenciaValido(0.0, -180.0);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.longitude").value(-180.0));
        }

        @Test
        @DisplayName("ADV-GEO-6: Longitude no limite superior exato (180.0) deve ser aceita")
        void deveAceitarLongitudeLimiteSuperiorExato() throws Exception {
            OcorrenciaRequest req = criarRequestOcorrenciaValido(0.0, 180.0);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.longitude").value(180.0));
        }

        @Test
        @DisplayName("ADV-GEO-7: Longitude logo abaixo do limite inferior (-180.0001) deve retornar 400 Bad Request")
        void deveRejeitarLongitudeAbaixoDoLimiteInferior() throws Exception {
            OcorrenciaRequest req = criarRequestOcorrenciaValido(0.0, -180.0001);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("longitude")));
        }

        @Test
        @DisplayName("ADV-GEO-8: Longitude logo acima do limite superior (180.0001) deve retornar 400 Bad Request")
        void deveRejeitarLongitudeAcimaDoLimiteSuperior() throws Exception {
            OcorrenciaRequest req = criarRequestOcorrenciaValido(0.0, 180.0001);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("longitude")));
        }
    }

    // =========================================================================
    // 2. TEXT BOUNDARIES (TITULO: 5-120; DESCRICAO: 10-1000; PARECER: 10-1500)
    // =========================================================================

    @Nested
    @DisplayName("2. Text Length Boundary Limits")
    class TextBoundaryTests {

        @Test
        @DisplayName("ADV-TXT-1: Título de ocorrência com 5 caracteres (mínimo exato) deve ser aceito")
        void deveAceitarTituloComExatos5Caracteres() throws Exception {
            String titulo5 = "12345";
            OcorrenciaRequest req = criarRequestComTitulo(titulo5);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.titulo").value(titulo5));
        }

        @Test
        @DisplayName("ADV-TXT-2: Título de ocorrência com 4 caracteres (abaixo do mínimo) deve retornar 400")
        void deveRejeitarTituloCom4Caracteres() throws Exception {
            String titulo4 = "1234";
            OcorrenciaRequest req = criarRequestComTitulo(titulo4);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("titulo")));
        }

        @Test
        @DisplayName("ADV-TXT-3: Título de ocorrência com 120 caracteres (máximo exato) deve ser aceito")
        void deveAceitarTituloComExatos120Caracteres() throws Exception {
            String titulo120 = "T".repeat(120);
            OcorrenciaRequest req = criarRequestComTitulo(titulo120);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.titulo").value(titulo120));
        }

        @Test
        @DisplayName("ADV-TXT-4: Título de ocorrência com 121 caracteres (acima do máximo) deve retornar 400")
        void deveRejeitarTituloCom121Caracteres() throws Exception {
            String titulo121 = "T".repeat(121);
            OcorrenciaRequest req = criarRequestComTitulo(titulo121);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("titulo")));
        }

        @Test
        @DisplayName("ADV-TXT-5: Descrição de ocorrência com 10 caracteres (mínimo exato) deve ser aceita")
        void deveAceitarDescricaoComExatos10Caracteres() throws Exception {
            String desc10 = "1234567890";
            OcorrenciaRequest req = criarRequestComDescricao(desc10);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.descricao").value(desc10));
        }

        @Test
        @DisplayName("ADV-TXT-6: Descrição de ocorrência com 9 caracteres (abaixo do mínimo) deve retornar 400")
        void deveRejeitarDescricaoCom9Caracteres() throws Exception {
            String desc9 = "123456789";
            OcorrenciaRequest req = criarRequestComDescricao(desc9);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("descricao")));
        }

        @Test
        @DisplayName("ADV-TXT-7: Descrição de ocorrência com 1000 caracteres (máximo exato) deve ser aceita")
        void deveAceitarDescricaoComExatos1000Caracteres() throws Exception {
            String desc1000 = "D".repeat(1000);
            OcorrenciaRequest req = criarRequestComDescricao(desc1000);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.descricao").value(desc1000));
        }

        @Test
        @DisplayName("ADV-TXT-8: Descrição de ocorrência com 1001 caracteres (acima do máximo) deve retornar 400")
        void deveRejeitarDescricaoCom1001Caracteres() throws Exception {
            String desc1001 = "D".repeat(1001);
            OcorrenciaRequest req = criarRequestComDescricao(desc1001);

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("descricao")));
        }

        @Test
        @DisplayName("ADV-TXT-9: Parecer final de conclusão com 10 caracteres (mínimo exato) deve ser aceito")
        void deveAceitarParecerFinalComExatos10Caracteres() throws Exception {
            String parecer10 = "1234567890";
            ConcluirProtocoloRequest req = new ConcluirProtocoloRequest(parecer10);

            mockMvc.perform(patch("/api/protocolos/{id}/concluir", protocoloBase.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ocorrenciaStatus").value("RESOLVIDA"))
                    .andExpect(jsonPath("$.parecerTecnico").value(parecer10));
        }

        @Test
        @DisplayName("ADV-TXT-10: Parecer final de conclusão com 9 caracteres (abaixo do mínimo) deve retornar 400")
        void deveRejeitarParecerFinalCom9Caracteres() throws Exception {
            String parecer9 = "123456789";
            ConcluirProtocoloRequest req = new ConcluirProtocoloRequest(parecer9);

            mockMvc.perform(patch("/api/protocolos/{id}/concluir", protocoloBase.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("parecerFinal")));
        }

        @Test
        @DisplayName("ADV-TXT-11: Parecer final com 1500 caracteres (máximo exato) deve ser aceito")
        void deveAceitarParecerFinalComExatos1500Caracteres() throws Exception {
            String parecer1500 = "P".repeat(1500);
            ConcluirProtocoloRequest req = new ConcluirProtocoloRequest(parecer1500);

            mockMvc.perform(patch("/api/protocolos/{id}/concluir", protocoloBase.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parecerTecnico").value(parecer1500));
        }

        @Test
        @DisplayName("ADV-TXT-12: Parecer final com 1501 caracteres (acima do máximo) deve retornar 400")
        void deveRejeitarParecerFinalCom1501Caracteres() throws Exception {
            String parecer1501 = "P".repeat(1501);
            ConcluirProtocoloRequest req = new ConcluirProtocoloRequest(parecer1501);

            mockMvc.perform(patch("/api/protocolos/{id}/concluir", protocoloBase.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("parecerFinal")));
        }
    }

    // =========================================================================
    // 3. PHONE NUMBER REGEX PATTERNS AND BOUNDARIES
    // =========================================================================

    @Nested
    @DisplayName("3. Phone Format Boundaries and Regular Expression")
    class PhoneFormatBoundaryTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "(11) 98765-4321",
                "11 98765-4321",
                "11987654321",
                "(11) 8765-4321",
                "11 8765-4321",
                "1187654321"
        })
        @DisplayName("ADV-TEL-1: Padrões válidos de telefone celular e fixo devem ser aceitos")
        void deveAceitarPadroesValidosDeTelefone(String telefoneValido) throws Exception {
            CidadaoRequest req = new CidadaoRequest(
                    "Munícipe Teste",
                    "cid." + System.nanoTime() + "@fiap.com.br",
                    telefoneValido,
                    "Mooca",
                    "senha123",
                    "ROLE_CIDADAO"
            );

            mockMvc.perform(post("/api/cidadaos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.telefone").value(telefoneValido));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "123",
                "telefone_invalido",
                "(11) 987-654",
                "(111) 98765-4321",
                "119876543210",
                "11-9876-54321-00"
        })
        @DisplayName("ADV-TEL-2: Padrões inválidos de telefone devem ser rejeitados com 400 Bad Request")
        void deveRejeitarPadroesInvalidosDeTelefone(String telefoneInvalido) throws Exception {
            CidadaoRequest req = new CidadaoRequest(
                    "Munícipe Teste",
                    "cid." + System.nanoTime() + "@fiap.com.br",
                    telefoneInvalido,
                    "Mooca",
                    "senha123",
                    "ROLE_CIDADAO"
            );

            mockMvc.perform(post("/api/cidadaos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("telefone")));
        }
    }

    // =========================================================================
    // 4. BEAN VALIDATION REJECTING NULLS AND BLANKS ON REQUIRED FIELDS
    // =========================================================================

    @Nested
    @DisplayName("4. Bean Validation Nulls and Blanks on Required Fields")
    class NullAndBlankValidationTests {

        @Test
        @DisplayName("ADV-REQ-1: Ocorrência sem campos obrigatórios deve rejeitar todos com lista de invalidParams")
        void deveRejeitarOcorrenciaComMultiplosCamposNulos() throws Exception {
            String payloadInvalido = "{}";

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadInvalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Requisição Inválida"))
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItems(
                            "titulo", "descricao", "categoria", "severidade", "endereco", "bairro", "cidadaoId"
                    )));
        }

        @Test
        @DisplayName("ADV-REQ-2: Ocorrência com campos em branco (espaços) deve ser rejeitada pelo @NotBlank")
        void deveRejeitarOcorrenciaComStringsEmBranco() throws Exception {
            OcorrenciaRequest req = new OcorrenciaRequest(
                    "     ",
                    "          ",
                    CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.MEDIA,
                    -23.55,
                    -46.63,
                    "   ",
                    "   ",
                    cidadaoBase.getId()
            );

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItems("titulo", "descricao", "endereco", "bairro")));
        }

        @Test
        @DisplayName("ADV-REQ-3: Cadastro de Cidadão com e-mail inválido, nome curto e senha curta deve ser rejeitado")
        void deveRejeitarCidadaoComViolacoesSimultaneas() throws Exception {
            CidadaoRequest req = new CidadaoRequest(
                    "Jo",
                    "email-invalido-sem-formato",
                    "11987654321",
                    " ",
                    "123",
                    "ROLE_INVALIDA"
            );

            mockMvc.perform(post("/api/cidadaos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItems("nome", "email", "bairro", "senha", "role")));
        }

        @Test
        @DisplayName("ADV-REQ-4: Emissão de Protocolo sem equipe e com data no passado deve ser rejeitada")
        void deveRejeitarProtocoloComEquipeEmBrancoEDataNoPassado() throws Exception {
            ProtocoloRequest req = new ProtocoloRequest(
                    "  ",
                    "Parecer",
                    LocalDateTime.now().minusDays(2),
                    ocorrenciaBase.getId()
            );

            mockMvc.perform(post("/api/protocolos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItems("equipeResponsavel", "dataPrevisaoAtendimento")));
        }

        @Test
        @DisplayName("ADV-REQ-5: Concluir Protocolo com parecer nulo ou em branco deve ser rejeitado")
        void deveRejeitarConcluirProtocoloComParecerEmBranco() throws Exception {
            String payloadEmBranco = """
                    {
                        "parecerFinal": "          "
                    }
                    """;

            mockMvc.perform(patch("/api/protocolos/{id}/concluir", protocoloBase.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadEmBranco))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.invalidParams[*].field", hasItem("parecerFinal")));
        }
    }

    // =========================================================================
    // 5. RFC 7807 PROBLEM DETAIL FORMATTING & INVALID_PARAMS SCHEMA
    // =========================================================================

    @Nested
    @DisplayName("5. RFC 7807 ProblemDetail Spec Conformance")
    class Rfc7807StructureTests {

        @Test
        @DisplayName("ADV-RFC-1: Resposta de erro 400 deve respeitar estritamente a RFC 7807")
        void deveRespeitarEspecificacaoRfc7807EmValidacao() throws Exception {
            String payloadInvalido = """
                    {
                        "titulo": "ab"
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadInvalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            JsonNode rootNode = objectMapper.readTree(responseBody);

            assertThat(rootNode.get("type").asText()).isEqualTo("https://alertacom.fiap.com.br/errors/bad-request");
            assertThat(rootNode.get("title").asText()).isEqualTo("Requisição Inválida");
            assertThat(rootNode.get("status").asInt()).isEqualTo(400);
            assertThat(rootNode.get("detail").asText()).isEqualTo("Erro de validação nos campos da requisição");
            assertThat(rootNode.hasNonNull("timestamp")).isTrue();

            JsonNode invalidParams = rootNode.get("invalidParams");
            assertThat(invalidParams.isArray()).isTrue();
            assertThat(invalidParams.size()).isGreaterThanOrEqualTo(1);

            for (JsonNode param : invalidParams) {
                assertThat(param.hasNonNull("field")).isTrue();
                assertThat(param.hasNonNull("message")).isTrue();
            }
        }
    }

    // =========================================================================
    // 6. DTO JSON ALIASES DESERIALIZATION AND SERIALIZATION
    // =========================================================================

    @Nested
    @DisplayName("6. DTO JsonAlias Compatibility Tests")
    class DtoJsonAliasTests {

        @Test
        @DisplayName("ADV-ALS-1: OcorrenciaRequest deve deserializar 'tipoRisco' perfeitamente para 'categoria'")
        void deveDeserializarTipoRiscoParaCategoria() throws Exception {
            String jsonPayload = String.format("""
                    {
                        "titulo": "Enxurrada severa na baixada",
                        "descricao": "Nível da água subindo rapidamente invadindo residências",
                        "tipoRisco": "ENXURRADA",
                        "severidade": "ALTA",
                        "latitude": -23.5510,
                        "longitude": -46.6340,
                        "endereco": "Av. do Estado, 5000",
                        "bairro": "Mooca",
                        "cidadaoId": %d
                    }
                    """, cidadaoBase.getId());

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.categoria").value("ENXURRADA"))
                    .andExpect(jsonPath("$.tipoRisco").value("ENXURRADA"));
        }

        @Test
        @DisplayName("ADV-ALS-2: OcorrenciaRequest deve aceitar tanto 'categoria' quanto 'tipoRisco'")
        void deveDeserializarDiretamenteCategoria() throws Exception {
            String jsonPayload = String.format("""
                    {
                        "titulo": "Deslizamento de terra em encosta",
                        "descricao": "Barreira cedeu atingindo muro de contenção",
                        "categoria": "DESLIZAMENTO",
                        "severidade": "CRITICA",
                        "latitude": -23.5520,
                        "longitude": -46.6350,
                        "endereco": "Rua da Encosta, 300",
                        "bairro": "Mooca",
                        "cidadaoId": %d
                    }
                    """, cidadaoBase.getId());

            mockMvc.perform(post("/api/ocorrencias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.categoria").value("DESLIZAMENTO"))
                    .andExpect(jsonPath("$.tipoRisco").value("DESLIZAMENTO"));
        }

        @Test
        @DisplayName("ADV-ALS-3: ConcluirProtocoloRequest deve aceitar 'parecerTecnico' como alias para 'parecerFinal'")
        void deveDeserializarParecerTecnicoParaParecerFinal() throws Exception {
            String jsonPayload = """
                    {
                        "parecerTecnico": "Reparo estrutural realizado e área devidamente liberada pela Defesa Civil."
                    }
                    """;

            mockMvc.perform(patch("/api/protocolos/{id}/concluir", protocoloBase.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ocorrenciaStatus").value("RESOLVIDA"))
                    .andExpect(jsonPath("$.parecerTecnico").value("Reparo estrutural realizado e área devidamente liberada pela Defesa Civil."));
        }

        @Test
        @DisplayName("ADV-ALS-4: ConcluirProtocoloRequest deve aceitar 'parecerFinal' diretamente")
        void deveDeserializarDiretamenteParecerFinal() throws Exception {
            String jsonPayload = """
                    {
                        "parecerFinal": "Vistoria concluída com parecer definitivo favorável ao término do alerta."
                    }
                    """;

            mockMvc.perform(patch("/api/protocolos/{id}/concluir", protocoloBase.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ocorrenciaStatus").value("RESOLVIDA"))
                    .andExpect(jsonPath("$.parecerTecnico").value("Vistoria concluída com parecer definitivo favorável ao término do alerta."));
        }

        @Test
        @DisplayName("ADV-ALS-5: OcorrenciaResponse deve serializar ambos os atributos 'categoria' e 'tipoRisco'")
        void deveSerializarAmbosOsAtributosNaResposta() throws Exception {
            OcorrenciaResponse resp = OcorrenciaResponse.fromEntity(ocorrenciaBase);
            String json = objectMapper.writeValueAsString(resp);
            JsonNode tree = objectMapper.readTree(json);

            assertThat(tree.hasNonNull("categoria")).isTrue();
            assertThat(tree.hasNonNull("tipoRisco")).isTrue();
            assertThat(tree.get("categoria").asText()).isEqualTo(tree.get("tipoRisco").asText());
        }
    }

    // =========================================================================
    // 7. DIRECT BEAN VALIDATION ORACLES (PURE UNIT ASSERTIONS)
    // =========================================================================

    @Nested
    @DisplayName("7. Pure Bean Validation Direct Oracle Assertions")
    class DirectValidatorOracles {

        @Test
        @DisplayName("ADV-ORC-1: Validador direto deve acusar violações exatas para OcorrenciaRequest")
        void deveIdentificarViolacoesDiretamenteEmOcorrenciaRequest() {
            OcorrenciaRequest req = new OcorrenciaRequest(
                    "abc", // < 5
                    "pequeno", // < 10
                    null,
                    null,
                    -95.0, // < -90.0
                    200.0, // > 180.0
                    "",
                    "",
                    null
            );

            Set<ConstraintViolation<OcorrenciaRequest>> violations = validator.validate(req);
            assertThat(violations).hasSize(9);

            assertThat(violations)
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("titulo", "descricao", "categoria", "severidade", "latitude", "longitude", "endereco", "bairro", "cidadaoId");
        }

        @Test
        @DisplayName("ADV-ORC-2: Validador direto deve validar request totalmente compatível com 0 violações")
        void deveTerZeroViolacoesEmRequestPerfeito() {
            OcorrenciaRequest req = new OcorrenciaRequest(
                    "Alerta de Risco Válido",
                    "Descrição com tamanho suficiente para validar sem erros",
                    CategoriaOcorrencia.ALAGAMENTO,
                    SeveridadeOcorrencia.MEDIA,
                    -23.5505,
                    -46.6333,
                    "Rua dos Trilhos, 100",
                    "Mooca",
                    1L
            );

            Set<ConstraintViolation<OcorrenciaRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("ADV-ORC-3: Validador direto deve verificar limites de ConcluirProtocoloRequest")
        void deveVerificarLimitesEmConcluirProtocoloRequest() {
            ConcluirProtocoloRequest reqCurto = new ConcluirProtocoloRequest("123456789"); // 9 chars
            Set<ConstraintViolation<ConcluirProtocoloRequest>> vCurto = validator.validate(reqCurto);
            assertThat(vCurto).hasSize(1);
            assertThat(vCurto.iterator().next().getPropertyPath().toString()).isEqualTo("parecerFinal");

            ConcluirProtocoloRequest reqExatoMin = new ConcluirProtocoloRequest("1234567890"); // 10 chars
            assertThat(validator.validate(reqExatoMin)).isEmpty();

            ConcluirProtocoloRequest reqExatoMax = new ConcluirProtocoloRequest("X".repeat(1500));
            assertThat(validator.validate(reqExatoMax)).isEmpty();

            ConcluirProtocoloRequest reqLongo = new ConcluirProtocoloRequest("X".repeat(1501));
            Set<ConstraintViolation<ConcluirProtocoloRequest>> vLongo = validator.validate(reqLongo);
            assertThat(vLongo).hasSize(1);
            assertThat(vLongo.iterator().next().getPropertyPath().toString()).isEqualTo("parecerFinal");
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private OcorrenciaRequest criarRequestOcorrenciaValido(Double latitude, Double longitude) {
        return new OcorrenciaRequest(
                "Ocorrência Coordenadas Válidas",
                "Descrição válida com mais de dez caracteres",
                CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.MEDIA,
                latitude,
                longitude,
                "Rua dos Trilhos, 1200",
                "Mooca",
                cidadaoBase.getId()
        );
    }

    private OcorrenciaRequest criarRequestComTitulo(String titulo) {
        return new OcorrenciaRequest(
                titulo,
                "Descrição válida com mais de dez caracteres",
                CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.MEDIA,
                -23.5505,
                -46.6333,
                "Rua dos Trilhos, 1200",
                "Mooca",
                cidadaoBase.getId()
        );
    }

    private OcorrenciaRequest criarRequestComDescricao(String descricao) {
        return new OcorrenciaRequest(
                "Título Válido",
                descricao,
                CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.MEDIA,
                -23.5505,
                -46.6333,
                "Rua dos Trilhos, 1200",
                "Mooca",
                cidadaoBase.getId()
        );
    }
}
