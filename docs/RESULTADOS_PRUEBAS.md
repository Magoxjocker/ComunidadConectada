# Resultados de pruebas

Fecha de ejecución en Chile **22 de noviembre de 2026**. Proyecto Firebase de prueba `demo-comunidad`. El despliegue está configurado para Node 22 y los emuladores locales utilizaron Node 24.16.0, con Firebase CLI 15.37.0. La compilación usa Gradle 9.5.0, AGP 9.3.3, Android SDK 37.0 y JDK 25 del IDE para producir Java 17.

| ID | Módulo y objetivo | Procedimiento | Resultado esperado | Resultado observado | Estado | Evidencia |
| --- | --- | --- | --- | --- | --- | --- |
| QA-01 | Formularios, permisos y porcentajes | JUnit y `npm test --prefix functions` | Entradas y duplicados rechazados, porcentajes correctos | 5 pruebas JVM y 3 de Node correctas | Aprobado | `app/build/test-results/testDebugUnitTest` y salida de Node |
| QA-02 | Auth y aislamiento | `test:rules` y `test:flows` | Acceso según perfil, pertenencia y rol | 3 pruebas de reglas y 5 de comandos correctas, incluidas consultas paginadas privadas, suspensión y acceso cruzado | Aprobado en emuladores | Firebase Emulator Suite |
| QA-03 | Arranque y preferencias | JUnit instrumentado en Android API 37 | Pantalla de configuración y preferencias después de recrear | Se comprobó arranque sin configuración, navegación accesible y persistencia del alto contraste después de recrear. Giro físico y API 26 pendientes. | Aprobado en el AVD | `app/build/qa/android-startup.xml` en la entrega |
| QA-04 | Chat y ventas | Flujo local y dos dispositivos | Mensaje visible en otras sesiones y cambio por propietario | El backend confirmó idempotencia, atribución, propiedad y las operaciones de ventas. Dos AVD simultáneos intercambiaron mensajes usando Firebase local. | Aprobado en emuladores | `functions/integration/flows.test.js` y `app/build/qa/functional-chat-*.log` |
| QA-05 | Anuncios y FCM | Publicar y abrir desde notificación | Título, resumen y detalle correctos | El servidor permitió crear, editar y eliminar anuncios al administrador y rechazó mutaciones del vecino. Entrega con Firebase y dispositivo configurados pendiente. | Parcial | Reglas y funciones locales |
| QA-06 | SOS y MQTT | Registrar SOS, autorizar suscripción y comprobar entrega con broker | Registro en RTDB, acuse, recibo y reconexión | En entorno local se confirmó idempotencia, estado pendiente, atención y cierre administrativos y JWT acotado. TLS, acuse y reconexión con EMQX pendiente. | Parcial | `functions/integration/flows.test.js` |
| QA-07 | Encuestas | Enviar dos votos simultáneos y leer agregado | Un voto y protección de boletas | Un único voto contabilizado. Los conteos ocultos y las boletas ajenas quedan protegidos. | Aprobado en emuladores | Pruebas de funciones y reglas |
| QA-08 | Mantenimiento | Resolver reporte y reintentar la misma actualización | Historial sin duplicados y lectura privada | Cambio único por ID y acceso limitado al vecino y administración | Aprobado en emuladores | Pruebas de funciones y reglas |
| QA-09 | Accesibilidad | Calcular contrastes y probar con TalkBack, Scanner y fuente ampliada | Contraste y operación accesible | Ocho combinaciones calculadas. La verificación manual con tecnologías de asistencia exige un recorrido en dispositivo. | Parcial | [Accesibilidad](ACCESIBILIDAD.md) |
| QA-10 | Usabilidad | Ejecutar protocolo con participantes | Éxito, tiempo y dificultades por tarea | Protocolo preparado. Sesiones con participantes pendientes. | Pendiente | [Plan de pruebas](PLAN_PRUEBAS.md) |

Compilación `assembleDebug`, pruebas `testDebugUnitTest` y `lintDebug` aprobadas, con cero hallazgos de Lint. Las dos pruebas instrumentadas se ejecutaron por separado según el modo de compilación, en el AVD Android 17.

