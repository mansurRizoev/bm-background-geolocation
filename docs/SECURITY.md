# Безопасность

## 1. Белый список хостов (`allowedHosts`)

Защищает от утечки координат, если в WebView выполнится чужой JS (XSS): без списка он мог бы вызвать `setConfig({ url: 'https://evil.example' })`. Список задаётся **в нативной конфигурации приложения**, а не через JS, поэтому изменить его из WebView нельзя.

Формат: имя хоста точно (`api.example.com`) или поддомены (`*.example.com`, сам `example.com` не входит). Без списка разрешены любые хосты (поведение по умолчанию, лучше список задать).

**Android** — `AndroidManifest.xml` приложения, внутри `<application>`:

```xml
<meta-data
    android:name="com.hulafo.bm_geolocation.ALLOWED_HOSTS"
    android:value="api.example.com,*.example.org" />
```

**iOS** — `Info.plist`:

```xml
<key>BMGeolocationAllowedHosts</key>
<array>
  <string>api.example.com</string>
  <string>*.example.org</string>
</array>
```

Проверка выполняется дважды: при `configure()/setConfig()` (JS получает ошибку `HOST_NOT_ALLOWED`) и перед каждой отправкой (событие `http` с `success: false`).

Дополнительно по умолчанию принимается только `https://` (для разработки есть `allowCleartext: true`), редиректы не выполняются.

## 2. Хранение токена

Заголовки (`headers`, обычно `Authorization`) не лежат в открытом виде:

- **Android:** шифруются AES-256-GCM, ключ в Android Keystore (неэкспортируемый). Шифртекст хранится в SharedPreferences. Если шифрование недоступно, заголовки не сохраняются (откат на открытый текст не делается). При восстановлении из бэкапа на другое устройство ключа нет, заголовки сбрасываются, и приложение должно задать токен заново.
- **iOS:** Keychain, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` (доступен при заблокированном экране и после перезагрузки, не попадает в бэкапы).
- JS никогда не получает значения: `getState()` возвращает `"***"`. Если вернуть `"***"` обратно в `setConfig`, сохранённое значение остаётся.

Обновление токена:

```ts
await bm_geolocation.setConfig({ headers: { Authorization: `Bearer ${newToken}` } });
```

Объект `headers` заменяет заголовки целиком.

## 3. Остаётся на вашей стороне

- **Бэкап.** Очередь координат (SQLite) и настройки не шифруются. На Android отключите бэкап (`android:allowBackup="false"`) или исключите файлы через `dataExtractionRules`.
- **Короткоживущий токен.** Токен лежит в защищённом хранилище, но токен курьера лучше делать с ограниченными правами (только запись координат) и сроком жизни.
- **XSS и CSP.** Не загружайте в WebView чужой контент, задайте строгий Content-Security-Policy. `getLocations()` доступен JS.
- **TLS pinning** в плагине не реализован.
- **Персональные данные.** Нужны согласие курьера, политика хранения (по умолчанию 3 дня в локальной очереди) и декларации Google Play / App Store.
