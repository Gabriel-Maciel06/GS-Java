package br.com.fiap.alertacom.ai;

import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import br.com.fiap.alertacom.domain.repository.OcorrenciaRepository;
import br.com.fiap.alertacom.dto.response.BoletimPreventivoResponse;
import br.com.fiap.alertacom.service.ai.BoletimIaService;
import br.com.fiap.alertacom.service.ai.OpenAiBoletimServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Testes unitários da camada de serviço Dual-Engine do Boletim Preventivo (Spring AI + Fallback Determinístico).
 */
@ExtendWith(MockitoExtension.class)
class BoletimIaServiceTest {

    @Mock
    private OcorrenciaRepository ocorrenciaRepository;

    @Mock
    private ChatModel chatModel;

    private BoletimIaService boletimIaServiceFallback;

    @BeforeEach
    void setUp() {
        boletimIaServiceFallback = new OpenAiBoletimServiceImpl(
                ocorrenciaRepository,
                chatModel,
                "mock-key-test",
                "gpt-4o-mini"
        );
    }

    @Test
    @DisplayName("Deve gerar boletim preventivo com nível CRITICO quando houver ocorrências críticas ativas")
    void deveGerarBoletimParaBairroCritico() {
        Ocorrencia oc1 = Ocorrencia.builder()
                .id(1L)
                .titulo("Deslizamento de encosta")
                .descricao("Fissuras profundas no talude próximo a residências")
                .categoria(CategoriaOcorrencia.DESLIZAMENTO)
                .severidade(SeveridadeOcorrencia.CRITICA)
                .status(StatusOcorrencia.ABERTA)
                .endereco("Rua do Alto, 10")
                .bairro("Morro Alto")
                .build();

        Ocorrencia oc2 = Ocorrencia.builder()
                .id(2L)
                .titulo("Alagamento severo")
                .descricao("Via intransitável")
                .categoria(CategoriaOcorrencia.ALAGAMENTO)
                .severidade(SeveridadeOcorrencia.ALTA)
                .status(StatusOcorrencia.EM_ATENDIMENTO)
                .endereco("Rua da Baixada, 55")
                .bairro("Morro Alto")
                .build();

        when(ocorrenciaRepository.findWithFilters(eq("Morro Alto"), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(oc1, oc2)));

        BoletimPreventivoResponse response = boletimIaServiceFallback.gerarBoletimPreventivo("Morro Alto");

        assertThat(response).isNotNull();
        assertThat(response.bairro()).isEqualTo("Morro Alto");
        assertThat(response.totalOcorrenciasAnalisadas()).isEqualTo(2);
        assertThat(response.nivelRiscoGeral()).isEqualTo("CRITICO");
        assertThat(response.resumoSituacaoAtual()).isNotBlank();
        assertThat(response.recomendacoesPopulacao()).isNotEmpty();
        assertThat(response.pontosCriticosEvitar())
                .isNotEmpty()
                .anyMatch(p -> p.contains("Rua do Alto, 10"))
                .anyMatch(p -> p.contains("Rua da Baixada, 55"));
        assertThat(response.planoAcaoDefesaCivil()).isNotBlank();
        assertThat(response.modoExecucao()).contains("FALLBACK");
    }

