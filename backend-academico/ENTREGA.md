# Entrega del backend académico — Reto 12

Instrucciones para construir, probar, iniciar, comprobar y detener el sistema desde una carpeta limpia, sin depender del IDE.

## 1. Requisitos

| Herramienta | Uso |
|---|---|
| JDK 25 | Compila el proyecto. `pom.xml` fija `maven.compiler.release=21`: el bytecode y la API utilizada corresponden a Java 21, sin funciones preview. |
| Docker con Compose v2 | Ejecuta PostgreSQL 17 y la aplicación. La imagen usa `eclipse-temurin:25-jre`, así que el JAR de Java 21 corre sobre Java 25. Docker también lo necesitan las pruebas con Testcontainers. |
| Git | Obtiene el repositorio. |
| Bash y curl | Ejecutan `evidencia/demostracion.sh`. En Windows, Git Bash incluye ambos. |

No se necesita Maven instalado: `mvnw` descarga la versión fijada en `.mvn/wrapper`.

Comprueba el JDK antes de construir:

```bash
java -version      # debe indicar 25
./mvnw -v          # en Windows: .\mvnw.cmd -v
```

## 2. Obtener el código

```bash
git clone https://github.com/Leoxd23/Cuaderno_Back-End.git
cd Cuaderno_Back-End
git checkout reto-12
cd backend-academico
```

Todos los comandos siguientes se ejecutan desde `backend-academico`.

## 3. Construir y probar

Con Docker iniciado:

```bash
./mvnw clean test package        # Windows: .\mvnw.cmd clean test package
```

El resultado es `target/backend-academico-0.0.12-SNAPSHOT.jar`. Las pruebas que respaldan esta entrega son:

| Prueba | Qué demuestra |
|---|---|
| `SeguridadTest` | 401 sin credenciales; 403 por rol y por CSRF ausente; el alumno consulta su registro, recibe 403 para uno ajeno o inexistente y ya no puede listar a todos; el docente consulta cualquiera y recibe 404 ante una ausencia. |
| `AlumnoControllerTest` | 400 con `DATOS_INVALIDOS` y 503 con `BASE_NO_DISPONIBLE` cuando JPA no puede abrir la transacción. |
| `CacheAlumnosTest` | Una segunda consulta usa la caché, una escritura la invalida y una ausencia no se almacena. |
| `PostgresIntegrationTest` | Cupo, inscripción duplicada y migraciones Flyway sobre PostgreSQL real. |

## 4. Iniciar

```bash
docker compose build
docker compose up -d
docker compose ps                # postgres healthy y app en ejecución
docker compose logs app          # Flyway, arranque de Spring y puerto 8080
```

La aplicación responde en `http://localhost:8080`. PostgreSQL ya no publica el puerto 5432 al anfitrión: sólo la aplicación lo alcanza dentro de la red de Compose, con el nombre `postgres`.

## 5. Peticiones y evidencia

```bash
bash evidencia/demostracion.sh               # incluye la falla controlada de PostgreSQL
bash evidencia/demostracion.sh --sin-falla   # sólo los casos de la API
```

El script obtiene un token CSRF por usuario, usa una matrícula nueva en cada ejecución e imprime `[OK]` o `[FALLA]` en cada observación. Termina con código 0 sólo si todas coinciden.

| Observación | Petición | Esperado |
|---|---|---|
| Éxito | `POST /api/v1/alumnos` como docente con CSRF | 201 y `Location` |
| Validación | `POST` con matrícula vacía, nombre de una letra y correo inválido | 400 `DATOS_INVALIDOS` |
| Ausencia | `GET /api/v1/alumnos/999999999` como docente | 404 `ALUMNO_NO_ENCONTRADO` |
| Conflicto | `POST` repitiendo la matrícula | 409 `MATRICULA_DUPLICADA` |
| Seguridad: autenticación | `GET` sin credenciales | 401 |
| Seguridad: rol | alumno con token CSRF válido intenta crear | 403 |
| Seguridad: origen | docente intenta crear sin token CSRF | 403 |
| Seguridad: Actuator | docente consulta `/actuator/metrics` | 403 (admin: 200) |
| Propietario | alumno consulta su registro / uno ajeno / un id inexistente / la lista | 200 / 403 / 403 / 403 |
| Falla de PostgreSQL | readiness / liveness / escritura | 503 / 200 / 503 `BASE_NO_DISPONIBLE` |
| Recuperación | readiness y escritura tras `docker compose start postgres` | 200 / 201 |

