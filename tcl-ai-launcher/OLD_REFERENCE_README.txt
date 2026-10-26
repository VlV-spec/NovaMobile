# TV AI Assistant 0.1 — TCL Android TV 11

Это исходники MVP. APK не включён: среда сборки Android SDK/Gradle здесь недоступна, приложение ещё не проверялось на реальном TCL.

## Состав
- `worker/` — Cloudflare Worker: `/health`, `/chat`, OpenAI Responses API, секреты, разрешённые TV-команды.
- `android/` — Java Android TV-приложение: текст, `RecognizerIntent`, системный TTS, HTTPS-запросы, разрешённые команды.

## 1. Развернуть сервер
Нужны Node.js 20+, аккаунт Cloudflare и OpenAI API с доступным балансом.

```bash
cd worker
npm install
npx wrangler login
npx wrangler deploy
npx wrangler secret put OPENAI_API_KEY
npx wrangler secret put DEVICE_TOKEN
```

Для `DEVICE_TOKEN` сгенерируйте случайную строку минимум 32 байта, например в PowerShell:
```powershell
$b=New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```
Скопируйте её в секрет Cloudflare и затем в настройки Android-приложения. Никому не отправляйте токен и OpenAI API key. Команда `secret put` может обновить развернутую версию Worker.

Проверка: `https://<worker>.workers.dev/health` должна вернуть `{"ok":true,"version":"0.1.0"}`.

В тестовом MVP токен устройства общий для одной установки; для публичного использования нужны ротация токенов, rate limiting и отдельная авторизация. Не публикуйте токен в GitHub.

## 2. Собрать APK
Установите Android Studio с JDK 17 и Android SDK Platform 35. Откройте папку `android/` как проект и дождитесь Gradle Sync. Далее Build > Build APK(s). Получится:
`android/app/build/outputs/apk/debug/app-debug.apk`.

В архиве нет Gradle Wrapper, поскольку не было возможности сгенерировать/проверить его в среде сборки. Android Studio может использовать установленный Gradle; если предложит создать wrapper, согласитесь. Подойдёт Gradle 8.7+ совместимый с AGP 8.6.1.

## 3. Установить на TCL
Из папки с APK на ПК при подключённом ADB:
```bash
adb devices
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.tvai.assistant -c android.intent.category.LEANBACK_LAUNCHER 1
```
Либо перенесите APK на телефон и установите на телевизор через Bugjaeger > Packages > Install APK (ADB подключён к TCL).

## 4. Первый запуск
Введите URL Worker (без `/chat`), `DEVICE_TOKEN`, текст вопроса. Нажмите «Отправить». Затем проверьте «Говорить».

На некоторых TCL `RecognizerIntent` не имеет доступного обработчика, либо системный TTS не содержит русского голоса — тогда текстовый режим работает, голосовой требует следующего этапа (собственный STT/TTS).

## Команды
«Открой YouTube», «Открой KinoPub», «Громче», «Тише», «Пауза», «Продолжи воспроизведение». Команды определяются только серверным allowlist; произвольные действия модели запрещены. Реальная поддержка управления воспроизведением зависит от приложения.

## Безопасность и ограничения
- Google Assistant, Projectivy, Happ VPN и другие приложения не отключаются и не модифицируются.
- OpenAI API key находится только в Cloudflare secret; `DEVICE_TOKEN` хранится на телевизоре в обычных SharedPreferences приложения (для MVP, не защищён от доступа при компрометации устройства).
- На сервер отправляется распознанный текст; системное распознавание речи может отправлять звук своему поставщику.
- Нет истории диалогов между запросами, wake word, фонового прослушивания, системной кнопки микрофона.
- Worker не развёрнут, реальные API-вызовы и установка на TCL не проверены.
- Важны расходы OpenAI API и ограничения доступности сервисов по региону.
