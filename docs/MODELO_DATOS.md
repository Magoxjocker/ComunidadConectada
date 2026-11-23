# Modelo de datos

> **Referencia histórica.** Para los nodos, reglas y permisos vigentes en Spark, consulta [Arquitectura técnica](ARQUITECTURA_TECNICA.md).

| Ruta | Datos | Acceso |
| --- | --- | --- |
| `accounts/{uid}` | Nombre, vivienda, comunidad y rol | Lectura propia verificada |
| `communityData/{id}/members/{uid}` | Nombre, vivienda, correo, rol y acceso | Lectura propia y listado administrativo |
| `communityData/{id}/messages/{record}` | Autor, vivienda, texto, comunidad y fecha | Miembros activos |
| `communityData/{id}/marketplace/{record}` | Autor, vivienda, categoría, precio CLP y estado | Lectura comunitaria, edición del propietario |
| `communityData/{id}/announcements/{record}` | Autor, fecha, descripción, prioridad y estado | Lectura comunitaria, gestión administrativa |
| `communityData/{id}/alerts/{record}` | Categoría, estado, entrega y atribución | Lectura comunitaria, actualización administrativa |
| `communityData/{id}/polls/{record}` | Pregunta, alternativas, cierre y resultados según configuración | Lectura comunitaria |
| `communityData/{id}/pollLedger/{record}/votes/{uid}` | Índice de la alternativa elegida | Lectura propia. Administración consulta agregados mediante función. |
| `communityData/{id}/reports/{record}` | Ubicación, propietario, categoría, estado e historial | Propietario y administración |
| `communityData/{id}/devices/{uid}/{hash}` | Token FCM y última actualización | Servidor |

Las escrituras corresponden al servidor. Los registros se identifican por UID y UUID de operación. `createdAt` usa el reloj del servidor y se indexa para listados. Mantenimiento también indexa el propietario para las páginas privadas. Mantenimiento conserva fecha, actor, estado y nota por cambio, con un máximo de 50 entradas por reporte.

Un perfil sin comunidad queda pendiente. Para habilitar el acceso, el servidor reserva la comunidad en el perfil y crea la membresía. Repetir el último comando completa la membresía si la operación se interrumpió entre esos pasos.
