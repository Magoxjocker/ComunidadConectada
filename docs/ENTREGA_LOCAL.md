# Entrega local de SOS y anuncios en Windows

El supervisor de Comunidad Conectada se ejecuta bajo la cuenta de Windows que configura las credenciales. Usa Node.js 22, Firebase Admin SDK y Mosquitto. Monitorea únicamente `communityData/comuna-centro`. Android continúa leyendo los registros y mostrando avisos con sus mecanismos actuales.

## Configuración

1. Desde la raíz del repositorio, instala las dependencias con `npm.cmd --prefix functions ci`.
2. Configura [Application Default Credentials (ADC)](CREACION_ADMINISTRADOR.md) para una identidad autorizada a leer los registros y escribir las confirmaciones RTDB con Admin SDK. Si habilitas anuncios, la identidad necesita además permiso para enviar mensajes FCM. Si empleas `GOOGLE_APPLICATION_CREDENTIALS`, guarda el archivo fuera del repositorio con acceso restringido a la cuenta que ejecutará el supervisor.
3. Comprueba el servicio y el puerto de Mosquitto. El estado del servicio y el acceso al puerto se comprueban por separado.

   ```powershell
   Get-Service mosquitto
   Test-NetConnection 192.168.18.9 -Port 8883
   ```

4. Configura el lanzador con la misma cuenta de Windows que usará la tarea.

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\functions\start-spark-local-delivery.ps1 -Configure
   ```

   Introduce el endpoint TLS sin esquema, el usuario MQTT publicador, su contraseña y, si tu CA es privada, la ruta absoluta a su certificado público. El certificado del broker debe incluir en su SAN el nombre DNS o la IP indicada en el endpoint. La ACL del usuario publicador debe conceder escritura en `comunidades/comuna-centro/alertas`.

   El lanzador guarda la contraseña con DPAPI en `%LOCALAPPDATA%\ComunidadConectada\Delivery\mqtt-credential.xml`. Windows la descifra bajo la misma cuenta y equipo. La configuración guarda rutas y ajustes, y el lanzador pasa la contraseña a Node.js mediante el entorno del proceso. Vuelve a ejecutar `-Configure` al rotar la contraseña.

   `RemoteSigned` se aplica al proceso de PowerShell que ejecuta el lanzador local. Si una directiva institucional exige scripts firmados, usa la firma aprobada para el lanzador.

   Para habilitar FCM, introduce una fecha UTC ISO 8601 fija como `2026-11-23T15:00:00Z`, elegida para el comienzo autorizado de la entrega. Se incluyen anuncios publicados desde esa fecha, también al reiniciar. Mantén la misma fecha para recuperar los pendientes. Deja el campo vacío para entregar solo SOS.

También puedes ejecutar `npm.cmd --prefix functions run spark:delivery:watch` con las variables ya cargadas en el proceso. El supervisor valida `CC_PROJECT_ID` y `CC_DATABASE_URL` contra la instancia real del proyecto, y usa `CC_MQTT_HOST`, `CC_MQTT_PUBLISHER_USERNAME`, `CC_MQTT_PUBLISHER_PASSWORD`, `NODE_EXTRA_CA_CERTS`, ADC o `GOOGLE_APPLICATION_CREDENTIALS`, y opcionalmente `CC_ANNOUNCEMENTS_SINCE`.

## Iniciar, detener y comprobar

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\functions\start-spark-local-delivery.ps1
Get-Content "$env:LOCALAPPDATA\ComunidadConectada\Delivery\delivery.log" -Tail 30 -Wait
```

Ejecuta la segunda línea desde otra consola. Usa **Ctrl+C** en la primera para detener el supervisor. Busca `firebase-connected`, `mqtt-ready` y `delivery-confirmed`. Si Mosquitto está caído, la comprobación inicial registra `mqtt-unavailable` y las alertas pendientes generan `delivery-retry` hasta confirmar la entrega. Si la configuración del broker cambia, reinicia después de actualizar las credenciales o el certificado.

### Inicio al iniciar sesión, pendiente de autorización

La configuración propuesta es una tarea **Comunidad Conectada - Entrega local** del Programador de tareas. Usa **Al iniciar sesión** para tu usuario, **Ejecutar solo cuando el usuario haya iniciado sesión** y privilegios normales. Configura la acción siguiente.

| Campo | Valor |
| --- | --- |
| Programa | `powershell.exe` |
| Argumentos | `-NoProfile -NonInteractive -ExecutionPolicy RemoteSigned -File "<raíz del repositorio>\functions\start-spark-local-delivery.ps1"` |
| Iniciar en | `<raíz del repositorio>` |

En **Configuración**, activa el reinicio ante errores cada minuto, desactiva el límite de duración y selecciona **No iniciar una instancia nueva**. Asegúrate de que `node.exe` esté en el PATH de esa cuenta. La tarea seguirá la política de suspensión y cierre de sesión del equipo.

Después de que se autorice y registre la tarea, se puede operar desde PowerShell.

