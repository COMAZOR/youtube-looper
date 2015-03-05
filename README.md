# Youtube-looper

Google Chrome extension that provides an AB-Loop feature for Youtube videos.

You can install the extension from [this page on the Chrome Web Store](https://chrome.google.com/webstore/detail/bidjeabmcpopfddfcnpniceojmkklcje).

The Firefox version is currently waiting for approval at Mozilla Addons.

The Safari version can be download from [this link](https://s3-sa-east-1.amazonaws.com/youtube-looper/youtube-looper.safariextz). It's also awaiting for approval on the Safari Extension Gallery.

## Building it

There are multiple cljsbuild builds available to run here, one for each supported
browser, examples:

```
lein cljsbuild auto firefox-dev
lein cljsbuild auto safari-dev
lein cljsbuild auto chrome-dev
```

Scripts for release builds are also available (only working for Chrome currently):

```
lein cljsbuild once chrome-release
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
