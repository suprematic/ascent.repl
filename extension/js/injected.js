var _cdtrepl = _cdtrepl || {};

_cdtrepl.loadScript = function(url, callback) {
  var document = window.document;
  var script = document.createElement('script');

  if(callback)
    script.onload = callback;

  script.setAttribute('src', url);
  script.setAttribute('defer', false);
  script.setAttribute('async', true);

  document.body.appendChild(script);
  document.body.removeChild(script);
};

_cdtrepl.EVENT_IN = "CDTReplEventIn";
_cdtrepl.EVENT_OUT = "CDTReplEventOut";

if(chrome.extension) { // we are content script
  chrome.runtime.onConnect.addListener(function (port) {
    var filters = {};
    filters["inject"] = function(message) {
      message.url = chrome.extension.getURL("js/compiled/goog/base.js");

      if(message.dependencies instanceof Array) {
        message.dependencies.forEach(function(dep) {
          dep.module = chrome.extension.getURL(dep.module);
        });
      }

      return message;
    };

    var documentListener = function(data) {
        delete data["returnValue"]; // prevent warning

        port.postMessage(data.detail);
    }

    var portListener = function (message) {
      var filter = filters[message.type];
      
      if(filter)
        message = filter(message);

      if(message)
        document.dispatchEvent(new CustomEvent(_cdtrepl.EVENT_IN, {detail: message}));
    };

    document.addEventListener(_cdtrepl.EVENT_OUT, documentListener);
    port.onMessage.addListener(portListener);

    port.onDisconnect.addListener(function (port) {
      document.removeEventListener(_cdtrepl.EVENT_OUT, documentListener);
      port.onMessage.removeListener(portListener);
    });

    _cdtrepl.loadScript(chrome.extension.getURL('js/injected.js'));
  });
}else{
  (function ()
    {
      var DEBUG = true;

      var sendOut = function(message) {
        document.dispatchEvent(new CustomEvent(_cdtrepl.EVENT_OUT, {detail: message})); 
      };

      var sendProbe = function() {
        var is_cljs = (typeof cljs) != "undefined" && cljs.core != undefined;;

        sendOut({destination: "background", type: "tab-info", agentInfo: {is_cljs: is_cljs}});  
      };

      var handlers = {};
      handlers["inject"] = function(request) {
        if(DEBUG)
          console.debug("injection requested: ", request);

        var onGoogAvailable = function () {
            var dependencies = request.dependencies;
            if(dependencies instanceof Array) {
              dependencies.forEach(function(dependency) {
                if(dependency.module && dependency.provides instanceof Array && dependency.requires instanceof Array)
                  goog.addDependency(dependency.module, dependency.provides, dependency.requires);
                else
                  console.warn("invalid dependency entry: ", dependency);
              });
            }

            var requires = request.requires;
            var count = 0;
            if(requires instanceof Array) {
              goog.writeScriptTag_ = function (url) {   // be able to load scripts after page load
                count++;

                _cdtrepl.loadScript(url, function() {
                  count--;

                  if(count === 0)
                    sendProbe();
                });    
              };

              requires.forEach(goog.require);  
            }
        };

        if((typeof goog) === "undefined") {
          if(DEBUG)
            console.info("goog is not available. loading it from " + request.url);

          _cdtrepl.loadScript(request.url, onGoogAvailable);
        } else
          onGoogAvailable();
      };

      handlers["create-ns"] = function(request) {
        var ns = request.ns;
        if(ns) {
          if(!goog.isProvided_(ns)) {
            goog.provide(ns);
            goog.require("cljs.core");

            if(request.immigrate) {
              to = goog.getObjectByName(ns);
              from  = cljs.core;

              for(prop in from)
                to[prop] = from[prop];
            }
          }else {
            if(DEBUG) 
              console.warn("namespace already exists: " + ns);
          }
        }
      };

      handlers["eval"] = function(request) {
        if(DEBUG)
          console.debug("eval request: ", request);

        var statement = request.statement;
        if(statement) {
          var result = null;

          try {
            result = String(eval(statement));
          }catch(e) {
            result = {exception: true, message: e.constructor.name + ": " + e.message};
          }

          sendOut({destination: request.source, type: "eval-response", id: request.id, result: result});
        }
      };

      document.addEventListener(_cdtrepl.EVENT_IN, function(data) {
        var handler = handlers[data.detail.type];
        if(handler)
          handler(data.detail);
      });

      if(document.readyState == "complete") {
        sendProbe();
      }else{
        window.addEventListener("load", sendProbe);
      }
    }
  )();
}







