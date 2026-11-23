# Comunidad Conectada

Aplicación Android para residentes de edificios, villas y condominios. Su objetivo es facilitar la comunicación vecinal, gestionar información comunitaria y registrar alertas y solicitudes de mantenimiento con permisos por comunidad.

**Asignatura** Fundamentos de Programación en Android  
**Docente** Benjamín Mora Torres  
**Integrantes** Karla Carrasco, Alejandro Rojas y Juan Tapia

## Funcionalidades

| Módulo | Alcance |
| --- | --- |
| Acceso y administración | Registro, verificación de correo, recuperación de contraseña, incorporación por solicitud y gestión vecinal. |
| Inicio y chat | Novedades y mensajes de la comunidad en tiempo real. |
| Ventas y anuncios | Publicaciones de residentes y canal oficial de administración. |
| Encuestas y mantenimiento | Voto por usuario, resultados controlados y seguimiento de reportes con historial. |
| Alertas SOS | Registro en Realtime Database, atención administrativa e indicador de recepción MQTT. |
| Perfil y accesibilidad | Edición de nombre y ajustes de tamaño de texto, tema y contraste. |

## Arquitectura y requisitos

Android usa Java 17, XML, AndroidX, Material Components y MVVM. Los Fragments presentan la interfaz, los ViewModels conservan los estados y el repositorio conecta Firebase Authentication, Realtime Database y Firebase Cloud Messaging (FCM). Las reglas RTDB verifican identidad, rol, membresía y comunidad. Eclipse Paho suscribe el dispositivo al tópico MQTT. Las herramientas de confianza se encuentran en `functions/` y se ejecutan con Node.js 22.

- Android Studio, plataforma API 37 y Build Tools 36.0.0.
- Android 8.0 o posterior para instalar el APK. Java 17 es el nivel del código fuente. Gradle usa un JDK compatible con Android Studio.
- Gradle Wrapper 9.5.0 y Android Gradle Plugin 9.3.3.
- Node.js 22, npm y Firebase CLI si se van a ejecutar las herramientas locales o Emulator Suite.

## Compilar y configurar Firebase

Abre la raíz del proyecto en Android Studio y sincroniza Gradle. Desde PowerShell, ejecuta el siguiente comando.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

En modo normal, coloca localmente el `google-services.json` de tu proyecto Firebase en `app/`. Configura el paquete `com.example.comunidadconectada`, Authentication por correo y contraseña y la URL de Realtime Database. Usa las [reglas locales](database.rules.json) como base para una instancia autorizada. Conserva la configuración y cualquier credencial de servidor fuera del repositorio. La app sin configuración muestra una indicación de instalación. [Manual de instalación y modo emulador](docs/MANUAL_INSTALACION.md)

Para probar sin usar Firebase real, utiliza una copia sin `app/google-services.json`, el proyecto ficticio `demo-comunidad` y `-PcommunityEmulators=true`. El manual explica cómo iniciar Auth y Database, preparar los datos ficticios y conectar Android mediante ADB. Las dependencias de Node se reproducen con `npm --prefix functions ci` y sus pruebas unitarias con `npm --prefix functions test`.

## Alertas y notificaciones

El equipo utiliza un broker Eclipse Mosquitto en una red local de pruebas. Un SOS se registra primero en Realtime Database con publicación pendiente. Para publicar su identificador en MQTT se ejecuta `spark:delivery` desde un entorno confiable autorizado. El emisor usa TLS, QoS 1 y un tópico por comunidad. El móvil señala la recepción al estar suscrito y visible. La publicación del broker y la recepción de otro dispositivo se verifican por separado.

MQTT funciona únicamente dentro de la red local configurada. Su disponibilidad fuera de esa red no está garantizada. Para recibir alertas se necesitan credenciales individuales del broker. La notificación local del SOS sigue pendiente. Los avisos de anuncios por FCM requieren un emisor confiable y permisos del dispositivo. La tabla de QA distingue las pruebas disponibles de las verificaciones pendientes. [Configuración Spark y MQTT](docs/FIREBASE_SPARK.md) · [Resultados QA](docs/MATRIZ_PRUEBAS_QA.md)

## Instalar el APK de prueba

El APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`. En Android 8.0 o posterior, abre el APK y sigue el aviso del sistema para permitir instalaciones desde la aplicación que abrió el archivo. Si prefieres instalar desde el equipo, conecta el teléfono con depuración USB y ejecuta el siguiente comando.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Es una compilación de depuración para evaluación académica. La variante de evaluación se compila con configuración cliente de Firebase real y funciona sin Emulator Suite ni ADB. El modo emulador es una variante separada para pruebas locales y requiere Emulator Suite y los reversos ADB. El acceso a la comunidad depende de una cuenta habilitada.

## Documentación

- [Manual de usuario](docs/MANUAL_USUARIO.md) y [manual de administrador](docs/MANUAL_ADMINISTRADOR.md).
- [Arquitectura técnica](docs/ARQUITECTURA_TECNICA.md), [informe final](docs/INFORME_FINAL.md) y [guía de demostración](docs/GUIA_DEMOSTRACION.md).
