# Informe técnico — Comunidad Conectada

> **Referencia histórica.** Para los resultados de estabilización, riesgos y alcance académico actuales, consulta el [informe final](INFORME_FINAL.md).

## Propósito y problema

Los residentes de un edificio, villa o condominio necesitan distinguir información oficial y emergencias de los mensajes cotidianos. Cada flujo tiene responsables, niveles de privacidad y formas de seguimiento diferentes. Comunidad Conectada reúne esos flujos con identidad individual y pertenencia verificada por la administración.

## Requerimientos e implementación

El proyecto usa Java 17, API mínima 26, API de compilación y destino 37, Gradle 9.5.0, AGP 9.3.3 y Material 3 con XML. ViewBinding vincula las vistas y RecyclerView presenta listados. Los ViewModels gestionan estados, sesiones y suscripciones. El repositorio agrupa Auth, RTDB y Functions. El servidor valida identidad, comunidad, propiedad y privilegios. Consulta los [requerimientos](REQUERIMIENTOS.md), la [arquitectura](ARQUITECTURA.md) y el [modelo](MODELO_DATOS.md).

La administración se aprovisiona con Admin SDK. Al registrarse, un vecino crea su cuenta y su perfil pendiente, verifica su correo y espera autorización. Puede actualizar su nombre desde el perfil una vez habilitado. La función replica el cambio en el perfil y en el registro de membresía mediante una sola escritura. Mantenimiento y votos tienen acceso acotado. Chat, productos, anuncios y alertas se comparten entre integrantes activos.

## Entrega de eventos

FCM dirige anuncios a instalaciones autorizadas y la notificación abre el detalle. El SOS se guarda primero. La función publica por MQTT con el mismo ID que usa Android para deduplicar mensajes. La interfaz distingue persistencia, acuse del broker, recepción local y atención administrativa. MQTT reconecta con credenciales de vida corta. Estos estados hacen visible el progreso durante fallos parciales.

## Diseño, seguridad y pruebas

La interfaz se adapta por tamaño de texto, tema y contraste. El diseño de acceso usa reglas RTDB y funciones autorizadas. La evidencia se conserva en [resultados](RESULTADOS_PRUEBAS.md) y se relaciona con la [rúbrica](MATRIZ_RUBRICA.md). La entrega real depende de Firebase, FCM, un broker EMQX con TLS y dispositivos configurados. La validación humana con TalkBack y usabilidad requiere ejecutar el protocolo. El mercado presenta fichas textuales.

## Defensa y conclusión

Demuestra ingreso autorizado frente a un intento de gestión desde una cuenta vecina. Publica un anuncio, sigue el ID de un SOS hasta el acuse y compara la pantalla del receptor. Envía dos votos simultáneos y contrasta el agregado. Resuelve un reporte y verifica su historial desde la sesión que lo creó. Los estados de entrega, el voto transaccional y la administración acotada resuelven necesidades distintas de la comunidad con una sola identidad.
