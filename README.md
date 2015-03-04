# Youtube-looper

Google Chrome extension that provides an AB-Loop feature for Youtube videos.

You can install the extension from [this page on the Chrome Web Store](https://chrome.google.com/webstore/detail/bidjeabmcpopfddfcnpniceojmkklcje).

The Firefox version is currently waiting for approval at Mozilla Addons.

The Safari version still on development (it's currently missing i18n features), and will
be released when this part is ready.

## Building it

There are multiple cljsbuild builds available to run here, one for each supported
browser, examples:

```
lein cljsbuild auto firefox-dev
lein cljsbuild auto safari-dev
lein cljsbuild auto chrome-dev
```

Scripts for release builds are also available (except for Firefox currently):

```
lein cljsbuild once chrome-release
lein cljsbuild once safari-release
```

Each build outputs the compiled sources at the correct browser package, you can find
those packages at the `browsers` folder.

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
