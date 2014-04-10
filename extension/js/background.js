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
  setPort(destination, "*", {postMessage: handler, tabId: "*"})
}

var getLocalPort = function(destination) {
  return getPort(destination, "*")
}

var onTabRemoved = function(tabId) {
  delete tab_infos[tabId];
};

chrome.tabs.onRemoved.addListener(function (tabId, removeInfo) {
  onTabRemoved(tabId);
});

chrome.tabs.onReplaced.addListener(function(added, removed) {
  onTabRemoved(removed);
});

var onMessage = function(sender, destination, tabId, message) {
  if(sender === "tab" && message.type === "tab-info") {
    tab_infos[tabId] = message;    

    var port = getPort("repl", tabId);
    if(port)
      port.postMessage(message);
  }
};

var onConnect = function(port, destination, tabId) {
  if(destination !== "tab" && tab_infos[tabId]) {
    if(DEBUG)
      console.debug("sending tab info to %s:%s", destination, tabId);

    port.postMessage(tab_infos[tabId]);
  }
};

var connect = function(connection, destination, tabId) {
  setPort(destination, tabId, connection);

  var localOnMessage = function(message, sender, sendResponse) {
    onMessage(destination, message.destination, tabId, message);

    var port = getLocalPort(message.destination) || getPort(message.destination, tabId);
    if(port) {
      message.source = destination;

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

  onConnect(connection, destination, tabId);
};

chrome.runtime.onConnect.addListener(function(connection) {
    if(connection.sender && connection.sender.tab) {
      if(DEBUG)
        console.debug("incoming connection from tab:%s", connection.sender.tab.id);

      connect(connection, "tab", connection.sender.tab.id);
    }else
    {
      var parts = connection.name.split(":");
      if(parts.length === 2) {
          if(DEBUG)
            console.debug("incoming connection from %s", connection.name);

          connect(connection, parts[0], parts[1]);
      }
    } 
});


setLocalPort("log", function(message) {
  if(DEBUG)
    console.debug(message.text);
});

chrome.tabs.onUpdated.addListener(function (tabId, changeInfo, tab) {
  if(changeInfo.status === "complete") {
    if(DEBUG)
      console.debug("connecting to tab:%s", tabId);

    var port = chrome.tabs.connect(tabId, {name: "bg:" + tabId});

    connect(port, "tab", String(tab.id));
  }
});






