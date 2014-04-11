var DEBUG = true;

var tab_infos  = {};
var connections = {};

var setPort = function(destination, tabId, port) {
  var forDestination = connections[destination];
  if(!forDestination) 
    forDestination = connections[destination] = {};

  port.tabId = tabId;

  forDestination[tabId] = port;
}

var removePort = function(destination, tabId) {
  if(connections[destination])
    delete connections[destination][tabId];
}

var getPort = function(destination, tabId) {
  var forDestination = connections[destination];
  if(forDestination)
    return forDestination[tabId];
  else
    return null;
}

var setLocalPort = function(destination, handler) {
  setPort("background", destination, {postMessage: handler, tabId: "*"})
}

var getLocalPort = function(destination) {
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

      if(DEBUG)
        console.debug("message %s:%s -> %s:%s", destination, tabId, message.destination, port.tabId, message);

      port.postMessage(message);
    }else{
      if(DEBUG)
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

      var tabInfo = tab_infos[parts[1]];
      if(tabInfo != null)
        connection.postMessage({type: "tab-info", info: tabInfo});
  }
});

setLocalPort("log", function(message) {
  if(DEBUG)
    console.debug(message.text);
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
  if(message.source === "tab") { 
    var tabInfo = tab_infos[message.sourceTabId];
    if(tabInfo) {
      tabInfo.agentInfo = message.agentInfo;

      var port = getPort("repl", message.sourceTabId);
      if(port != null)
        port.postMessage({type: "tab-info", info: tabInfo});
    }
  }
});


chrome.tabs.onUpdated.addListener(function (tabId, changeInfo, tab) {
  if(changeInfo.status === "complete") {
    tab_infos[tabId] = {agentInfo: null, url: tab.url};

    var port = getPort("repl", tabId);
    if(port) {
      if(DEBUG)
        console.log("to repl:%s", tabId, tab_infos[tabId]);

      port.postMessage({type: "tab-info", info: tab_infos[tabId]});
    }
  }
});

chrome.tabs.onRemoved.addListener(function (tabId, removeInfo) {
  delete tab_infos[tabId];
});

chrome.tabs.onReplaced.addListener(function(added, removed) {
  delete tab_infos[tabId];
});








