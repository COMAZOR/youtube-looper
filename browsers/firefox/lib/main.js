var pageMod = require("sdk/page-mod");
var self = require("sdk/self");
var l10n = require("sdk/l10n");

var L10N_KEYS = ["new_loop", "new_loop_name", "disable_loop", "unnamed_section"];

function translationService(worker) {
  worker.port.on("locale", function () {
    var localeMap = {};

    L10N_KEYS.forEach(function(id) {
      localeMap[id] = l10n.get(id, id);
    });

    worker.port.emit("locale-map", localeMap);
  });
}

pageMod.PageMod({
  include: "*.youtube.com",
  contentScriptFile: [
    self.data.url("js/youtube-looper.js"),
    self.data.url("init.js")
  ],
  contentStyleFile: self.data.url("youtube-looper.css"),

  onAttach: function (worker) {
    translationService(worker);
  }
});
