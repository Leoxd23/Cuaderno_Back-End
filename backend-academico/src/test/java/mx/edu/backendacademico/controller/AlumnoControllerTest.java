package mx.edu.backendacademico.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(
            new AlumnoController(new AlumnoService(new AlumnoRepositoryEnMemoria()))
        ).build();
    }
}
