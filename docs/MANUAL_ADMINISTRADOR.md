# Manual de administrador

## Acceso y primer administrador

El primer administrador se aprovisiona mediante Firebase Admin SDK desde un equipo autorizado. La cuenta debe contar con el perfil administrativo, una membresía activa de la comunidad y los *custom claims* `role=admin` y `community`. Las reglas exigen además correo verificado. Android comprueba estos requisitos antes de mostrar **Administración**.

Para una instalación nueva y expresamente autorizada, prepara Node.js 22, las dependencias desde el archivo de bloqueo y credenciales ADC de una cuenta de servicio aprobada. La opción preferente es la impersonación, cuando IAM concede al operador el permiso para generar credenciales de esa cuenta. Otra opción es ejecutar en un entorno de confianza que ya provea las ADC autorizadas. Configura `CC_PROJECT_ID`, `CC_DATABASE_URL` y `CC_ADMIN_SERVICE_ACCOUNT`. El script verifica la identidad y el destino y rechaza entornos de emulador.

El comando se ejecuta en una consola interactiva. Los cinco argumentos corresponden a código, correo de la cuenta de la app, nombre de persona, unidad y nombre visible de la comunidad. Los marcadores se sustituyen únicamente en la ejecución autorizada.

```cmd
npm --prefix functions --silent run admin -- "CODIGO" "CORREO_ADMIN" "NOMBRE" "UNIDAD" "NOMBRE_COMUNIDAD"
```

La consola solicita y confirma la contraseña con entrada oculta. El script confirma el perfil, la membresía, los metadatos y los *claims*. Una cuenta suspendida, una identidad existente incompatible o un fallo parcial requieren revisar el estado antes de reintentar. El script conserva los datos legítimos al repetir la misma operación. [Procedimiento, IAM y recuperación](CREACION_ADMINISTRADOR.md) · [Script de aprovisionamiento](../functions/provision-admin.js)

Después, inicia sesión en Android. Si falta verificar el correo, usa el envío de verificación de la pantalla de acceso, abre el enlace y pulsa **Ya verifiqué mi correo**. Con correo verificado y permisos vigentes se abre **Administración**. Para la evaluación local usa el administrador ficticio de la [guía de demostración](GUIA_DEMOSTRACION.md).

## Gestionar vecinos

1. Comparte el código de comunidad por un canal autorizado. El vecino crea su cuenta, verifica su correo y envía su solicitud con ese código.
2. Confirma la identidad y vivienda y solicita el identificador mostrado en la aplicación.
3. Abre **Administración → Vecinos → Agregar vecino**, ingresa el identificador y confirma **Habilitar**. La aprobación actualiza el perfil y crea la membresía activa en una escritura multipath. El vecino puede actualizar su sesión después de la aprobación.
4. Abre el detalle de un vecino para **Suspender** o **Habilitar**. La suspensión conserva sus datos y las reglas impiden nuevas operaciones comunitarias que requieran membresía activa. Si MQTT estaba configurado, coordina la revocación de la credencial en el broker y el cierre de su conexión.

La interfaz de vecinos no ofrece asignación de administradores. Los permisos de administración se conceden mediante el procedimiento de confianza y están limitados a la comunidad autorizada.

## Operar la comunidad

| Pantalla | Tarea administrativa |
| --- | --- |
| **Anuncios** | Crear con título, descripción y prioridad. Abrir el detalle para editar o eliminar. La publicación queda visible en RTDB. El aviso externo depende del emisor FCM. |
| **Encuestas** | Crear con dos a seis alternativas distintas, una por línea, fecha futura en formato `DD/MM/AAAA HH:mm` y visibilidad de resultados. Abrir el detalle para ver totales o cerrar la votación. |
| **Alertas** | Abrir la alerta y marcarla atendida o cerrada. El estado operativo y la entrega MQTT son datos separados. |
| **Mantenimiento** | Abrir un reporte y pulsar **Actualizar reporte**. Seleccionar **Pendiente**, **En proceso** o **Resuelto**. Al resolver, escribir una observación o resolución antes de guardar. El detalle permite revisar el historial. |
| **Chat y Ventas** | Utilizar los flujos comunitarios. La gestión de una venta sigue la propiedad del registro. |

Si un formulario muestra **Guardado pendiente**, puedes volver y esperar la confirmación. Reabrir el mismo intento evita un segundo envío mientras siga en curso. [Uso durante una desconexión](MANUAL_USUARIO.md)
