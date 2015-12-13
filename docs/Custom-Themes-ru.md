[EN](https://github.com/miku-nyan/Overchan-Android/blob/gh-pages/docs/Custom-Themes-en.md)

## Создание пользовательской темы

Тема представляет собой файл в формате JSON.
Пример (значения из темы Neutron):

```json
{
  "baseTheme": "dark",
  "materialPrimary": "#212121",
  "materialPrimaryDark": "#000000",
  "materialNavigationBar": "#000000",
  "textColorPrimary": "#698CC0",
  "activityRootBackground": "#212121",
  "sidebarBackground": "#CC212121",
  "sidebarSelectedItem": "#6E6EA5",
  "listSeparatorBackground": "#191919",
  "postUnreadOverlay": "#7F7F3F3F",
  "postBackground": "#2C2C2C",
  "postForeground": "#698CC0",
  "postIndexForeground": "#789922",
  "postIndexOverBumpLimit": "#C41E3A",
  "postNumberForeground": "#C9BE89",
  "postNameForeground": "#B4B9CD",
  "postOpForeground": "#008000",
  "postSageForeground": "#993333",
  "postTripForeground": "#228854",
  "postTitleForeground": "#3941AC",
  "postQuoteForeground": "#789922",
  "spoilerForeground": "#48B0FD",
  "spoilerBackground": "#575757",
  "urlLinkForeground": "#C9BE89",
  "itemInfoForeground": "#999999",
  "searchHighlightBackground": "#EF0FFF"
}
```

Ключ "baseTheme" определяет базовую (системную) тему, допустимые значения: "dark" и "light".
Все остальные значения могут присутствовать опционально (если какое-то отсутствует, то будут использоваться соответствующие значения из встроенных тем: Neutron для "dark", Futaba для "light"). Каждое из этих значений представляет собой цвет (в формате "#RRGGBB" или "#AARRGGBB"):

![colors](https://github.com/miku-nyan/Overchan-Android/raw/gh-pages/docs/Custom-Themes-pic.png)

Итоговый файл с темой должен иметь расширение ".json" (чтобы тема была видна в диалоге выбора файла).
