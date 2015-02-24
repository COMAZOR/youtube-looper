var pageMod = require("sdk/page-mod");
var self = require("sdk/self");

pageMod.PageMod({
  include: "*.youtube.com",
  contentScriptFile: [
    self.data.url("js/youtube-looper.js"),
    self.data.url("init.js")
  ],
  contentStyleFile: self.data.url("youtube-looper.css")
});
