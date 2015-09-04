var video = document.querySelector('video');

window.onload = function(){
    if (hasGetUserMedia()) {
        buildPage();
    } else {
      alert('getUserMedia() is not supported in your browser');
    }
};

var buildPage = function(){
    MediaStreamTrack.getSources(function(sourceInfos) {
        var audioSource = null;
        var videoSource = null;

        for (var i = 0; i != sourceInfos.length; ++i) {
            var sourceInfo = sourceInfos[i];
            if (sourceInfo.kind === 'audio') {
                audioSource = sourceInfo.id;
            } else if (sourceInfo.kind === 'video') {
                videoSource = sourceInfo.id;
            } else {
                console.log('Some other kind of source: ', sourceInfo);
            }
        }

        sourceSelected(audioSource, videoSource);
    });
};

var sourceSelected = function(audioSource, videoSource) {
    var constraints = {
        audio: {
            optional: [{sourceId: audioSource}]
        },
        video: {
            optional: [{sourceId: videoSource}]
        }
    };

    navigator.webkitGetUserMedia(constraints, 
    successCallback, 
    errorCallback);
}

var successCallback = function(stream){
    video.src = window.URL.createObjectURL(stream);
};

var errorCallback = function(e) {
    console.log("Didn't work");
};
