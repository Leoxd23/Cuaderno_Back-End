package mx.edu.backendacademico.exception;

public class ClaveIdempotenciaException extends RuntimeException {
    private final String codigo;

    public ClaveIdempotenciaException(String codigo, String mensaje) {
        super(mensaje);
        // El código distingue una clave mal formada de una clave usada con otro contenido.
        this.codigo = codigo;
    }

    public String getCodigo() { return codigo; }
}
