package br.com.fiap.alertacom.security;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes Unitários do UserDetailsServiceImpl")
class UserDetailsServiceImplTest {

    @Mock
    private CidadaoRepository cidadaoRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @DisplayName("Deve carregar cidadão existente com suas authorities mapeadas")
    void deveCarregarUsuarioExistentePorEmail() {
        Cidadao cidadao = Cidadao.builder()
                .id(1L)
                .email("carlos@alertacom.com")
                .nome("Carlos Eduardo")
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build();

        when(cidadaoRepository.findByEmail("carlos@alertacom.com")).thenReturn(Optional.of(cidadao));

        UserDetails userDetails = userDetailsService.loadUserByUsername("carlos@alertacom.com");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("carlos@alertacom.com");
        assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_CIDADAO");
    }

    @Test
    @DisplayName("Deve lançar UsernameNotFoundException quando e-mail não existir na base de dados")
    void deveLancarUsernameNotFoundExceptionQuandoEmailNaoExistir() {
        when(cidadaoRepository.findByEmail("inexistente@alertacom.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("inexistente@alertacom.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("inexistente@alertacom.com");
    }

    @Test
    @DisplayName("Deve lançar UsernameNotFoundException quando e-mail for nulo ou vazio")
    void deveLancarUsernameNotFoundExceptionQuandoEmailNuloOuVazio() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(null))
                .isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("   "))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
