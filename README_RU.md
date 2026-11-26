# BubbleChat XML для Sketchware Pro

Полное имя класса: `com.mes.bc.BubbleChat`  
XML namespace: `http://schemas.android.com/apk/res-auto`

Это Android Library project. ZIP содержит исходники библиотеки, а не готовый AAR: для получения AAR проект нужно собрать Android Gradle Plugin с установленным Android SDK (compileSdk 35).

## XML

```xml
<com.mes.bc.BubbleChat
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/bubble_chat"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:padding="12dp"
    app:bc_bubbleColor="#277A65"
    app:bc_arrowSide="right"
    app:bc_arrowWidth="12dp"
    app:bc_arrowHeight="9dp"
    app:bc_arrowPosition="0.72"
    app:bc_cornerTopLeft="18dp"
    app:bc_cornerTopRight="18dp"
    app:bc_cornerBottomRight="18dp"
    app:bc_cornerBottomLeft="18dp"
    app:bc_strokeColor="#00000000"
    app:bc_strokeWidth="0dp"
    app:bc_groupWindowMs="60000"
    app:bc_showArrow="true" />
```

Внутрь `BubbleChat` можно вложить обычные `TextView`, `ImageView`, `LinearLayout` и т. д.

## Правило стрелки между сообщениями

В адаптере списка при привязке каждого сообщения вызывай:

```java
bubble.setMessageGrouping(
    currentMessageTime,
    previousMessageTime,
    currentMessageSenderId.equals(previousMessageSenderId)
);
```

- Первое сообщение: стрелка есть.
- Тот же отправитель, прошло не больше 60 секунд: стрелки нет.
- Другой отправитель: стрелка есть.
- Между сообщениями прошло больше 60 секунд: стрелка снова есть.

Время передавай в миллисекундах (например, `message.getTime()` из `System.currentTimeMillis()`).

Если у тебя нет адаптера и ты сам рассчитываешь группировку:
```java
bubble.setGroupedWithPrevious(true);  // скрыть стрелку
bubble.setGroupedWithPrevious(false); // показать стрелку
```

## Сборка AAR

Открой проект в Android Studio и выполни задачу `:bubblechat:assembleRelease`. Готовый файл будет здесь:

`bubblechat/build/outputs/aar/bubblechat-release.aar`

После этого импортируй AAR через Local Library Manager в Sketchware Pro.

## Важно для Sketchware

XML-класс должен быть доступен компилятору и установленная библиотека должна быть включена в проект. Если Sketchware не принимает custom view из XML, можно добавить тот же `com.mes.bc.BubbleChat` программно через Java, сохранив XML-атрибуты в layout-файле для проекта Android Studio.
