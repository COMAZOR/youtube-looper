# Youtube-looper

Google Chrome extension that provides an AB-Loop feature for Youtube videos.

You can install the extension from [this page on the Chrome Web Store](https://chrome.google.com/webstore/detail/bidjeabmcpopfddfcnpniceojmkklcje).

## Building it

Run the following command to start building and the REPL with Figwheel

```
lein run -m clojure.main scripts/figwheel.clj
```

## Working with the locales

Each browser has it's own way to deal with i18n (and Safari has no support at all). To
handle it properly we have a compilation step to output the locales on the specific
formats required by each browser.

To change anything about the locales, just work with the files at `resources/locales`
folder (you can also add more locale files there for new translations). Then just run
`./scripts/sync-locales` and their respective versions will be generated.

## License

Copyright © 2015 Wilker Lúcio

Distributed under the MIT License.
