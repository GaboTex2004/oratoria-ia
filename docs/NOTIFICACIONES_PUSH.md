# PWA y notificaciones push

OratoriaAI se instala desde el navegador como aplicación (PWA) y envía notificaciones push que llegan
aunque la app esté cerrada. No hay APK: la versión móvil es la PWA instalada.

## Qué se puede demostrar

| Requisito | Dónde está |
|---|---|
| PWA instalable | `frontend/public/manifest.webmanifest`, íconos en `frontend/public/icons/`, botón «Instalar aplicación» (`src/shared/pwa/InstallApp.tsx`) |
| Diseño responsive | Pruebas `e2e/responsive.spec.ts` (320 a 1440 px) y `e2e/pwa.spec.ts` (320 px) |
| Service worker | `frontend/public/sw.js`: caché de la interfaz, apertura sin conexión, recepción y clic de notificaciones |
| Notificaciones push | Backend `com.oratoria.backend.notification`, sección «Notificaciones» del Perfil, aviso masivo en Administración → Sistema |

## Avisos que envía la plataforma

1. **Resultado listo:** cuando termina la evaluación de una práctica (`PracticeEvaluatedEvent`). Al tocarlo abre `/reports/{id}`.
2. **Recordatorio diario:** desde la hora elegida en la zona horaria de la persona, una vez por día y solo si ese día no
   empezó ninguna práctica (`DailyReminderScheduler`, revisa cada 5 minutos).
3. **Aviso del administrador:** Administración → Sistema → «Notificación push a todos». Queda en la bitácora.
4. **Prueba:** Perfil → «Enviar notificación de prueba», para comprobar un dispositivo.

Cada persona elige en el Perfil qué avisos recibe; el permiso del navegador se pide solo al tocar «Activar notificaciones».

## Cómo funciona

```
Navegador                       Backend (Spring Boot)               Servicio push del navegador
---------                       ---------------------               ---------------------------
1. GET /api/notifications/config ──► clave pública VAPID
2. pushManager.subscribe(clave) ──────────────────────────────────► crea un endpoint para este dispositivo
3. POST /api/notifications/subscriptions {endpoint, p256dh, auth} ─► guarda en push_subscriptions
4.                               evento (resultado, recordatorio…)
                                 cifra el mensaje (RFC 8291) y
                                 firma con VAPID (RFC 8292) ───────► POST al endpoint (Google, Mozilla,
                                                                     Microsoft o Apple)
5. sw.js recibe "push" ◄──────────────────────────────────────────── entrega al dispositivo
   descifra (lo hace el navegador) y muestra la notificación, aunque la app esté cerrada
6. clic → abre o enfoca la app en la URL del aviso
```

- **Sin librerías nuevas:** el cifrado `aes128gcm` y la firma ES256 usan solo el JDK (`WebPushCrypto.java`). Se verificó
  que la librería independiente `http_ece` (la que usa el paquete `web-push` de Node) descifra lo que genera el backend.
- **Claves VAPID:** si `PUSH_VAPID_PUBLIC_KEY` y `PUSH_VAPID_PRIVATE_KEY` están vacías, el backend genera un par al arrancar y
  lo guarda en `push_vapid_keys`. Esa tabla no entra en los respaldos, para que la clave privada no salga del servidor.
- **Privacidad:** el servicio push del navegador solo ve el destino; el texto viaja cifrado. Nunca se envían audios,
  transcripciones ni puntajes, solo frases como «Tu resultado está listo».
- **Seguridad:** solo se aceptan endpoints HTTPS de servicios push conocidos (`PUSH_ALLOWED_HOSTS`), lo que evita que el
  servidor haga peticiones a direcciones arbitrarias (SSRF). Nadie puede apropiarse del endpoint de otra cuenta con claves
  distintas. Al cerrar sesión o cambiar la contraseña, los dispositivos dejan de recibir los avisos de la cuenta.
- **Fiabilidad:** las suscripciones anuladas por el navegador (404/410) se borran solas y las que fallan 5 veces seguidas
  también. Un rechazo del servidor (401/403/413/429: VAPID, reloj o cuota) se registra en el log y **no** borra
  suscripciones. Los envíos salen de una cola propia: el aviso masivo responde 202 al instante y un servicio push lento
  no frena las evaluaciones ni los trabajos programados.
- **Claves y responsable:** si se configuran `PUSH_VAPID_*`, el backend verifica al arrancar que el par corresponda.
  `PUSH_SUBJECT` debe ser un `mailto:` o `https:` real; en el despliegue de Ubuntu toma `PUBLIC_ORIGIN` por defecto.
- **Respaldos:** restaurar un respaldo devuelve también las suscripciones que existían cuando se generó.

## Requisitos y limitaciones

- **HTTPS** (o `localhost`) para instalar y para push. El despliegue en Ubuntu ya usa HTTPS.
- **Android, Windows, macOS y Linux:** Chrome, Edge y Firefox, desde el navegador o con la app instalada.
- **iPhone/iPad:** iOS 16.4 o superior y **solo con la app instalada** desde Safari («Agregar a inicio»).
- **No funciona en ventanas de incógnito** de Chrome/Edge (limitación del navegador).
- En `npm run dev` el service worker no se registra; probar push con el build (`docker compose` o `vite preview`).

## Guion de demostración

1. Abrir la app por HTTPS en el celular e instalarla (Android: «Instalar»; iPhone: Compartir → «Agregar a inicio»).
2. Abrir la app instalada, iniciar sesión e ir a **Perfil → Notificaciones → Activar notificaciones** y aceptar el permiso.
3. Tocar **Enviar notificación de prueba**: llega al instante.
4. Cerrar la app por completo. Desde otra computadora, entrar como administrador a **Administración → Sistema** y enviar
   una «Notificación push a todos»: llega al celular con la app cerrada. Al tocarla, la app se abre.
5. Opcional: hacer una práctica, cerrar la app y esperar el aviso «Tu resultado está listo».

## Pruebas

- Backend: `WebPushCryptoTests` (descifrado del lado del navegador y firma VAPID) y `NotificationIntegrationTests`
  (suscripción, prueba, SSRF, suscripciones anuladas, preferencias, aviso de resultado, recordatorio diario y aviso masivo).
- Frontend: `tests/pwa.test.ts` y `e2e/pwa.spec.ts` (`E2E_PWA=1`, levanta `vite preview`).
- Verificación manual (19-nov-2026, Edge en Windows): suscripción real en `notify.windows.com`, notificación de prueba
  recibida con la pestaña abierta y también con todas las pestañas de OratoriaAI cerradas.
