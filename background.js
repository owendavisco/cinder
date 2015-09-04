//Declare dataSources, these are the types of data sources chrome can draw from
var dataSources = ['screen', 'window'];
var desktopMediaRequestId = '';

//Add a connection listener between the webpage and our extenstion
chrome.runtime.onConnect.addListener(function(port) {
    port.onMessage.addListener(function (msg) {
        //if the request it start screensharing, start screensharing
        if(msg.type === 'SHARESCREEN_REQUEST') {
            console.log("requesting share");
            requestScreenSharing(port, msg);
        }
        
        //if this is a cancel request, cancel the screensharing
        if(msg.type === 'SHARESCREEN_CANCEL') {
            cancelScreenSharing(msg);
        }
    });
});

function requestScreenSharing(port, msg) {
    //Use chrome desktopCapture to grab the request id from the created stream
    //This will bring up a menu for the user to choose which screen they want to share
    desktopMediaRequestId = chrome.desktopCapture.chooseDesktopMedia(dataSources, port.sender.tab,         
    function(streamId) 
    {     
        //When they choose the screen to share, send a success message and the stream id
        if (streamId) {
            msg.type = 'SHARESCREEN_SUCCESS';
            msg.streamId = streamId;
        }
        
        //Otherwise send a cancel message
        else {
            msg.type = 'SHARESCREEN_CANCEL';
        }
        
        //Post the message to the webpage
        port.postMessage(msg);
    });
}

function cancelScreenSharing(msg) {
    //If there is a desktop sharing session to cancel
    if (desktopMediaRequestId) {
        //cancel the screen sharing session
        chrome.desktopCapture.cancelChooseDesktopMedia(desktopMediaRequestId);
  }
}

// Avoiding a reload
chrome.windows.getAll({
  populate: true
}, function (windows) {
  var details = { file: 'content-script.js' },
      currentWindow;

  for(var i = 0; i < windows.length; i++ ) {
    currentWindow = windows[i];
    var currentTab;

    for(var j = 0; j < currentWindow.tabs.length; j++ ) {
      currentTab = currentWindow.tabs[j];
      // Skip chrome:// pages
      console.log(currentTab.url);
      if(currentTab.url.match("http://localhost:8080/")) {
        // https://developer.chrome.com/extensions/tabs#method-executeScript
        chrome.tabs.executeScript(currentTab.id, details, function() {
          console.log('Injected content-script.');
        });
      }
    }
  }
});