var DEBUG = true;

var tab_infos  = {};
var connections = {};


var assertString = function(v) {
  if((typeof v) !== "string")
    throw Error("string parameter expected");
}

var setPort = function(destination, tabId, port) {
  assertString(tabId);
  assertString(destination);

  var forDestination = connections[destination];
  if(!forDestination) 
    forDestination = connections[destination] = {};

  port.tabId = tabId;

  forDestination[tabId] = port;
}

var setTabInfo = function(tabId, info) {
  assertString(tabId);

  tab_infos[tabId] = info;
}

var getTabInfo = function(tabId) {
  assertString(tabId);

  return tab_infos[tabId];
}

var removeTabInfo = function(tabId) {
  assertString(tabId);

  delete tab_infos[tabId];
}

var removePort = function(destination, tabId) {
  assertString(tabId);
  assertString(destination);

  if(connections[destination])
    delete connections[destination][tabId];
}

var getPort = function(destination, tabId) {
  assertString(tabId);
  assertString(destination);

  var forDestination = connections[destination];
  if(forDestination)
    return forDestination[tabId];
  else
    return null;
}

var setLocalPort = function(destination, handler) {
  assertString(destination);

  setPort("background", destination, {postMessage: handler, tabId: "*"})
}

var getLocalPort = function(destination) {
  assertString(destination);

  return getPort("background", destination)
}

var connect = function(connection, destination, tabId) {
  setPort(destination, tabId, connection);

  var localOnMessage = function(message, sender, sendResponse) {
    var port = null;

    if(message.destination === "background")
      port = getLocalPort(message.type);
    else
      port = getPort(message.destination, tabId);

    if(port) {
      message.source = destination;
      message.sourceTabId = tabId;

      if(DEBUG && message.type !== "log")
        console.debug("message %s:%s -> %s:%s", destination, tabId, message.destination, port.tabId, message);

      port.postMessage(message);
    }else{
      if(DEBUG && message.type !== "log")
        console.debug("message %s:%s -> /dev/null", destination, tabId, message);
    }
  };

  var onDisconnect = function(connection) {
    if(DEBUG)
      console.debug("desconnecting %s:%s", destination, tabId);

    connection.onMessage.removeListener(localOnMessage);  
    removePort(destination, tabId);
  };

  connection.onMessage.addListener(localOnMessage);
  connection.onDisconnect.addListener(onDisconnect);
};

chrome.runtime.onConnect.addListener(function(connection) {
  var parts = connection.name.split(":");
  if(parts.length === 2) {
      if(DEBUG)
        console.debug("incoming connection from %s", connection.name);

      connect(connection, parts[0], parts[1]);

      var tabInfo = getTabInfo(parts[1]);
      if(tabInfo != null)
        connection.postMessage({type: "tab-info", info: tabInfo});
  }
});

setLocalPort("log", function(message) {
  if(DEBUG)
    console.debug("*** " + message.text);
});

setLocalPort("inject-agent", function(message) {
  if(DEBUG)
    console.debug("agent injection requested: ", message);

  var tabId = message.tabId;

  chrome.tabs.executeScript(tabId, 
    {
      file: "js/injected.js"    
    },
    function(_result) {
      if(DEBUG)
        console.debug("connecting to tab:%s", tabId);

      var port = chrome.tabs.connect(tabId, {name: "background:" + tabId});

      connect(port, "tab", String(tabId));    
    });  
});

setLocalPort("tab-info", function(message) {
  if(DEBUG)
    console.debug("background page received tab-info: ", message);

  if(message.source === "tab") { 
    var tabInfo = getTabInfo(message.sourceTabId);
    if(tabInfo) {
      tabInfo.agentInfo = message.agentInfo;

      var port = getPort("repl", message.sourceTabId);
      if(port != null) {
        var out = {type: "tab-info", info: tabInfo};

        if(DEBUG) {
          console.debug("sending tab-info to repl:" + message.sourceTabId, out);
          console.debug("port:", port);
        }

        port.postMessage(out);
      }else
        console.warn("cannot find port repl:%s", message.sourceTabId);
    }else
      console.warn("no tab-info found for tab-info message: ", message);
  }
});


chrome.tabs.onUpdated.addListener(function (tabId, changeInfo, tab) {
  var tabId = String(tabId);

  if(changeInfo.status === "complete") {
    setTabInfo(tabId, {agentInfo: null, url: tab.url});

    var port = getPort("repl", tabId);
    if(port) {
      if(DEBUG)
        console.log("to repl:%s", tabId, getTabInfo(tabId));

      port.postMessage({type: "tab-info", info: getTabInfo(tabId)});
    }
  }
});

chrome.tabs.onRemoved.addListener(function (tabId, removeInfo) {
  removeTabInfo(String(tabId));
});

chrome.tabs.onReplaced.addListener(function(added, removed) {
  removeTabInfo(String(tabId));
});








