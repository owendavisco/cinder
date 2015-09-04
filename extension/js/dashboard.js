var streamURL = null;
var video = null;

window.onload = function(){
    video = $("#rtcvid").get(0);
    
    $("#start-stream").click(beginStream);
    $("#start-stream").click(endStream);
};

var beginStream = function(){
    navigator.webkitGetUserMedia({video: true}, 
    function(stream){
        console.log("Media acquired.");
        
        streamURL = window.URL.createObjectURL(stream)
        video.src = streamURL;
    },
    function(e){
        console.log("Unable to access media.", e);
    });
};

var endStream = function(){
    
};

var setupStyleListeners = function(){
};