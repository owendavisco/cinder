
var ws = new WebSocket('ws://' + "localhost:8080" + '/call');
var desktopVideo;
var webcamVideo;
var webRtcPeerDesktop;
var webRtcPeerWebcam;
var webcamStream;
var desktopStream;

window.onload = function() {
	  desktopVideo = $("#desktop-video")[0];
	  webcamVideo = $("#webcam-video")[0];
	
	 // var $video  = $("#video-holder"),
  //   $window = $(window); 

  //   $(window).resize(function(){
        
  //       var height = $window.height() - $('#menu').height();
  //       $video.css('height', height);
        
  //       var videoWidth = $video.width(),
  //           windowWidth = $window.width(),
  //       marginLeftAdjust =   (windowWidth - videoWidth) / 2;
        
  //       $video.css({
  //           'height': height, 
  //           'marginLeft' : marginLeftAdjust
  //       });
  //   }).resize();
    
    $('[data-toggle="popover"]').popover();

    $("#broadcast").click(function(){
        startBroadcast();
    });

    $("#viewBroadcast").click(function(){
        viewer();
    });

    $("#playRecording").click(function(){
        player();
    });

    $("#terminate").click(function(){
        stop();
    });
};

window.onbeforeunload = function() {
	ws.close();
};

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'broadcasterResponse':
		broadcasterResponse(parsedMessage);
		break;
	case 'viewerResponse':
		viewerResponse(parsedMessage);
		break;
	case 'playResponse':
		playResponse(parsedMessage);
		break;
	case 'iceCandidate':
	  if(parsedMessage.media === 'desktop'){
  		webRtcPeerDesktop.addIceCandidate(parsedMessage.candidate, function(error) {
  			if (error)
  				return console.error('Error adding candidate: ' + error);
  		});
	  }
	  else if(parsedMessage.media === 'webcam'){
	    webRtcPeerWebcam.addIceCandidate(parsedMessage.candidate, function(error) {
  			if (error)
  				return console.error('Error adding candidate: ' + error);
  		});
	  }
		break;
	case 'stopCommunication':
		dispose();
		break;
	default:
		console.error('Unrecognized message', parsedMessage);
	}
};

function broadcasterResponse(message) {
	if (message.response != 'accepted') {
		var errorMsg = message.message ? message.message : 'Unknow error';
		console.info('Starting broadcast failed for the following reason: ' + errorMsg);
		dispose();
	} else {
		if(message.media === 'desktop')
		  webRtcPeerDesktop.processAnswer(message.sdpAnswer, errorFunction);
		else if(message.media === 'webcam')
		  webRtcPeerWebcam.processAnswer(message.sdpAnswer, errorFunction);
	}
}

function errorFunction(error){
  if (error)
    return console.error(error);
}

function viewerResponse(message) {
	if (message.response != 'accepted') {
		var errorMsg = message.message ? message.message : 'Unknow error';
		console.info('Viewing broadcast failed for the following reason: ' + errorMsg);
		dispose();
	} else {
		if(message.media === 'desktop')
		  webRtcPeerDesktop.processAnswer(message.sdpAnswer, errorFunction);
		else
		  webRtcPeerWebcam.processAnswer(message.sdpAnswer, errorFunction);
	}
}

function playResponse(message) {
	if (message.response != 'accepted') {
		var errorMsg = message.message ? message.message : 'Unknow error';
		console.info('Play recording for the following reason: ' + errorMsg);
		dispose();
	} else {
		if(message.media == 'desktop')
		  webRtcPeerDesktop.processAnswer(message.sdpAnswer, errorFunction);
		else if(message.media === 'webcam')
		  webRtcPeerWebcam.processAnswer(message.sdpAnswer, errorFunction);
	}
}

function startBroadcast(){
  if(!webRtcPeerWebcam || !webRtcPeerDesktop){
    webcamStreamConstraints = {
      video: true,
      audio: true
    };
    
    navigator.getUserMedia(webcamStreamConstraints, captureWebcamStream, 
    function(e){
      console.error("An error occured when getting audio stream, " + e.message);
    });
  }
}

function captureWebcamStream(stream){
  webcamStream = stream;
  
  var options = {
    localVideo: webcamVideo,
    onicecandidate: onIceCandidateWebcam,
    webcamStream: webcamStream
  };
  
  webRtcPeerWebcam = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
  function(error) {
    if (error) {
      return console.error(error);
    }
    webRtcPeerWebcam.generateOffer(onOfferBroadcastWebcam);
  });
  
  //TODO: desktop capture
  captureDesktop();
}

