package mx.edu.backendacademico.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SaludoControllerTest {
    @Test
    void saludoDevuelveMensajeEnJson() throws Exception {
        mockMvc().perform(get("/api/v1/saludo"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.mensaje").value("Hola backend"));
    }

    @Test
    void rutaNoDefinidaDevuelve404() throws Exception {
        mockMvc().perform(get("/api/v1/no-existe"))
            .andExpect(status().isNotFound());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new SaludoController()).build();
    }
}
