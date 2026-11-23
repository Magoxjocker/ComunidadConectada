# Comunidad Conectada en Firebase Spark

Android usa Java 17, XML, MVVM y API mínima 26. Authentication comprueba correo y contraseña; las reglas de Realtime Database autorizan el acceso a cada comunidad y validan las escrituras. La configuración Firebase de este proyecto contiene Authentication y Realtime Database. El código histórico de Functions permanece disponible para revisión.

## Funcionalidad en Spark

| Función | Servicio |
| --- | --- |
| Registro, ingreso, verificación y recuperación | Firebase Authentication y perfil propio con rol inicial de vecino |
| Incorporación y suspensión de vecinos | Solicitud verificada, aprobación del administrador de la comunidad y control de membresía activa en cada operación |
| Chat, ventas y anuncios | Realtime Database. Las ventas pertenecen a su autor y solo la administración publica anuncios. |
| Encuestas | Realtime Database. El voto privado y su incremento se guardan juntos. El resultado solo se lee si es público o si el usuario es administrador de la comunidad. |
| Mantenimiento | Realtime Database. Solo el vecino propietario y la administración pueden leer un reporte. El estado y el evento de auditoría se guardan juntos. |
| SOS | Realtime Database y, si se configura, MQTT. La app registra la alerta en la comunidad. Solo un emisor de confianza confirma la publicación MQTT. |
| Accesibilidad y navegación | Componentes locales de Android |

