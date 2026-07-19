# RememBel

**Tu memoria de audio.** Graba en segundo plano y recupera exactamente lo que pasó en un momento concreto del pasado.

## ¿Qué es esto?

RememBel es una app Android pensada para quien necesita recordar algo que se dijo en un momento
concreto y no lo apuntó a tiempo:

- **Personas mayores** que no recuerdan bien lo que les dijo su médico, su hija o su vecino.
- **Estudiantes** que quieren repasar lo que explicó el profesor en clase.
- **Cualquiera** que alguna vez ha pensado "¿qué me dijeron exactamente hace un rato?".

La app graba continuamente en trozos de 15 minutos y te permite recuperar y escuchar
cualquier intervalo de tiempo concreto (día, hora de inicio, hora de fin), sin necesidad
de haber pulsado "grabar" en el momento exacto en que pasó lo importante.

## Funcionalidades

- **Tres modos de grabación**: constante (manual), horario fijo diario, o duración determinada.
- **Recuperación por intervalo**: elige día y horas, y la app recompone el audio de ese rango exacto.
- **Biblioteca personal**: guarda los audios recuperados en carpetas, con crear/renombrar/mover/borrar.
- **Reproductor completo**: velocidad ajustable (0.75x–2x), saltos de ±10 segundos.
- **Azulejo de Ajustes Rápidos**: empieza/para la grabación sin abrir la app.
- **Privacidad por diseño**: todo el audio se queda en el propio dispositivo, sin conexión a
  internet, sin analítica, sin anuncios. Protección contra capturas de pantalla.
- **Retención automática**: los trozos de grabación en bruto se borran solos pasados 7 días.

## Capturas de pantalla

_(Pendiente: añade aquí 2-3 capturas del móvil mostrando la pantalla principal, la biblioteca,
y el reproductor)_

## Requisitos

- Android 8.0 (API 26) o superior.
- Permiso de micrófono (obligatorio para el funcionamiento de la app).

## Compilar el proyecto

1. Clona este repositorio: `git clone https://github.com/TU_USUARIO/RememBel.git`
2. Ábrelo con Android Studio (versión reciente recomendada).
3. Deja que Gradle sincronice las dependencias.
4. Conecta un dispositivo Android (o usa un emulador) y ejecuta la app.

## Arquitectura, en resumen

- **`RecordingService`**: servicio en primer plano (`foreground service`) que graba en trozos
  de 15 minutos alineados al reloj, usando `MediaRecorder`.
- **`AudioRecuperador`**: motor de recuperación — localiza los trozos reales en disco, los
  recorta con `MediaExtractor`/`MediaMuxer` y los une en un único archivo para el intervalo pedido.
- **`AlarmScheduler`** + **`GrabacionReceiver`**: gestionan las alarmas del sistema para el modo
  de horario fijo y duración limitada, con reprogramación automática diaria.
- **`PantallaBiblioteca`**: gestor de archivos en Jetpack Compose para los audios guardados.
- Estado reactivo con `StateFlow` (`RecordingService.estaGrabando`) para mantener la interfaz
  siempre sincronizada con la realidad del servicio, sin importar qué lo dispare (botón, alarma,
  azulejo).

## Limitaciones conocidas

Este proyecto se ha probado principalmente en un dispositivo Xiaomi (MIUI), que aplica una
gestión de batería más agresiva que el Android estándar:

- El arranque automático tras reiniciar el móvil puede no ser fiable en algunos MIUI, incluso
  con los permisos correctos concedidos — es una restricción del fabricante, no de la app.
- Se recomienda desactivar las restricciones de batería para RememBel en los ajustes del
  fabricante, para que la grabación en segundo plano no se interrumpa.

## Privacidad

Toda la grabación, procesamiento y almacenamiento ocurre **en el propio dispositivo**. La app
no se conecta a ningún servidor, no usa analítica ni publicidad, y no comparte datos con terceros.

**Importante**: graba audio ambiente, incluyendo a otras personas presentes. El usuario es
responsable de conocer y respetar las leyes de su país sobre consentimiento de grabación de
conversaciones.

## Apoya el proyecto

_(Próximamente: enlaces de GitHub Sponsors / Ko-fi)_

## Licencia

Este proyecto está publicado bajo la licencia **GPL-3.0** — ver el archivo [LICENSE](LICENSE)
para el texto completo. En resumen: eres libre de usar, modificar y distribuir este código,
pero cualquier versión derivada que distribuyas debe seguir siendo de código abierto bajo la
misma licencia.