package br.com.fiap.alertacom.security;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suíte de Testes Adversários de Segurança do Challenger (Milestone M1).
 * Executa ataques empíricos simulando atacantes éticos e cenários de stress para verificar a resiliência das defesas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Suíte Adversária do Challenger — Testes Empíricos de Segurança M1")
class AdversarialSecurityChallengerTest {

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

    @Value("${jwt.secret:${api.security.token.secret:AlertaComSuperChaveSecretaGlobalSolutionFIAP2026}}")
    private String secret;

    private Cidadao cidadaoA;
    private Cidadao cidadaoB;
    private Cidadao gestor;
    private String tokenCidadaoA;
    private String tokenGestor;

    @BeforeEach
    void setUp() {
        cidadaoA = cidadaoRepository.save(Cidadao.builder()
                .nome("Munícipe Alfa")
                .email("alfa@alertacom.com")
                .telefone("(11) 91111-0001")
                .bairro("Pinheiros")
                .senha(passwordEncoder.encode("SenhaForte@123"))
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build());
        tokenCidadaoA = tokenService.gerarToken(cidadaoA);

        cidadaoB = cidadaoRepository.save(Cidadao.builder()
                .nome("Munícipe Beta")
                .email("beta@alertacom.com")
                .telefone("(11) 92222-0002")
                .bairro("Mooca")
                .senha(passwordEncoder.encode("SenhaForte@123"))
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build());

        gestor = cidadaoRepository.save(Cidadao.builder()
                .nome("Gestor Oficial")
                .email("gestor.oficial@alertacom.com")
                .telefone("(11) 99999-0003")
                .bairro("Sé")
                .senha(passwordEncoder.encode("SenhaForte@123"))
                .role("ROLE_GESTOR")
                .ativo(true)
                .build());
        tokenGestor = tokenService.gerarToken(gestor);
    }

    // =========================================================================
    // ATAQUE 1: Escalada de Privilégio (F1.1)
    // =========================================================================
    @Nested
    @DisplayName("Ataque 1 — Escalada de Privilégio em POST /api/cidadaos")
    class PrivilegeEscalationAttacks {

        @Test
        @DisplayName("1.1 Atacante anônimo tenta cadastrar ROLE_GESTOR -> Persiste estritamente ROLE_CIDADAO")
        void atacanteAnonimoTentaCadastrarGestor() throws Exception {
            String payload = """
                {
                    "nome": "Atacante Anonimo",
                    "email": "hacker.anonimo@alertacom.com",
                    "telefone": "(11) 97777-1111",
                    "bairro": "Centro",
                    "senha": "SenhaForte@123",
                    "role": "ROLE_GESTOR"
                }
                """;

            mockMvc.perform(post("/api/cidadaos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role", is("ROLE_CIDADAO")));

            Optional<Cidadao> persistido = cidadaoRepository.findByEmail("hacker.anonimo@alertacom.com");
            assertThat(persistido).isPresent();
            assertThat(persistido.get().getRole()).isEqualTo("ROLE_CIDADAO");
        }