## Corrección de arranque y navegación

El proyecto local carecía de `app/google-services.json`. Logcat registró que Firebase no encontró opciones de inicialización. `MainActivity` es la actividad `MAIN/LAUNCHER` y el enrutamiento existente usa `FragmentManager` y `BottomNavigationView`. El estado de configuración mostraba exclusivamente el diagnóstico y el acceso a Accesibilidad. La navegación autenticada elegía Inicio para ambos roles y recordaba un solo destino de regreso.

El arranque presenta el acceso y explica el bloqueo si faltan datos de Firebase. La comprobación de una sesión almacenada valida el usuario, el token y la membresía, con un límite de 15 segundos. Una sesión habilitada llega a Inicio o Administración según su rol. El historial de navegación se conserva al recrear la actividad para la misma cuenta, comunidad y rol. Cerrar sesión elimina el historial protegido.

| Modo Android en AVD API 37 | Comprobación | Resultado |
| --- | --- | --- |
| Debug normal | Aviso de configuración, acceso visible y bloqueado, entrada y regreso de Accesibilidad, alto contraste tras recrear | Aprobada |
| Firebase Emulator Suite | Arranque sin sesión, acceso al formulario de registro, vecino habilitado, pestañas y secciones de Más, retorno y cierre de sesión | Aprobada |
| Firebase Emulator Suite | Arranque administrativo, Vecinos, Anuncios y retorno después de recrear | Aprobada |
| Firebase Emulator Suite | Correo pendiente de verificación y cuenta pendiente de habilitación, navegación protegida oculta | Aprobada |
| Firebase Emulator Suite | Mensaje visible en dos cuentas consecutivas | Aprobada |

`assembleDebug`, `testDebugUnitTest` y `lintDebug` completaron correctamente. La validación instrumentada ejecutó cinco pruebas en dos variantes. Los XML se conservan en `app/build/qa/navigation-startup.xml` y `app/build/qa/navigation-sessions.xml`. El registro del AVD de prueba no mostró excepciones fatales del paquete de la aplicación.

La validación sobre un Firebase real requiere incluir `app/google-services.json` y tener habilitados Authentication y Realtime Database. Sigue la [guía de Firebase](CONFIGURACION_FIREBASE.md).

## Corrección integral de autenticación y módulos

Las acciones del formulario dependían únicamente del estado de Firebase y de carga. El APK sin configuración deshabilitaba el envío aunque los datos fueran válidos. El APK configurado permitía pulsarlo con los campos vacíos. La validación reactiva ahora controla los botones según los campos y la operación en curso. Al enviar un formulario válido sin Firebase, aparece el aviso de configuración sin otorgar una sesión.

El registro guarda un perfil pendiente asociado al UID antes de enviar la verificación. Si falla ese guardado, la app sigue enviando la verificación y permite completar los datos más adelante. La edición del nombre actualiza el perfil y la membresía en la misma escritura. La vivienda, el acceso y el rol se resuelven en el servidor.

| Módulo | Estado | Alcance comprobado |
| --- | --- | --- |
| Acceso y registro | Implementado y probado en emuladores | Campos vacíos e inválidos, envío habilitado con datos válidos, error de credenciales y reintento, solicitud de recuperación, registro y aviso de verificación. |
| Administración y navegación | Implementado y probado en emuladores | Aprovisionamiento con Admin SDK repetible, claims y membresía habilitada, rutas de administrador y vecino, retorno, verificación pendiente y cierre de sesión. |
| Chat | Implementado y probado en emuladores | Dos AVD simultáneos con cuentas distintas y confirmación bidireccional de un mensaje único. Atribución, fecha, historial y aislamiento cubiertos por pruebas locales. |
| Ventas | Implementado y probado en emuladores | Operaciones de servidor de creación, edición, eliminación, propiedad, precio y estados Disponible, Reservado y Vendido. |
| Anuncios | Implementado y probado en emuladores | Operaciones de servidor de creación, edición, eliminación y tres prioridades. Control de escritura por rol. |
| FCM | Bloqueado por configuración externa | Registro y entrega reales requieren Firebase configurado, despliegue de Functions y un dispositivo con servicios de Google. Pendientes el token real, permiso, recepción y apertura desde notificación. |
| SOS y MQTT | Implementado, pendiente de prueba real | Se registró el SOS con protección de duplicados y se probaron atención, cierre y alcance del JWT. Publicación TLS, acuse, recepción y reconexión requieren EMQX. |
| Encuestas | Implementado y probado en emuladores | Creación, cierre, voto concurrente único, agregación y privacidad. |
| Mantenimiento | Implementado y probado en emuladores | Creación, observación, resolución, historial sin duplicados y lectura privada. |
| Perfil | Implementado y probado en emuladores | Visualización de nombre y vivienda, actualización del nombre y protección de rol y vivienda. |
| Accesibilidad | Implementado, pendiente de prueba real | Se probaron los tres tamaños, modo oscuro, alto contraste, altura táctil de 48 dp y foco del botón habilitado. TalkBack y Accessibility Scanner requieren comprobación manual en dispositivo. |

