package br.com.fiap.alertacom.config;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestorSeedInitializerTest {

    @Mock
    private CidadaoRepository cidadaoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private GestorSeedInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new GestorSeedInitializer(cidadaoRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "gestorEmail", "gestor@alertacom.com.br");
        ReflectionTestUtils.setField(initializer, "gestorNome", "Gestor Defesa Civil");
        ReflectionTestUtils.setField(initializer, "gestorSenha", "Gestor@2026!");
        ReflectionTestUtils.setField(initializer, "gestorTelefone", "(11) 99999-0000");
        ReflectionTestUtils.setField(initializer, "gestorBairro", "Centro");
    }

    @Test
    @DisplayName("Deve provisionar gestor padrão com ROLE_GESTOR e senha encriptada quando não existir no banco")
    void deveCadastrarGestorPadraoQuandoNaoExistir() {
        when(cidadaoRepository.existsByEmail("gestor@alertacom.com.br")).thenReturn(false);
        when(passwordEncoder.encode("Gestor@2026!")).thenReturn("$2a$10$encodedSecureHash");

        initializer.run();

        ArgumentCaptor<Cidadao> captor = ArgumentCaptor.forClass(Cidadao.class);
        verify(cidadaoRepository, times(1)).save(captor.capture());

        Cidadao gestorSalvo = captor.getValue();
        assertThat(gestorSalvo.getEmail()).isEqualTo("gestor@alertacom.com.br");
        assertThat(gestorSalvo.getNome()).isEqualTo("Gestor Defesa Civil");
        assertThat(gestorSalvo.getRole()).isEqualTo("ROLE_GESTOR");
        assertThat(gestorSalvo.getSenha()).isEqualTo("$2a$10$encodedSecureHash");
        assertThat(gestorSalvo.getAtivo()).isTrue();
        assertThat(gestorSalvo.getBairro()).isEqualTo("Centro");
        assertThat(gestorSalvo.getTelefone()).isEqualTo("(11) 99999-0000");
    }

    @Test
    @DisplayName("Não deve recadastrar gestor padrão quando e-mail já estiver presente no banco (idempotência)")
    void naoDeveCadastrarGestorQuandoJaExistir() {
        when(cidadaoRepository.existsByEmail("gestor@alertacom.com.br")).thenReturn(true);

        initializer.run();

        verify(cidadaoRepository, never()).save(any(Cidadao.class));
        verify(passwordEncoder, never()).encode(any());
    }
}
