window.onload = function(){
    console.log($("#video"));
    
    navigator.webkitGetUserMedia({video: true}, 
    function(stream){
        console.log("Media acquired.");
        
        $("#rtcvid").attr('src', window.URL.createObjectURL(stream));
    },
    function(){
        console.log("Unable to access media.");
    });
};

var setupStyleListeners = function(){
};