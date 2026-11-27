#!/usr/bin/env bash
# Evidencia reproducible del Reto 12, sin IDE: sólo curl y docker compose.
# Requiere la aplicación iniciada con "docker compose up -d" desde backend-academico.
# Uso: bash evidencia/demostracion.sh            (incluye la falla controlada de PostgreSQL)
#      bash evidencia/demostracion.sh --sin-falla (omite detener PostgreSQL)
set -u
# docker compose debe encontrar compose.yaml: se trabaja desde backend-academico.
cd "$(dirname "$0")/.." || exit 1

BASE="${BASE_URL:-http://localhost:8080}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
TOTAL=0
FALLAS=0

# 1. Envía una petición, guarda el cuerpo en $TMP/cuerpo e imprime sólo el status HTTP.
http() {
    curl -s -o "$TMP/cuerpo" -w '%{http_code}' --max-time 40 "$@"
}

# 2. Compara el status esperado con el obtenido y muestra el código de problema, si existe.
comprobar() {
    local descripcion="$1" esperado="$2" obtenido="$3" codigo
    TOTAL=$((TOTAL + 1))
    codigo="$(sed -n 's/.*"codigo" *: *"\([^"]*\)".*/\1/p' "$TMP/cuerpo" 2>/dev/null)"
    if [ "$esperado" = "$obtenido" ]; then
        printf '[OK]    %-62s %s %s\n' "$descripcion" "$obtenido" "$codigo"
    else
        printf '[FALLA] %-62s esperado %s, obtenido %s %s\n' "$descripcion" "$esperado" "$obtenido" "$codigo"
        FALLAS=$((FALLAS + 1))
    fi
}

esperar_readiness() {
    local intento
    for intento in $(seq 1 60); do
        [ "$(http "$BASE/actuator/health/readiness")" = "200" ] && return 0
        sleep 2
    done
    return 1
}

# 3. Cada usuario obtiene un token CSRF ligado a su propia cookie de sesión.
token_csrf() {
    curl -s -c "$3" -b "$3" -u "$1:$2" "$BASE/api/v1/csrf" | sed -n 's/.*"token" *: *"\([^"]*\)".*/\1/p'
}

crear() {
    curl -s -o "$TMP/cuerpo" -w '%{http_code}' --max-time 40 -X POST "$BASE/api/v1/alumnos" \
        -u "$1" -b "$2" -H "X-CSRF-TOKEN: $3" -H 'Content-Type: application/json' -d "$4"
}

id_del_cuerpo() {
    sed -n 's/.*"id" *: *\([0-9]*\).*/\1/p' "$TMP/cuerpo"
}

echo "== Disponibilidad inicial ($BASE)"
if ! esperar_readiness; then
    echo "La aplicación no está lista; ejecuta docker compose up -d y revisa docker compose logs app"
    exit 1
fi
comprobar "Readiness con PostgreSQL disponible" 200 "$(http "$BASE/actuator/health/readiness")"

DOCENTE="$TMP/docente.cookies"
ALUMNO="$TMP/alumno.cookies"
TOKEN_DOCENTE="$(token_csrf docente docente123 "$DOCENTE")"
TOKEN_ALUMNO="$(token_csrf alumno alumno123 "$ALUMNO")"
MATRICULA="E$(date +%s)$RANDOM"

echo "== Casos de la API"
comprobar "Éxito: docente crea $MATRICULA" 201 \
    "$(crear docente:docente123 "$DOCENTE" "$TOKEN_DOCENTE" "{\"matricula\":\"$MATRICULA\",\"nombre\":\"Ada Lovelace\",\"correo\":\"ada@u.mx\"}")"
ID_AJENO="$(id_del_cuerpo)"
comprobar "Validación: matrícula vacía, nombre corto y correo inválido" 400 \
    "$(crear docente:docente123 "$DOCENTE" "$TOKEN_DOCENTE" '{"matricula":"","nombre":"A","correo":"incorrecto"}')"
comprobar "Ausencia: docente consulta un id inexistente" 404 \
    "$(http -u docente:docente123 "$BASE/api/v1/alumnos/999999999")"
comprobar "Conflicto: docente repite la matrícula $MATRICULA" 409 \
    "$(crear docente:docente123 "$DOCENTE" "$TOKEN_DOCENTE" "{\"matricula\":\"$MATRICULA\",\"nombre\":\"Otra persona\",\"correo\":\"otra@u.mx\"}")"

echo "== Rechazos de seguridad"
comprobar "Sin credenciales (no autenticado)" 401 \
    "$(http "$BASE/api/v1/alumnos/$ID_AJENO")"
comprobar "Alumno con token CSRF válido intenta crear (rol insuficiente)" 403 \
    "$(crear alumno:alumno123 "$ALUMNO" "$TOKEN_ALUMNO" "{\"matricula\":\"X$MATRICULA\",\"nombre\":\"Sin permiso\",\"correo\":\"x@u.mx\"}")"
comprobar "Docente sin token CSRF intenta crear (origen no verificado)" 403 \
    "$(http -X POST "$BASE/api/v1/alumnos" -u docente:docente123 -b "$DOCENTE" -H 'Content-Type: application/json' \
        -d "{\"matricula\":\"Y$MATRICULA\",\"nombre\":\"Sin token\",\"correo\":\"y@u.mx\"}")"
comprobar "Docente consulta métricas de Actuator (sólo ADMIN)" 403 \
    "$(http -u docente:docente123 "$BASE/actuator/metrics")"
comprobar "Administrador consulta métricas de Actuator" 200 \
    "$(http -u admin:admin123 "$BASE/actuator/metrics")"

echo "== Acceso por propietario"
# El usuario de laboratorio "alumno" es dueño del registro con matrícula ALUMNO.
ESTADO="$(crear docente:docente123 "$DOCENTE" "$TOKEN_DOCENTE" '{"matricula":"ALUMNO","nombre":"Alumno de laboratorio","correo":"alumno@u.mx"}')"
if [ "$ESTADO" = "201" ]; then
    ID_PROPIO="$(id_del_cuerpo)"
else
    # En una segunda ejecución el registro ya existe (409); se busca su id en el listado.
    curl -s -u docente:docente123 "$BASE/api/v1/alumnos" -o "$TMP/lista"
    ID_PROPIO="$(grep -o '"id" *: *[0-9]*, *"matricula" *: *"ALUMNO"' "$TMP/lista" | sed 's/"id" *: *\([0-9]*\),.*/\1/')"
fi
comprobar "Alumno consulta su propio registro (id $ID_PROPIO)" 200 \
    "$(http -u alumno:alumno123 "$BASE/api/v1/alumnos/$ID_PROPIO")"
comprobar "Alumno consulta un registro ajeno (id $ID_AJENO)" 403 \
    "$(http -u alumno:alumno123 "$BASE/api/v1/alumnos/$ID_AJENO")"
comprobar "Alumno consulta un id inexistente: mismo 403, no revela ausencia" 403 \
    "$(http -u alumno:alumno123 "$BASE/api/v1/alumnos/999999999")"
comprobar "Alumno intenta listar a todos los alumnos" 403 \
    "$(http -u alumno:alumno123 "$BASE/api/v1/alumnos")"

if [ "${1:-}" != "--sin-falla" ]; then
    echo "== Falla controlada de PostgreSQL"
    docker compose stop postgres >/dev/null 2>&1
    comprobar "Readiness sin PostgreSQL (deja de recibir tráfico)" 503 \
        "$(http "$BASE/actuator/health/readiness")"
    echo "        Detalle para ADMIN: $(curl -s --max-time 40 -u admin:admin123 "$BASE/actuator/health/readiness")"
    comprobar "Liveness sin PostgreSQL (no exige reiniciar la aplicación)" 200 \
        "$(http "$BASE/actuator/health/liveness")"
    comprobar "Escritura sin PostgreSQL" 503 \
        "$(crear docente:docente123 "$DOCENTE" "$TOKEN_DOCENTE" "{\"matricula\":\"Z$MATRICULA\",\"nombre\":\"Sin base\",\"correo\":\"z@u.mx\"}")"
    docker compose start postgres >/dev/null 2>&1
    if esperar_readiness; then
        comprobar "Readiness tras reiniciar sólo PostgreSQL" 200 "$(http "$BASE/actuator/health/readiness")"
    else
        comprobar "Readiness tras reiniciar sólo PostgreSQL" 200 "sin respuesta"
    fi
    comprobar "Escritura después de la recuperación" 201 \
        "$(crear docente:docente123 "$DOCENTE" "$TOKEN_DOCENTE" "{\"matricula\":\"Z$MATRICULA\",\"nombre\":\"Con base\",\"correo\":\"z@u.mx\"}")"
fi

echo "== Resultado: $TOTAL observaciones, $FALLAS fallas"
[ "$FALLAS" -eq 0 ]
