package br.com.fiap.alertacom.security;

import br.com.fiap.alertacom.domain.model.Cidadao;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/**
 * Serviço responsável pela emissão, validação criptográfica HMAC256
 * e extração de claims de tokens JWT no AlertaCom.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    public static final String ISSUER = "alertacom-api";
    public static final long EXPIRACAO_PADRAO_MILLIS = 86400000L; // 24 horas
    public static final int TAMANHO_MINIMO_SEGREDO = 32; // 256 bits para HMAC256
    public static final String SEGREDO_PADRAO_DEV = "AlertaComSuperChaveSecretaGlobalSolutionFIAP2026";

    @Value("${jwt.secret:${api.security.token.secret:}}")
    private String secret;

    @Autowired(required = false)
    private Environment environment;

    public TokenService() {
    }

    public TokenService(String secret) {
        this.secret = secret;
        validarComprimentoSegredo(this.secret);
    }

    public TokenService(String secret, Environment environment) {
        this.secret = secret;
        this.environment = environment;
        validarConfiguracaoSegredo();
    }

    @PostConstruct
    public void validarConfiguracaoSegredo() {
        boolean isAmbienteProducaoOuDocker = verificarSeAmbienteProducaoOuDocker();

        if (this.secret == null || this.secret.isBlank()) {
            if (isAmbienteProducaoOuDocker) {
                throw new IllegalStateException(
                        "Falha crítica de segurança: A variável de ambiente JWT_SECRET é obrigatória nos perfis 'docker'/'prod' e não foi definida.");
            }
            log.warn("Variável JWT_SECRET não informada. Utilizando segredo padrão para ambiente de desenvolvimento/teste.");
            this.secret = SEGREDO_PADRAO_DEV;
        }

        validarComprimentoSegredo(this.secret);

        if (isAmbienteProducaoOuDocker && SEGREDO_PADRAO_DEV.equals(this.secret.trim())) {
            throw new IllegalStateException(
                    "Falha crítica de segurança: O segredo padrão de desenvolvimento não pode ser utilizado nos perfis 'docker'/'prod'. Configure uma chave única via JWT_SECRET.");
        }
    }

    private void validarComprimentoSegredo(String chave) {
        if (chave == null || chave.trim().length() < TAMANHO_MINIMO_SEGREDO) {
            int compAtual = (chave == null) ? 0 : chave.trim().length();
            throw new IllegalStateException(
                    String.format("Falha crítica de segurança: O segredo JWT possui entropia insuficiente (mínimo %d caracteres / 256 bits). Comprimento atual: %d",
                            TAMANHO_MINIMO_SEGREDO, compAtual));
        }
    }

    private boolean verificarSeAmbienteProducaoOuDocker() {
        if (this.environment == null) {
            return false;
        }
        List<String> perfisRestritos = List.of("docker", "prod", "production");
        List<String> perfisAtivos = Arrays.asList(this.environment.getActiveProfiles());
        for (String perfilRestrito : perfisRestritos) {
            if (perfisAtivos.contains(perfilRestrito)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Emite um novo token JWT com assinatura HMAC256 e claims de identificação e perfil.
     */
    public String gerarToken(Cidadao cidadao) {
        if (cidadao == null) {
            throw new IllegalArgumentException("Cidadão não pode ser nulo para geração de token");
        }
        validarComprimentoSegredo(this.secret);
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(cidadao.getEmail())
                    .withClaim("id", cidadao.getId() != null ? cidadao.getId() : 1L)
                    .withClaim("nome", cidadao.getNome() != null ? cidadao.getNome() : "")
                    .withClaim("role", cidadao.getRole() != null ? cidadao.getRole() : "ROLE_CIDADAO")
                    .withClaim("bairro", cidadao.getBairro() != null ? cidadao.getBairro() : "")
                    .withIssuedAt(Instant.now())
                    .withExpiresAt(getExpiracaoInstant())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            log.error("Falha criptográfica ao assinar token JWT: {}", exception.getMessage());
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    /**
     * Valida o token JWT contra a assinatura HMAC256 e emissor esperado.
     * Retorna o subject (e-mail) se válido, ou null caso seja inválido, adulterado ou expirado.
     */
    public String validarToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            DecodedJWT decodedJWT = verificarToken(token);
            return decodedJWT.getSubject();
        } catch (JWTVerificationException exception) {
            log.debug("Token JWT inválido ou expirado: {}", exception.getMessage());
            return null;
        } catch (Exception exception) {
            log.debug("Erro inesperado na validação do token JWT: {}", exception.getMessage());
            return null;
        }
    }

    /**
     * Executa a verificação estrita do token lançando JWTVerificationException em caso de falha.
     */
    public DecodedJWT verificarToken(String token) throws JWTVerificationException {
        if (token == null || token.isBlank()) {
            throw new JWTVerificationException("Token JWT ausente ou em branco");
        }
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
        return verifier.verify(token);
    }

    /**
     * Decodifica a estrutura do token sem verificação criptográfica (para leitura de claims).
     */
    public DecodedJWT decodificarToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return JWT.decode(token);
        } catch (Exception e) {
            log.debug("Não foi possível decodificar o token: {}", e.getMessage());
            return null;
        }
    }

    public Long extrairId(String token) {
        DecodedJWT jwt = decodificarToken(token);
        if (jwt == null || jwt.getClaim("id").isNull()) {
            return null;
        }
        return jwt.getClaim("id").asLong();
    }

    public String extrairRole(String token) {
        DecodedJWT jwt = decodificarToken(token);
        if (jwt == null || jwt.getClaim("role").isNull()) {
            return null;
        }
        return jwt.getClaim("role").asString();
    }

    public String extrairNome(String token) {
        DecodedJWT jwt = decodificarToken(token);
        if (jwt == null || jwt.getClaim("nome").isNull()) {
            return null;
        }
        return jwt.getClaim("nome").asString();
    }

    public String extrairBairro(String token) {
        DecodedJWT jwt = decodificarToken(token);
        if (jwt == null || jwt.getClaim("bairro").isNull()) {
            return null;
        }
        return jwt.getClaim("bairro").asString();
    }

    public Instant getExpiracaoInstant() {
        return Instant.now().plus(24, ChronoUnit.HOURS);
    }

    public Long getExpiracaoMillis() {
        return EXPIRACAO_PADRAO_MILLIS;
    }

    public String getIssuer() {
        return ISSUER;
    }
}
