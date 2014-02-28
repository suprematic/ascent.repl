goog.writeScriptTag_ = function(src) {
  if (goog.inHtmlDocument_()) {
    var doc = goog.global.document;
    
    var script = document.createElement('script');
    script.setAttribute('src', src);

    document.head.appendChild(script);
      
    return true;
  } else {
    return false;
  }
};

goog.addDependency("base.js", ['goog'], []);
goog.addDependency("string/string.js", ['goog.string', 'goog.string.Unicode'], []);
goog.addDependency("debug/error.js", ['goog.debug.Error'], []);
goog.addDependency("asserts/asserts.js", ['goog.asserts', 'goog.asserts.AssertionError'], ['goog.debug.Error', 'goog.string']);
goog.addDependency("array/array.js", ['goog.array', 'goog.array.ArrayLike'], ['goog.asserts']);
goog.addDependency("object/object.js", ['goog.object'], []);
goog.addDependency("string/stringbuffer.js", ['goog.string.StringBuffer'], []);
goog.addDependency("../cljs/core.js", ['cljs.core'], ['goog.string', 'goog.array', 'goog.object', 'goog.string.StringBuffer']);

goog.require('cljs.core');