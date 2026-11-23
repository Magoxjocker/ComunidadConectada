# Seguridad

> **Referencia histórica.** Para la autorización vigente mediante RTDB y el alcance de Firebase Admin SDK, consulta [Arquitectura técnica](ARQUITECTURA_TECNICA.md).

- Las reglas RTDB deniegan por defecto. Conceden lecturas expresas acotadas por identidad verificada, membresía, comunidad y consulta.
- Las escrituras pasan por funciones con comandos definidos y validación de tipo, longitud, enumeración, propiedad y fecha. Admin SDK omite las reglas, por lo que el servidor verifica cada operación.
- Las funciones consultan la identidad actual de Auth y la membresía. Una operación administrativa requiere rol de miembro y claims de la cuenta y su token.
- La suspensión conserva el perfil y revoca acceso comunitario. Una operación ya autorizada en vuelo puede terminar. La credencial MQTT emitida tiene una vida máxima de cinco minutos.
- Los vecinos solo leen sus reportes y su voto. Administración consulta agregados de encuestas. FCM dirige avisos a miembros activos y el cliente comprueba el acceso antes de mostrarlos.
- Los secretos viven en Secret Manager. Las credenciales confiables se obtienen mediante ADC. Android requiere TLS y conserva el JWT de MQTT en memoria.

El ID se mantiene durante reintentos del formulario. Los votos validan opción, plazo y unicidad dentro de la transacción. Las funciones de entrega pueden reintentar y la notificación usa el identificador para evitar avisos visibles duplicados. Para operación pública, falta evaluar controles adicionales contra abuso con App Check, retención, límites y cargas representativas.

[Condiciones y consultas de Firebase RTDB](https://firebase.google.com/docs/database/security/rules-conditions)
