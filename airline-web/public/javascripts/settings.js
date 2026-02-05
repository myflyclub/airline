//determines if the user has a set theme
function toggleSettings() {
    if (!$("#settingsModal").is(":visible")){
        updateCustomWallpaperPanel()
        $("#settingsModal").fadeIn(500)
    } else {
        closeModal($('#settingsModal'))
    }
}


var wallpaperTemplates = [
    {
        "background" : "var(--fallback-background)"
    },
    {
        "background" : "linear-gradient(to bottom, rgba(40, 49, 77, 0.8) 30%, rgba(29, 35, 71, 0.8) 50%, rgba(19, 25, 28, 0.8) 80%, rgba(15, 14, 14, .8) 100%), url(/assets/images/background/pixel_city_1.gif)"
    },
    {
        "background" : "linear-gradient(to bottom, rgba(40, 49, 77, 0.2) 30%, rgba(29, 35, 71, 0.2) 50%, rgba(19, 25, 28, 0.2) 80%, rgba(15, 14, 14, .2) 100%), url(/assets/images/background/pixel_city_2.gif)"
    },
    {
        "background" : "linear-gradient(to bottom, rgba(40, 49, 77, 0.2) 30%, rgba(29, 35, 71, 0.2) 50%, rgba(19, 25, 28, 0.2) 80%, rgba(15, 14, 14, .2) 100%), url(/assets/images/background/pixel_city_3.gif)"
    },
    {
        "background" : "linear-gradient(to bottom, rgba(40, 49, 77, 0.2) 30%, rgba(29, 35, 71, 0.2) 50%, rgba(19, 25, 28, 0.2) 80%, rgba(15, 14, 14, .2) 100%), url(/assets/images/background/pixel_city_4.gif)"
    },
    {
        "background" : "linear-gradient(to bottom, rgba(40, 49, 77, 0.8) 30%, rgba(29, 35, 71, 0.8) 50%, rgba(19, 25, 28, 0.8) 80%, rgba(15, 14, 14, .8) 100%), url(/assets/images/background/airport.jpg)"
    }

]

function changeWallpaper() {
    var wallpaperIndex = 0
    if (localStorage.getItem('wallpaperIndex')) {
        wallpaperIndex = parseInt(localStorage.getItem('wallpaperIndex'))
    }

    if (activeUser && activeUser.hasWallpaper) {
        removeCustomWallpaper()
    }
    wallpaperIndex = (wallpaperIndex + 1) % wallpaperTemplates.length
    localStorage.setItem('wallpaperIndex', wallpaperIndex)
    refreshWallpaper()
}

function refreshWallpaper() {
    var template
    if (activeUser && activeUser.hasWallpaper) {
        template = {
            "background" : "url(users/" + activeUser.id + "/wallpaper)"
        }
    } else {
        const body = document.querySelector("body");
        var wallpaperIndex = 0
        if (localStorage.getItem('wallpaperIndex')) {
            wallpaperIndex = parseInt(localStorage.getItem('wallpaperIndex'))
            if (wallpaperIndex >= wallpaperTemplates.length) {
                wallpaperIndex = 0
            }
        }
        if (wallpaperIndex < wallpaperTemplates.length) {
            template = wallpaperTemplates[wallpaperIndex]
        } else { //somehow an index that does not exist, might happen when wallpaper list switches
            template = wallpaperTemplates[0]
        }
        if (wallpaperIndex === 0) {
            body.removeAttribute('style');
            body.style.backgroundColor = template.background;
        } else if (wallpaperIndex < 4) {
            body.style.background = template.background;
            body.style.imageRendering = "pixelated";
        } else {
            body.style.background = template.background;
            body.style.imageRendering = "auto";
        }
    }


    $("body").css("background", template.background)
    $("body").css("background-repeat", "no-repeat")
    $("body").css("background-attachment", "fixed")
    $("body").css("background-size", "cover")
    $("body > div").css("image-rendering", "auto") // this prevents the non-pixel images from looking weird

}

var wallpaperUploaderObj

function removeCustomWallpaper() {
    activeUser.hasWallpaper = false
    $.ajax({
        type: 'DELETE',
        url: "/users/" + activeUser.id + "/wallpaper",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function() {

        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function updateCustomWallpaperPanel() {
    if (wallpaperUploaderObj) {
        wallpaperUploaderObj.reset()
    }

    if (!activeUser) {
        $('#settingsModal .customWallpaper').hide()
    } else {
        $('#settingsModal .customWallpaper').show()
        $('#settingsModal .customWallpaper .warning').hide()

        if (activeUser.level >= 0) {
            wallpaperUploaderObj = $("#settingsModal .customWallpaper .fileuploader").uploadFile({
                        url:"users/" + activeUser.id + "/wallpaper",
                        multiple:false,
                        dragDrop:false,
                        acceptFiles:"image/png,image/gif,image/jpg",
                        fileName:"wallpaperFile",
                        maxFileSize:2 * 1024 * 1024,
                        onSuccess:function(files,data,xhr,pd)
                        {
                            if (data.success) {
                                $('#settingsModal .customWallpaper .warning').hide()
                                wallpaperUploaderObj.reset()
                                activeUser.hasWallpaper = true
                                refreshWallpaper()
                            } else if (data.error) {
                                $('#settingsModal .customWallpaper .warning').text(data.error)
                                $('#settingsModal .customWallpaper .warning').show()
                            }

                        }
                    });
        } else {
              $('#settingsModal .customWallpaper .warning').text("Feature is only avaiable to Patreons")
              $('#settingsModal .customWallpaper .warning').show()
        }
    }





}
