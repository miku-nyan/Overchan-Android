[RU](https://github.com/miku-nyan/Overchan-Android/blob/gh-pages/docs/Custom-Themes-ru.md)

## Creating a custom theme

Custom theme is a JSON file.
Example (values from Neutron theme):

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

Key "baseTheme" defines the base (system) theme, valid values: "dark", "light".
All other keys are optional (if not defined, default values from Neutron or Futaba will be used), define colors ("#RRGGBB" or "#AARRGGBB"). 

![colors](https://github.com/miku-nyan/Overchan-Android/raw/gh-pages/docs/Custom-Themes-pic.png)

The result file should have ".json" extension (to be visible in file dialog).
