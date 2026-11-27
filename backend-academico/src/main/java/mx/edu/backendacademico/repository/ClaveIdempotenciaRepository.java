package mx.edu.backendacademico.repository;

// Guarda en PostgreSQL la reserva de cada clave; no depende de memoria local del proceso.

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClaveIdempotenciaRepository {
    private final JdbcTemplate jdbc;

    public ClaveIdempotenciaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean reservar(String solicitante, String clave, String huella, LocalDateTime momento) {
        // ON CONFLICT espera a la transacción que insertó la misma clave y no aborta la actual.
        int filas = jdbc.update("""
            INSERT INTO claves_idempotencia(solicitante, clave, huella, creada_en)
            VALUES (?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT uk_idempotencia_solicitante_clave DO NOTHING
            """, solicitante, clave, huella, momento);
        return filas == 1;
    }

    public void asociar(String solicitante, String clave, Long alumnoId) {
        // Se ejecuta en la misma transacción que la reserva y la creación del alumno.
        jdbc.update("UPDATE claves_idempotencia SET alumno_id = ? WHERE solicitante = ? AND clave = ?",
            alumnoId, solicitante, clave);
    }

    public Optional<Registro> buscar(String solicitante, String clave) {
        return jdbc.query("SELECT huella, alumno_id FROM claves_idempotencia WHERE solicitante = ? AND clave = ?",
            (fila, numero) -> new Registro(fila.getString("huella"), fila.getObject("alumno_id", Long.class)),
            solicitante, clave).stream().findFirst();
    }

    public record Registro(String huella, Long alumnoId) { }
}
