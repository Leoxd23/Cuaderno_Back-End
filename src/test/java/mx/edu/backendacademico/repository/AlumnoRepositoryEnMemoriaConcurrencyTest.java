package mx.edu.backendacademico.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import org.junit.jupiter.api.Test;

class AlumnoRepositoryEnMemoriaConcurrencyTest {
    @Test
    void soloUnaTareaGuardaLaMismaMatriculaYLaOtraRecibeConflicto() throws Exception {
        var repository = new AlumnoRepositoryEnMemoria();
        var alumno = new Alumno(null, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var listos = new CountDownLatch(2);
        var inicio = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            Callable<Resultado> guardar = () -> {
                listos.countDown();
                if (!inicio.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("No se liberó el inicio de las tareas");
                }
                try {
                    repository.guardar(alumno);
                    return Resultado.GUARDADO;
                } catch (MatriculaDuplicadaException exception) {
                    return Resultado.CONFLICTO;
                }
            };

            var primeraTarea = executor.submit(guardar);
            var segundaTarea = executor.submit(guardar);

            assertTrue(listos.await(5, TimeUnit.SECONDS), "Las tareas no llegaron a la barrera");
            inicio.countDown();

            var resultados = List.of(
                primeraTarea.get(5, TimeUnit.SECONDS),
                segundaTarea.get(5, TimeUnit.SECONDS)
            );
            assertEquals(1, resultados.stream().filter(r -> r == Resultado.GUARDADO).count());
            assertEquals(1, resultados.stream().filter(r -> r == Resultado.CONFLICTO).count());
            assertEquals(1, repository.buscarTodos().size());
        } finally {
            inicio.countDown();
            executor.shutdownNow();
        }
    }

    private enum Resultado {
        GUARDADO, CONFLICTO
    }
}
