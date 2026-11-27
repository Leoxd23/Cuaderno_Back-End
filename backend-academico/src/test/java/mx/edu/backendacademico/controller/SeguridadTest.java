package mx.edu.backendacademico.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.*;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlumnoController.class)
@Import(SecurityConfig.class)
class SeguridadTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService service;
    private static final String DATOS = """
        {"matricula":"S1","nombre":"Ada","correo":"ada@u.mx"}
        """;

    @Test void sinCredencialesRecibe401() throws Exception {
        mvc.perform(get("/api/v1/alumnos")).andExpect(status().isUnauthorized());
    }

    @Test void alumnoNoPuedeCrearAunqueTengaCsrf() throws Exception {
        // Token correcto: el 403 de esta prueba se debe al rol.
        mvc.perform(post("/api/v1/alumnos").with(httpBasic("alumno", "alumno123"))
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DATOS))
            .andExpect(status().isForbidden());
    }

    @Test void docenteSinCsrfRecibe403() throws Exception {
        // Rol correcto: esta vez falta la protección de origen de la operación.
        mvc.perform(post("/api/v1/alumnos").with(httpBasic("docente", "docente123"))
            .contentType(MediaType.APPLICATION_JSON).content(DATOS))
            .andExpect(status().isForbidden());
    }

    @Test void docenteConCsrfPuedeCrear() throws Exception {
        when(service.crear(anyString(), anyString(), anyString())).thenReturn(
            new Alumno(1L, "S1", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
        mvc.perform(post("/api/v1/alumnos").with(httpBasic("docente", "docente123"))
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DATOS))
            .andExpect(status().isCreated());
    }

    @Test void alumnoConsultaSuPropioRegistro() throws Exception {
        // En el laboratorio, la matrícula del alumno coincide con su nombre de usuario.
        when(service.buscar(1L)).thenReturn(new Alumno(1L, "ALUMNO", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
        mvc.perform(get("/api/v1/alumnos/1").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk());
    }

    @Test void alumnoRecibe403ParaUnRegistroAjeno() throws Exception {
        when(service.buscar(2L)).thenReturn(new Alumno(2L, "OTRO", "Emmy", "emmy@u.mx", EstatusAlumno.ACTIVO));
        mvc.perform(get("/api/v1/alumnos/2").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void alumnoRecibeElMismo403CuandoElRegistroNoExiste() throws Exception {
        // Un 404 distinto permitiría al alumno descubrir qué identificadores existen.
        when(service.buscar(99L)).thenThrow(new AlumnoNoEncontradoException(99L));
        mvc.perform(get("/api/v1/alumnos/99").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void docenteConsultaCualquierRegistroYConoceLaAusencia() throws Exception {
        when(service.buscar(2L)).thenReturn(new Alumno(2L, "OTRO", "Emmy", "emmy@u.mx", EstatusAlumno.ACTIVO));
        when(service.buscar(99L)).thenThrow(new AlumnoNoEncontradoException(99L));
        mvc.perform(get("/api/v1/alumnos/2").with(httpBasic("docente", "docente123")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/alumnos/99").with(httpBasic("docente", "docente123")))
            .andExpect(status().isNotFound());
    }

    @Test void alumnoYaNoPuedeListarATodos() throws Exception {
        mvc.perform(get("/api/v1/alumnos").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden());
    }
}
