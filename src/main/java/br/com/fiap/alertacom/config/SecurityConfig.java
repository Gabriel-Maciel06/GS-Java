package br.com.fiap.alertacom.config;

import br.com.fiap.alertacom.security.CustomAccessDeniedHandler;
import br.com.fiap.alertacom.security.CustomAuthenticationEntryPoint;
import br.com.fiap.alertacom.security.SecurityFilter;
import br.com.fiap.alertacom.security.TokenService;
import br.com.fiap.alertacom.security.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuração central do Spring Security 6.3 para o AlertaCom.
 * Estabelece autenticação stateless com tokens JWT, proteção de cabeçalhos,
 * pontos de interceptação de erros RFC 7807 e matriz de controle de acesso granular RBAC.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilter securityFilter(TokenService tokenService, UserDetailsServiceImpl userDetailsService) {
        return new SecurityFilter(tokenService, userDetailsService);
    }

    @Bean
    public CustomAuthenticationEntryPoint customAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new CustomAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public CustomAccessDeniedHandler customAccessDeniedHandler(ObjectMapper objectMapper) {
        return new CustomAccessDeniedHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityFilter securityFilter,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .authorizeHttpRequests(auth -> auth
                        // 1. Rotas Públicas (permitAll)
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/cidadaos").permitAll()
                        .requestMatchers("/api/protocolos/numero/**", "/api/protocolos/consultar/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()

                        // 2. Rotas para Cidadão e Gestor (hasAnyRole)
                        .requestMatchers(HttpMethod.POST, "/api/ocorrencias").hasAnyRole("CIDADAO", "GESTOR")
                        .requestMatchers(HttpMethod.GET, "/api/ocorrencias/minhas").hasAnyRole("CIDADAO", "GESTOR")
                        .requestMatchers(HttpMethod.GET, "/api/cidadaos/{id}").hasAnyRole("CIDADAO", "GESTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/cidadaos/{id}").hasAnyRole("CIDADAO", "GESTOR")

                        // 3. Rotas Exclusivas de Gestor (hasRole)
                        .requestMatchers(HttpMethod.PATCH, "/api/ocorrencias/{id}/status").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/ocorrencias/{id}").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.POST, "/api/protocolos").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/protocolos/{id}/concluir").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/protocolos/{id}/concluir").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.GET, "/api/cidadaos").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/cidadaos/{id}").hasRole("GESTOR")
                        .requestMatchers("/api/relatorios/**", "/api/boletim/**", "/api/boletins/**").hasRole("GESTOR")

                        // 4. Qualquer outra requisição exige autenticação
                        .anyRequest().authenticated()
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .build();
    }
}