```powershell
$taskName = 'Comunidad Conectada - Entrega local'
Start-ScheduledTask -TaskName $taskName
Get-ScheduledTask -TaskName $taskName | Select-Object TaskName, State
Get-ScheduledTaskInfo -TaskName $taskName
Stop-ScheduledTask -TaskName $taskName
```

## Entrega, recuperación y límites

- **SOS.** El supervisor procesa registros activos y pendientes que contienen `community: "comuna-centro"`. Publica `{"id":"ID_SOS"}` mediante MQTT 5 sobre TLS con QoS 1 y sin retención. MQTT 5 permite distinguir un PUBACK de rechazo por ACL. Ante un PUBACK exitoso registra primero la confirmación en una ruta administrativa y actualiza `delivery` y `brokerAcknowledgedAt` en el SOS. Los clientes desconectados conservan la consulta del historial en RTDB. El PUBACK acredita la recepción del broker. [Códigos MQTT 5 de Mosquitto](https://mosquitto.org/api/files/mqtt_protocol-h.html)
- **Anuncios.** FCM usa el mismo mensaje de datos del emisor manual y selecciona registros recientes de miembros activos, con un envío por token. Persiste los resultados definitivos por hash del token, reintenta los fallos pendientes y registra cantidades y códigos de error. `notificationDispatchedAt` indica que FCM aceptó al menos una solicitud y se resolvieron los fallos pendientes de los destinos vigentes. La aceptación de FCM precede a la entrega al teléfono. [Referencia del SDK de Admin para FCM](https://firebase.google.com/docs/reference/admin/node/firebase-admin.messaging.messaging)
- **Concurrencia.** `sparkDelivery/<comunidad>/<colección>/<id>` conserva reservas temporales y resultados, fuera de las fichas que escribe Android. Las reglas actuales restringen esta ruta a identidades administrativas. El comando manual actual y el supervisor comparten esa reserva. Si se desplegaron las funciones históricas de `functions/delivery.js` o hay emisores de versiones anteriores, coordina su retirada antes de habilitar el supervisor. Mantén sincronizado el reloj del equipo para la expiración de las reservas.
- **Ventana de duplicación.** Un corte entre la aceptación de MQTT o FCM y el registro de su resultado puede repetir ese envío. Las reservas y el registro reducen las repeticiones, pero estos servicios no comparten una transacción. Android ya deduplica por ID. Si el proceso cae con una reserva activa, la recuperación espera hasta su vencimiento de cinco minutos.
- **Operación.** El supervisor lee la colección de alertas al conectar para recuperar pendientes con los índices actuales. Cada canal tiene su propia cola y reintenta con espera creciente de hasta un minuto. Los resultados administrativos y el log local crecen con la actividad del servicio.

## Pruebas aisladas

Ejecuta `npm.cmd --prefix functions test` para la suite unitaria. Para integración, levanta RTDB Emulator en loopback con el proyecto ficticio `demo-comunidad-delivery`. Prepara una configuración de Mosquitto de prueba con TLS, contraseñas ficticias, un puerto libre en `127.0.0.1` y una ACL de publicación y suscripción para el tópico de `comuna-centro`. La prueba inicia y detiene su propia instancia de Mosquitto usando `CC_TEST_MOSQUITTO_EXE` y `CC_TEST_MOSQUITTO_CONFIG`. Configura `FIREBASE_DATABASE_EMULATOR_HOST`, las variables MQTT, la CA de prueba, `CC_TEST_MQTT_SUBSCRIBER_USERNAME` y `CC_TEST_MQTT_SUBSCRIBER_PASSWORD`. Usa una instancia desechable de RTDB, porque la prueba escribe y elimina los datos ficticios de esa comunidad.

```powershell
node --test functions/integration/spark-local-delivery.test.js
```

La integración usa un doble de FCM para probar rechazos parciales, deduplicación, recuperación y limpieza de tokens vencidos. Firebase Emulator Suite no ofrece un emulador FCM. [Emuladores disponibles](https://firebase.google.com/docs/emulator-suite)

## Estado de validación académica

| Flujo | Evidencia | Estado |
| --- | --- | --- |
| MQTT/SOS | Recepción validada en Android físico. La integración aislada cubre MQTT TLS con QoS 1, confirmación, reconexión y recuperación de pendientes. | Validado. |
| FCM individual | FCM-11 confirmó la recepción de un aviso en Android físico. | Validado. |
| FCM automático | Integración aprobada con RTDB Emulator y transporte FCM simulado. Incluye corte para anuncios previos, destinatarios de la comunidad y reintentos parciales. | Implementado. Activación y prueba física de publicación a recepción pendientes. |

Para la prueba física automática, confirma primero que la función histórica y su disparador estén inactivos. Una vez autorizada la activación, fija el corte UTC después de los anuncios existentes. Publica un solo anuncio nuevo y correlaciona su ID con el lote FCM, el registro de entrega y el aviso visible en Android. La prueba concluye al comprobar que el toque abre el anuncio correcto.
