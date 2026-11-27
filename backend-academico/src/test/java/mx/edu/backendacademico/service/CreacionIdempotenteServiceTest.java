package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import mx.edu.backendacademico.domain.*;
import mx.edu.backendacademico.exception.ClaveIdempotenciaException;
import mx.edu.backendacademico.repository.ClaveIdempotenciaRepository;
import mx.edu.backendacademico.repository.ClaveIdempotenciaRepository.Registro;
import org.junit.jupiter.api.Test;

class CreacionIdempotenteServiceTest {
    private final ClaveIdempotenciaRepository claves = mock(ClaveIdempotenciaRepository.class);
    private final AlumnoService alumnos = mock(AlumnoService.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC);
    private final CreacionIdempotenteService servicio = new CreacionIdempotenteService(claves, alumnos, reloj);
    private final Alumno ada = new Alumno(7L, "A1", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);

    @Test void unaClaveNuevaCreaYAsociaElAlumno() {
        when(claves.reservar(eq("docente"), eq("k1"), anyString(), any())).thenReturn(true);
        when(alumnos.crear("A1", "Ada", "ada@u.mx")).thenReturn(ada);
        var resultado = servicio.crear("docente", "k1", "A1", "Ada", "ada@u.mx");
        assertFalse(resultado.repetido());
        verify(claves).asociar("docente", "k1", 7L);
    }

    @Test void unaRepeticionConElMismoContenidoDevuelveElAlumnoOriginal() {
        when(claves.reservar(any(), any(), any(), any())).thenReturn(false);
        when(claves.buscar("docente", "k1")).thenReturn(Optional.of(
            new Registro(CreacionIdempotenteService.huella("A1", "Ada", "ada@u.mx"), 7L)));
        when(alumnos.buscar(7L)).thenReturn(ada);
        var resultado = servicio.crear("docente", "k1", "A1", "Ada", "ada@u.mx");
        assertTrue(resultado.repetido());
        assertEquals(7L, resultado.alumno().id());
        verify(alumnos, never()).crear(any(), any(), any());
    }

    @Test void laMismaClaveConOtroContenidoNoCreaOtroAlumno() {
        when(claves.reservar(any(), any(), any(), any())).thenReturn(false);
        when(claves.buscar("docente", "k1")).thenReturn(Optional.of(
            new Registro(CreacionIdempotenteService.huella("A1", "Ada", "ada@u.mx"), 7L)));
        var error = assertThrows(ClaveIdempotenciaException.class, () ->
            servicio.crear("docente", "k1", "A1", "Ada Byron", "ada@u.mx"));
        assertEquals("CLAVE_IDEMPOTENCIA_REUTILIZADA", error.getCodigo());
        verify(alumnos, never()).crear(any(), any(), any());
    }

    @Test void unaClaveMalFormadaSeRechazaAntesDeReservar() {
        var error = assertThrows(ClaveIdempotenciaException.class, () ->
            servicio.crear("docente", "clave con espacios", "A1", "Ada", "ada@u.mx"));
        assertEquals("CLAVE_IDEMPOTENCIA_INVALIDA", error.getCodigo());
        verifyNoInteractions(claves, alumnos);
    }

    @Test void laHuellaDistingueLosLimitesEntreCampos() {
        // Sin longitudes, "A"+"BC" y "AB"+"C" producirían el mismo texto concatenado.
        assertNotEquals(CreacionIdempotenteService.huella("A", "BC", "x@u.mx"),
                        CreacionIdempotenteService.huella("AB", "C", "x@u.mx"));
        assertEquals(64, CreacionIdempotenteService.huella("A1", "Ada", "ada@u.mx").length());
    }
}
