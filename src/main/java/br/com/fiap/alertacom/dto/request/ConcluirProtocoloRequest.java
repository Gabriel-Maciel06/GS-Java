package br.com.fiap.alertacom.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConcluirProtocoloRequest(
    @JsonAlias({"parecerFinal", "parecerTecnico"})
    @NotBlank(message = "Parecer final de conclusão é obrigatório")
    @Size(min = 10, max = 1500, message = "Parecer final deve ter entre 10 e 1500 caracteres")
    String parecerFinal
) {
}
