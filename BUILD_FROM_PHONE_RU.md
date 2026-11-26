# Сборка BubbleChat AAR прямо с телефона

Этот вариант использует GitHub Actions: проект отправляется в GitHub с телефона, а GitHub собирает настоящий AAR в облаке. Termux и Android SDK на телефоне не нужны.

1. Создайте аккаунт на https://github.com/ и войдите в него в браузере.
2. Создайте новый репозиторий (например, `BubbleChat`), можно сделать его Private.
3. Распакуйте этот ZIP на телефоне.
4. На странице репозитория нажмите Add file → Upload files и загрузите ВСЁ содержимое папки `BubbleChat_XML`, включая скрытую папку `.github`.
   Важно: в корне репозитория должны сразу находиться `settings.gradle`, `build.gradle`, папка `bubblechat` и папка `.github`.
5. Нажмите Commit changes.
6. Откройте вкладку Actions → workflow `Build BubbleChat AAR` → Run workflow (если workflow ещё не стартовал автоматически).
7. Дождитесь зелёной галочки у запуска сборки.
8. Откройте завершённый запуск, прокрутите до Artifacts и скачайте `bubblechat-release`.
9. Внутри скачанного архива будет `bubblechat-release.aar`. Его импортируйте в Sketchware Pro через Local Library.

Если сборка завершится ошибкой, откройте запуск Actions, скопируйте последние строки лога и пришлите их сюда.
