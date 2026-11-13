package br.com.fiap.alertacom.service;

import br.com.fiap.alertacom.domain.model.*;
import br.com.fiap.alertacom.domain.repository.CidadaoRepository;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.dto.request.AtualizarStatusRequest;
import br.com.fiap.alertacom.dto.request.OcorrenciaRequest;
import br.com.fiap.alertacom.dto.response.OcorrenciaResponse;
import br.com.fiap.alertacom.event.AlertaCriticoEvent;
import br.com.fiap.alertacom.exception.RegraNegocioException;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcorrenciaServiceTest {

    @Mock
    private OcorrenciaRepository ocorrenciaRepository;

    @Mock
    private CidadaoRepository cidadaoRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AlertaPublisherService alertaPublisherService;

    @InjectMocks
    private OcorrenciaService ocorrenciaService;

    @Test
    @DisplayName("Deve criar ocorrência com status inicial ABERTA sem publicar evento para severidade MEDIA")
    void deveCriarOcorrenciaSeveridadeMediaSemEventoAmqp() {
        OcorrenciaRequest request = new OcorrenciaRequest(
                "Bueiro entupido na esquina",
                "Acúmulo de lixo impedindo o escoamento de água pluvial",
                CategoriaOcorrencia.BUEIRO_OBSTRUIDO,
                SeveridadeOcorrencia.MEDIA,
                -23.55052,
                -46.63330,
                "Rua da Mooca, 100",
                "Mooca",
                1L
        );

        Cidadao cidadao = Cidadao.builder().id(1L).nome("João").email("joao@email.com").build();
        when(cidadaoRepository.findById(1L)).thenReturn(Optional.of(cidadao));

        Ocorrencia salva = Ocorrencia.builder()
                .id(100L)
                .titulo(request.titulo())
                .descricao(request.descricao())
                .categoria(request.categoria())
                .severidade(request.severidade())
                .status(StatusOcorrencia.ABERTA)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .endereco(request.endereco())
                .bairro(request.bairro())
                .cidadao(cidadao)
                .dataCriacao(LocalDateTime.now())
                .build();

        when(ocorrenciaRepository.save(any(Ocorrencia.class))).thenReturn(salva);

        OcorrenciaResponse response = ocorrenciaService.criar(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(StatusOcorrencia.ABERTA);
        assertThat(response.tipoRisco()).isEqualTo("BUEIRO_OBSTRUIDO");

        verify(cidadaoRepository, times(1)).findById(1L);
        verify(ocorrenciaRepository, times(1)).save(any(Ocorrencia.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Deve publicar evento AMQP resiliente ao registrar ocorrência com severidade ALTA ou CRITICA")
    void devePublicarEventoParaOcorrenciaCritica() {
        OcorrenciaRequest request = new OcorrenciaRequest(
                "Deslizamento de encosta iminente",
                "Trincas profundas no talude próximo às residências",
                CategoriaOcorrencia.DESLIZAMENTO,
                SeveridadeOcorrencia.CRITICA,
                -23.55052,
                -46.63330,
                "Rua Alta, 500",
                "Vila Prudente",
                2L
        );

        Cidadao cidadao = Cidadao.builder().id(2L).nome("Maria").email("maria@email.com").build();
        when(cidadaoRepository.findById(2L)).thenReturn(Optional.of(cidadao));

        Ocorrencia salva = Ocorrencia.builder()
                .id(200L)
                .titulo(request.titulo())
                .descricao(request.descricao())
                .categoria(request.categoria())
                .severidade(request.severidade())
                .status(StatusOcorrencia.ABERTA)
                .bairro("Vila Prudente")
                .cidadao(cidadao)
                .dataCriacao(LocalDateTime.now())
                .build();

        AlertaCriticoEvent eventoMock = new AlertaCriticoEvent(
                200L,
                request.titulo(),
                "Vila Prudente",
                "CRITICA",
                "DESLIZAMENTO",
                request.descricao(),
                "Maria",
                null,
                null,
                LocalDateTime.now()
        );

        when(ocorrenciaRepository.save(any(Ocorrencia.class))).thenReturn(salva);
        when(alertaPublisherService.construirEvento(salva)).thenReturn(eventoMock);

        OcorrenciaResponse response = ocorrenciaService.criar(request);

        assertThat(response).isNotNull();
        assertThat(response.severidade()).isEqualTo(SeveridadeOcorrencia.CRITICA);

        verify(eventPublisher, times(1)).publishEvent(eventoMock);
    }

    @Test
    @DisplayName("Deve persistir ocorrência com sucesso mesmo se o broker RabbitMQ estiver offline")
    void deveManterPersistenciaQuandoBrokerEstiverIndisponivel() {
        OcorrenciaRequest request = new OcorrenciaRequest(
                "Alagamento de grande proporção",
                "Água atingindo 1 metro de altura",
                CategoriaOcorrencia.ALAGAMENTO,
                SeveridadeOcorrencia.ALTA,
                -23.55052,
                -46.63330,
                "Av dos Estados, 1500",
                "Ipiranga",
                3L
        );

        Cidadao cidadao = Cidadao.builder().id(3L).nome("Pedro").build();
        when(cidadaoRepository.findById(3L)).thenReturn(Optional.of(cidadao));

        Ocorrencia salva = Ocorrencia.builder()
                .id(300L)
                .titulo(request.titulo())
                .descricao(request.descricao())
                .categoria(request.categoria())
                .severidade(request.severidade())
                .status(StatusOcorrencia.ABERTA)
                .bairro("Ipiranga")
                .cidadao(cidadao)
                .dataCriacao(LocalDateTime.now())
                .build();

        when(ocorrenciaRepository.save(any(Ocorrencia.class))).thenReturn(salva);
        when(alertaPublisherService.construirEvento(salva)).thenReturn(new AlertaCriticoEvent(
                300L, request.titulo(), "Ipiranga", "ALTA", "ALAGAMENTO", request.descricao(), "Pedro", null, null, null
        ));

        OcorrenciaResponse response = ocorrenciaService.criar(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(300L);
        verify(ocorrenciaRepository, times(1)).save(any(Ocorrencia.class));
        verify(eventPublisher, times(1)).publishEvent(any(AlertaCriticoEvent.class));
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar criar ocorrência com cidadão inexistente")
    void deveLancarResourceNotFoundExceptionQuandoCidadaoInexistente() {
        OcorrenciaRequest request = new OcorrenciaRequest(
                "Título Qualquer",
                "Descrição válida do problema",
                CategoriaOcorrencia.OUTROS,
                SeveridadeOcorrencia.BAIXA,
                null,
                null,
                "Rua X",
                "Centro",
                999L
        );

        when(cidadaoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ocorrenciaService.criar(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cidadão solicitante não encontrado");

        verify(ocorrenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve listar ocorrências com filtros dinâmicos e paginação")
    void deveListarComFiltrosPaginados() {
        Pageable pageable = PageRequest.of(0, 10);
        Ocorrencia oc1 = Ocorrencia.builder().id(1L).titulo("Oc 1").bairro("Mooca").build();

        when(ocorrenciaRepository.findWithFilters(eq("Mooca"), eq(StatusOcorrencia.ABERTA), eq(SeveridadeOcorrencia.ALTA), eq(CategoriaOcorrencia.ALAGAMENTO), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(oc1), pageable, 1));

        Page<OcorrenciaResponse> resultado = ocorrenciaService.listar(
                pageable,
                "Mooca",
                SeveridadeOcorrencia.ALTA,
                StatusOcorrencia.ABERTA,
                CategoriaOcorrencia.ALAGAMENTO
        );

        assertThat(resultado).isNotNull();
        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).titulo()).isEqualTo("Oc 1");
    }

    @Test
    @DisplayName("Deve buscar ocorrência por ID com detalhes carregados")
    void deveBuscarPorIdComSucesso() {
        Ocorrencia oc = Ocorrencia.builder()
                .id(50L)
                .titulo("Ocorrência 50")
                .status(StatusOcorrencia.ABERTA)
                .build();

        when(ocorrenciaRepository.findByIdWithDetails(50L)).thenReturn(Optional.of(oc));

        OcorrenciaResponse response = ocorrenciaService.buscarPorId(50L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(50L);
        verify(ocorrenciaRepository, times(1)).findByIdWithDetails(50L);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao buscar ocorrência inexistente")
    void deveLancarResourceNotFoundExceptionAoBuscarInexistente() {
        when(ocorrenciaRepository.findByIdWithDetails(888L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ocorrenciaService.buscarPorId(888L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ocorrência não encontrada");
    }

    @Test
    @DisplayName("Deve transicionar status válido da ocorrência (ABERTA -> EM_ANALISE)")
    void deveTransicionarStatusValido() {
        Ocorrencia ocorrencia = Ocorrencia.builder()
                .id(10L)
                .status(StatusOcorrencia.ABERTA)
                .build();

        AtualizarStatusRequest request = new AtualizarStatusRequest(StatusOcorrencia.EM_ANALISE, "Iniciando análise técnica");

        when(ocorrenciaRepository.findById(10L)).thenReturn(Optional.of(ocorrencia));
        when(ocorrenciaRepository.save(any(Ocorrencia.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OcorrenciaResponse response = ocorrenciaService.transicionarStatus(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(StatusOcorrencia.EM_ANALISE);
        verify(ocorrenciaRepository, times(1)).save(ocorrencia);
    }

    @Test
    @DisplayName("Deve lançar RegraNegocioException ao tentar transição inválida (RESOLVIDA -> ABERTA)")
    void deveLancarRegraNegocioExceptionEmTransicaoInvalida() {
        Ocorrencia ocorrencia = Ocorrencia.builder()
                .id(10L)
                .status(StatusOcorrencia.RESOLVIDA)
                .build();

        AtualizarStatusRequest request = new AtualizarStatusRequest(StatusOcorrencia.ABERTA, "Tentativa inválida de reabrir");

        when(ocorrenciaRepository.findById(10L)).thenReturn(Optional.of(ocorrencia));

        assertThatThrownBy(() -> ocorrenciaService.transicionarStatus(10L, request))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Transição de status inválida");

        verify(ocorrenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve cancelar ocorrência com sucesso quando em status ABERTA")
    void deveCancelarOcorrenciaAberta() {
        Ocorrencia ocorrencia = Ocorrencia.builder()
                .id(15L)
                .status(StatusOcorrencia.ABERTA)
                .build();

        when(ocorrenciaRepository.findById(15L)).thenReturn(Optional.of(ocorrencia));

        ocorrenciaService.cancelar(15L, "Duplicidade de chamado");

        assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.CANCELADA);
        verify(ocorrenciaRepository, times(1)).save(ocorrencia);
    }

    @Test
    @DisplayName("Deve lançar RegraNegocioException ao tentar cancelar ocorrência já RESOLVIDA")
    void deveLancarRegraNegocioExceptionAoCancelarOcorrenciaResolvida() {
        Ocorrencia ocorrencia = Ocorrencia.builder()
                .id(25L)
                .status(StatusOcorrencia.RESOLVIDA)
                .build();

        when(ocorrenciaRepository.findById(25L)).thenReturn(Optional.of(ocorrencia));

        assertThatThrownBy(() -> ocorrenciaService.cancelar(25L, "Tentativa de cancelamento"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não pode ser cancelada");

        verify(ocorrenciaRepository, never()).save(any());
    }
}
