package br.com.fiap.alertacom.service;

import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.ProtocoloDefesaCivil;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.domain.repository.ProtocoloDefesaCivilRepository;
import br.com.fiap.alertacom.dto.request.ConcluirProtocoloRequest;
import br.com.fiap.alertacom.dto.request.ProtocoloRequest;
import br.com.fiap.alertacom.dto.response.ProtocoloResponse;
import br.com.fiap.alertacom.exception.RegraNegocioException;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtocoloServiceTest {

    @Mock
    private ProtocoloDefesaCivilRepository protocoloRepository;

    @Mock
    private OcorrenciaRepository ocorrenciaRepository;

    @InjectMocks
    private ProtocoloService protocoloService;

    @Test
    @DisplayName("Deve emitir protocolo oficial com número formatado e transicionar ocorrência para ENCAMINHADA_DEFESA_CIVIL")
    void deveEmitirProtocoloComSucesso() {
        Ocorrencia ocorrencia = Ocorrencia.builder()
                .id(10L)
                .status(StatusOcorrencia.ABERTA)
                .build();

        ProtocoloRequest request = new ProtocoloRequest(
                "Equipe Tática Bravo",
                "Despachando caminhão pipa e equipe de contenção",
                LocalDateTime.now().plusHours(4),
                10L
        );

        when(ocorrenciaRepository.findById(10L)).thenReturn(Optional.of(ocorrencia));
        when(protocoloRepository.existsByOcorrenciaId(10L)).thenReturn(false);
        when(protocoloRepository.count()).thenReturn(5L);
        when(protocoloRepository.existsByNumeroProtocolo(anyString())).thenReturn(false);

        when(protocoloRepository.save(any(ProtocoloDefesaCivil.class))).thenAnswer(invocation -> {
            ProtocoloDefesaCivil p = invocation.getArgument(0);
            return ProtocoloDefesaCivil.builder()
                    .id(1L)
                    .numeroProtocolo(p.getNumeroProtocolo())
                    .equipeResponsavel(p.getEquipeResponsavel())
                    .parecerTecnico(p.getParecerTecnico())
                    .dataEmissao(LocalDateTime.now())
                    .dataPrevisaoAtendimento(p.getDataPrevisaoAtendimento())
                    .ocorrencia(ocorrencia)
                    .build();
        });

        ProtocoloResponse response = protocoloService.emitir(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.numeroProtocolo()).matches("^DEF-\\d{4}-\\d{5}$");
        assertThat(response.equipeResponsavel()).isEqualTo("Equipe Tática Bravo");
        assertThat(ocorrencia.getStatus()).isEqualTo(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL);

        verify(protocoloRepository, times(1)).save(any(ProtocoloDefesaCivil.class));
    }

    @Test
    @DisplayName("Deve lançar RegraNegocioException ao tentar emitir protocolo duplicado para a mesma ocorrência")
    void deveLancarRegraNegocioExceptionParaOcorrenciaComProtocoloExistente() {
        Ocorrencia ocorrencia = Ocorrencia.builder().id(10L).build();

        ProtocoloRequest request = new ProtocoloRequest(
                "Equipe Alfa",
                "Parecer preliminar",
                LocalDateTime.now().plusDays(1),
                10L
        );

        when(ocorrenciaRepository.findById(10L)).thenReturn(Optional.of(ocorrencia));
        when(protocoloRepository.existsByOcorrenciaId(10L)).thenReturn(true);

        assertThatThrownBy(() -> protocoloService.emitir(request))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já possui protocolo oficial");

        verify(protocoloRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar emitir protocolo para ocorrência inexistente")
    void deveLancarResourceNotFoundExceptionParaOcorrenciaInexistente() {
        ProtocoloRequest request = new ProtocoloRequest("Equipe", "Parecer", LocalDateTime.now().plusDays(1), 999L);
        when(ocorrenciaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> protocoloService.emitir(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Ocorrência não encontrada");

        verify(protocoloRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve buscar protocolo por ID com sucesso")
    void deveBuscarPorIdComSucesso() {
        ProtocoloDefesaCivil p = ProtocoloDefesaCivil.builder()
                .id(1L)
                .numeroProtocolo("DEF-2026-00001")
                .equipeResponsavel("Equipe Alfa")
                .build();

        when(protocoloRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(p));

        ProtocoloResponse response = protocoloService.buscarPorId(1L);

        assertThat(response).isNotNull();
        assertThat(response.numeroProtocolo()).isEqualTo("DEF-2026-00001");
        verify(protocoloRepository, times(1)).findByIdWithDetails(1L);
    }

    @Test
    @DisplayName("Deve buscar protocolo por número oficial com sucesso")
    void deveBuscarPorNumeroComSucesso() {
        ProtocoloDefesaCivil p = ProtocoloDefesaCivil.builder()
                .id(2L)
                .numeroProtocolo("DEF-2026-00042")
                .equipeResponsavel("Equipe Beta")
                .build();

        when(protocoloRepository.findByNumeroProtocoloWithDetails("DEF-2026-00042")).thenReturn(Optional.of(p));

        ProtocoloResponse response = protocoloService.buscarPorNumero("DEF-2026-00042");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.numeroProtocolo()).isEqualTo("DEF-2026-00042");
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao buscar número de protocolo inexistente")
    void deveLancarResourceNotFoundExceptionParaNumeroInexistente() {
        when(protocoloRepository.findByNumeroProtocoloWithDetails("DEF-9999-99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> protocoloService.buscarPorNumero("DEF-9999-99999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Protocolo não encontrado");
    }

    @Test
    @DisplayName("Deve listar protocolos paginados")
    void deveListarProtocolosPaginados() {
        Pageable pageable = PageRequest.of(0, 10);
        ProtocoloDefesaCivil p1 = ProtocoloDefesaCivil.builder().id(1L).numeroProtocolo("DEF-2026-00001").build();
        when(protocoloRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(p1), pageable, 1));

        Page<ProtocoloResponse> page = protocoloService.listar(pageable);

        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).numeroProtocolo()).isEqualTo("DEF-2026-00001");
    }

    @Test
    @DisplayName("Deve concluir protocolo com sucesso e avançar ocorrência para RESOLVIDA")
    void deveConcluirProtocoloComSucesso() {
        Ocorrencia oc = Ocorrencia.builder()
                .id(10L)
                .status(StatusOcorrencia.ENCAMINHADA_DEFESA_CIVIL)
                .build();

        ProtocoloDefesaCivil protocolo = ProtocoloDefesaCivil.builder()
                .id(1L)
                .numeroProtocolo("DEF-2026-00001")
                .equipeResponsavel("Equipe Tática")
                .ocorrencia(oc)
                .build();

        ConcluirProtocoloRequest request = new ConcluirProtocoloRequest("Desobstrução do leito e drenagem concluídas com sucesso.");

        when(protocoloRepository.findById(1L)).thenReturn(Optional.of(protocolo));
        when(protocoloRepository.save(any(ProtocoloDefesaCivil.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProtocoloResponse response = protocoloService.concluir(1L, request);

        assertThat(response).isNotNull();
        assertThat(protocolo.getDataConclusao()).isNotNull();
        assertThat(protocolo.getParecerTecnico()).isEqualTo(request.parecerFinal());
        assertThat(oc.getStatus()).isEqualTo(StatusOcorrencia.RESOLVIDA);
        assertThat(response.ocorrenciaStatus()).isEqualTo("RESOLVIDA");

        verify(protocoloRepository, times(1)).save(protocolo);
    }

    @Test
    @DisplayName("Deve lançar RegraNegocioException ao tentar concluir protocolo que já havia sido concluído")
    void deveLancarRegraNegocioExceptionAoConcluirProtocoloJaConcluido() {
        ProtocoloDefesaCivil protocolo = ProtocoloDefesaCivil.builder()
                .id(1L)
                .dataConclusao(LocalDateTime.now().minusHours(2))
                .build();

        ConcluirProtocoloRequest request = new ConcluirProtocoloRequest("Parecer duplicado de conclusão técnica");

        when(protocoloRepository.findById(1L)).thenReturn(Optional.of(protocolo));

        assertThatThrownBy(() -> protocoloService.concluir(1L, request))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já foi concluído anteriormente");

        verify(protocoloRepository, never()).save(any());
    }
}
