# Guía de demostración académica

## Preparación

1. Usa una copia sin configuración Firebase real, un AVD API 37 y el proyecto ficticio `demo-comunidad`. Inicia Auth y RTDB y siembra una sesión nueva. Instala el APK compilado con `-PcommunityEmulators=true`. [Pasos de instalación y emuladores](MANUAL_INSTALACION.md)
2. Usa el código `androidQa` y las cuentas ficticias `android-vecino@example.test` y `android-admin@example.test`. La entrada de estas cuentas se obtiene localmente del [script de datos de prueba](../functions/integration/seed-android.js). El material de presentación usa solo identidades ficticias y muestra la pantalla una vez completado el ingreso.
3. Ten a mano la [matriz QA](MATRIZ_PRUEBAS_QA.md). La corrida previa de instrumentación modifica datos locales, por lo que la demostración parte de una sesión limpia.

## Recorrido de 8 a 10 minutos

| Tiempo | Acción | Resultado esperado |
| --- | --- | --- |
| 0–1 min | Inicia sesión como vecina. Señala la barra inferior y abre **Más**. | Aparece **Inicio** y se muestran las secciones vecinales. **Administración** queda reservada al rol autorizado. |
| 1–2 min | Abre **Chat**, escribe «Prueba académica de convivencia» y envía. | Aparece la confirmación y el mensaje queda en el chat de la comunidad. |
| 2–3 min | Ve a **Más → Mantenimiento**. Revisa el primer reporte y pulsa **Cargar anteriores**. | «Reporte QA reciente» está primero. La última página incorpora el reporte más antiguo y el listado contiene los 51 reportes iniciales. |
| 3–4 min | Cierra la sesión vecinal e ingresa con la cuenta administrativa. Abre **Vecinos** y revisa los datos ficticios. | Se abre **Administración** y aparecen miembros de esa comunidad. |
| 4–6 min | En **Mantenimiento**, abre «Reporte QA reciente». Intenta actualizarlo a **Resuelto** con la observación vacía. Añade «Luminaria de prueba revisada» y vuelve a guardar. | Primero aparece el aviso «Agrega una nota para resolver el reporte». Luego se confirma la actualización y el historial contiene el cambio con su nota. |
| 6–7 min | Abre **Anuncios** desde Administración y publica un aviso ficticio breve si deseas mostrar el flujo manual. Cierra sesión y abre **Más → Anuncios** como vecina. | El aviso guardado se consulta desde RTDB. El ejercicio muestra la publicación comunitaria. |
| 7–9 min | Presenta la regresión de guardado sin red desde la matriz. Si reproduces el caso en Android, ejecuta la prueba instrumentada diseñada para desconectar la instancia local de RTDB. | El caso comprueba salida, borrador, protección contra duplicados, reconexión y confirmación. |
| 9–10 min | Abre **Accesibilidad**, muestra texto ampliado o alto contraste y cierra sesión. | El ajuste se aplica y **Cerrar sesión** devuelve al acceso. |

Para ejecutar solo la regresión de desconexión, deja activos los emuladores y los reversos ADB e inicia con datos recién sembrados.

```powershell
.\gradlew.bat -PcommunityEmulators=true connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.comunidadconectada.CommunityFlowTest#canLeavePendingFormAndReconnectWithoutSubmittingTwice'
```

Usa la evidencia local para explicar SOS, FCM y MQTT. El guion prescinde de brokers, notificaciones externas y cuentas reales. Si muestras una alerta adicional, confirma antes que el APK pertenece al entorno demo y usa datos ficticios. Al terminar, cierra el AVD de demostración y detén los emuladores que iniciaste.