function captureDesktop() {
  chrome.desktopCapture.chooseDesktopMedia(['screen', 'window'], getDesktopMedia);
}

function getDesktopMedia(chromeMediaSourceId){
  var media_constraints = {
    audio: false,
    video: {
      mandatory: {
          chromeMediaSource: 'desktop',
          chromeMediaSourceId: chromeMediaSourceId,
          maxWidth: 1920,
          maxHeight: 1080,
          minFrameRate: 30,
          maxFrameRate: 64,
          // minAspectRatio: 1.77,
          googLeakyBucket: true,
          googTemporalLayeredScreencast: true
        },
        optional: []
    }
  };
  
  navigator.getUserMedia(media_constraints, captureDesktopStream, 
  function(e){
    console.error("An error occured when getting desktop stream, " + e.message);
  });
}

function captureDesktopStream(stream){
  desktopStream = stream;
  
  var options = {
    localVideo: desktopVideo,
    onicecandidate: onIceCandidateDesktop,
    videoStream: desktopStream
  };
  
  webRtcPeerDesktop = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
  function(error) {
    if (error) {
      return console.error(error);
    }
    webRtcPeerDesktop.generateOffer(onOfferBroadcastDesktop);
  });
}

function onOfferBroadcastWebcam(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'broadcast',
		media : 'webcam',
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function onOfferBroadcastDesktop(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'broadcast',
		media : 'desktop',
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function viewer() {
  if(!webRtcPeerWebcam){
    var options = {
  		remoteVideo : webcamVideo,
  		onicecandidate : onIceCandidateWebcam
  	};
  	webRtcPeerWebcam = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
  			function(error) {
  				if (error) {
  					return console.error(error);
  				}
  				this.generateOffer(onOfferViewerWebcam);
  			});
  }
  if(!webRtcPeerDesktop){
    var options = {
  		remoteVideo : desktopVideo,
  		onicecandidate : onIceCandidateDesktop
  	};
  	webRtcPeerDesktop = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
  			function(error) {
  				if (error) {
  					return console.error(error);
  				}
  				this.generateOffer(onOfferViewerDesktop);
  			});
  }
}

function onOfferViewerWebcam(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'viewer',
		media : 'webcam',
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function onOfferViewerDesktop(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'viewer',
		media : 'desktop',
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function player() {
	if (!webRtcPeerWebcam) {

		var options = {
			remoteVideo : webcamVideo,
			onicecandidate : onIceCandidateWebcam
		};
		webRtcPeerWebcam = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
				function(error) {
					if (error) {
						return console.error(error);
					}
					this.generateOffer(onOfferPlayerWebcam);
				});
	}
	if (!webRtcPeerDesktop) {

		var options = {
			remoteVideo : desktopVideo,
			onicecandidate : onIceCandidateDesktop
		};
		webRtcPeerDesktop = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
				function(error) {
					if (error) {
						return console.error(error);
					}
					this.generateOffer(onOfferPlayerDesktop);
				});
	}
}

function onOfferPlayerWebcam(error, offerSdp) {
	console.log('Invoking SDP offer callback function');
	var message = {
		id : 'player',
		media : 'webcam',
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function onOfferPlayerDesktop(error, offerSdp) {
	console.log('Invoking SDP offer callback function');
	var message = {
		id : 'player',
		media : 'desktop',
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function onIceCandidateWebcam(candidate) {
	console.log("Local candidate" + JSON.stringify(candidate));

	var message = {
		id : 'onIceCandidate',
		media : 'webcam',
		candidate : candidate
	};
	sendMessage(message);
}

function onIceCandidateDesktop(candidate) {
	console.log("Local candidate" + JSON.stringify(candidate));

	var message = {
		id : 'onIceCandidate',
		media : 'desktop',
		candidate : candidate
	};
	sendMessage(message);
}

function stop() {
	var message = {
		id : 'stop'
	};
	sendMessage(message);
	dispose();
}

function dispose(){
	if(webRtcPeerWebcam){
		webRtcPeerWebcam.dispose();
		webRtcPeerWebcam = null;
	}
	if(webRtcPeerDesktop){
	  webRtcPeerDesktop.dispose();
	  webRtcPeerDesktop = null;
	}
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Sending message: ' + jsonMessage);
	ws.send(jsonMessage);
}