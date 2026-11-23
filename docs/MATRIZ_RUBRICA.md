# Matriz de rúbrica

| Criterio | Funcionalidad y archivos | Prueba o evidencia | Estado |
| --- | --- | --- | --- |
| 1. Informe técnico | `INFORME_TECNICO.md` | Arquitectura, flujos y limitaciones | Documentado |
| 2. Propósito y requisitos | `REQUERIMIENTOS.md` | Criterios y plan QA | Documentado |
| 3. Organización y dominio | `app/src/main/java`, `functions/` | MVVM, JVM, Node y Lint | Compilación y controles locales aprobados. |
| 4. Auth y perfiles | `SessionViewModel`, `access.js`, `provision-admin.js` | Validación al escribir, registro, recuperación, edición del nombre y aprovisionamiento repetible | Implementado y probado en emuladores. Cuenta real pendiente. |
| 5. RTDB y seguridad | `database.rules.json`, `access.js` | Aislamiento, privilegios y privacidad | Implementado. Ver resultados locales. |
| 6. Chat y ventas | `ChatFragment`, `SalesFragment`, `records.js` | Chat bidireccional en dos AVD simultáneos y operaciones de ventas por propietario | Implementado y probado en entorno local. |
| 7. Anuncios y FCM | `AnnouncementsFragment`, `CommunityMessagingService`, `delivery.js` | CRUD y prioridades de anuncios verificados. El envío FCM requiere despliegue y credenciales | Anuncios probados en local. Entrega FCM bloqueada por configuración externa. |
| 8. MQTT y SOS | `AlertsFragment`, `AlertSubscription`, `delivery.js` | Creación, idempotencia, atención, cierre y alcance del JWT comprobados | SOS probado en local. Entrega MQTT pendiente de prueba real con EMQX. |
| 9. Pruebas y mejoras | `app/src/test`, `app/src/androidTest`, `functions/integration` | [Resultados](RESULTADOS_PRUEBAS.md) | Pruebas y protocolos entregados |
| 10. Innovación y defensa | Confirmaciones independientes de RTDB, broker y dispositivo | Prueba guiada en informe | Guion preparado. Evidencia externa pendiente. |
