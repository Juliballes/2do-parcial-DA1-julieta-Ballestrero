# Correcciones de Jornadas del Sur

Se conserva la aplicación recibida: Kotlin, Compose, ViewModel, Retrofit, servicio local y Room. No se modifica el laboratorio ni la plantilla de impresión legacy.

## 41 · Programa sin señal

- El catálogo sigue mostrando la información de Room aunque falle una actualización. El aviso informa que se utilizan datos guardados y mantiene disponible el reintento.
- La tabla `estado_programa` registra que hubo una descarga exitosa, incluso si contenía cero charlas. Así v4 es una edición válida y no se confunde con una instalación que nunca descargó.
- Una falla de red o de escritura no sustituye el programa anterior. La carga se termina en `finally`; la cancelación se propaga.
- La base pasa de versión 1 a 2 mediante `MIGRACION_1_2`, que conserva todas las charlas y sus favoritas. Si ya había filas, registra la edición previa. No se usa migración destructiva.

## 42 · Agenda personal

- Cada respuesta es una instantánea completa: se reemplaza el catálogo dentro de una transacción.
- Las favoritas se leen de la base cuando se aplica la respuesta, después de esperar la red. Se conservan por ID estable, aunque cambien título o sala.
- Las charlas retiradas se eliminan, incluidas sus marcas. Si una vuelve en otra edición, aparece sin marcar.
- Una marca agregada o quitada mientras se espera la respuesta se respeta. La transacción serializa esa aplicación con las otras operaciones de Room y evita estados parciales.

## 43 · Volver al punto de lectura

- Día y tema se mantienen en `SavedStateHandle`, con cambios a través de funciones del ViewModel.
- El filtro de tema deja de ser un `remember` local que desaparecía al salir del catálogo.
- Se conserva la navegación ya implementada con `rememberSaveable`: sección e ID de detalle. La ficha se recupera desde Room mediante ese ID.
- El estado de lectura pertenece a la tarea de Android. Una tarea restaurada puede recuperarlo; una nueva empieza con Ambos días y Todos.

## 44 · Control de descargas

- `Servicios.obtener()` comparte una instancia por proceso, utilizando el contexto de aplicación.
- El Repository registra mediante `AtomicBoolean` que la carga inicial ya fue solicitada en este proceso, tanto si tuvo éxito como si falló.
- La Activity solicita esa carga desde `onCreate()`, después de aplicar el extra de disponibilidad del laboratorio. Se eliminan las cargas adicionales de `onStart()` y `LaunchedEffect` del catálogo.
- `MutableStateFlow.compareAndSet` reserva la descarga antes de su primera suspensión. Si ya existe una en curso, otro toque retorna sin encolarse ni iniciar una segunda petición.
- Después de finalizar, `finally` libera el estado de carga. Los filtros no se deshabilitan durante la espera.

## Recorrido para defender la app

1. La pantalla comunica Actualizar al ViewModel.
2. El ViewModel inicia la operación en `viewModelScope`.
3. El Repository evita descargas simultáneas y consulta `ProgramaApi` con Retrofit.
4. `ServicioLocal` responde desde los JSON de assets y contabiliza el intento.
5. El DAO aplica la instantánea y las favoritas en una transacción de Room.
6. El Flow de Room publica los registros; la interfaz los observa con `collectAsStateWithLifecycle()`.

Las operaciones suspendidas de Room y Retrofit administran su ejecución asíncrona. El trabajo de red se realiza antes de abrir la transacción de base de datos.

## Validación

Las pruebas reproducibles y los escenarios manuales están en [VALIDACION.md](VALIDACION.md). El workflow de GitHub compila el APK y ejecuta la suite JVM con Robolectric. El resultado de cada ejecución puede revisarse en Actions.
