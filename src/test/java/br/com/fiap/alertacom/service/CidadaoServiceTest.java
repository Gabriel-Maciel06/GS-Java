package br.com.fiap.alertacom.service;

import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.dto.request.AtualizarCidadaoRequest;
import br.com.fiap.alertacom.dto.request.CidadaoRequest;
import br.com.fiap.alertacom.dto.response.CidadaoResponse;
import br.com.fiap.alertacom.exception.DuplicateResourceException;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CidadaoServiceTest {

    @Mock
    private CidadaoRepository cidadaoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CidadaoService cidadaoService;

    @Test
    @DisplayName("Deve cadastrar cidadão com sucesso criptografando a senha")
    void deveCadastrarCidadaoComSucesso() {
        CidadaoRequest request = new CidadaoRequest(
                "Maria Silva",
                "maria.silva@email.com",
                "(11) 98765-4321",
                "Mooca",
                "senhaForte123",
                "ROLE_CIDADAO"
        );

        when(cidadaoRepository.existsByEmail("maria.silva@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senhaForte123")).thenReturn("$2a$10$encodedHashMock");

        Cidadao cidadaoSalvo = Cidadao.builder()
                .id(1L)
                .nome("Maria Silva")
                .email("maria.silva@email.com")
                .telefone("(11) 98765-4321")
                .bairro("Mooca")
                .senha("$2a$10$encodedHashMock")
                .role("ROLE_CIDADAO")
                .ativo(Boolean.TRUE)
                .dataCadastro(LocalDateTime.now())
                .build();

        when(cidadaoRepository.save(any(Cidadao.class))).thenReturn(cidadaoSalvo);

        CidadaoResponse response = cidadaoService.cadastrar(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nome()).isEqualTo("Maria Silva");
        assertThat(response.email()).isEqualTo("maria.silva@email.com");
        assertThat(response.role()).isEqualTo("ROLE_CIDADAO");
        assertThat(response.ativo()).isTrue();

        verify(cidadaoRepository, times(1)).existsByEmail("maria.silva@email.com");
        verify(passwordEncoder, times(1)).encode("senhaForte123");
        verify(cidadaoRepository, times(1)).save(any(Cidadao.class));
    }

    @Test
    @DisplayName("Deve lançar DuplicateResourceException ao tentar cadastrar e-mail já existente")
    void deveLancarExcecaoAoCadastrarEmailDuplicado() {
        CidadaoRequest request = new CidadaoRequest(
                "Maria Silva",
                "maria.silva@email.com",
                "(11) 98765-4321",
                "Mooca",
                "senhaForte123",
                "ROLE_CIDADAO"
        );

        when(cidadaoRepository.existsByEmail("maria.silva@email.com")).thenReturn(true);

        assertThatThrownBy(() -> cidadaoService.cadastrar(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("E-mail já cadastrado");

        verify(cidadaoRepository, times(1)).existsByEmail("maria.silva@email.com");
        verify(passwordEncoder, never()).encode(anyString());
        verify(cidadaoRepository, never()).save(any(Cidadao.class));
    }

    @Test
    @DisplayName("Deve listar cidadãos paginados com mapeamento correto para DTO")
    void deveListarCidadaosPaginados() {
        Pageable pageable = PageRequest.of(0, 10);
        Cidadao c1 = Cidadao.builder().id(1L).nome("Cidadão 1").email("c1@email.com").bairro("Tatuapé").role("ROLE_CIDADAO").ativo(true).build();
        Cidadao c2 = Cidadao.builder().id(2L).nome("Cidadão 2").email("c2@email.com").bairro("Belém").role("ROLE_GESTOR").ativo(true).build();

        when(cidadaoRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(c1, c2), pageable, 2));

        Page<CidadaoResponse> page = cidadaoService.listar(pageable);

        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).nome()).isEqualTo("Cidadão 1");
        assertThat(page.getContent().get(1).nome()).isEqualTo("Cidadão 2");
        verify(cidadaoRepository, times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("Deve buscar cidadão por ID com sucesso")
    void deveBuscarPorIdComSucesso() {
        Cidadao cidadao = Cidadao.builder()
                .id(10L)
                .nome("Carlos Alberto")
                .email("carlos@email.com")
                .bairro("Pinheiros")
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build();

        when(cidadaoRepository.findById(10L)).thenReturn(Optional.of(cidadao));

        CidadaoResponse response = cidadaoService.buscarPorId(10L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.nome()).isEqualTo("Carlos Alberto");
        verify(cidadaoRepository, times(1)).findById(10L);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao buscar ID inexistente")
    void deveLancarResourceNotFoundExceptionAoBuscarPorIdInexistente() {
        when(cidadaoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cidadaoService.buscarPorId(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cidadão não encontrado");

        verify(cidadaoRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Deve atualizar dados cadastrais do cidadão")
    void deveAtualizarCidadaoComSucesso() {
        Cidadao existente = Cidadao.builder()
                .id(5L)
                .nome("Nome Antigo")
                .email("email@email.com")
                .telefone("(11) 91111-1111")
                .bairro("Bairro Antigo")
                .role("ROLE_CIDADAO")
                .ativo(true)
                .build();

        AtualizarCidadaoRequest request = new AtualizarCidadaoRequest(
                "Nome Novo",
                "(11) 92222-2222",
                "Bairro Novo"
        );

        when(cidadaoRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(cidadaoRepository.save(any(Cidadao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CidadaoResponse atualizado = cidadaoService.atualizar(5L, request);

        assertThat(atualizado).isNotNull();
        assertThat(atualizado.nome()).isEqualTo("Nome Novo");
        assertThat(atualizado.telefone()).isEqualTo("(11) 92222-2222");
        assertThat(atualizado.bairro()).isEqualTo("Bairro Novo");
        verify(cidadaoRepository, times(1)).findById(5L);
        verify(cidadaoRepository, times(1)).save(existente);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao atualizar cidadão inexistente")
    void deveLancarResourceNotFoundExceptionAoAtualizarInexistente() {
        AtualizarCidadaoRequest request = new AtualizarCidadaoRequest("Nome", "(11) 91111-1111", "Bairro");
        when(cidadaoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cidadaoService.atualizar(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cidadaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve desativar cidadão (soft-delete)")
    void deveDesativarCidadaoComSucesso() {
        Cidadao cidadao = Cidadao.builder()
                .id(7L)
                .nome("João")
                .email("joao@email.com")
                .bairro("Mooca")
                .ativo(true)
                .build();

        when(cidadaoRepository.findById(7L)).thenReturn(Optional.of(cidadao));

        cidadaoService.desativar(7L);

        assertThat(cidadao.getAtivo()).isFalse();
        verify(cidadaoRepository, times(1)).save(cidadao);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao desativar cidadão inexistente")
    void deveLancarResourceNotFoundExceptionAoDesativarInexistente() {
        when(cidadaoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cidadaoService.desativar(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cidadaoRepository, never()).save(any());
    }
}