        @Test
        @DisplayName("1.2 Munícipe comum autenticado tenta cadastrar outro com ROLE_GESTOR -> Forçado a ROLE_CIDADAO")
        void municipeComumTentaCadastrarGestor() throws Exception {
            String payload = """
                {
                    "nome": "Tentativa Munícipe",
                    "email": "tentativa.municipe@alertacom.com",
                    "telefone": "(11) 97777-2222",
                    "bairro": "Vila Mariana",
                    "senha": "SenhaForte@123",
                    "role": "ROLE_GESTOR"
                }
                """;

            mockMvc.perform(post("/api/cidadaos")
                            .header("Authorization", "Bearer " + tokenCidadaoA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role", is("ROLE_CIDADAO")));

            Optional<Cidadao> persistido = cidadaoRepository.findByEmail("tentativa.municipe@alertacom.com");
            assertThat(persistido).isPresent();
            assertThat(persistido.get().getRole()).isEqualTo("ROLE_CIDADAO");
        }

        @Test
        @DisplayName("1.3 Atacante tenta enviar roles arbitrárias (ADMIN, ROOT) -> Rejeitado na validação com 400 Bad Request")
        void atacanteTentaRolesArbitrariasRejeitadoNaValidacao() throws Exception {
            String[] rolesArbitrarias = {"ADMIN", "ROOT", "SUPERUSER", "role_gestor", "GESTOR"};

            for (int i = 0; i < rolesArbitrarias.length; i++) {
                String email = "arbitrario" + i + "@alertacom.com";
                String payload = String.format("""
                    {
                        "nome": "Adversario %d",
                        "email": "%s",
                        "telefone": "(11) 97777-333%d",
                        "bairro": "Bela Vista",
                        "senha": "SenhaForte@123",
                        "role": "%s"
                    }
                    """, i, email, i, rolesArbitrarias[i]);

                mockMvc.perform(post("/api/cidadaos")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status", is(400)))
                        .andExpect(jsonPath("$.invalidParams[0].field", is("role")));

                assertThat(cidadaoRepository.findByEmail(email)).isEmpty();
            }
        }

        @Test
        @DisplayName("1.4 Gestor autenticado cadastra gestor -> Permitido com ROLE_GESTOR")
        void gestorAutenticadoPodeCadastrarGestor() throws Exception {
            String payload = """
                {
                    "nome": "Novo Gestor Autorizado",
                    "email": "novo.gestor@alertacom.com",
                    "telefone": "(11) 99999-4444",
                    "bairro": "Sé",
                    "senha": "SenhaForte@123",
                    "role": "ROLE_GESTOR"
                }
                """;

            mockMvc.perform(post("/api/cidadaos")
                            .header("Authorization", "Bearer " + tokenGestor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role", is("ROLE_GESTOR")));

            Optional<Cidadao> persistido = cidadaoRepository.findByEmail("novo.gestor@alertacom.com");
            assertThat(persistido).isPresent();
            assertThat(persistido.get().getRole()).isEqualTo("ROLE_GESTOR");
        }
    }

    // =========================================================================
    // ATAQUE 2: Token Fantasma / Backdoor no SecurityFilter (F1.2)
    // =========================================================================
    @Nested
    @DisplayName("Ataque 2 — Token Fantasma & Tentativa de Reativação de Backdoor")
    class GhostTokenAttacks {

        @Test
        @DisplayName("2.1 Token assinado com chave real porém email inexistente -> Rejeitado com 401 (sem criação em memória)")
        void tokenFantasmaDeveRetornar401EmMultiplasRotas() throws Exception {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            String emailFantasma = "fantasma@inexistente.com";
            String tokenFantasma = JWT.create()
                    .withIssuer("alertacom-api")
                    .withSubject(emailFantasma)
                    .withClaim("role", "ROLE_GESTOR")
                    .withClaim("id", 999999L)
                    .withExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                    .sign(algorithm);

            // Tenta rota restrita a Gestor (GET /api/cidadaos)
            mockMvc.perform(get("/api/cidadaos")
                            .header("Authorization", "Bearer " + tokenFantasma))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)));

            // Tenta rota restrita a Cidadão/Gestor (GET /api/ocorrencias/minhas)
            mockMvc.perform(get("/api/ocorrencias/minhas")
                            .header("Authorization", "Bearer " + tokenFantasma))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)));