La compilación final generó `app/build/outputs/apk/debug/app-debug.apk`, con API mínima 26 y destino 37. Android Lint terminó sin hallazgos. Se aprobaron 5 pruebas de JVM, 11 pruebas de Node y Firebase local, 5 casos instrumentados con Firebase, 2 del APK sin configuración y el intercambio bidireccional ejecutado una vez por AVD. Logcat se revisó en los AVD de prueba. Los reportes disponibles están en `app/build/qa`.

La prueba bidireccional usa `CommunityFlowTest#exchangesMessagesAcrossTwoEmulators`. Ambos AVD apuntan al mismo proyecto local `demo-comunidad` por ADB. Se ejecuta la misma prueba en paralelo con `chatPeer=admin` y `chatPeer=neighbor` y un valor compartido de `chatExchange`.

Para usar cuentas reales, agrega `app/google-services.json`, habilita Authentication y Realtime Database, despliega las reglas y las funciones actualizadas y aprovisiona el primer administrador. Las cuentas de QA usan exclusivamente Firebase Emulator Suite. [Configuración de Firebase](CONFIGURACION_FIREBASE.md) · [Aprovisionamiento](CREACION_ADMINISTRADOR.md)

## Integración del SDK con la configuración de Firebase Console

Se comprobó que `app/google-services.json` coincide con `com.example.comunidadconectada` y que Gradle lo procesa. El archivo incluye los datos del cliente Android y carece de `project_info.firebase_url`. El arranque muestra un aviso específico para Realtime Database. El archivo permanece excluido de Git.

Google Services **4.6.0** y Firebase BoM **35.2.0** ya estaban declarados. Se confirmaron las versiones efectivas de Auth **25.0.0**, Realtime Database **23.0.0**, Cloud Functions **23.0.0** y Cloud Messaging **26.0.2**. No se agregaron dependencias.

| Comprobación | Resultado |
| --- | --- |
| Configuración Gradle y resolución | `processDebugGoogleServices` y `dependencies` completaron correctamente. |
| APK debug y Lint | Compilación aprobada. Android Lint registró cero hallazgos. |
| Pruebas JVM | 5 aprobadas. |
| Arranque con el JSON registrado | 1 prueba instrumentada aprobada en un AVD con API 37. Se obtuvieron los SDK de Auth, Functions y Messaging y se comprobó el diagnóstico de RTDB. Evidencia en `app/build/qa/firebase-sdk-startup.xml`. |

La verificación de Auth remoto requiere activar Correo electrónico y contraseña y disponer de la URL de la base para que la app pueda validar la membresía. La conexión, reglas desplegadas y operaciones de RTDB quedan pendientes de verificar en Firebase Console. La recepción de FCM y la ejecución remota de Functions necesitan el backend desplegado. Consulta la [guía de configuración](CONFIGURACION_FIREBASE.md).

Para repetir las pruebas en Android abre el proyecto y usa los comandos de la [guía de inicio](../README.md). Para repetir las pruebas de integración, sigue la sección de emuladores de la [guía Firebase](CONFIGURACION_FIREBASE.md).
