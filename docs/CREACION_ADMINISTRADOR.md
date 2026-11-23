# Primer administrador de Comunidad Central

El comando usa Firebase Admin SDK desde un equipo de confianza. Funciona en Spark mediante Authentication y Realtime Database. Espera autorización expresa antes de configurar permisos remotos o ejecutar el aprovisionamiento real.

## Proyecto e identidad

- Proyecto Firebase `comunidadconectada-35c3c`.
- Instancia `https://comunidadconectada-35c3c-default-rtdb.firebaseio.com/`.
- Comunidad `comuna-centro` con nombre visible **Comunidad Central**.
- Cuenta de la aplicación `administrador@ejemplo.com`. La cuenta de servicio que ejecuta el aprovisionamiento es otra identidad.

El comando exige una cuenta de servicio del proyecto en `CC_ADMIN_SERVICE_ACCOUNT` y compara esa dirección con la identidad que resuelven las ADC. Valida el proyecto y la URL canónica, y rechaza variables de Firebase Emulator Suite aunque su valor esté vacío. Google Cloud CLI tiene su propia sesión y cambiar el proyecto activo de esa sesión no sustituye las ADC.

## Permisos y autenticación

- La cuenta de servicio necesita consultar, crear y actualizar usuarios en Auth, incluidos sus *custom claims*. Los permisos mínimos documentados son `firebaseauth.users.get`, `firebaseauth.users.create` y `firebaseauth.users.update`. En RTDB necesita lectura y escritura administrativa sobre la instancia. Los roles predefinidos `roles/firebaseauth.admin` y `roles/firebasedatabase.admin` son más amplios. El responsable de IAM puede usar permisos ajustados y temporales según la política del proyecto. [Permisos de Auth](https://docs.cloud.google.com/identity-platform/docs/access-control) · [Permisos de Realtime Database](https://docs.cloud.google.com/iam/docs/roles-permissions/firebasedatabase)
- Para impersonar, el operador requiere `roles/iam.serviceAccountTokenCreator` sobre esa cuenta y la API IAM Service Account Credentials debe estar disponible. Si el proyecto de cuota lo exige, verifica `serviceusage.services.use`. El acceso se concede por IAM, sin requerir Blaze para Auth o RTDB. [Impersonación y ADC](https://docs.cloud.google.com/docs/authentication/set-up-adc-local-dev-environment) · [Requisitos de impersonación](https://docs.cloud.google.com/docs/authentication/use-service-account-impersonation) · [Proyecto de cuota](https://docs.cloud.google.com/docs/quotas/set-quota-project)
- Si el operador carece de impersonación, el titular de una identidad autorizada puede ejecutar el procedimiento desde un entorno confiable que ya proporcione ADC de esa cuenta de servicio. Si la política permite una credencial de servicio preexistente, el titular puede indicar su ubicación protegida mediante `GOOGLE_APPLICATION_CREDENTIALS`, fuera del repositorio y el APK. El procedimiento no genera ni descarga claves. Firebase Authentication rechaza las ADC de usuario obtenidas con el cliente OAuth estándar de Google Cloud CLI. [Configuración de ADC para Firebase Admin SDK](https://firebase.google.com/docs/admin/setup#testing_with_gcloud_end_user_credentials)

## Ejecución futura autorizada

En **Google Cloud SDK Shell de Windows (CMD)**, con Node 22 instalado, prepara una sesión limpia. Indica la dirección real de la cuenta de servicio aprobada y usa la siguiente invocación de ADC si la impersonación está autorizada.

```cmd
set "CC_ADMIN_SERVICE_ACCOUNT=CORREO_DE_LA_CUENTA_DE_SERVICIO_AUTORIZADA"
set "GOOGLE_APPLICATION_CREDENTIALS="
```

Reemplaza el marcador con el correo autorizado. Ejecuta la autenticación como un comando independiente.

```cmd
gcloud auth application-default login --project=comunidadconectada-35c3c --impersonate-service-account="%CC_ADMIN_SERVICE_ACCOUNT%"
```

Si trabajas con ADC de servicio ya configuradas, conserva ese mecanismo y define `CC_ADMIN_SERVICE_ACCOUNT` con la misma identidad. Continúa únicamente si la preparación de credenciales fue exitosa. Conserva la contraseña del administrador en un gestor aprobado.

```cmd
cd /d "C:\Users\MagoxJockers\AndroidStudioProjects\ComunidadConectada"
npm --prefix functions ci
set "CC_PROJECT_ID=comunidadconectada-35c3c"
set "CC_DATABASE_URL=https://comunidadconectada-35c3c-default-rtdb.firebaseio.com/"
```

Sustituye `NOMBRE_PERSONA` y `UNIDAD_ADMIN` y ejecuta la siguiente línea por separado, con el símbolo del sistema disponible.

```cmd
npm --prefix functions --silent run admin -- "comuna-centro" "administrador@ejemplo.com" "NOMBRE_PERSONA" "UNIDAD_ADMIN" "Comunidad Central"
```

Espera el aviso de contraseña. Introduce una sola línea y pulsa Enter. Espera el aviso de confirmación antes de introducirla otra vez. La consola oculta ambos valores. Si el lector recibe varias líneas juntas, descarta la entrada y termina. Ante una contraseña inválida o distinta de la confirmación, permite hasta tres intentos en total dentro de tres minutos. El comando confirma el perfil, la membresía y los permisos almacenados. En Android, inicia sesión, solicita el correo de verificación si está pendiente, abre el enlace y pulsa **Ya verifiqué mi correo**. Luego comprueba el panel **Administración**. Un administrador con correo pendiente carece de acceso a funciones protegidas. [Propagación de los custom claims](https://firebase.google.com/docs/auth/admin/custom-claims)

## Versiones y auditoría del aprovisionamiento

Usa Node.js 22 LTS. La versión 22.23.5 pasó las pruebas junto con Node.js 24.16.0. El proyecto declara Node.js 22. La instalación con Node.js 24 puede emitir `EBADENGINE` aunque el código probado funcione en esa versión. Instala con `npm --prefix functions ci` para reproducir el archivo de bloqueo.

Firebase Admin SDK queda fijado en `14.5.1` y el SDK histórico `firebase-functions` en `7.4.0`, que declara compatibilidad con Admin SDK 14. Los cambios mayores relevantes son el requisito de Node.js 22 para Admin SDK, la retirada de su espacio de nombres histórico, los cambios en manejo de errores y la retirada de `functions.config()`. El proyecto usa los puntos de entrada modulares y `params`. Se fija `@firebase/database-compat@2.1.7` para Admin SDK. Esa versión incorpora `@firebase/database@1.1.5`, compatible con Node.js 22. La versión `2.1.8` exige Node.js 24.12 o superior. El aprovisionamiento usa Auth y RTDB. El módulo local de validación importa de Functions únicamente la clase `HttpsError`. [Notas de Admin SDK](https://firebase.google.com/support/release-notes/admin/node) · [Cambios de Functions 7](https://github.com/firebase/firebase-functions/releases/tag/v7.0.0)

El árbol anterior, con `firebase-admin@13.10.0` y `firebase-functions@6.6.0`, registró nueve hallazgos moderados propagados desde `uuid@9.0.1` por las ramas opcionales de Firestore y Cloud Storage. El script carga `gaxios` a través de Google Auth, pero la rama anterior que dependía del `uuid` afectado no se cargó al importar el script. El aviso afecta determinadas funciones de `uuid` con un búfer proporcionado por el llamador, y el aprovisionamiento usa `node:crypto` para el UID. La actualización obtuvo cero hallazgos con `npm --prefix functions audit --omit=dev`. [Aviso de uuid](https://github.com/uuidjs/uuid/security/advisories/GHSA-w5hq-g745-h8pq)

La verificación incluyó ADC impersonadas ficticias con conexiones HTTP bloqueadas, una consulta de usuario inexistente en Auth Emulator, una transacción RTDB, la suite de reglas y la carga del módulo histórico. La auditoría de dependencias no crea cuentas ni verifica permisos efectivos en producción.

## Datos, reglas y recuperación

- El nombre se guarda en `communityData/comuna-centro/metadata/displayName`. El código se conserva en los perfiles y los *claims*. Las [reglas locales](../database.rules.json) permiten leer el nombre visible a miembros activos y verificados de la misma comunidad. La escritura queda reservada a un proceso administrativo con permisos IAM. Publicar ese cambio de reglas exige autorización aparte. Android usa el código si la app carece de permiso de lectura o aún falta el nombre.
- El script conserva el UID, contraseña, perfil, membresía, metadatos y *claims* válidos en repeticiones. Un nombre existente distinto, una cuenta o membresía suspendida y un rol o comunidad incompatibles requieren revisión. El script no reactiva ni reasigna estas cuentas.
- Auth y RTDB se confirman por separado. Ante un error, revisa el usuario en Authentication, `accounts/{uid}`, `communityData/comuna-centro/members/{uid}`, los metadatos y los permisos asignados. Evita ejecuciones simultáneas. Repite con los mismos datos solo si el estado parcial coincide con el aprovisionamiento previsto.
