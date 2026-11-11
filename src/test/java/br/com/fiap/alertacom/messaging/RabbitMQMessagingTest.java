package br.com.fiap.alertacom.messaging;

import br.com.fiap.alertacom.config.RabbitMQConfig;
import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.Cidadao;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import br.com.fiap.alertacom.event.AlertaCriticoEvent;
import br.com.fiap.alertacom.service.AlertaConsumerService;
import br.com.fiap.alertacom.service.AlertaPublisherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQMessagingTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private RabbitMQConfig rabbitMQConfig;
    private AlertaPublisherService publisherService;
    private AlertaConsumerService consumerService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        rabbitMQConfig = new RabbitMQConfig();
        publisherService = new AlertaPublisherService(rabbitTemplate);
        consumerService = new AlertaConsumerService();
    }

    @Test
    @DisplayName("Deve configurar corretamente os beans de Exchange, Queue, Binding e Jackson2JsonMessageConverter")
    void deveConfigurarBeansDoRabbitMQCorretamente() {
        TopicExchange exchange = rabbitMQConfig.alertaExchange();
        Queue queue = rabbitMQConfig.alertasCriticosQueue();
        Binding binding = rabbitMQConfig.bindingAlertasCriticos(queue, exchange);
        MessageConverter converter = rabbitMQConfig.jackson2JsonMessageConverter(objectMapper);

        assertThat(exchange.getName()).isEqualTo(RabbitMQConfig.EXCHANGE_ALERTA);
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();

        assertThat(queue.getName()).isEqualTo(RabbitMQConfig.QUEUE_ALERTAS_CRITICOS);
        assertThat(queue.isDurable()).isTrue();

        assertThat(binding.getExchange()).isEqualTo(RabbitMQConfig.EXCHANGE_ALERTA);
        assertThat(binding.getDestination()).isEqualTo(RabbitMQConfig.QUEUE_ALERTAS_CRITICOS);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitMQConfig.ROUTING_KEY_CRITICO);

        assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    @DisplayName("Deve publicar evento crítico no RabbitTemplate com routing key normalizada para ocorrência CRITICA e ALTA")
    void devePublicarEventoParaOcorrenciaCriticaEAlta() {
        Cidadao cidadao = Cidadao.builder().id(10L).nome("Carlos Defesa").build();
        Ocorrencia ocorrenciaCritica = Ocorrencia.builder()
                .id(101L)
                .titulo("Enxurrada torrencial na Rua São João! 🌊⚠️")
                .descricao("Água invadiu 5 casas")
                .categoria(CategoriaOcorrencia.ENXURRADA)
                .severidade(SeveridadeOcorrencia.CRITICA)
                .status(StatusOcorrencia.ABERTA)
                .bairro("São João Clímaco")
                .latitude(-23.6200)
                .longitude(-46.5900)
                .cidadao(cidadao)
                .dataCriacao(LocalDateTime.of(2026, 5, 10, 14, 30))
                .build();

        boolean publicado = publisherService.publicarAlertaCritico(ocorrenciaCritica);

        assertThat(publicado).isTrue();

        ArgumentCaptor<AlertaCriticoEvent> captor = ArgumentCaptor.forClass(AlertaCriticoEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_ALERTA),
                eq("alerta.critico.sao_joao_climaco"),
                captor.capture()
        );

        AlertaCriticoEvent evento = captor.getValue();
        assertThat(evento.ocorrenciaId()).isEqualTo(101L);
        assertThat(evento.titulo()).isEqualTo("Enxurrada torrencial na Rua São João! 🌊⚠️");
        assertThat(evento.severidade()).isEqualTo("CRITICA");
        assertThat(evento.tipoRisco()).isEqualTo("ENXURRADA");
        assertThat(evento.cidadaoNome()).isEqualTo("Carlos Defesa");
        assertThat(evento.latitude()).isEqualTo(-23.6200);
        assertThat(evento.longitude()).isEqualTo(-46.5900);
    }

    @Test
    @DisplayName("Deve suprimir publicação de evento para ocorrências de severidade BAIXA ou MEDIA")
    void deveSuprimirPublicacaoParaSeveridadeBaixaOuMedia() {
        Ocorrencia ocorrenciaBaixa = Ocorrencia.builder()
                .id(102L)
                .titulo("Folhas acumuladas")
                .categoria(CategoriaOcorrencia.BUEIRO_OBSTRUIDO)
                .severidade(SeveridadeOcorrencia.BAIXA)
                .bairro("Mooca")
                .build();

        boolean publicado = publisherService.publicarAlertaCritico(ocorrenciaBaixa);

        assertThat(publicado).isFalse();
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Deve tratar falha de conexão do broker com resiliência sem propagar exceção")
    void deveManterResilienciaQuandoBrokerFalhar() {
        doThrow(new AmqpConnectException(new RuntimeException("Broker offline")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        Ocorrencia ocorrenciaAlta = Ocorrencia.builder()
                .id(103L)
                .titulo("Alagamento severo")
                .categoria(CategoriaOcorrencia.ALAGAMENTO)
                .severidade(SeveridadeOcorrencia.ALTA)
                .bairro("Vila Prudente")
                .build();

        assertThatCode(() -> {
            boolean publicado = publisherService.publicarAlertaCritico(ocorrenciaAlta);
            assertThat(publicado).isFalse();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Deve processar eventos CRITICA e ALTA no AlertaConsumerService e manter resiliência a campos nulos")
    void deveProcessarAlertaNoConsumerServiceComResiliencia() {
        AlertaCriticoEvent eventoValido = new AlertaCriticoEvent(
                501L,
                "Deslizamento em encosta",
                "Vila Prudente",
                "CRITICA",
                "DESLIZAMENTO",
                "Trincas no solo",
                "Maria Silva",
                -23.5850,
                -46.5920,
                LocalDateTime.now()
        );

        AlertaCriticoEvent eventoParcial = new AlertaCriticoEvent(
                502L,
                null,
                null,
                "ALTA",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatCode(() -> {
            consumerService.processarAlertaCritico(eventoValido);
            consumerService.processarAlertaCritico(eventoParcial);
            consumerService.processarAlertaCritico(null);
        }).doesNotThrowAnyException();

        assertThat(consumerService.getUltimosAlertasProcessados()).hasSize(2);
        assertThat(consumerService.getUltimosAlertasProcessados().get(0).ocorrenciaId()).isEqualTo(501L);
        assertThat(consumerService.getUltimosAlertasProcessados().get(1).ocorrenciaId()).isEqualTo(502L);
    }

    @Test
    @DisplayName("Deve serializar e desserializar AlertaCriticoEvent preservando emojis, acentos e ignorando campos desconhecidos")
    void deveSerializarEDesserializarAlertaCriticoEventComTolerancia() throws Exception {
        String jsonComCampoExtraEEmojis = """
                {
                  "ocorrenciaId": 777,
                  "titulo": "Enxurrada torrencial na Rua São João! 🌊⚠️",
                  "bairro": "Mooca",
                  "severidade": "CRITICA",
                  "tipoRisco": "ALAGAMENTO",
                  "descricao": "Água invadiu 5 casas; atenção máxima!",
                  "cidadaoNome": "João da Conceição",
                  "latitude": 90.0,
                  "longitude": 180.0,
                  "dataHora": "2026-05-10T15:30:00",
                  "campoInesperadoFuturo": "compatibilidade_retroativa"
                }
                """;

        AlertaCriticoEvent evento = objectMapper.readValue(jsonComCampoExtraEEmojis, AlertaCriticoEvent.class);

        assertThat(evento).isNotNull();
        assertThat(evento.ocorrenciaId()).isEqualTo(777L);
        assertThat(evento.titulo()).isEqualTo("Enxurrada torrencial na Rua São João! 🌊⚠️");
        assertThat(evento.bairro()).isEqualTo("Mooca");
        assertThat(evento.severidade()).isEqualTo("CRITICA");
        assertThat(evento.tipoRisco()).isEqualTo("ALAGAMENTO");
        assertThat(evento.cidadaoNome()).isEqualTo("João da Conceição");
        assertThat(evento.latitude()).isEqualTo(90.0);
        assertThat(evento.longitude()).isEqualTo(180.0);
        assertThat(evento.dataHora()).isEqualTo(LocalDateTime.of(2026, 5, 10, 15, 30, 0));

        String jsonSerializado = objectMapper.writeValueAsString(evento);
        assertThat(jsonSerializado).contains("Enxurrada torrencial na Rua São João! 🌊⚠️");
    }
}