`requests.http` contiene las mismas peticiones para quien prefiera el cliente HTTP del IDE, pero no es necesario.

## 6. Falla controlada de PostgreSQL y readiness

El script ejecuta `docker compose stop postgres` con la aplicación en marcha. Para repetirlo a mano:

```bash
docker compose stop postgres
curl -i http://localhost:8080/actuator/health/readiness               # 503
curl -u admin:admin123 http://localhost:8080/actuator/health/readiness # detalle: db DOWN
curl -i http://localhost:8080/actuator/health/liveness                # 200
docker compose start postgres
curl -i http://localhost:8080/actuator/health/readiness               # 200 tras reconectar
```

**Qué ocurre.** El grupo `readiness` incluye `readinessState` y `db`. Cuando el indicador `db` no obtiene conexión, readiness pasa a DOWN y responde 503: la instancia indica que no debe recibir tráfico. `liveness` no incluye la base y sigue en 200, porque reiniciar la aplicación no repara una base caída y, con varias réplicas, provocaría reinicios simultáneos. Mientras tanto, una escritura responde 503 con `BASE_NO_DISPONIBLE` en lugar de un 500 genérico. Al volver PostgreSQL, Hikari crea conexiones nuevas, readiness regresa a 200 y la aplicación sigue funcionando sin reiniciarse.

`spring.datasource.hikari.connection-timeout=5000` limita a 5 segundos la espera por una conexión. Con el valor por omisión (30 s), cada comprobación de readiness y cada petición tardarían medio minuto en fallar. `depends_on: service_healthy` sólo coordina el primer arranque y no protege contra esta falla posterior.

## 7. Decisión sobre la caché

Se conserva la caché local de `alumnos` (por id) y `alumnosLista`, con estas condiciones:

- **La clave es suficiente.** El valor guardado no depende de quién consulta: la regla de propietario se evalúa en el controlador después de obtener el alumno y en cada petición, sin almacenar decisiones de acceso. Un alumno no puede recibir de la caché un registro que la regla le negaría.
- **El valor es inmutable.** `Alumno` es un `record` y las listas son copias no modificables, así que nadie puede alterar una entrada compartida.
- **La invalidación es completa y ocurre al confirmar.** Crear, actualizar y eliminar vacían ambas cachés, y `TransactionAwareCacheManagerProxy` difiere esa invalidación hasta el commit. Las excepciones, como una ausencia, no se almacenan.
- **Los límites son explícitos.** No hay TTL, límite de tamaño ni coherencia entre réplicas. Con una sola instancia y un catálogo pequeño es aceptable. Con dos o más réplicas, cada JVM tendría datos distintos tras una escritura: en ese caso conviene desactivarla (`spring.cache.type=none`) o usar una caché compartida con TTL. Tampoco se usa para decidir cupo ni para comprobar unicidad: esas reglas consultan PostgreSQL.

## 8. Mejora de acceso por propietario

En el Reto 10, la regla de propietario existía en `/expediente`, pero un ALUMNO podía leer cualquier registro con `GET /api/v1/alumnos/{id}` o con el listado. Además, un 404 frente a un 403 le revelaba qué identificadores existían. Ahora:

- La cadena de filtros permite a ALUMNO únicamente `GET /api/v1/alumnos/*`. El listado queda para DOCENTE y ADMIN.
- El controlador toma la identidad de `Authentication` y compara la matrícula del registro con el usuario. En el laboratorio, el usuario `alumno` es dueño del registro con matrícula `ALUMNO`.
- Un registro ajeno y uno inexistente producen el mismo 403 `ACCESO_DENEGADO`, así que el alumno no puede enumerar ids. DOCENTE y ADMIN conservan el 404 informativo.

## 9. Cierre

```bash
docker compose down        # detiene y elimina contenedores; conserva el volumen datos_academicos
docker compose down -v     # además elimina los datos
```

La aplicación usa `server.shutdown=graceful` con 20 segundos por fase y Compose concede `stop_grace_period: 30s`: las peticiones en curso terminan antes de que el contenedor se detenga. El volumen conserva los datos entre reinicios, pero no es un respaldo. Un respaldo exige generar una copia (por ejemplo, `pg_dump`) y comprobar que se puede restaurar.
