var DEFAULT_LANGUAGE = "en-us";
var CURRENT_LANGUAGE = navigator.language;
var BASE_URI = safari.extension.baseURI;

var request = function(url, callback) {
  var req = new XMLHttpRequest();
  req.open("GET", url, true);
  req.addEventListener("readystatechange", function() {
    if (req.readyState == 4) {
      callback(req);
    }
  });
  req.send(null);
};

var loadLanguage = function (name, callback) {
  var url = BASE_URI + "locale/" + name + ".edn";

  request(url, function(xhr) {
    if (xhr.responseText.length > 0) {
      callback(xhr.responseText);
    } else {
      loadLanguage(DEFAULT_LANGUAGE, callback);
    }
  });
};

var localeCache = {};

var loadLanguageCached = function (name, callback) {
  if (localeCache[name]) {
    callback(localeCache[name]);
  } else {
    loadLanguage(name, function (value) {
      localeCache[name] = value;
      callback(value);
    });
  }
};

safari.application.addEventListener("message", function (event) {
  loadLanguageCached(CURRENT_LANGUAGE, function (languageData) {
    event.target.page.dispatchMessage("localeData", languageData);
  });
}, false);
