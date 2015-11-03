
var ws = new WebSocket('ws://' + "localhost:8080" + '/call');
var video;
var webRtcPeer;

window.onload = function() {
	video = document.getElementById('video');
    
    $('[data-toggle="popover"]').popover();

    $("#broadcast").click(function(){
        captureDesktop();
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
		webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
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
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	}
}

function viewerResponse(message) {
	if (message.response != 'accepted') {
		var errorMsg = message.message ? message.message : 'Unknow error';
		console.info('Viewing broadcast failed for the following reason: ' + errorMsg);
		dispose();
	} else {
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	}
}

function playResponse(message) {
	if (message.response != 'accepted') {
		var errorMsg = message.message ? message.message : 'Unknow error';
		console.info('Play recording for the following reason: ' + errorMsg);
		dispose();
	} else {
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	}
}

function captureDesktop() {
    var desktop_id = chrome.desktopCapture.chooseDesktopMedia(['screen', 'window'], broadcaster);
}

function broadcaster(chromeMediaSourceId) {
	if (!webRtcPeer) {

    var MEDIA_CONSTRAINTS = {
            audio: false,
            video: {
                mandatory: {
                    chromeMediaSource: 'desktop',
                    chromeMediaSourceId: chromeMediaSourceId,
                    maxWidth: 1920,
                    maxHeight: 1080,
                    minFrameRate: 30,
                    maxFrameRate: 64,
                    minAspectRatio: 1.77,
                    googLeakyBucket: true,
                    googTemporalLayeredScreencast: true
                },
                optional: []
            }
    };
    
    navigator.getUserMedia({video: false, audio:true},
    function(webcamStream){
        navigator.getUserMedia(MEDIA_CONSTRAINTS, 
        function(desktopStream){
          desktopStream.addTrack(webcamStream.getAudioTracks()[0]);
          var options = {
          	  localVideo : video,
          		onicecandidate : onIceCandidate,
          		videoStream : desktopStream
        		};
        		
        	webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
        			function(error) {
        				if (error) {
        					return console.error(error);
        				}
        				webRtcPeer.generateOffer(onOfferbroadcaster);
        			});
        },
        function(error){
          console.log("Error getting the desktop stream " + e.message);
        });
    },
    function(error){
        console.log("Error getting the webcam stream " + e.message);
    });
	}
}

function onOfferbroadcaster(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'broadcaster',
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function viewer() {
	if (!webRtcPeer) {

		var options = {
			remoteVideo : video,
			onicecandidate : onIceCandidate
		};
		webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
				function(error) {
					if (error) {
						return console.error(error);
					}
					this.generateOffer(onOfferViewer);
				});
	}
}

function onOfferViewer(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id : 'viewer',
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function player() {
	if (!webRtcPeer) {

		var options = {
			remoteVideo : video,
			onicecandidate : onIceCandidate
		};
		webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
				function(error) {
					if (error) {
						return console.error(error);
					}
					this.generateOffer(onOfferPlayer);
				});
	}
}

function onOfferPlayer(error, offerSdp) {
	console.log('Invoking SDP offer callback function');
	var message = {
		id : 'player',
		sdpOffer : offerSdp
	};
	sendMessage(message);
}

function onIceCandidate(candidate) {
	console.log("Local candidate" + JSON.stringify(candidate));

	var message = {
		id : 'onIceCandidate',
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

function dispose() {
	if (webRtcPeer) {
		webRtcPeer.dispose();
		webRtcPeer = null;
	}
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Senging message: ' + jsonMessage);
	ws.send(jsonMessage);
}