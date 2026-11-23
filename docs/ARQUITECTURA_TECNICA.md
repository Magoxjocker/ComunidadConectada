# Arquitectura técnica

## Componentes y capas

| Componente | Responsabilidad |
| --- | --- |
| `MainActivity` y `ui/` | Una Activity coordina el arranque, la barra inferior, el historial de navegación y los Fragments. XML, ViewBinding y RecyclerView forman la interfaz. |
| `SessionViewModel` | Gestiona Auth y los estados de carga, configuración, ingreso, verificación, perfil, membresía pendiente o suspendida, error y acceso activo. |
| `FeedViewModel` y `OwnedReports` | Gestionan el estado de los listados y los cursores. Mantenimiento privado usa rangos autorizados por propietario y orden cronológico en Android. |
| `FormDialog` y `OperationViewModel` | Validación de formularios, seguimiento del guardado, borrador en memoria e identificador del intento. |
| `CommunityRepository` y `SparkCommands` | Encapsulan Firebase Auth y RTDB. Los comandos obtienen la cuenta y la membresía para construir escrituras. RTDB valida los permisos y el contenido en el servidor. |
| `domain/` | Validación de entradas y lógica de encuestas. |
| `AlertSubscription`, `AlertChannelViewModel` y `CommunityMessagingService` | Suscripción y recibos MQTT, y recepción de referencias a anuncios por FCM. |

El código reside en [el módulo Android](../app/src/main/java/com/example/comunidadconectada). Gradle usa AGP 9.3.3, Java 17, mínimo API 26 y destino API 37. Firebase BoM 35.2.0 agrupa Auth, Database y Messaging. Paho MQTT 1.2.5 implementa el cliente. La carpeta `functions/` contiene herramientas locales, pruebas y código histórico. La aplicación trabaja en Spark enviando sus operaciones directamente a Auth y RTDB. [Gradle Android](../app/build.gradle) · [Dependencias Node.js](../functions/package.json)

## Modelo de datos y permisos

| Ruta RTDB | Uso y acceso |
| --- | --- |
| `accounts/{uid}` y `joinRequests/{uid}` | Perfil propio y solicitud de incorporación. La administración habilita la solicitud autorizada de su comunidad. |
| `communityData/{comunidad}/metadata/displayName` y `members/{uid}` | Nombre visible y pertenencia. La identidad se vincula mediante el UID. La lista de vecinos requiere permisos administrativos. |
| `communityData/{comunidad}/messages`, `marketplace` y `announcements` | Chat, ventas y anuncios comunitarios. Las ventas se modifican por su propietario y los anuncios por la administración. |
| `communityData/{comunidad}/polls`, `pollLedger/{encuesta}/votes/{uid}` y `pollTotals/{encuesta}` | Configuración, boleta privada y agregado. El voto y su incremento se coordinan para impedir duplicados. La lectura del total depende de su visibilidad y del rol. |
| `communityData/{comunidad}/reports` | Reportes visibles para su propietario y la administración. La actualización administrativa de estado y el evento de historial se escriben juntos. |
| `communityData/{comunidad}/alerts` | Registro comunitario, estado operativo y confirmación de entrega MQTT cuando existe. |
| `communityData/{comunidad}/devices/{uid}` y `mqttAccess/{uid}` | Registro de dispositivos FCM y acceso MQTT individual. Las reglas protegen su lectura y el emisor confiable obtiene los destinos autorizados. |

Las reglas deniegan por defecto. Los recursos protegidos verifican correo confirmado, comunidad del perfil, coincidencia de rol y membresía activa. Las operaciones administrativas comprueban adicionalmente los *custom claims* de rol y comunidad. El registro vecinal crea un perfil mínimo y la aprobación enlaza la cuenta y su membresía de manera atómica. Los permisos de Admin SDK se controlan por IAM y deben limitarse a operadores autorizados. [Reglas RTDB](../database.rules.json) · [Aprovisionamiento](CREACION_ADMINISTRADOR.md)

## Flujos y comportamiento ante fallos

- **Sesión.** Firebase Auth identifica al usuario. El cliente actualiza la sesión tras verificar correo, observa perfil y membresía y dirige al vecino activo a Inicio o al administrador activo a Administración. Los estados pendientes y suspendidos quedan fuera de las pantallas protegidas.
- **Paginación de mantenimiento privado.** Cada consulta de propietario está acotada a 50 registros y comparte el límite con la siguiente. El cliente elimina los duplicados, ordena por `createdAt` e ID y presenta de 50 en 50, del más reciente al más antiguo. La primera página requiere leer todo el historial propio.
- **Guardado.** La operación se identifica por cuenta, comunidad, acción y registro cuando corresponde. Volver del formulario conserva el seguimiento. Un fallo permite reintentar con el mismo ID en la sesión activa. Firebase confirma la escritura o informa del fallo. La app conserva el estado y borrador en memoria y la cobertura no incluye reinicio del proceso. [Comportamiento de RTDB sin conexión](https://firebase.google.com/docs/database/android/offline-capabilities)
- **FCM.** El emisor manual en `spark-delivery.js` usa Admin SDK desde un entorno confiable y selecciona dispositivos de miembros activos. Android verifica acceso antes de abrir el anuncio. La aceptación del servicio y la visualización en el dispositivo son etapas distintas. [Entorno de servidor FCM](https://firebase.google.com/docs/cloud-messaging/server-environment)
- **MQTT.** Requiere broker con TLS y ACL por comunidad, cuentas de suscripción y una identidad emisora independiente. El emisor publica el identificador con QoS 1. El acuse del broker y la recepción local se registran por separado. La suspensión en Firebase requiere revocación adicional en el broker para detener el uso de credenciales ya emitidas. [Configuración Spark y canales opcionales](FIREBASE_SPARK.md)

La [matriz QA](MATRIZ_PRUEBAS_QA.md) distingue las pruebas de reglas, la instrumentación Android y las comprobaciones externas pendientes.
