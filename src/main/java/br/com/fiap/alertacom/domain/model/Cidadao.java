package br.com.fiap.alertacom.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Entidade JPA representando o cidadão cadastrado ou gestor municipal no AlertaCom.
 * Implementa UserDetails do Spring Security para viabilizar autenticação JWT direta.
 */
@Entity
@Table(
    name = "TB_CIDADAO",
    uniqueConstraints = {
        @UniqueConstraint(name = "UK_CIDADAO_EMAIL", columnNames = {"email"})
    }
)
@Builder
@AllArgsConstructor
public class Cidadao implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cidadao", nullable = false, updatable = false)
    private Long id;

    @Column(name = "nome", length = 150, nullable = false)
    private String nome;

    @Column(name = "email", length = 100, nullable = false, unique = true)
    private String email;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "bairro", length = 100, nullable = false)
    private String bairro;

    @Column(name = "senha", length = 255, nullable = false)
    private String senha;

    @Builder.Default
    @Column(name = "role", length = 30, nullable = false)
    private String role = "ROLE_CIDADAO";

    @Column(name = "data_cadastro", nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @Builder.Default
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = Boolean.TRUE;

    @Builder.Default
    @OneToMany(
        mappedBy = "cidadao",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private List<Ocorrencia> ocorrencias = new ArrayList<>();

    // =========================================================================
    // Construtores
    // =========================================================================

    public Cidadao() {
        this.ocorrencias = new ArrayList<>();
    }

    public Cidadao(String nome, String email, String telefone, String bairro, String senha, String role) {
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.bairro = bairro;
        this.senha = senha;
        this.role = (role != null && !role.isBlank()) ? role : "ROLE_CIDADAO";
        this.ativo = Boolean.TRUE;
        this.ocorrencias = new ArrayList<>();
    }

    public Cidadao(Long id, String nome, String email, String telefone, String bairro, String senha, String role, Boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.bairro = bairro;
        this.senha = senha;
        this.role = role;
        this.ativo = ativo;
        this.ocorrencias = new ArrayList<>();
    }

    // =========================================================================
    // Callbacks do Ciclo de Vida JPA
    // =========================================================================

    @PrePersist
    protected void prePersist() {
        if (this.dataCadastro == null) {
            this.dataCadastro = LocalDateTime.now();
        }
        if (this.ativo == null) {
            this.ativo = Boolean.TRUE;
        }
        if (this.role == null || this.role.isBlank()) {
            this.role = "ROLE_CIDADAO";
        }
    }

    // =========================================================================
    // Métodos de Conveniência Bidirecional
    // =========================================================================

    public void addOcorrencia(Ocorrencia ocorrencia) {
        if (ocorrencia != null) {
            if (this.ocorrencias == null) {
                this.ocorrencias = new ArrayList<>();
            }
            this.ocorrencias.add(ocorrencia);
            ocorrencia.setCidadao(this);
        }
    }

    public void removeOcorrencia(Ocorrencia ocorrencia) {
        if (ocorrencia != null) {
            this.ocorrencias.remove(ocorrencia);
            ocorrencia.setCidadao(null);
        }
    }

    // =========================================================================
    // Contrato UserDetails (Spring Security)
    // =========================================================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == null || this.role.isBlank()) {
            return List.of(new SimpleGrantedAuthority("ROLE_CIDADAO"));
        }
        return List.of(new SimpleGrantedAuthority(this.role));
    }

    @Override
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.ativo);
    }

    // =========================================================================
    // Getters e Setters
    // =========================================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public List<Ocorrencia> getOcorrencias() {
        return ocorrencias;
    }

    public void setOcorrencias(List<Ocorrencia> ocorrencias) {
        this.ocorrencias = ocorrencias;
    }

    // =========================================================================
    // Equals, HashCode e ToString
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cidadao)) return false;
        Cidadao cidadao = (Cidadao) o;
        return id != null && Objects.equals(id, cidadao.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Cidadao{" +
                "id=" + id +
                ", nome='" + nome + '\'' +
                ", email='" + email + '\'' +
                ", bairro='" + bairro + '\'' +
                ", role='" + role + '\'' +
                ", ativo=" + ativo +
                ", dataCadastro=" + dataCadastro +
                '}';
    }
}
