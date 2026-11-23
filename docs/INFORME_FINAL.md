# Informe final de entrega académica

## Resumen ejecutivo

Comunidad Conectada reúne comunicación, ventas, anuncios, encuestas, mantenimiento y alertas para una comunidad residencial. Android usa Firebase Authentication y Realtime Database en Spark. Las reglas controlan el acceso por identidad, comunidad, propiedad y rol. El paquete documental y el núcleo de la aplicación están preparados para una demostración académica en Firebase Emulator Suite con datos ficticios.

## Correcciones verificadas

| Hallazgo de auditoría | Corrección y comprobación |
| --- | --- |
| Resolución de mantenimiento sin nota | Android exige la nota antes de escribir y RTDB mantiene su validación. Una nota vacía conserva el estado anterior. Una válida guarda la resolución y un evento de historial. |
| Reportes recientes fuera de la primera página | Se conserva la consulta autorizada por propietario y se ordena por fecha e ID antes de presentar las páginas. Se comprobaron 51 reportes, sin duplicados ni omisiones. |
| Formulario bloqueado al perder la conexión | El usuario puede volver mientras Firebase espera confirmación. El borrador y el identificador del intento se conservan en memoria. Se comprobó un reintento, reconexión y una sola escritura confirmada. |

## Resultado final

- **6** pruebas unitarias Android aprobadas, **22** pruebas unitarias Node.js aprobadas y **19** pruebas de Firebase Emulator Suite aprobadas.
- **10** pruebas instrumentadas Android aprobadas y **2** omitidas. Los casos omitidos fueron chat simultáneo en dos AVD y arranque con configuración Firebase real.
- **APK debug compilado**, en `app/build/outputs/apk/debug/app-debug.apk`. SHA-256 del artefacto de estabilización: `4195CAEEF7332266A52E6AE09A7E094733E8A8926D1B417247D246768D32868D`.
- **Android Lint sin errores ni advertencias.**

La [matriz QA](MATRIZ_PRUEBAS_QA.md) enlaza el código de prueba y registra la evidencia y el estado de cada caso.

## Limitaciones conocidas

| Área | Alcance y pendiente |
| --- | --- |
| MQTT y FCM | El registro SOS y los anuncios en RTDB se comprueban por separado. MQTT requiere broker TLS, ACL y emisor. FCM requiere un emisor de confianza y dispositivos habilitados. Ambos canales tienen implementación opcional. La entrega externa queda pendiente de una prueba de extremo a extremo. |
| Desconexión | La corrección cubre salir del formulario, volver y completar el intento en la sesión activa. El seguimiento en memoria queda limitado a la vida del proceso y del ViewModel. |
| Paginación | La primera página de mantenimiento vecino recorre todo su historial mediante consultas acotadas. El costo crece con el historial propio. |
| Cobertura | Parte de los CRUD se comprobó mediante reglas y SDK local. Quedan pendientes ciclos completos desde Android, API 26, TalkBack, correo real y paridad entre reglas auditadas y publicadas. El primer acceso administrativo real fue informado por el titular y no se repitió en esta ronda. |

## Conclusión

**Preparado para entrega académica y demostración local del alcance verificado.** Para la presentación, usa el entorno demo y sus cuentas ficticias. El uso operativo con notificaciones externas o una declaración de cobertura sobre producción exige comprobaciones adicionales autorizadas. [Guía de demostración](GUIA_DEMOSTRACION.md) · [Manual de instalación](MANUAL_INSTALACION.md)
