# Configuración MQTT

> **Referencia histórica.** La integración Spark utiliza un emisor de confianza y un broker TLS opcional. Sigue la [guía Spark](FIREBASE_SPARK.md) para su configuración.

1. Usa EMQX administrado o privado con ACL en JWT y desconexión al expirar. Habilita un listener TLS, habitualmente 8883, con certificado válido en Android y Node.js y nombre DNS coincidente. Deshabilita conexiones anónimas para el servicio comunitario.
2. Configura el autenticador JWT para HS256, clave aleatoria de al menos 32 bytes, token desde **Password**, claim `username` igual al nombre MQTT y desconexión al expirar. Usa la clave tal como se entrega a Node, sin decodificar Base64. Configura autorización por defecto **deny** y retira reglas generales que concedan permisos. Los JWT contienen una sola regla positiva.
3. Define `CC_MQTT_HOST` en Functions como `broker.ejemplo.cl:8883`, sin esquema ni ruta. Configura la misma clave mediante Firebase CLI y redespliega cuando se rote.

```powershell
firebase functions:secrets:set CC_MQTT_JWT_SECRET --project PROJECT_ID
firebase deploy --only functions --project PROJECT_ID
```

[Autenticación JWT de EMQX](https://docs.emqx.com/en/emqx/latest/guides/access-control/authn/jwt.html) · [ACL de EMQX](https://docs.emqx.com/en/emqx/latest/guides/access-control/authn/acl.html)

## Flujo y verificación

El tópico `comunidades/{id}/alertas` usa MQTT 3.1.1, QoS 1 y mensajes efímeros. El servidor expide JWT de cinco minutos, con permiso de suscripción a miembros y permiso de publicación a la función. El JSON solo transporta el identificador. El nombre, la vivienda y la descripción se consultan en RTDB.

RTDB registra la alerta antes de emitir el evento. La función marca el acuse del broker. Un dispositivo muestra **Recibida** si observó el ID en su sesión MQTT. Android se conecta mientras la aplicación está visible y la reconexión solicita credenciales vigentes. La consulta histórica permanece en RTDB.

Con dos dispositivos, abre **Alertas** en ambos y envía un SOS de evaluación. Comprueba `brokerAcknowledgedAt`, recepción en el segundo dispositivo, deduplicación y rechazo de suscripción con una cuenta de otra comunidad. Repite con el broker interrumpido y restablecido. Registra los IDs y estados evitando tokens, secretos y datos personales.
