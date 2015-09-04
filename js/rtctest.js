window.onload = function(){
    if (hasGetUserMedia()) {
        buildPage();
    } else {
      alert('getUserMedia() is not supported in your browser');
    }
};

var buildPage = function(){
    navigator.webkitGetUserMedia({video: true, audio: true}, streamCallback, errorCallback);
};

var streamCallback = function(localMediaStream){
    var video = document.querySelector('video');
    video.src = window.URL.createObjectURL(localMediaStream);
        
    video.onloadedmetadata = function(e) {
            
    };
};

var errorCallback = function(e) {
    console.log("Didn't work");
};