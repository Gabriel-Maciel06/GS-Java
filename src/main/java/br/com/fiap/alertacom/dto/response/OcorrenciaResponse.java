package br.com.fiap.alertacom.dto.response;

import br.com.fiap.alertacom.domain.model.CategoriaOcorrencia;
import br.com.fiap.alertacom.domain.model.Ocorrencia;
import br.com.fiap.alertacom.domain.model.SeveridadeOcorrencia;
import br.com.fiap.alertacom.domain.model.StatusOcorrencia;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record OcorrenciaResponse(
    Long id,
    String titulo,
    String descricao,
    CategoriaOcorrencia categoria,
    SeveridadeOcorrencia severidade,
    StatusOcorrencia status,
    Double latitude,
    Double longitude,
    String endereco,
    String bairro,
    LocalDateTime dataCriacao,
    LocalDateTime dataAtualizacao,
    CidadaoResumoResponse cidadao,
    ProtocoloResumoResponse protocolo
) {
    @JsonProperty("tipoRisco")
    public String tipoRisco() {
        return categoria != null ? categoria.name() : null;
    }

    public static OcorrenciaResponse fromEntity(Ocorrencia o) {
        if (o == null) return null;
        return new OcorrenciaResponse(
            o.getId(),
            o.getTitulo(),
            o.getDescricao(),
            o.getCategoria(),
            o.getSeveridade(),
            o.getStatus(),
            o.getLatitude(),
            o.getLongitude(),
            o.getEndereco(),
            o.getBairro(),
            o.getDataCriacao(),
            o.getDataAtualizacao(),
            CidadaoResumoResponse.fromEntity(o.getCidadao()),
            ProtocoloResumoResponse.fromEntity(o.getProtocolo())
        );
    }
}
