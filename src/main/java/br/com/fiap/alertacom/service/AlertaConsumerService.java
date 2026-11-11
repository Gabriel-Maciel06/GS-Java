package br.com.fiap.alertacom.service;

import br.com.fiap.alertacom.config.RabbitMQConfig;
import br.com.fiap.alertacom.event.AlertaCriticoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Consumidor assíncrono de eventos críticos na fila {@code alertas.criticos.queue}.
 * Simula o acionamento imediato da Defesa Civil, sirenes comunitárias e broadcast
 * para moradores do bairro afetado.
 */
@Service
public class AlertaConsumerService {

    private static final Logger log = LoggerFactory.getLogger(AlertaConsumerService.class);
    private static final int MAX_HISTORICO_MEMORIA = 100;

    private final List<AlertaCriticoEvent> ultimosAlertasProcessados = new CopyOnWriteArrayList<>();

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ALERTAS_CRITICOS)
    public void processarAlertaCritico(AlertaCriticoEvent evento) {
        if (evento == null) {
            log.warn("⚠️ [AMQP CONSUMER] Evento nulo recebido na fila '{}'. Ignorando.",
                    RabbitMQConfig.QUEUE_ALERTAS_CRITICOS);
            return;
        }

        String bairro = evento.bairro() != null && !evento.bairro().isBlank() ? evento.bairro() : "Bairro Não Informado";
        String severidade = evento.severidade() != null ? evento.severidade() : "DESCONHECIDA";
        String tipoRisco = evento.tipoRisco() != null ? evento.tipoRisco() : "NAO_ESPECIFICADO";
        String titulo = evento.titulo() != null ? evento.titulo() : "Sem título";
        String cidadao = evento.cidadaoNome() != null && !evento.cidadaoNome().isBlank()
                ? evento.cidadaoNome()
                : "Cidadão Anônimo";

        log.info("================================================================================");
        log.info("🚨 [DEFESA CIVIL - ALERTA CRÍTICO RECEBIDO] Ocorrência #{} | Severidade: {}",
                evento.ocorrenciaId(), severidade);
        log.info("📍 Bairro: {} | Risco: {} | Coordenadas: ({}, {})",
                bairro, tipoRisco, evento.latitude(), evento.longitude());
        log.info("📢 Título: {} | Relator: {}", titulo, cidadao);
        log.info("🔔 [AÇÃO IMEDIATA] Acionando sirenes comunitárias e broadcast para moradores de '{}'...",
                bairro);
        log.info("================================================================================");

        registrarNoHistorico(evento);
    }

    private void registrarNoHistorico(AlertaCriticoEvent evento) {
        if (ultimosAlertasProcessados.size() >= MAX_HISTORICO_MEMORIA) {
            ultimosAlertasProcessados.remove(0);
        }
        ultimosAlertasProcessados.add(evento);
    }

    public List<AlertaCriticoEvent> getUltimosAlertasProcessados() {
        return Collections.unmodifiableList(ultimosAlertasProcessados);
    }

    public void limparHistorico() {
        ultimosAlertasProcessados.clear();
    }
}
