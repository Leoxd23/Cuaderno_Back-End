package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.InscripcionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
class InscripcionConcurrentePostgresTest {
    @Autowired private InscripcionService servicio;
    @Autowired private AlumnoService alumnos;
    @Autowired private InscripcionRepository inscripciones;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @Test
    void admiteSoloUnAlumnoCuandoDosSolicitudesCompitenPorUnLugar() throws Exception {
        String token = UUID.randomUUID().toString().replace("-", "");
        String matriculaUno = "U8" + token.substring(0, 12);
        String matriculaDos = "U8" + token.substring(12, 24);
        String claveMateria = "R08" + token.substring(0, 12);
        Long materiaId = null;
        Long alumnoUnoId = null;
        Long alumnoDosId = null;
        ExecutorService tareas = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);

        try {
            materiaId = jdbc.queryForObject(
                "INSERT INTO materias(clave, nombre, creditos, cupo) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, claveMateria, "Materia de prueba " + token, 5, 1);
            var alumnoUno = alumnos.crear(matriculaUno, "Alumno de prueba uno", "uno-" + token + "@example.test");
            var alumnoDos = alumnos.crear(matriculaDos, "Alumno de prueba dos", "dos-" + token + "@example.test");
            alumnoUnoId = alumnoUno.id();
            alumnoDosId = alumnoDos.id();

            Long materiaFinal = materiaId;
            Long alumnoUnoFinal = alumnoUnoId;
            Long alumnoDosFinal = alumnoDosId;
            List<Future<Long>> resultados = List.of(
                tareas.submit(() -> admitirTrasBarreras(alumnoUnoFinal, materiaFinal, listos, inicio)),
                tareas.submit(() -> admitirTrasBarreras(alumnoDosFinal, materiaFinal, listos, inicio)));

            assertTrue(listos.await(10, TimeUnit.SECONDS), "Las dos tareas deben estar listas");
            try (var bloqueador = dataSource.getConnection()) {
                bloqueador.setAutoCommit(false);
                try (var consulta = bloqueador.prepareStatement(
                        "SELECT id FROM materias WHERE id = ? FOR UPDATE")) {
                    consulta.setLong(1, materiaId);
                    try (var fila = consulta.executeQuery()) {
                        assertTrue(fila.next(), "La materia de prueba debe existir");
                    }
                }
                inicio.countDown();
                assertTrue(esperarBloqueosPostgres(2, 10, TimeUnit.SECONDS),
                    "Las dos sesiones PostgreSQL deben esperar el bloqueo de la fila");
                bloqueador.commit();
            }

            int admitidos = 0;
            int rechazadosPorCupo = 0;
            for (Future<Long> resultado : resultados) {
                try {
                    assertNotNull(resultado.get(20, TimeUnit.SECONDS));
                    admitidos++;
                } catch (ExecutionException ex) {
                    assertTrue(ex.getCause() instanceof OperacionAcademicaException);
                    var error = (OperacionAcademicaException) ex.getCause();
                    assertEquals("SIN_CUPO", error.getCodigo());
                    rechazadosPorCupo++;
                }
            }

            assertEquals(1, admitidos);
            assertEquals(1, rechazadosPorCupo);
            assertEquals(1, inscripciones.countByMateriaId(materiaId));
        } finally {
            inicio.countDown();
            tareas.shutdownNow();
            tareas.awaitTermination(10, TimeUnit.SECONDS);
            if (materiaId != null) {
                jdbc.update("DELETE FROM inscripciones WHERE materia_id = ?", materiaId);
            }
            if (alumnoUnoId != null) {
                jdbc.update("DELETE FROM alumnos WHERE id = ?", alumnoUnoId);
            }
            if (alumnoDosId != null) {
                jdbc.update("DELETE FROM alumnos WHERE id = ?", alumnoDosId);
            }
            if (materiaId != null) {
                jdbc.update("DELETE FROM materias WHERE id = ?", materiaId);
            }
        }
    }

    private Long admitirTrasBarreras(Long alumnoId, Long materiaId, CountDownLatch listos,
                                     CountDownLatch inicio) throws InterruptedException {
        listos.countDown();
        if (!inicio.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("No se liberó la barrera de admisión");
        }
        return servicio.inscribir(alumnoId, materiaId);
    }

    private boolean esperarBloqueosPostgres(int esperados, long tiempo, TimeUnit unidad)
            throws InterruptedException {
        long limite = System.nanoTime() + unidad.toNanos(tiempo);
        while (System.nanoTime() < limite) {
            Long bloqueos = jdbc.queryForObject("""
                SELECT count(*)
                FROM pg_stat_activity
                WHERE datname = current_database()
                  AND pid <> pg_backend_pid()
                  AND wait_event_type = 'Lock'
                  AND lower(query) LIKE '%materias%'
                """, Long.class);
            if (bloqueos != null && bloqueos >= esperados) return true;
            Thread.sleep(25);
        }
        return false;
    }
}
