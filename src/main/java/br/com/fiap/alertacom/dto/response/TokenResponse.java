package br.com.fiap.alertacom.dto.response;

public record TokenResponse(
    String token,
    String tipo,
    Long expiraEm,
    String role,
    String nome
) {
    public TokenResponse(String token, Long expiraEm, String role, String nome) {
        this(token, "Bearer", expiraEm, role, nome);
    }
}
