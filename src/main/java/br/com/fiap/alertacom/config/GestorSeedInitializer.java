package br.com.fiap.alertacom.config;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Inicializador idempotente de seed para provisionamento do gestor municipal inicial.
 * Executa no startup da aplicação e garante a presença de um gestor ativo com ROLE_GESTOR.
 */
@Component
@Order(1)
public class GestorSeedInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GestorSeedInitializer.class);

    private final CidadaoRepository cidadaoRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${alertacom.gestor.default.email:gestor@alertacom.com.br}")
    private String gestorEmail;

    @Value("${alertacom.gestor.default.nome:Gestor Defesa Civil}")
    private String gestorNome;

    @Value("${alertacom.gestor.default.senha:Gestor@2026!}")
    private String gestorSenha;

    @Value("${alertacom.gestor.default.telefone:(11) 99999-0000}")
    private String gestorTelefone;

    @Value("${alertacom.gestor.default.bairro:Centro}")
    private String gestorBairro;

    public GestorSeedInitializer(CidadaoRepository cidadaoRepository, PasswordEncoder passwordEncoder) {
        this.cidadaoRepository = cidadaoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!cidadaoRepository.existsByEmail(gestorEmail)) {
            log.info("Provisionando gestor municipal padrão: {}", gestorEmail);
            Cidadao gestor = Cidadao.builder()
                    .nome(gestorNome)
                    .email(gestorEmail)
                    .telefone(gestorTelefone)
                    .bairro(gestorBairro)
                    .senha(passwordEncoder.encode(gestorSenha))
                    .role("ROLE_GESTOR")
                    .ativo(Boolean.TRUE)
                    .dataCadastro(LocalDateTime.now())
                    .build();
            cidadaoRepository.save(gestor);
            log.info("Gestor municipal provisionado com sucesso: {}", gestorEmail);
        } else {
            log.info("Gestor municipal '{}' já cadastrado. Seed ignorado com sucesso.", gestorEmail);
        }
    }
}
