-- Una clave pertenece a un solicitante; la huella fija el contenido aceptado con ella.
CREATE TABLE claves_idempotencia (
    id BIGSERIAL PRIMARY KEY,
    solicitante VARCHAR(100) NOT NULL,
    clave VARCHAR(100) NOT NULL,
    huella VARCHAR(64) NOT NULL,
    alumno_id BIGINT,
    creada_en TIMESTAMP NOT NULL,
    CONSTRAINT uk_idempotencia_solicitante_clave UNIQUE (solicitante, clave),
    CONSTRAINT ck_idempotencia_huella CHECK (LENGTH(huella) = 64),
    -- Si el alumno se elimina, la clave deja de reservar ese resultado.
    CONSTRAINT fk_idempotencia_alumno FOREIGN KEY (alumno_id) REFERENCES alumnos(id) ON DELETE CASCADE
);