            // Tenta emissão de protocolo oficial (POST /api/protocolos)
            mockMvc.perform(post("/api/protocolos")
                            .header("Authorization", "Bearer " + tokenFantasma)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)));

            // Verifica banco relacional: usuário fantasma NUNCA foi inserido
            assertThat(cidadaoRepository.findByEmail(emailFantasma)).isEmpty();
        }
    }

    // =========================================================================
    // ATAQUE 3: Usuário Inativo (F1.2)
    // =========================================================================
    @Nested
    @DisplayName("Ataque 3 — Usuário Desativado no Banco de Dados")
    class InactiveUserAttacks {

        @Test
        @DisplayName("3.1 Usuário inativo no banco tenta acessar rota com JWT válido -> Rejeitado com 401")
        void usuarioInativoRejeitadoCom401() throws Exception {
            Cidadao inativo = cidadaoRepository.save(Cidadao.builder()
                    .nome("Munícipe Bloqueado")
                    .email("bloqueado@alertacom.com")
                    .telefone("(11) 93333-0003")
                    .bairro("Mooca")
                    .senha(passwordEncoder.encode("SenhaForte@123"))
                    .role("ROLE_CIDADAO")
                    .ativo(false)
                    .build());

            String tokenInativo = tokenService.gerarToken(inativo);

            mockMvc.perform(get("/api/ocorrencias/minhas")
                            .header("Authorization", "Bearer " + tokenInativo))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)));

            mockMvc.perform(get("/api/cidadaos/" + inativo.getId())
                            .header("Authorization", "Bearer " + tokenInativo))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)));
        }

        @Test
        @DisplayName("3.2 Revogação dinâmica de sessão: Usuário ativo tem conta desativada -> Próxima requisição falha com 401")
        void desativacaoDinamicaRevogaAcessoImediatamente() throws Exception {
            // Inicialmente ativo
            Cidadao temporario = cidadaoRepository.save(Cidadao.builder()
                    .nome("Munícipe Temporário")
                    .email("temporario@alertacom.com")
                    .telefone("(11) 94444-0004")
                    .bairro("Lapa")
                    .senha(passwordEncoder.encode("SenhaForte@123"))
                    .role("ROLE_CIDADAO")
                    .ativo(true)
                    .build());

            String token = tokenService.gerarToken(temporario);

            // Requisição 1: Sucesso (200 OK)
            mockMvc.perform(get("/api/cidadaos/" + temporario.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // Acontece a desativação administrativa no banco
            temporario.setAtivo(false);
            cidadaoRepository.save(temporario);

            // Requisição 2 com o mesmo token: Deve falhar imediatamente com 401 Unauthorized
            mockMvc.perform(get("/api/cidadaos/" + temporario.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)));
        }
    }

    // =========================================================================
    // ATAQUE 4: IDOR em Cidadão e Ocorrência (F1.4 & F1.5)
    // =========================================================================
    @Nested
    @DisplayName("Ataque 4 — Exploração de IDOR (Insecure Direct Object Reference)")
    class IdorAttacks {

        @Test
        @DisplayName("4.1 IDOR Cidadão: Munícipe Alfa tenta visualizar e alterar Munícipe Beta -> Rejeitado com 403 Forbidden")
        void idorCidadaoRejeitadoCom403() throws Exception {
            // Tentativa de espionagem (GET perfil de terceiro)
            mockMvc.perform(get("/api/cidadaos/" + cidadaoB.getId())
                            .header("Authorization", "Bearer " + tokenCidadaoA))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)));

            // Tentativa de sequestro cadastral (PUT perfil de terceiro)
            String payloadAlteracao = """
                {
                    "nome": "Beta Modificado pelo Alfa",
                    "telefone": "(11) 99999-9999",
                    "bairro": "Centro"
                }
                """;

            mockMvc.perform(put("/api/cidadaos/" + cidadaoB.getId())
                            .header("Authorization", "Bearer " + tokenCidadaoA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadAlteracao))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)));

            // Garante que os dados de Beta não foram corrompidos
            Cidadao betaAtual = cidadaoRepository.findById(cidadaoB.getId()).orElseThrow();
            assertThat(betaAtual.getNome()).isEqualTo("Munícipe Beta");
        }

        @Test
        @DisplayName("4.2 IDOR Ocorrência: Munícipe Alfa cria ocorrência com cidadaoId apontando para Beta -> Ignora e vincula a Alfa")
        void idorOcorrenciaVinculaAoAutenticado() throws Exception {
            String payload = String.format("""
                {
                    "titulo": "Queda de Árvore na Via",
                    "descricao": "Galho grande obstruindo faixa direita",
                    "categoria": "QUEDA_ARVORE",
                    "severidade": "BAIXA",
                    "latitude": -23.5610,
                    "longitude": -46.6550,
                    "endereco": "Av Paulista, 1000",
                    "bairro": "Bela Vista",
                    "cidadaoId": %d
                }
                """, cidadaoB.getId());

            MvcResult result = mockMvc.perform(post("/api/ocorrencias")
                            .header("Authorization", "Bearer " + tokenCidadaoA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cidadao.id", is(cidadaoA.getId().intValue())))
                    .andExpect(jsonPath("$.cidadao.email", is("alfa@alertacom.com")))
                    .andReturn();

            // Extrai ID da ocorrência persistida e confirma no banco relacional
            String location = result.getResponse().getHeader("Location");
            Long idOcorrencia = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

            Ocorrencia ocorrenciaNoBanco = ocorrenciaRepository.findById(idOcorrencia).orElseThrow();
            assertThat(ocorrenciaNoBanco.getCidadao().getId()).isEqualTo(cidadaoA.getId());
            assertThat(ocorrenciaNoBanco.getCidadao().getEmail()).isEqualTo("alfa@alertacom.com");
        }

        @Test
        @DisplayName("4.3 Gestor pode criar ocorrência em nome de terceiros (Atendimento 199 Defesa Civil)")
        void gestorPodeCriarOcorrenciaParaTerceiro() throws Exception {
            String payload = String.format("""
                {
                    "titulo": "Chamado 199 - Deslizamento",
                    "descricao": "Morador acionou via telefone emergencial",
                    "categoria": "DESLIZAMENTO",
                    "severidade": "ALTA",
                    "latitude": -23.5700,
                    "longitude": -46.6600,
                    "endereco": "Rua das Encostas, 50",
                    "bairro": "Vila Prudente",
                    "cidadaoId": %d
                }
                """, cidadaoB.getId());

            mockMvc.perform(post("/api/ocorrencias")
                            .header("Authorization", "Bearer " + tokenGestor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cidadao.id", is(cidadaoB.getId().intValue())))
                    .andExpect(jsonPath("$.cidadao.email", is("beta@alertacom.com")));
        }
    }

    // =========================================================================
    // ATAQUE 5: Adulteração Criptográfica & Boundary Cases de Cabeçalho (F1.3)
    // =========================================================================
    @Nested
    @DisplayName("Ataque 5 — Adulteração Criptográfica e Headers Malformados")
    class CryptoAndHeaderBoundaryAttacks {

        @Test
        @DisplayName("5.1 Token assinado com chave incorreta -> 401 Unauthorized")
        void tokenComChaveIncorretaRejeitado() throws Exception {
            Algorithm wrongAlgorithm = Algorithm.HMAC256("ChaveFalsaTotalmenteDiferente1234567890123");
            String tokenFalso = JWT.create()
                    .withIssuer("alertacom-api")
                    .withSubject(cidadaoA.getEmail())
                    .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .sign(wrongAlgorithm);

            mockMvc.perform(get("/api/ocorrencias/minhas")
                            .header("Authorization", "Bearer " + tokenFalso))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)));
        }

        @Test
        @DisplayName("5.2 Cabeçalhos malformados e tokens corrompidos -> 401 Unauthorized")
        void headersMalformadosRejeitados() throws Exception {
            String[] headersMaliciosos = {
                    "Bearer ",
                    "Bearer",
                    "Bearer null",
                    "Bearer undefined",
                    "Bearer   ",
                    "Basic dXNlcjpzZW5oYQ==",
                    "Bearer token.invalido.corrompido",
                    "Bearer " + tokenCidadaoA + "PADDING_EXTRA"
            };

            for (String header : headersMaliciosos) {
                mockMvc.perform(get("/api/ocorrencias/minhas")
                                .header("Authorization", header))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.status", is(401)));
            }
        }
    }

    // =========================================================================
    // ATAQUE 6: Fail-Fast de Entropia em Ambientes Restritos (F1.3)
    // =========================================================================
    @Nested
    @DisplayName("Ataque 6 — Validação de Entropia e Fail-Fast no TokenService")
    class EntropyAndFailFastAttacks {

        @Test
        @DisplayName("6.1 Profile 'prod' ou 'docker' com chave default ou fraca -> Lança IllegalStateException")
        void profileProducaoRejeitaChaveFracaOuDefault() {
            MockEnvironment envProd = new MockEnvironment();
            envProd.setActiveProfiles("prod");

            // 1. Chave nula
            assertThatThrownBy(() -> new TokenService(null, envProd))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET é obrigatória");

            // 2. Chave vazia
            assertThatThrownBy(() -> new TokenService("   ", envProd))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET é obrigatória");

            // 3. Chave fraca (< 32 chars)
            assertThatThrownBy(() -> new TokenService("ChaveCurta123", envProd))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("entropia insuficiente");

            // 4. Chave default em produção
            assertThatThrownBy(() -> new TokenService(TokenService.SEGREDO_PADRAO_DEV, envProd))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("segredo padrão de desenvolvimento não pode ser utilizado");

            // 5. Chave forte com ≥ 32 chars em produção -> SUCESSO
            TokenService tokenServiceProd = new TokenService("ChaveForteComMaisDeTrintaEDoisCaracteresHex2026!", envProd);
            assertThat(tokenServiceProd).isNotNull();
        }
    }
}
