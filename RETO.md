# Reto de la Unidad 3

`Alumno.reactivar()` conserva los datos del alumno y devuelve una nueva instancia
con estatus `ACTIVO`. Al ser un `record`, el objeto original no cambia; la prueba
comprueba tanto el estado nuevo como la conservación del estado `BAJA` original.

La igualdad de un `record` considera todos sus componentes. Por eso, dos alumnos
con el mismo id y matrícula pero distinto correo no son iguales. Las pruebas de
`AlumnoTest` verifican ambos comportamientos.
