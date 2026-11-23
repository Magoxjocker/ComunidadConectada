# Plan de pruebas

Registra fecha, APK, API, cuentas ficticias, resultado y evidencia. Conserva capturas sin credenciales ni datos personales.

| ID | Módulo | Procedimiento | Resultado esperado | Evidencia |
| --- | --- | --- | --- | --- |
| QA-01 | Validaciones | Ejecutar JUnit y Node con correo, nombres, duplicados, fecha y porcentajes. | Se rechazan datos inválidos y se calculan proporciones correctas. | Reportes unitarios |
| QA-02 | Seguridad y Auth | Ejecutar reglas y comandos en Firebase Emulator Suite con vecino, admin, suspendido y otra comunidad. | Solo roles y pertenencia autorizados pueden operar. | `test:rules` y `test:flows` |
| QA-03 | Android | Abrir APK sin configuración, entrar a accesibilidad, modificar contraste, recrear y girar. | Vista estable y preferencias persistentes. | JUnit instrumentado y capturas |
| QA-04 | Chat y ventas | Con dos sesiones, enviar mensaje, consultar historial, publicar, buscar y cambiar estado. | Cambios en ambos dispositivos y edición solo por el vendedor. | Capturas e IDs |
| QA-05 | Anuncios y FCM | Publicar con otra sesión en primer plano y segundo plano. Tocar el aviso y repetir al revocar el acceso. | Título, resumen y navegación al registro correcto. | Registros y pantalla de detalle |
| QA-06 | SOS y MQTT | Con dos sesiones activas, enviar. Cortar internet y, por separado, detener el broker y restaurarlo. | Estado pendiente, acuse tras recuperación y recibo deduplicado por dispositivo. | ID, estado y logs |
| QA-07 | Encuestas | Ejecutar dos votos concurrentes y repetir después de cerrar. | Un voto individual y porcentajes según visibilidad. | Prueba de funciones |
| QA-08 | Mantenimiento | Crear, resolver con observación y consultar como otro residente. | Historial consistente y privacidad. | Funciones y captura |
| QA-09 | Accesibilidad | Recorrer registro, navegación, SOS y formularios con TalkBack, Scanner, teclado y fuente ampliada. | Foco lógico, texto legible y acciones activables. | Informe Scanner y notas |
| QA-10 | Usabilidad | Si lo exige la pauta, invitar a tres personas con consentimiento. Solicitar registro, búsqueda, voto, tamaño de texto y confirmación de prueba de SOS. | Registrar tiempo, éxito, ayuda y observaciones por tarea. | Hojas anonimizadas |

Para usabilidad, asigna códigos a las personas, usa el mismo orden de tareas y registra si lograron cada objetivo con ayuda o de forma autónoma. Evalúa Android 8.0 y una versión reciente. Completa la [matriz de resultados](RESULTADOS_PRUEBAS.md) con evidencia observada.
