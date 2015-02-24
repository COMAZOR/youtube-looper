// For some reason the original implementation of this function ends up raising a DOMException
// claiming security issues, by overriding this function with a new implemention is the
// only way that I found to make it work, this is just a shorter version of the original
// function, but just using the latest implementation
goog.async.nextTick.getSetImmediateEmulator_ = function() {
  return function(cb) {
    goog.global.setTimeout(cb, 0);
  };
};

// boot the extension
youtube_looper.core.init();
