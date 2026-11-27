package mx.edu.backendacademico.service;

// Asocia clave, solicitante, huella del contenido y alumno creado en una sola transacción.

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.exception.ClaveIdempotenciaException;
import mx.edu.backendacademico.repository.ClaveIdempotenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreacionIdempotenteService {
    private static final Pattern FORMATO_CLAVE = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private final ClaveIdempotenciaRepository claves;
    private final AlumnoService alumnos;
    private final Clock reloj;

    public CreacionIdempotenteService(ClaveIdempotenciaRepository claves, AlumnoService alumnos, Clock reloj) {
        this.claves = claves;
        this.alumnos = alumnos;
        this.reloj = reloj;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Resultado crear(String solicitante, String clave, String matricula, String nombre, String correo) {
        if (clave == null || !FORMATO_CLAVE.matcher(clave).matches()) {
            throw new ClaveIdempotenciaException("CLAVE_IDEMPOTENCIA_INVALIDA",
                "Idempotency-Key admite de 1 a 100 letras, dígitos, guion o guion bajo");
        }
        String huella = huella(matricula, nombre, correo);
        // 1. Reserva la clave; otra sesión con la misma clave espera el commit o rollback de ésta.
        if (claves.reservar(solicitante, clave, huella, LocalDateTime.now(reloj))) {
            // 2. Reserva, alumno y asociación se confirman juntos o se revierten juntos.
            var creado = alumnos.crear(matricula, nombre, correo);
            claves.asociar(solicitante, clave, creado.id());
            return new Resultado(creado, false);
        }
        // 3. La clave ya estaba confirmada: sólo se repite el resultado del mismo contenido.
        var registrada = claves.buscar(solicitante, clave).orElseThrow(() ->
            new IllegalStateException("La clave reservada no es visible después del conflicto"));
        if (!registrada.huella().equals(huella)) {
            throw new ClaveIdempotenciaException("CLAVE_IDEMPOTENCIA_REUTILIZADA",
                "La clave ya se usó con un contenido distinto");
        }
        return new Resultado(alumnos.buscar(registrada.alumnoId()), true);
    }

    static String huella(String matricula, String nombre, String correo) {
        // Prefija cada longitud: contenidos distintos no producen el mismo texto canónico.
        var canonico = new StringBuilder();
        for (String valor : List.of(matricula, nombre, correo)) {
            canonico.append(valor.length()).append(':').append(valor).append(';');
        }
        try {
            var sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(canonico.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("La JVM no ofrece SHA-256", ex);
        }
    }

    public record Resultado(Alumno alumno, boolean repetido) { }
}
