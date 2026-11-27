package mx.edu.backendacademico.exception;

// Distingue una regla de propiedad del objeto de los rechazos de rol de la cadena de filtros.

public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
