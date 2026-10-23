package mx.edu.backendacademico.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SaludoController.class)
class SaludoControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void saludoDevuelveMensajeEnJson() throws Exception {
        mockMvc.perform(get("/api/v1/saludo"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.mensaje").value("Hola backend"));
    }

    @Test
    void rutaNoDefinidaDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe"))
            .andExpect(status().isNotFound());
    }
}
