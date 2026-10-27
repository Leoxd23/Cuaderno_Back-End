package mx.edu.backendacademico.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.repository.AlumnoRepositoryEnMemoria;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlumnoControllerTest {
    @Test
    void creaConsultaActualizaYEliminaUsandoElIdReal() throws Exception {
        MockMvc mockMvc = mockMvc();

        MvcResult creado = mockMvc.perform(post("/api/v1/alumnos")
                .contentType("application/json")
                .content("""
                    {"matricula":"A005","nombre":"Ada Lovelace","correo":"ada@universidad.mx"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                "/api/v1/alumnos/\\d+")))
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andReturn();

        String respuesta = creado.getResponse().getContentAsString();
        long id = Long.parseLong(respuesta.replaceFirst(".*\"id\":(\\d+).*", "$1"));
        String ruta = "/api/v1/alumnos/" + id;

        mockMvc.perform(get(ruta))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is((int) id)))
            .andExpect(jsonPath("$.nombre", is("Ada Lovelace")));

        mockMvc.perform(put(ruta)
                .contentType("application/json")
                .content("""
                    {"nombre":"Ada Byron","correo":"ada.byron@universidad.mx"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is((int) id)))
            .andExpect(jsonPath("$.matricula", is("A005")))
            .andExpect(jsonPath("$.nombre", is("Ada Byron")))
            .andExpect(jsonPath("$.correo", is("ada.byron@universidad.mx")));

        mockMvc.perform(delete(ruta))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }

    @Test
    void unRepositorioNuevoIniciaSinAlumnos() throws Exception {
        AlumnoService service = new AlumnoService(new AlumnoRepositoryEnMemoria());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AlumnoController(service)).build();

        mockMvc.perform(get("/api/v1/alumnos"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void filtraPorEstatusSinModificarElRepositorioYConservaElContratoSinFiltro() throws Exception {
        AlumnoRepositoryEnMemoria repository = new AlumnoRepositoryEnMemoria();
        repository.guardar(new Alumno(null, "A005", "Ada", "ada@universidad.mx", EstatusAlumno.ACTIVO));
        repository.guardar(new Alumno(null, "B006", "Grace", "grace@universidad.mx", EstatusAlumno.BAJA));
        List<Alumno> antesDeConsultar = repository.buscarTodos();
        AlumnoService service = new AlumnoService(repository);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AlumnoController(service)).build();

        mockMvc.perform(get("/api/v1/alumnos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(2)));
        mockMvc.perform(get("/api/v1/alumnos?estatus=ACTIVO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].estatus", is("ACTIVO")));
        mockMvc.perform(get("/api/v1/alumnos?estatus=BAJA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].estatus", is("BAJA")));

        assertEquals(antesDeConsultar, repository.buscarTodos());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(
            new AlumnoController(new AlumnoService(new AlumnoRepositoryEnMemoria()))
        ).build();
    }
}
