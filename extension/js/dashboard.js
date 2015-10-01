var streamURL = null;
var streamTrack = null;
var video = null;

var audioTracks = [];
var videoTracks = [];

window.onload = function(){
    video = $("#rtcvid").get(0);
    
    buildSourceSelect();
    
    $("#start-stream").click(beginStream);
    $("#end-stream").click(endStream);
};

var beginStream = function(){
    var audioSource = audioTracks[$("#audio-select").val()].id;
    var videoSource = videoTracks[$("#video-select").val()].id;
    
    var constraints = {
        audio: {
            optional: [{sourceId: audioSource}]
        },
        video: {
            optional: [{sourceId: videoSource}]
        }
    };
    
    if($("#desktop-checkbox").is(":checked")){
        console.log("checked");
        
        constraints = {
            audio: false,
            video: {
            mandatory: {
                chromeMediaSource: 'screen',
                maxWidth: 1280,
                maxHeight: 720
            },
            optional: []
            }
        }
    }
    
    navigator.webkitGetUserMedia(constraints, 
    function(localstream){
        console.log("Media acquired.");
        
        streamTracks = localstream.getTracks();
        streamURL = window.URL.createObjectURL(localstream)
        video.src = streamURL;
    },
    function(e){
        console.log("Unable to access media.", e);
    });
};

var endStream = function(){
    $.each(streamTracks, function(i, track){
        track.stop();
    });
    console.log("Media ended.");
};

var buildSourceSelect = function(){
    MediaStreamTrack.getSources(function(sourceInfos){
        
        //Retrieve all sources
        $.each(sourceInfos, function(i, info){
            if(info.kind == "audio"){
                audioTracks.push(info);
            }
            if(info.kind == "video"){
                videoTracks.push(info);
            }
        });
        
        console.log("Audio Tracks:", audioTracks, "Video Tracks:", videoTracks);
        
        if(audioTracks.length > 0){
            $("#audio-select")
                .empty()
                .prop("disabled", false);
            
            $.each(audioTracks, function(i, track){
                $('<option>')
                    .html(track.label || "Audio-"+(i+1))
                    .attr('value', i)
                    .appendTo("#audio-select");
            });
        }
        
        if(videoTracks.length > 0){
            $("#video-select")
                .empty()
                .prop("disabled", false);
            
            $.each(videoTracks, function(i, track){
                $('<option>')
                    .html(track.label || "Video-"+(i+1))
                    .attr('value', i)
                    .appendTo("#video-select");
            });
        }
    });
};

var setupStyleListeners = function(){
};