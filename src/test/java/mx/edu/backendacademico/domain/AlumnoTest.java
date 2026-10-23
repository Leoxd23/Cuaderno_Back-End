package mx.edu.backendacademico.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AlumnoTest {
    @Test void bajaConservaIdentidadSinModificarElOriginal() {
        var alumno = new Alumno(1L, " a001 ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var baja = alumno.darDeBaja();
        // Comprueba tanto el resultado como la ausencia de efectos laterales.
        assertEquals("A001", baja.matricula());
        assertEquals(alumno.id(), baja.id());
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        assertEquals(EstatusAlumno.ACTIVO, alumno.estatus());
    }

    @Test void reactivarCambiaBajaAActivoSinModificarElOriginal() {
        var alumno = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.BAJA);
        var reactivado = alumno.reactivar();

        assertEquals(EstatusAlumno.ACTIVO, reactivado.estatus());
        assertEquals(EstatusAlumno.BAJA, alumno.estatus());
        assertEquals(alumno.id(), reactivado.id());
        assertEquals(alumno.matricula(), reactivado.matricula());
        assertNotSame(alumno, reactivado);
    }

    @Test void igualdadDelRecordIncluyeCorreoAunqueCoincidaElId() {
        var primero = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var segundo = new Alumno(1L, "A001", "Ada", "ada.personal@u.mx", EstatusAlumno.ACTIVO);

        assertEquals(primero.id(), segundo.id());
        assertEquals(primero.matricula(), segundo.matricula());
        assertNotEquals(primero, segundo);
    }

    @Test void rechazaMatriculaVacia() {
        assertThrows(IllegalArgumentException.class, () ->
                new Alumno(null, " ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
    }

    @Test void materiaRechazaCreditosNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> new Materia(null, "M1", "Análisis", 0));
    }
}