package br.com.fiap.alertacom.security;

import br.com.fiap.alertacom.domain.model.Cidadao;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Testes Unitários do TokenService (Auth0 JWT HMAC256)")
class TokenServiceTest {

    private static final String SECRET_TESTE = "AlertaComSuperChaveSecretaGlobalSolutionFIAP2026";
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(SECRET_TESTE);
    }

    @Test
    @DisplayName("Deve gerar token JWT válido com HMAC256 e emissor alertacom-api")
    void deveGerarTokenComSucesso() {
        Cidadao cidadao = Cidadao.builder()
                .id(10L)
                .email("ana.souza@alertacom.com")
                .nome("Ana Souza")
                .bairro("Tatuapé")
                .role("ROLE_CIDADAO")
                .senha("hash123")
                .ativo(true)
                .build();

        String token = tokenService.gerarToken(cidadao);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);

        DecodedJWT decoded = JWT.decode(token);
        assertThat(decoded.getIssuer()).isEqualTo("alertacom-api");
        assertThat(decoded.getSubject()).isEqualTo("ana.souza@alertacom.com");
    }

    @Test
    @DisplayName("Deve incluir todas as claims obrigatórias no payload do JWT")
    void deveConterClaimsObrigatorias() {
        Cidadao cidadao = Cidadao.builder()
                .id(25L)
                .email("gestor.silva@alertacom.com")
                .nome("Carlos Silva")
                .bairro("Sé")
                .role("ROLE_GESTOR")
                .senha("hash123")
                .ativo(true)
                .build();

        String token = tokenService.gerarToken(cidadao);

        assertThat(tokenService.extrairId(token)).isEqualTo(25L);
        assertThat(tokenService.extrairNome(token)).isEqualTo("Carlos Silva");
        assertThat(tokenService.extrairRole(token)).isEqualTo("ROLE_GESTOR");
        assertThat(tokenService.extrairBairro(token)).isEqualTo("Sé");
    }

    @Test
    @DisplayName("Deve validar com sucesso e retornar o e-mail do subject")
    void deveValidarTokenValidoRetornandoSubject() {
        Cidadao cidadao = Cidadao.builder()
                .id(1L)
                .email("usuario@exemplo.com")
                .nome("Usuario Teste")
                .bairro("Centro")
                .role("ROLE_CIDADAO")
                .build();

        String token = tokenService.gerarToken(cidadao);
        String subject = tokenService.validarToken(token);

        assertThat(subject).isEqualTo("usuario@exemplo.com");
    }

    @Test
    @DisplayName("Deve retornar nulo para tokens nulos, vazios ou em branco")
    void deveRetornarNuloParaEntradasInvalidas() {
        assertThat(tokenService.validarToken(null)).isNull();
        assertThat(tokenService.validarToken("")).isNull();
        assertThat(tokenService.validarToken("   ")).isNull();
        assertThat(tokenService.extrairId(null)).isNull();
        assertThat(tokenService.extrairRole("")).isNull();
    }

    @Test
    @DisplayName("Deve rejeitar token expirado retornando nulo na validação suave e lançando TokenExpiredException na estrita")
    void deveRejeitarTokenExpirado() {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_TESTE);
        String tokenExpirado = JWT.create()
                .withIssuer("alertacom-api")
                .withSubject("expirado@alertacom.com")
                .withExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .sign(algorithm);

        assertThat(tokenService.validarToken(tokenExpirado)).isNull();
        assertThatThrownBy(() -> tokenService.verificarToken(tokenExpirado))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    @DisplayName("Deve rejeitar token com assinatura adulterada (tampered token)")
    void deveRejeitarTokenComAssinaturaAdulterada() {
        Cidadao cidadao = Cidadao.builder()
                .id(1L)
                .email("adulterado@alertacom.com")
                .role("ROLE_CIDADAO")
                .build();
        String tokenValido = tokenService.gerarToken(cidadao);
        int lastDot = tokenValido.lastIndexOf('.');
        String tokenAdulterado = tokenValido.substring(0, lastDot + 1) + "XXXXAssinaturaFalsa";

        assertThat(tokenService.validarToken(tokenAdulterado)).isNull();
        assertThatThrownBy(() -> tokenService.verificarToken(tokenAdulterado))
                .isInstanceOf(SignatureVerificationException.class);
    }

    @Test
    @DisplayName("Deve rejeitar token assinado com chave secreta incorreta")
    void deveRejeitarTokenAssinadoComChaveInvalida() {
        Algorithm algorithmFalso = Algorithm.HMAC256("ChaveInvalidaFalsificada999");
        String tokenFalso = JWT.create()
                .withIssuer("alertacom-api")
                .withSubject("hacker@malicioso.com")
                .withExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .sign(algorithmFalso);

        assertThat(tokenService.validarToken(tokenFalso)).isNull();
        assertThatThrownBy(() -> tokenService.verificarToken(tokenFalso))
                .isInstanceOf(SignatureVerificationException.class);
    }

    @Test
    @DisplayName("Deve calcular expiração padrão de exatamente 24 horas (86.400.000 ms)")
    void deveConfigurarExpiracaoCorreta() {
        Instant agora = Instant.now();
        Instant expiracao = tokenService.getExpiracaoInstant();

        long diferencaHoras = ChronoUnit.HOURS.between(agora, expiracao);
        assertThat(diferencaHoras).isEqualTo(24L);
        assertThat(tokenService.getExpiracaoMillis()).isEqualTo(86400000L);
    }

    @Test
    @DisplayName("Deve lançar IllegalArgumentException quando cidadão for nulo")
    void deveLancarExcecaoQuandoCidadaoNulo() {
        assertThatThrownBy(() -> tokenService.gerarToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
