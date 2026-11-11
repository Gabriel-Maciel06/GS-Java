package br.com.fiap.alertacom.dto.response;

import br.com.fiap.alertacom.domain.model.Cidadao;

import java.time.LocalDateTime;

public record CidadaoResponse(
    Long id,
    String nome,
    String email,
    String telefone,
    String bairro,
    String role,
    LocalDateTime dataCadastro,
    Boolean ativo
) {
    public static CidadaoResponse fromEntity(Cidadao c) {
        if (c == null) return null;
        return new CidadaoResponse(
            c.getId(),
            c.getNome(),
            c.getEmail(),
            c.getTelefone(),
            c.getBairro(),
            c.getRole(),
            c.getDataCadastro(),
            c.getAtivo()
        );
    }
}
