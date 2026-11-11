package br.com.fiap.alertacom.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Nível de severidade da ocorrência reportada.
 * Ocorrências com severidade ALTA ou CRITICA acionam mensageria assíncrona (RabbitMQ).
 */
public enum SeveridadeOcorrencia {

    BAIXA("Baixa - Sem risco iminente à vida ou ao patrimônio físico"),
    MEDIA("Média - Risco moderado requerendo vistoria e acompanhamento programado"),
    ALTA("Alta - Risco acentuado requerendo despacho tático prioritário"),
    CRITICA("Crítica - Risco iminente à vida humana, acionamento emergencial imediato");

    private final String descricao;

    SeveridadeOcorrencia(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Verifica se a ocorrência demanda publicação assíncrona de evento crítico no RabbitMQ.
     *
     * @return true se severidade for ALTA ou CRITICA; false caso contrário.
     */
    public boolean isCriticaOuAlta() {
        return this == ALTA || this == CRITICA;
    }

    @JsonValue
    public String getNome() {
        return this.name();
    }

    @JsonCreator
    public static SeveridadeOcorrencia fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SeveridadeOcorrencia severidade : values()) {
            if (severidade.name().equalsIgnoreCase(value.trim())) {
                return severidade;
            }
        }
        throw new IllegalArgumentException("Severidade de ocorrência inválida: " + value);
    }
}
