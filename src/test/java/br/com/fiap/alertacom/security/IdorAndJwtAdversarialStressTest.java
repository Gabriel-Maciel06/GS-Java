package br.com.fiap.alertacom.security;

import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bateria de Testes Adversários de Segurança (Milestone M1).
 * Foco exclusivo em:
 * 1. IDOR (Insecure Direct Object Reference) em Cidadão (GET/PUT /api/cidadaos/{id})
 * 2. IDOR em Ocorrência (POST /api/ocorrencias com cidadaoId forjado)
 * 3. Validação de Entropia de Segredo JWT e Fail-Fast nos perfis docker/prod
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Bateria Adversária de Testes de Segurança M1 (IDOR & Entropia JWT)")
class IdorAndJwtAdversarialStressTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CidadaoRepository cidadaoRepository;

    @Autowired
    private OcorrenciaRepository ocorrenciaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Cidadao cidadaoA;
    private Cidadao cidadaoB;
    private Cidadao gestor;

    private String tokenA;
    private String tokenB;
    private String tokenGestor;

    @BeforeEach
    void setupAdversarialEnvironment() {
        // Cidadão A (Atacante ou Munícipe Comum)
        cidadaoA = cidadaoRepository.save(Cidadao.builder()
                .nome("Munícipe Atacante A")
                .email("atacante.a@alertacom.com")
                .telefone("(11) 91111-0001")
                .bairro("Tatuapé")
                .senha(passwordEncoder.encode("SenhaForte@123"))
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build());
        tokenA = tokenService.gerarToken(cidadaoA);

        // Cidadão B (Vítima)
        cidadaoB = cidadaoRepository.save(Cidadao.builder()
                .nome("Munícipe Vítima B")
                .email("vitima.b@alertacom.com")
                .telefone("(11) 92222-0002")
                .bairro("Mooca")
                .senha(passwordEncoder.encode("SenhaForte@123"))
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build());
        tokenB = tokenService.gerarToken(cidadaoB);

        // Gestor Defesa Civil
        gestor = cidadaoRepository.save(Cidadao.builder()
                .nome("Gestor Operacional")
                .email("gestor.defesa@alertacom.com")
                .telefone("(11) 93333-0003")
                .bairro("Sé")
                .senha(passwordEncoder.encode("SenhaForte@123"))
                .role("ROLE_GESTOR")
                .ativo(true)
                .build());
        tokenGestor = tokenService.gerarToken(gestor);
    }

    // =========================================================================
    // 1. ATAQUE DE IDOR EM CIDADÃO (/api/cidadaos/{id})
    // =========================================================================
    @Nested
    @DisplayName("Cenário 1: Testes Adversários de IDOR em Cidadão")
    class IdorCidadaoTests {

        @Test
        @DisplayName("ATAQUE: Munícipe A tenta realizar GET /api/cidadaos/{idB} (leitura de dados privados da vítima) -> Deve retornar 403 Forbidden RFC 7807")
        void deveBloquearLeituraIdorCidadaoCom403() throws Exception {
            mockMvc.perform(get("/api/cidadaos/" + cidadaoB.getId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.title", is("Acesso Proibido")))
                    .andExpect(jsonPath("$.detail", containsString("Acesso negado")));
        }

        @Test
        @DisplayName("ATAQUE: Munícipe A tenta realizar PUT /api/cidadaos/{idB} (adulteração de cadastro da vítima) -> Deve retornar 403 Forbidden e preservar integridade dos dados")
        void deveBloquearEscritaIdorCidadaoCom403EPreservarDados() throws Exception {
            String payloadAdulteracao = """
                {
                    "nome": "Nome Vítima Corrompido",
                    "telefone": "(11) 99999-9999",
                    "bairro": "Bairro Inexistente"
                }
                """;

            mockMvc.perform(put("/api/cidadaos/" + cidadaoB.getId())
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadAdulteracao))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                    .andExpect(jsonPath("$.status", is(403)));

            // Verificação de Integridade no Banco Relacional: Dados da Vítima B NÃO foram adulterados
            Cidadao vitimaNoBanco = cidadaoRepository.findById(cidadaoB.getId()).orElseThrow();
            assertThat(vitimaNoBanco.getNome()).isEqualTo("Munícipe Vítima B");
            assertThat(vitimaNoBanco.getTelefone()).isEqualTo("(11) 92222-0002");
            assertThat(vitimaNoBanco.getBairro()).isEqualTo("Mooca");
        }

        @Test
        @DisplayName("ATAQUE (Anti-Enumeração): Munícipe A tenta GET /api/cidadaos/999999 (ID inexistente) -> Deve retornar 403 Forbidden antes do banco, prevenindo vazamento de existência (oráculo 404 vs 403)")
        void deveBloquearIdorCidadaoInexistenteCom403PrevenindoEnumeracao() throws Exception {
            mockMvc.perform(get("/api/cidadaos/999999")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)))
                    .andExpect(jsonPath("$.title", is("Acesso Proibido")));
        }

        @Test
        @DisplayName("ACESSO LEGÍTIMO: Munícipe A consulta seu próprio cadastro GET /api/cidadaos/{idA} -> 200 OK")
        void devePermitirMunicipeConsultarProprioCadastro() throws Exception {
            mockMvc.perform(get("/api/cidadaos/" + cidadaoA.getId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(cidadaoA.getId().intValue())))
                    .andExpect(jsonPath("$.email", is("atacante.a@alertacom.com")));
        }

        @Test
        @DisplayName("ACESSO LEGÍTIMO: Munícipe A atualiza seu próprio cadastro PUT /api/cidadaos/{idA} -> 200 OK")
        void devePermitirMunicipeAtualizarProprioCadastro() throws Exception {
            String payloadAtualizacao = """
                {
                    "nome": "Munícipe A Atualizado",
                    "telefone": "(11) 91111-8888",
                    "bairro": "Anália Franco"
                }
                """;

            mockMvc.perform(put("/api/cidadaos/" + cidadaoA.getId())
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadAtualizacao))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nome", is("Munícipe A Atualizado")))
                    .andExpect(jsonPath("$.bairro", is("Anália Franco")));
        }

        @Test
        @DisplayName("ACESSO ADMINISTRATIVO: Gestor consulta cadastro de qualquer cidadão -> 200 OK")
        void devePermitirGestorConsultarQualquerCidadao() throws Exception {
            mockMvc.perform(get("/api/cidadaos/" + cidadaoB.getId())
                            .header("Authorization", "Bearer " + tokenGestor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(cidadaoB.getId().intValue())));
        }
    }

    // =========================================================================
    // 2. ATAQUE DE IDOR EM OCORRÊNCIA (/api/ocorrencias)
    // =========================================================================
    @Nested
    @DisplayName("Cenário 2: Testes Adversários de IDOR em Ocorrência")
    class IdorOcorrenciaTests {

        @Test
        @DisplayName("ATAQUE: Munícipe A envia POST /api/ocorrencias com cidadaoId forjado para Vítima B (ID 20) -> Criação associada ESTRITAMENTE a Munícipe A (ID 10)")
        void deveNeutralizarIdorOcorrenciaVinculandoEstritamenteAoAutenticado() throws Exception {
            String payloadIdor = String.format("""
                {
                    "titulo": "Rua totalmente alagada com bueiros entupidos",
                    "descricao": "Tentativa de spoofing de autor de ocorrência via IDOR",
                    "categoria": "ALAGAMENTO",
                    "severidade": "ALTA",
                    "latitude": -23.55052,
                    "longitude": -46.63330,
                    "endereco": "Rua dos Trilhos, 1500",
                    "bairro": "Mooca",
                    "cidadaoId": %d
                }
                """, cidadaoB.getId());

            MvcResult result = mockMvc.perform(post("/api/ocorrencias")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadIdor))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cidadao.id", is(cidadaoA.getId().intValue())))
                    .andExpect(jsonPath("$.cidadao.email", is("atacante.a@alertacom.com")))
                    .andReturn();

            // Verificação Estrita na Camada de Persistência (JPA / Banco H2)
            JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
            Long ocorrenciaId = jsonNode.get("id").asLong();

            Ocorrencia ocorrenciaNoBanco = ocorrenciaRepository.findById(ocorrenciaId).orElseThrow();
            assertThat(ocorrenciaNoBanco.getCidadao().getId())
                    .as("Ocorrência no banco relacional DEVE estar associada ao ID do Cidadão A autenticado")
                    .isEqualTo(cidadaoA.getId());
            assertThat(ocorrenciaNoBanco.getCidadao().getId())
                    .as("Ocorrência no banco NÃO PODE sob hipótese alguma estar associada ao ID da Vítima B informada no payload")
                    .isNotEqualTo(cidadaoB.getId());
        }

        @Test
        @DisplayName("ATAQUE: Munícipe A envia POST /api/ocorrencias com cidadaoId inexistente (999999L) -> Payload ignorado e associado a Munícipe A com 201 Created")
        void deveIgnorarCidadaoIdInexistenteQuandoMunicipeAutenticado() throws Exception {
            String payloadIdorInexistente = """
                {
                    "titulo": "Árvore de grande porte com risco de queda sobre pedestres",
                    "descricao": "Munícipe enviando ID arbitrário não existente",
                    "categoria": "QUEDA_ARVORE",
                    "severidade": "MEDIA",
                    "latitude": -23.56000,
                    "longitude": -46.64000,
                    "endereco": "Avenida Paulista, 1000",
                    "bairro": "Bela Vista",
                    "cidadaoId": 999999
                }
                """;

            MvcResult result = mockMvc.perform(post("/api/ocorrencias")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadIdorInexistente))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cidadao.id", is(cidadaoA.getId().intValue())))
                    .andReturn();

            JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
            Long ocorrenciaId = jsonNode.get("id").asLong();

            Ocorrencia ocorrenciaNoBanco = ocorrenciaRepository.findById(ocorrenciaId).orElseThrow();
            assertThat(ocorrenciaNoBanco.getCidadao().getId()).isEqualTo(cidadaoA.getId());
        }

        @Test
        @DisplayName("ACESSO GESTOR: Gestor pode legitimamente registrar ocorrência em nome de um munícipe específico (atendimento telefônico 199)")
        void devePermitirGestorRegistrarOcorrenciaParaOutroCidadao() throws Exception {
            String payloadGestor = String.format("""
                {
                    "titulo": "Deslizamento iminente na encosta",
                    "descricao": "Atendimento emergencial 199 registrado pelo gestor para a vítima",
                    "categoria": "DESLIZAMENTO",
                    "severidade": "CRITICA",
                    "latitude": -23.57000,
                    "longitude": -46.65000,
                    "endereco": "Rua da Encosta, 42",
                    "bairro": "Ipiranga",
                    "cidadaoId": %d
                }
                """, cidadaoB.getId());

            MvcResult result = mockMvc.perform(post("/api/ocorrencias")
                            .header("Authorization", "Bearer " + tokenGestor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadGestor))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cidadao.id", is(cidadaoB.getId().intValue())))
                    .andReturn();

            JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
            Long ocorrenciaId = jsonNode.get("id").asLong();

            Ocorrencia ocorrenciaNoBanco = ocorrenciaRepository.findById(ocorrenciaId).orElseThrow();
            assertThat(ocorrenciaNoBanco.getCidadao().getId()).isEqualTo(cidadaoB.getId());
        }

        @Test
        @DisplayName("PROTEÇÃO GESTOR: Gestor enviando cidadaoId inexistente (999999L) -> 404 Not Found")
        void deveRetornar404QuandoGestorIndicarCidadaoInexistente() throws Exception {
            String payloadGestorInexistente = """
                {
                    "titulo": "Ocorrência com cidadão inválido",
                    "descricao": "Gestor informando ID de munícipe que não consta na base",
                    "categoria": "ALAGAMENTO",
                    "severidade": "BAIXA",
                    "latitude": -23.57000,
                    "longitude": -46.65000,
                    "endereco": "Rua Qualquer, 1",
                    "bairro": "Centro",
                    "cidadaoId": 999999
                }
                """;

            mockMvc.perform(post("/api/ocorrencias")
                            .header("Authorization", "Bearer " + tokenGestor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadGestorInexistente))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.title", is("Recurso Não Encontrado")));
        }
    }

    // =========================================================================
    // 3. TESTES DE FAIL-FAST E ENTROPIA DE SEGREDO JWT (TokenService)
    // =========================================================================
    @Nested
    @DisplayName("Cenário 3: Validação de Entropia JWT e Fail-Fast de Inicialização")
    class JwtSecretEntropyFailFastTests {

        @Test
        @DisplayName("FAIL-FAST DOCKER: Lança IllegalStateException quando JWT_SECRET ausente/vazio")
        void deveLancarExcecaoEmPerfilDockerSemSegredo() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("docker");

            assertThatThrownBy(() -> new TokenService("", env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET é obrigatória nos perfis 'docker'/'prod'");

            assertThatThrownBy(() -> new TokenService(null, env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET é obrigatória nos perfis 'docker'/'prod'");
        }

        @Test
        @DisplayName("FAIL-FAST DOCKER: Lança IllegalStateException quando JWT_SECRET for o segredo padrão de dev")
        void deveLancarExcecaoEmPerfilDockerComSegredoPadraoDev() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("docker");

            assertThatThrownBy(() -> new TokenService(TokenService.SEGREDO_PADRAO_DEV, env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("segredo padrão de desenvolvimento não pode ser utilizado nos perfis 'docker'/'prod'");
        }

        @Test
        @DisplayName("FAIL-FAST PROD: Lança IllegalStateException quando JWT_SECRET for fraco (< 32 caracteres / 256 bits)")
        void deveLancarExcecaoEmPerfilProdComSegredoFraco() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");

            // 15 caracteres (< 32)
            assertThatThrownBy(() -> new TokenService("segredoCurto123", env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("mínimo 32 caracteres / 256 bits");

            // 31 caracteres (limite inferior estrito)
            assertThatThrownBy(() -> new TokenService("1234567890123456789012345678901", env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("mínimo 32 caracteres / 256 bits");
        }

        @Test
        @DisplayName("FAIL-FAST PRODUCTION: Lança IllegalStateException no perfil 'production' com segredo padrão dev")
        void deveLancarExcecaoEmPerfilProductionComSegredoPadraoDev() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("production");

            assertThatThrownBy(() -> new TokenService(TokenService.SEGREDO_PADRAO_DEV, env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("segredo padrão de desenvolvimento não pode ser utilizado nos perfis 'docker'/'prod'");
        }

        @Test
        @DisplayName("GERAL: Qualquer perfil rejeita segredos com menos de 32 caracteres (RFC 7518)")
        void deveRejeitarSegredoCurtoEmQualquerPerfil() {
            assertThatThrownBy(() -> new TokenService("segredoMuitoCurto"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("mínimo 32 caracteres / 256 bits");
        }

        @Test
        @DisplayName("SUCESSO: Inicializa com sucesso nos perfis docker e prod quando segredo é forte (>= 32 chars e não padrão)")
        void devePermitirInicializacaoComSegredoForteEmDockerEProd() {
            String segredoForte = "ChaveCriptograficaSuperSeguraComAltaEntropiaParaProducao2026!";

            MockEnvironment envDocker = new MockEnvironment();
            envDocker.setActiveProfiles("docker");
            TokenService serviceDocker = new TokenService(segredoForte, envDocker);
            assertThat(serviceDocker).isNotNull();

            MockEnvironment envProd = new MockEnvironment();
            envProd.setActiveProfiles("prod");
            TokenService serviceProd = new TokenService(segredoForte, envProd);
            assertThat(serviceProd).isNotNull();
        }

        @Test
        @DisplayName("DEV/TEST: Tolera segredo padrão dev com log de aviso nos perfis dev e test")
        void deveTolerarSegredoPadraoEmDevETest() {
            MockEnvironment envTest = new MockEnvironment();
            envTest.setActiveProfiles("test");
            TokenService serviceTest = new TokenService(null, envTest);
            assertThat(serviceTest).isNotNull();

            MockEnvironment envDev = new MockEnvironment();
            envDev.setActiveProfiles("dev");
            TokenService serviceDev = new TokenService("", envDev);
            assertThat(serviceDev).isNotNull();
        }
    }
}