Las cuotas de Spark siguen vigentes. Los límites de tamaño de campos y las consultas acotadas reducen el consumo. Un usuario habilitado puede enviar muchos registros válidos; las reglas de RTDB no constituyen un limitador de frecuencia por usuario. Revisa el consumo de conexiones, almacenamiento, descarga y correo en Firebase Console. El SOS es una alerta comunitaria. [Planes de Firebase](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans) · [Condiciones de reglas RTDB](https://firebase.google.com/docs/database/security/rules-conditions)

## Primer administrador

1. En un equipo administrativo configura ADC para una cuenta de servicio autorizada. La [guía de creación del administrador](CREACION_ADMINISTRADOR.md) indica los permisos IAM, la impersonación, las alternativas y el comando completo para **Comunidad Central**.
2. Una vez autorizado, usa `npm --prefix functions run admin -- CODIGO_COMUNIDAD CORREO "Nombre de la persona" "Unidad" "Nombre de la comunidad"`. El script exige `CC_PROJECT_ID`, `CC_DATABASE_URL` y la identidad esperada en `CC_ADMIN_SERVICE_ACCOUNT`. Valida el destino y la ausencia de variables de emulador antes de usar ADC.
3. El script confirma el perfil, la membresía activa y los *custom claims*. Guarda el nombre visible en `communityData/CODIGO_COMUNIDAD/metadata/displayName`. Las reglas locales agregan lectura del nombre visible exclusivamente para miembros activos y verificados de la comunidad. La publicación de ese cambio es independiente del aprovisionamiento. Android presenta el código cuando todavía no puede leer el nombre.
4. En Android, inicia sesión, solicita el correo de verificación, confirma el enlace y actualiza la sesión. Las reglas exigen correo verificado, coincidencia de comunidad, rol en perfil y membresía, membresía activa y claims de administrador.
5. Facilita el código de comunidad a los vecinos. Cada vecino lo ingresa después de verificar su correo, envía su solicitud y comparte el identificador que muestra la app por el canal oficial. La administración valida la vivienda y usa **Agregar vecino** con ese identificador. La incorporación vincula el perfil y la membresía en una sola escritura.

## Pruebas locales

El [manual de instalación](MANUAL_INSTALACION.md) reúne los comandos de Node.js 22, Firebase Emulator Suite e instrumentación Android en el proyecto ficticio demo-comunidad. La [matriz QA](MATRIZ_PRUEBAS_QA.md) registra los resultados y omisiones de la estabilización.

## Publicación posterior de reglas

1. Autoriza el cambio de producción y exporta un respaldo de los datos y de las reglas vigentes desde Firebase Console.
2. Si existen clientes o Functions antiguos con acceso mediante Admin SDK, prepara primero su retirada y coordina una ventana de cambio. El SDK administrativo tiene permisos propios y puede escribir por fuera de estas reglas.
3. Revisa las cuentas y membresías anteriores. Cada perfil debe tener `name`, `unit`, `community` y `role`. Cada miembro debe tener `name`, `unit`, `email`, `role` y `active`. Corrige respaldos parciales por un proceso administrativo autorizado.
4. Abre **Firebase Console → ComunidadConectada → Realtime Database → Reglas** y selecciona la instancia `comunidadconectada-35c3c-default-rtdb`. Copia todo el contenido de [database.rules.json](../database.rules.json), revisa la validación de la consola y pulsa **Publicar** dentro de la ventana autorizada.
5. Distribuye el APK compilado con la configuración real y comprueba registro, inicio de sesión, aprobación y escrituras de vecino y administrador. Si necesitas revertir, restaura la versión de reglas registrada para el cambio y considera los datos creados durante la ventana.

### Encuestas anteriores

Los clientes pueden consultar las encuestas previas y sus totales públicos. Votar o acceder a totales privados de una encuesta anterior requiere reconstruir sus totales desde las boletas históricas. Con las escrituras antiguas detenidas, exporta un respaldo JSON de un nodo `communityData/CODIGO_COMUNIDAD`. En un equipo administrativo ejecuta `node functions/plan-spark-poll-migration.js CODIGO_COMUNIDAD RUTA_RESPALDO_LOCAL.json`. La herramienta genera un mapa de rutas y valores, comprueba la revisión y coherencia de la encuesta y preserva las boletas. Revisa y conserva el resultado en almacenamiento protegido. Cuando se autorice la migración, aplícalo mediante una única escritura multipath de Admin SDK, usando el respaldo vigente. El plan debe probarse primero en un emulador. Los respaldos contienen información privada.

## FCM desde un equipo confiable

FCM acepta mensajes HTTP v1 desde un servidor de confianza con OAuth 2.0. Un equipo existente puede ejecutar Admin SDK, sin una infraestructura de pago. La consola de Firebase permite comunicaciones manuales generales; una entrega segmentada por pertenencia a la comunidad requiere el emisor autorizado.

Con las credenciales administrativas configuradas y los dispositivos registrados, ejecuta `npm --prefix functions run spark:delivery -- fcm CODIGO_COMUNIDAD ID_ANUNCIO` desde ese equipo. El comando selecciona dispositivos recientes de miembros activos y envía referencias al anuncio. Android lee su contenido con la sesión autorizada y verifica que el permiso de notificaciones esté concedido. Una respuesta aceptada por FCM representa aceptación del envío, y el dispositivo aún depende de Google Play Services, conectividad y permisos del sistema. El comando es manual. Para automatizarlo se necesita un proceso en un equipo que permanezca disponible, con reintentos, deduplicación y control de acceso. [Precios de Firebase](https://firebase.google.com/pricing) · [Entorno de servidor FCM](https://firebase.google.com/docs/cloud-messaging/server-environment)

## MQTT académico con Mosquitto

Usa Mosquitto de código abierto en una máquina institucional existente. Habilita un listener TLS en el puerto 8883, un certificado de un dominio confiable para Android, autenticación obligatoria y ACL por comunidad. Restringe el acceso al laboratorio o a su VPN. La aplicación comprueba certificado y nombre del servidor.

```conf
listener 8883
certfile /ruta/protegida/fullchain.pem
keyfile /ruta/protegida/privkey.pem
allow_anonymous false
password_file /ruta/protegida/mosquitto.passwd
acl_file /ruta/protegida/mosquitto.acl
```

1. Crea un usuario distinto por cuenta móvil con `mosquitto_passwd`, introduciendo la contraseña de forma interactiva. Concede solo `topic read comunidades/CODIGO_COMUNIDAD/alertas` mediante la ACL.
2. Crea una identidad de publicación independiente con permiso `topic write comunidades/CODIGO_COMUNIDAD/alertas`.
3. Con Admin SDK, incorpora para cada cuenta autorizada el registro privado `mqttAccess/UID` con `community`, `host` en formato DNS:8883, `topic` igual al de su comunidad, `username` y `token` con la contraseña individual. Las reglas permiten leer el registro solo al titular activo. Conserva las credenciales de publicación en un almacén externo a Android.
4. Configura `CC_MQTT_HOST`, `CC_MQTT_PUBLISHER_USERNAME` y `CC_MQTT_PUBLISHER_PASSWORD` en el entorno del emisor confiable. Con la autorización correspondiente, ejecuta `npm --prefix functions run spark:delivery -- mqtt CODIGO_COMUNIDAD ID_SOS`. El emisor publica solo el identificador con QoS 1 y registra la confirmación del broker. Se puede repetir una entrega si se pierde la confirmación del estado. La aplicación usa el identificador para deduplicar.
5. Si suspendes una cuenta, elimina sus credenciales de RTDB y revócalas en el broker, desconectando la sesión MQTT. El bloqueo de Firebase impide nuevas lecturas de la contraseña, y la revocación del broker impide que se sigan usando copias anteriores. Usa una identidad exclusiva por cuenta para que la revocación sea individual.

Si el broker está pendiente, la app informa el estado de MQTT y las alertas se siguen consultando en Realtime Database. La entrega automática y el acceso externo dependen de la configuración y disponibilidad del equipo anfitrión. [Configuración oficial de Mosquitto](https://mosquitto.org/man/mosquitto-conf-5.html)
