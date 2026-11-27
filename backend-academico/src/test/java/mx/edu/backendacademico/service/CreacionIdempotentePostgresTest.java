package mx.edu.backendacademico.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import mx.edu.backendacademico.exception.ClaveIdempotenciaException;
import mx.edu.backendacademico.service.CreacionIdempotenteService.Resultado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CreacionIdempotentePostgresTest {
    private static final String DOCENTE = "docente";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // La reserva depende de la unicidad real de PostgreSQL, no de un mapa en memoria.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private CreacionIdempotenteService servicio;
    @Autowired private PlatformTransactionManager transacciones;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void dosSolicitudesSimultaneasConLaMismaClaveCreanUnSoloAlumno() throws Exception {
        String clave = UUID.randomUUID().toString();
        var desenlace = competir(clave,
            new Solicitud("IDEM1", "Ada Lovelace", "ada@u.mx"),
            new Solicitud("IDEM1", "Ada Lovelace", "ada@u.mx"));

        assertNull(desenlace.errorSegunda(), "La repetición no debe fallar");
        assertFalse(desenlace.primera().repetido());
        assertTrue(desenlace.segunda().repetido());
        assertEquals(desenlace.primera().alumno().id(), desenlace.segunda().alumno().id());
        assertEquals(1, contar("SELECT count(*) FROM alumnos WHERE matricula = ?", "IDEM1"));
        assertEquals(1, contar("SELECT count(*) FROM claves_idempotencia WHERE clave = ?", clave));
    }

    @Test
    void laMismaClaveConOtroContenidoSeRechazaAunqueLleguenAlMismoTiempo() throws Exception {
        String clave = UUID.randomUUID().toString();
        var desenlace = competir(clave,
            new Solicitud("IDEM2", "Emmy Noether", "emmy@u.mx"),
            new Solicitud("IDEM2", "Emmy N.", "emmy@u.mx"));

        assertFalse(desenlace.primera().repetido());
        var error = assertInstanceOf(ClaveIdempotenciaException.class, desenlace.errorSegunda());
        assertEquals("CLAVE_IDEMPOTENCIA_REUTILIZADA", error.getCodigo());
        // El rechazo no crea otro alumno ni modifica el registrado con el contenido original.
        assertEquals(1, contar("SELECT count(*) FROM alumnos WHERE matricula = ?", "IDEM2"));
        assertEquals("Emmy Noether", jdbc.queryForObject(
            "SELECT nombre FROM alumnos WHERE matricula = ?", String.class, "IDEM2"));
    }

    @Test
    void laClavePerteneceAlSolicitante() {
        String clave = UUID.randomUUID().toString();
        var docente = servicio.crear(DOCENTE, clave, "IDEM3", "Sophie Germain", "sophie@u.mx");
        var admin = servicio.crear("admin", clave, "IDEM4", "Mary Somerville", "mary@u.mx");

        // La misma clave en otro solicitante es una reserva independiente.
        assertFalse(admin.repetido());
        assertNotEquals(docente.alumno().id(), admin.alumno().id());
        assertTrue(servicio.crear(DOCENTE, clave, "IDEM3", "Sophie Germain", "sophie@u.mx").repetido());
    }

    private Desenlace competir(String clave, Solicitud primera, Solicitud segunda) throws Exception {
        ExecutorService tareas = Executors.newFixedThreadPool(2);
        CountDownLatch reservada = new CountDownLatch(1);
        CountDownLatch confirmar = new CountDownLatch(1);
        var plantilla = new TransactionTemplate(transacciones);

        try {
            // 1. La primera solicitud reserva la clave y crea el alumno, pero todavía no confirma.
            Future<Resultado> original = tareas.submit(() -> plantilla.execute(estado -> {
                var resultado = crear(clave, primera);
                reservada.countDown();
                esperar(confirmar);
                return resultado;
            }));
            assertTrue(reservada.await(10, SECONDS), "La primera solicitud debe reservar la clave");

            // 2. La segunda solicitud usa la misma clave y queda detenida por la reserva abierta.
            Future<Resultado> repetida = tareas.submit(() -> crear(clave, segunda));
            assertTrue(esperarBloqueoPostgres(10, SECONDS),
                "La segunda sesión PostgreSQL debe esperar la reserva de la primera");

            // 3. Al confirmar la primera, la segunda encuentra la clave ya registrada.
            confirmar.countDown();
            Resultado resultadoOriginal = original.get(20, SECONDS);
            try {
                return new Desenlace(resultadoOriginal, repetida.get(20, SECONDS), null);
            } catch (ExecutionException ex) {
                return new Desenlace(resultadoOriginal, null, ex.getCause());
            }
        } finally {
            confirmar.countDown();
            tareas.shutdownNow();
            tareas.awaitTermination(10, SECONDS);
        }
    }

    private Resultado crear(String clave, Solicitud solicitud) {
        return servicio.crear(DOCENTE, clave, solicitud.matricula(), solicitud.nombre(), solicitud.correo());
    }

    private static void esperar(CountDownLatch senal) {
        try {
            if (!senal.await(10, SECONDS)) {
                throw new IllegalStateException("No se liberó la confirmación de la primera solicitud");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private boolean esperarBloqueoPostgres(long tiempo, java.util.concurrent.TimeUnit unidad)
            throws InterruptedException {
        long limite = System.nanoTime() + unidad.toNanos(tiempo);
        while (System.nanoTime() < limite) {
            Long bloqueos = jdbc.queryForObject("""
                SELECT count(*)
                FROM pg_stat_activity
                WHERE datname = current_database()
                  AND pid <> pg_backend_pid()
                  AND wait_event_type = 'Lock'
                  AND lower(query) LIKE '%claves_idempotencia%'
                """, Long.class);
            if (bloqueos != null && bloqueos >= 1) return true;
            Thread.sleep(25);
        }
        return false;
    }

    private long contar(String sql, Object... argumentos) {
        return jdbc.queryForObject(sql, Long.class, argumentos);
    }

    private record Solicitud(String matricula, String nombre, String correo) { }

    private record Desenlace(Resultado primera, Resultado segunda, Throwable errorSegunda) { }
}
