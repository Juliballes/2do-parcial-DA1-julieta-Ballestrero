# Validación reproducible

## Compilación y pruebas automáticas

Requisitos: JDK 17, plataforma Android 34 y Build Tools 34.0.0.

```bash
./gradlew assembleDebug testDebugUnitTest
```

En Windows:

```bat
gradlew.bat assembleDebug testDebugUnitTest
```

La suite utiliza Room real en Robolectric y una API controlada que lee los mismos assets de la app. Cubre:

- v1 con doce charlas y registro de descarga.
- Falla sin edición previa y reintento posterior.
- Falla con edición previa: conserva datos y favorita de la charla 11.
- v2: título y sala nuevos sin perder la marca de la 11.
- v3: retira la 11, agrega la 90 y conserva las marcas de charlas vigentes.
- Regreso de una charla retirada sin su marca anterior.
- Alta y baja de favoritas durante una descarga suspendida.
- Dos solicitudes durante una descarga producen un solo intento.
- Una carga inicial por instancia de sesión y actualización explícita posterior.
- Una carga inicial fallida no se repite automáticamente.
- v4 vacía válida, incluso después de reabrir la base sin señal.
- Rollback ante una falla de escritura controlada después de empezar el reemplazo.
- Migración desde el esquema v1 conservando registros y favoritas.
- Restauración de filtros con el estado guardado de una Activity y valores iniciales en una nueva tarea.

El reporte queda en `app/build/reports/tests/testDebugUnitTest/index.html`. GitHub Actions adjunta el APK y los reportes de cada ejecución.

## Comprobaciones manuales de aceptación

Estos pasos complementan las pruebas automáticas. El caso de muerte real del proceso debe verificarse en un emulador o dispositivo, no se sustituye por una simple rotación.

### 41 · Sin señal y edición vacía

1. Abrir con servicio disponible y v1; comprobar doce charlas.
2. Abrir una ficha, marcarla y regresar. En Mesa apagar el servicio; Actualizar.
3. Resultado esperado: lista, detalle y favorita conservados, con aviso de datos guardados y reintento.
4. Encender el servicio, elegir v4 y actualizar. Debe mostrar `Sin charlas programadas`.
5. Apagar el servicio y reabrir la tarea. La edición vacía sigue siendo válida y se informa el uso de datos guardados.
6. Solo para probar una instalación sin datos, usar los comandos destructivos de preparación indicados en la consigna. Debe ofrecer Reintentar.

### 42 · Actualizaciones y marcas durante la espera

1. Descargar v1 y marcar la 11 y otra charla vigente, como la 12.
2. Descargar v2: la 11 conserva la marca, muestra `Compose en producción avanzada` y `Sala Puerto`.
3. Descargar v3: la 11 desaparece del catálogo y de Mi agenda; la 90 aparece sin marcar; la 12 conserva su marca.
4. Volver a v1: la 11 regresa sin marcar.
5. Activar demora larga. Iniciar una actualización y, durante la espera, agregar o quitar marcas de charlas vigentes. Al finalizar deben respetarse las últimas decisiones.

### 43 · Filtros, ficha y recuperación de tarea

1. En Programa elegir Día 2 y Datos. Deben aparecer dos resultados.
2. Abrir `Cachés para viajeros`, volver y rotar. Deben mantenerse ambos filtros y los dos resultados.
3. Abrir nuevamente la ficha, presionar Inicio y ejecutar:

```bash
adb shell am kill ar.jornadas
```

4. Regresar desde Recientes. Debe recuperarse la ficha; al volver, Día 2 y Datos.
5. Finalizar la tarea y abrir una nueva. Debe iniciar sin filtros.

### 44 · Contador y solicitudes simultáneas

1. Abrir en un proceso nuevo y consultar el contador de Mesa: un intento automático.
2. Rotar, abrir fichas y cambiar de pestañas: el contador no aumenta.
3. Activar demora larga y tocar Actualizar dos veces durante la espera: aumenta un intento.
4. Editar los filtros durante la espera: siguen respondiendo.
5. Después de éxito o falla, Actualizar permite un nuevo intento.

## Actualización de una instalación existente

Compilar ambas versiones con la misma clave de firma. Instalar primero la base v1, descargar y marcar charlas, y actualizar con `adb install -r` usando el APK corregido. No desinstalar ni borrar datos: se debe comprobar que la migración conserva la agenda.
