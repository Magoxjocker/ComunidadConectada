# Matriz de pruebas QA

## Criterios y resultados

**Aprobado** indica que se comprobó el resultado en el entorno descrito. **Parcialmente verificado** indica que hay evidencia para una parte del flujo. **Fallido** indica un comportamiento contrario al esperado. **Bloqueado** señala un requisito de entorno pendiente. **No probado** indica que la comprobación no se ejecutó o se omitió.

| Validación | Entorno | Resultado final |
| --- | --- | --- |
| Compilación | Gradle en Windows | APK debug y APK de instrumentación generados. |
| Android Lint | `lintDebug` | Aprobado, 0 errores y 0 advertencias. |
| Unitarias Android | JVM y JUnit | 6 aprobadas. |
| Unitarias Node.js | Node 22.23.5 | 22 aprobadas, 0 fallos. |
| Integración Firebase | Auth y RTDB de `demo-comunidad` | 19 aprobadas, 0 fallos. Incluye aprovisionamiento simulado, reglas y flujos. |
| Instrumentación comunitaria | AVD Android 17, API 37, Auth y RTDB demo | 8 aprobadas, 1 omitida. |
| Instrumentación de arranque | AVD Android 17, API 37, copia sin configuración Firebase | 2 aprobadas, 1 omitida. |

Los encabezados de AndroidJUnitRunner `OK (9 tests)` y `OK (3 tests)` cuentan también los casos omitidos por precondiciones. En ambos registros aparece un `INSTRUMENTATION_STATUS_CODE: -4`. El total de la ronda es **10 pruebas instrumentadas aprobadas y 2 omitidas**.

## Casos y alcance

| ID | Caso y resultado esperado | Evidencia y estado |
| --- | --- | --- |
| AUT-01 | Registrar perfil mínimo, verificar correo y volver a iniciar sesión. Una cuenta pendiente no entra a funciones protegidas. | **Aprobado** en Auth Emulator y en UI para validaciones, registro sin verificar y pantallas de acceso pendiente. Correo real de verificación y recuperación **no probado** en esta ronda. |
| AUT-02 | Mostrar las pantallas correctas según rol, restaurar navegación y cerrar sesión. | **Aprobado** en un AVD para vecino y administrador. El arranque instrumentado con configuración real se omitió. |
| SEG-01 | Rechazar escalamiento de roles, lecturas cruzadas y consultas que exceden lo permitido. | **Aprobado** con las reglas locales. Comparación con las reglas publicadas **no probada**. |
| ADM-01 | Aprovisionar, confirmar claims, preservar datos en reintentos y detectar estados incompatibles. Aprobar un vecino por UID y revocar su acceso. | **Aprobado** por integración local. La aprobación y suspensión desde el formulario administrativo completo quedan **parcialmente verificadas**. El primer acceso real fue informado por el responsable del proyecto. |
| CHA-01 | Persistir mensajes y mostrarlos a otra sesión autorizada. | **Aprobado** en RTDB y en Android con dos sesiones sucesivas en un AVD. Chat simultáneo en dos AVD **omitido**. Historial completo de varias páginas desde Android **no probado**. |
| VEN-01 | Aplicar propiedad a ventas y permitir operaciones autorizadas. | **Aprobado** en RTDB. CRUD y filtros completos desde Android **no probados**. |
| ANU-01 | Permitir a la administración publicar y gestionar anuncios de su comunidad. | **Aprobado** en RTDB. Recorrido CRUD completo desde Android **no probado**. |
| ENC-01 | Conservar votos privados, impedir votos duplicados y mantener totales coherentes. | **Aprobado** en RTDB, incluida concurrencia y reanudación de una encuesta migrada. Recorrido completo desde los formularios Android **no probado**. |
| MAN-01 | Impedir resolver sin nota, conservar estado y guardar una resolución válida con historial. | **Aprobado** en JVM, Firebase Emulator Suite y Android. Caso `rejectsBlankResolutionAndSavesAValidNote`. |
| MAN-02 | Mostrar los reportes propios más recientes primero y recorrer las páginas sin duplicados u omisiones. | **Aprobado** con 51 reportes y empate de fechas. Caso `ownReportsPageByDateAndKeepAllRecordsAtTheBoundary`. Aislamiento probado por reglas. Carga con historiales grandes **no probada**. |
| RED-01 | Salir del formulario con escritura pendiente, conservar borrador, retener ID en reintento y confirmar una escritura tras reconexión. | **Aprobado** en Android y RTDB demo con un escritor de prueba en el límite de Firebase. Caso `canLeavePendingFormAndReconnectWithoutSubmittingTwice`. Recuperación tras perder el proceso **no probada**. |
| ALE-01 | Guardar SOS ficticio, proteger entrega y aplicar permisos para atender y cerrar. | **Aprobado** en RTDB local. Generación de SOS real **fuera del alcance**. |
| ACC-01 | Mantener controles utilizables con texto aumentado, modo oscuro y alto contraste. | **Aprobado** para los casos instrumentados. TalkBack de extremo a extremo y ejecución en API 26 **no probados**. |
| EXT-01 | Entregar anuncios por FCM a dispositivos activos y autorizados. | Selección de destinatarios y privacidad **verificadas localmente**. Entrega de extremo a extremo **bloqueada** por falta de entorno externo autorizado. |
| EXT-02 | Publicar y recibir alerta MQTT mediante TLS y ACL de la comunidad. | Privacidad del acceso **verificada en RTDB local**. Publicación, recepción y revocación en un broker **bloqueadas** por falta de entorno externo autorizado. |

## Trazabilidad

- **Código de pruebas Android:** [Unitarias](../app/src/test/java/com/example/comunidadconectada/domain) · [Flujos](../app/src/androidTest/java/com/example/comunidadconectada/CommunityFlowTest.java) · [Arranque](../app/src/androidTest/java/com/example/comunidadconectada/StartupTest.java).
- **Código de pruebas Node y Firebase:** [Unitarias Node](../functions/test) · [Flujos](../functions/integration/flows.test.js) · [Reglas](../functions/integration/rules.test.js) · [Aprovisionamiento](../functions/integration/provision-admin.test.js).
- **Actas locales de la sesión de QA:** `AUDITORIA_QA_COMUNIDAD_CONECTADA.html`, `project-build.log`, `node-unit.log`, `firebase-test.log`, `instrumentation-release.log` e `instrumentation-startup.log`. Se conservan en el entorno original, fuera del repositorio. Los conteos de esta matriz se cotejaron contra esos archivos. La [guía de instalación](MANUAL_INSTALACION.md) permite generar evidencias nuevas en otro equipo.
- **Huella SHA-256 del archivo local de reglas probado:** `6DDF1AE5B455C436F991CE82EEF4BC98B109A3EC2BD95D501CED671E533CF79D`.
