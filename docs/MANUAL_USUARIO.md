# Manual de usuario

## Crear y habilitar una cuenta vecinal

1. Selecciona **Crear mi cuenta** e indica nombre, vivienda, correo y contraseña. Confirma la contraseña y envía el formulario.
2. Abre el enlace de verificación recibido por correo y pulsa **Ya verifiqué mi correo**. Si el perfil quedó incompleto, la aplicación pide completarlo después de verificar el correo.
3. Ingresa el código de comunidad entregado por la administración y envía la solicitud. Comparte por el canal oficial el identificador que muestra la app. La administración utiliza ese identificador para aprobar la incorporación.
4. Cuando tu membresía esté activa aparece **Inicio**. Si el acceso sigue pendiente, usa **Actualizar** tras confirmar la aprobación. Si aparece **Acceso suspendido**, contacta a la administración.

Para recuperar la cuenta, escribe tu correo y pulsa **Olvidé mi contraseña**. Sigue las instrucciones del correo de Firebase.

## Navegar por la comunidad

| Pantalla | Operación |
| --- | --- |
| **Inicio** | Consulta los anuncios destacados y los accesos rápidos. |
| **Chat** | Pulsa **Escribir mensaje**, escribe y envía. Usa **Cargar anteriores** para ampliar el historial. |
| **Ventas** | Publica con título, descripción, precio en pesos chilenos y categoría. Usa la búsqueda y los filtros. Abre una publicación propia para editarla, cambiar su estado o eliminarla. |
| **Alertas** | Consulta las alertas y su estado. **Activar SOS** pide categoría, descripción y confirmación. El SOS genera un registro comunitario. Ante peligro real, contacta al servicio de emergencia correspondiente. |
| **Más → Anuncios** | Abre los anuncios publicados por tu comunidad. |
| **Más → Encuestas** | Revisa la pregunta y el plazo, elige una alternativa y confirma. Cada cuenta puede emitir un voto. Los resultados vecinales aparecen cuando la encuesta los habilita. |
| **Más → Mantenimiento** | Pulsa **Crear reporte** e indica título, ubicación, categoría y descripción. Abre un reporte propio para ver su estado y el historial disponible. Los más recientes aparecen primero. **Cargar anteriores** amplía la lista. |
| **Más → Mi perfil** | Revisa tu cuenta, edita tu nombre y abre el ajuste de notificaciones. El permiso de Android habilita su visualización cuando hay un emisor FCM operativo. |
| **Más → Accesibilidad** | Ajusta el tamaño del texto, el tema y el alto contraste. TalkBack se activa desde los ajustes del dispositivo. |
| **Más → Cerrar sesión** | Cierra la sesión. El perfil también ofrece este acceso. |

La barra inferior contiene **Inicio**, **Chat**, **Ventas**, **Alertas** y **Más**. Para volver de un detalle utiliza la flecha superior o el botón Atrás de Android.

## Guardados con pérdida de conexión

- **Pendiente.** El formulario espera respuesta de Firebase. Puedes pulsar **Volver**. Al reabrir el mismo formulario en la sesión activa puedes ver el borrador y el envío sigue deshabilitado mientras se espera la respuesta.
- **Confirmado.** Espera el mensaje de guardado o revisa el registro correspondiente.
- **Fallido.** Lee el mensaje, corrige el dato si procede y reintenta desde el formulario. Si vuelves tras cerrar la aplicación por completo, comprueba si existe el registro antes de repetirlo. La recuperación del borrador tras el cierre del proceso queda fuera de la cobertura.

Consulta el [informe final](INFORME_FINAL.md) para conocer la cobertura de las notificaciones externas, la accesibilidad y las pruebas de red.
