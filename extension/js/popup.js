window.onload = function(){
    $("#test").click(function(){
        chrome.tabs.create({url: "../dashboard.html/"});
    });
};