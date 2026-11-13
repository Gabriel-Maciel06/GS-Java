package br.com.fiap.alertacom.service;

import br.com.fiap.alertacom.config.RabbitMQConfig;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.event.AlertaCriticoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.text.Normalizer;
import java.time.LocalDateTime;

/**
 * Serviço responsável por publicar eventos assíncronos de alertas críticos (CRITICA e ALTA)
 * na Topic Exchange do RabbitMQ com resiliência a falhas de conectividade do broker.
 */
@Service
public class AlertaPublisherService {

    private static final Logger log = LoggerFactory.getLogger(AlertaPublisherService.class);

    private final RabbitTemplate rabbitTemplate;

    public AlertaPublisherService(@Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Listener desacoplado acionado imediatamente após o COMMIT da transação de banco.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public boolean publicarAlertaCritico(AlertaCriticoEvent evento) {
        if (evento == null) {
            log.warn("Evento de alerta crítico nulo recebido para publicação AMQP. Ignorando.");
            return false;
        }

        if (rabbitTemplate == null) {
            log.info("RabbitTemplate ausente ou inativo. Mensagem de alerta não despachada.");
            return false;
        }

        try {
            String routingKey = construirRoutingKey(evento.bairro());
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_ALERTA, routingKey, evento);
            log.info("🚨 [AMQP EVENT] Evento crítico despachado: Ocorrência #{} na rota '{}'",
                    evento.ocorrenciaId(), routingKey);
            return true;
        } catch (AmqpException e) {
            log.warn("Falha de conexão com RabbitMQ para ocorrência #{}. Transação do banco de dados mantida. Erro: {}",
                    evento.ocorrenciaId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Erro inesperado na publicação AMQP para ocorrência #{}: {}",
                    evento.ocorrenciaId(), e.getMessage());
            return false;
        }
    }

    /**
     * Sobrecarga de compatibilidade para publicação direta a partir de entidade Ocorrencia.
     */
    public boolean publicarAlertaCritico(Ocorrencia ocorrencia) {
        if (ocorrencia == null || !ocorrencia.isCriticaOuAlta()) {
            return false;
        }
        AlertaCriticoEvent evento = construirEvento(ocorrencia);
        return publicarAlertaCritico(evento);
    }

    public AlertaCriticoEvent construirEvento(Ocorrencia oc) {
        if (oc == null) {
            return null;
        }
        return new AlertaCriticoEvent(
                oc.getId(),
                oc.getTitulo(),
                oc.getBairro(),
                oc.getSeveridade() != null ? oc.getSeveridade().name() : "CRITICA",
                oc.getCategoria() != null ? oc.getCategoria().name() : "OUTROS",
                oc.getDescricao(),
                oc.getCidadao() != null && oc.getCidadao().getNome() != null
                        ? oc.getCidadao().getNome()
                        : "Cidadão Anônimo",
                oc.getLatitude(),
                oc.getLongitude(),
                oc.getDataCriacao() != null ? oc.getDataCriacao() : LocalDateTime.now()
        );
    }

    public String construirRoutingKey(String bairro) {
        return "alerta.critico." + normalizarBairro(bairro);
    }

    public String normalizarBairro(String bairro) {
        if (bairro == null || bairro.isBlank()) {
            return "geral";
        }
        return Normalizer.normalize(bairro, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_");
    }
}
