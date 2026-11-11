package br.com.fiap.alertacom.controller;

import br.com.fiap.alertacom.exception.DuplicateResourceException;
import br.com.fiap.alertacom.exception.GlobalExceptionHandler;
import br.com.fiap.alertacom.exception.RegraNegocioException;
import br.com.fiap.alertacom.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleValidation: Deve gerar ProblemDetail 400 com propriedade invalidParams e timestamp")
    void deveTratarValidationException() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "objetoTeste");
        bindingResult.addError(new FieldError("objetoTeste", "email", "Formato de e-mail inválido"));
        bindingResult.addError(new FieldError("objetoTeste", "nome", "Nome é obrigatório"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"),
                -1
        );

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ProblemDetail problem = exceptionHandler.handleValidation(ex);

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Requisição Inválida");
        assertThat(problem.getProperties()).containsKey("timestamp");
        assertThat(problem.getProperties()).containsKey("invalidParams");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> invalidParams = (List<Map<String, String>>) problem.getProperties().get("invalidParams");
        assertThat(invalidParams).hasSize(2);
        assertThat(invalidParams.get(0)).containsEntry("field", "email").containsEntry("message", "Formato de e-mail inválido");
        assertThat(invalidParams.get(1)).containsEntry("field", "nome").containsEntry("message", "Nome é obrigatório");
    }

    @Test
    @DisplayName("handleIllegalArgument: Deve gerar ProblemDetail 400")
    void deveTratarIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Parâmetro page não pode ser negativo");

        ProblemDetail problem = exceptionHandler.handleIllegalArgument(ex);

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Parâmetro Inválido");
        assertThat(problem.getDetail()).isEqualTo("Parâmetro page não pode ser negativo");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleResourceNotFound: Deve gerar ProblemDetail 404")
    void deveTratarResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Cidadão não encontrado com ID: 42");

        ProblemDetail problem = exceptionHandler.handleResourceNotFound(ex);

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Recurso Não Encontrado");
        assertThat(problem.getDetail()).isEqualTo("Cidadão não encontrado com ID: 42");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleDuplicateResource: Deve gerar ProblemDetail 409")
    void deveTratarDuplicateResourceException() {
        DuplicateResourceException ex = new DuplicateResourceException("E-mail duplicado no sistema");

        ProblemDetail problem = exceptionHandler.handleDuplicateResource(ex);

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Conflito de Recurso");
        assertThat(problem.getDetail()).isEqualTo("E-mail duplicado no sistema");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleDataIntegrity: Deve gerar ProblemDetail 409")
    void deveTratarDataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique constraint violation");

        ProblemDetail problem = exceptionHandler.handleDataIntegrity(ex);

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Conflito de Integridade");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleRegraNegocio: Deve gerar ProblemDetail 422")
    void deveTratarRegraNegocioException() {
        RegraNegocioException ex = new RegraNegocioException("Transição de status proibida");

        ProblemDetail problem = exceptionHandler.handleRegraNegocio(ex);

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getTitle()).isEqualTo("Violação de Regra de Negócio");
        assertThat(problem.getDetail()).isEqualTo("Transição de status proibida");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleIllegalState: Deve gerar ProblemDetail 422")
    void deveTratarIllegalStateException() {
        IllegalStateException ex = new IllegalStateException("Estado inconsistente da máquina de estados");

        ProblemDetail problem = exceptionHandler.handleIllegalState(ex);

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getTitle()).isEqualTo("Estado Inválido da Entidade");
        assertThat(problem.getDetail()).isEqualTo("Estado inconsistente da máquina de estados");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleGeneral: Deve gerar ProblemDetail 500 para erro inesperado")
    void deveTratarExceptionGenerica() {
        Exception ex = new RuntimeException("Erro inesperado no banco");

        ProblemDetail problem = exceptionHandler.handleGeneral(ex);

        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Erro Interno do Servidor");
        assertThat(problem.getDetail()).contains("Contate o suporte técnico");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }
}
