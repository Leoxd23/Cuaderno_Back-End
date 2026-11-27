package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Optional;
import mx.edu.backendacademico.config.CacheConfig;
import mx.edu.backendacademico.domain.*;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

// Contexto mínimo: el proxy de caché real alrededor del servicio y un repositorio simulado.
@SpringJUnitConfig({CacheConfig.class, AlumnoService.class})
class CacheAlumnosTest {
    @Autowired AlumnoService servicio;
    @MockitoBean AlumnoRepository repository;

    @Test void unaSegundaConsultaReutilizaElValorYUnaEscrituraLoInvalida() {
        var ada = new Alumno(1L, "ALUMNO", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(ada));
        when(repository.guardar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.buscar(1L);
        servicio.buscar(1L);
        // La clave sólo es el id: el valor no depende de quién consulta.
        verify(repository, times(1)).buscarPorId(1L);

        // actualizar lee directo (llamada interna sin proxy) y después invalida la caché.
        servicio.actualizar(1L, "Ada Byron", "ada@u.mx");
        servicio.buscar(1L);
        verify(repository, times(3)).buscarPorId(1L);
    }

    @Test void unaAusenciaNoSeGuardaEnLaCache() {
        when(repository.buscarPorId(9L)).thenReturn(Optional.empty());
        assertThrows(AlumnoNoEncontradoException.class, () -> servicio.buscar(9L));
        assertThrows(AlumnoNoEncontradoException.class, () -> servicio.buscar(9L));
        // Una excepción no produce entrada: el alumno creado después será visible.
        verify(repository, times(2)).buscarPorId(9L);
    }
}