    @Test
    @DisplayName("Deve gerar boletim com risco BAIXO e listas não vazias quando bairro não possuir ocorrências")
    void deveGerarBoletimParaBairroSemOcorrencias() {
        when(ocorrenciaRepository.findWithFilters(eq("Jardim Tranquilo"), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        BoletimPreventivoResponse response = boletimIaServiceFallback.gerarBoletimPreventivo("Jardim Tranquilo");

        assertThat(response).isNotNull();
        assertThat(response.bairro()).isEqualTo("Jardim Tranquilo");
        assertThat(response.totalOcorrenciasAnalisadas()).isZero();
        assertThat(response.nivelRiscoGeral()).isEqualTo("BAIXO");
        assertThat(response.resumoSituacaoAtual()).isNotBlank();
        assertThat(response.recomendacoesPopulacao()).isNotEmpty();
        assertThat(response.pontosCriticosEvitar()).isNotEmpty();
        assertThat(response.planoAcaoDefesaCivil()).isNotBlank();
    }

    @Test
    @DisplayName("Deve descartar ocorrências com status RESOLVIDA ou CANCELADA da análise do boletim")
    void deveDescartarOcorrenciasResolvidasOuCanceladas() {
        Ocorrencia resolvida = Ocorrencia.builder()
                .id(10L)
                .titulo("Ocorrência já resolvida")
                .descricao("Limpeza de bueiro concluída")
                .categoria(CategoriaOcorrencia.BUEIRO_OBSTRUIDO)
                .severidade(SeveridadeOcorrencia.CRITICA)
                .status(StatusOcorrencia.RESOLVIDA)
                .endereco("Rua Resolvida, 1")
                .bairro("Ipiranga")
                .build();

        Ocorrencia cancelada = Ocorrencia.builder()
                .id(11L)
                .titulo("Ocorrência cancelada por duplicidade")
                .descricao("Chamado duplicado")
                .categoria(CategoriaOcorrencia.ALAGAMENTO)
                .severidade(SeveridadeOcorrencia.ALTA)
                .status(StatusOcorrencia.CANCELADA)
                .endereco("Rua Cancelada, 2")
                .bairro("Ipiranga")
                .build();

        when(ocorrenciaRepository.findWithFilters(eq("Ipiranga"), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resolvida, cancelada)));

        BoletimPreventivoResponse response = boletimIaServiceFallback.gerarBoletimPreventivo("Ipiranga");

        assertThat(response.totalOcorrenciasAnalisadas()).isZero();
        assertThat(response.nivelRiscoGeral()).isEqualTo("BAIXO");
    }

    @Test
    @DisplayName("Deve rejeitar bairro nulo ou em branco com IllegalArgumentException")
    void deveRejeitarBairroVazioOuNulo() {
        assertThatThrownBy(() -> boletimIaServiceFallback.gerarBoletimPreventivo(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> boletimIaServiceFallback.gerarBoletimPreventivo("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> boletimIaServiceFallback.gerarBoletimPreventivo(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Deve acionar fallback determinístico automaticamente quando ChatModel lançar exceção")
    void deveAcionarFallbackQuandoChatModelLancarExcecao() {
        BoletimIaService serviceComChaveReal = new OpenAiBoletimServiceImpl(
                ocorrenciaRepository,
                chatModel,
                "sk-live-chave-real-producao-123456",
                "gpt-4o-mini"
        );

        Ocorrencia ativa = Ocorrencia.builder()
                .id(20L)
                .titulo("Inundação total de via")
                .descricao("Água cobrindo a pista")
                .categoria(CategoriaOcorrencia.ALAGAMENTO)
                .severidade(SeveridadeOcorrencia.ALTA)
                .status(StatusOcorrencia.ABERTA)
                .endereco("Rua do Rio, 100")
                .bairro("Várzea")
                .build();

        when(ocorrenciaRepository.findWithFilters(eq("Várzea"), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ativa)));
        when(chatModel.call(anyString()))
                .thenThrow(new RuntimeException("OpenAI API Connection Timeout"));

        BoletimPreventivoResponse response = serviceComChaveReal.gerarBoletimPreventivo("Várzea");

        assertThat(response).isNotNull();
        assertThat(response.nivelRiscoGeral()).isEqualTo("ALTO");
        assertThat(response.totalOcorrenciasAnalisadas()).isEqualTo(1);
        assertThat(response.modoExecucao()).isEqualTo(OpenAiBoletimServiceImpl.MODO_FALLBACK);
        assertThat(response.pontosCriticosEvitar()).anyMatch(p -> p.contains("Rua do Rio, 100"));
    }

    @Test
    @DisplayName("Deve utilizar resposta do ChatModel quando chave real estiver configurada e OpenAI responder com sucesso")
    void deveUtilizarChatModelQuandoOpenAiResponderComSucesso() {
        BoletimIaService serviceComChaveReal = new OpenAiBoletimServiceImpl(
                ocorrenciaRepository,
                chatModel,
                "sk-live-chave-real-producao-123456",
                "gpt-4o-mini"
        );

        Ocorrencia ativa = Ocorrencia.builder()
                .id(30L)
                .titulo("Queda de árvore na via")
                .descricao("Árvore obstruindo corredor de ônibus")
                .categoria(CategoriaOcorrencia.QUEDA_ARVORE)
                .severidade(SeveridadeOcorrencia.MEDIA)
                .status(StatusOcorrencia.EM_ANALISE)
                .endereco("Av. Paulista, 1000")
                .bairro("Bela Vista")
                .build();

        when(ocorrenciaRepository.findWithFilters(eq("Bela Vista"), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ativa)));
        when(chatModel.call(anyString()))
                .thenReturn("Síntese gerada pela IA: Ocorrência de queda de árvore em atendimento na Bela Vista.");

        BoletimPreventivoResponse response = serviceComChaveReal.gerarBoletimPreventivo("Bela Vista");

        assertThat(response).isNotNull();
        assertThat(response.nivelRiscoGeral()).isEqualTo("MODERADO");
        assertThat(response.modoExecucao()).isEqualTo(OpenAiBoletimServiceImpl.MODO_OPENAI);
        assertThat(response.resumoSituacaoAtual()).contains("Síntese gerada pela IA");
    }
}
