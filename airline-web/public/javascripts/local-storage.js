//get the OS preference for color scheme
function getOSPreference(){
    if (!window.matchMedia) {
        return "dark"; //fallback if matchMedia not supported
    }
    return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

//determines if the user has a set theme
function detectColorScheme(){
    var themeMode = localStorage.getItem("themeMode") || "device"; //default to device preference
    var theme;

    if (themeMode === "device") {
        theme = getOSPreference();
        //listen for OS preference changes
        if (window.matchMedia) {
            window.matchMedia("(prefers-color-scheme: light)").addEventListener("change", function(e) {
                if (localStorage.getItem("themeMode") === "device") {
                    theme = e.matches ? "light" : "dark";
                    document.documentElement.setAttribute("data-theme", theme);
                    if (window.AirlineMap) window.AirlineMap.updateMapStyle(theme);
                }
            });
        }
    } else if (themeMode === "light") {
        theme = "light";
    } else {
        theme = "dark";
    }

    document.documentElement.setAttribute("data-theme", theme);
}
detectColorScheme();

function getDefaultMapCentering() {
    var saved = localStorage.getItem('mapCentering');
    if (saved) return saved;
    return window.innerWidth >= 1800 ? 'center' : 'left';
}

//function that changes the theme mode, and sets a localStorage variable to track it between page loads
function switchTheme(mode) {
    localStorage.setItem('themeMode', mode);
    
    var theme;
    if (mode === "device") {
        theme = getOSPreference();
    } else {
        theme = mode;
    }
    
    document.documentElement.setAttribute('data-theme', theme);
    if (window.AirlineMap) window.AirlineMap.updateMapStyle(theme);
}

function switchMapProjection() {
    var projection = $("#switchProjectionFlat").is(':checked') ? "flat" : "globe";
    localStorage.setItem('mapProjection', projection);
    if (window.AirlineMap) window.AirlineMap.applyMapProjection(projection);
}

function switchMapCentering() {
    var centering = $("#switchCenteringCenter").is(':checked') ? "center" : "left";
    localStorage.setItem('mapCentering', centering);
    if (window.AirlineMap) window.AirlineMap.applyMapCentering(centering);
}


$( document ).ready(function() {
    //initialize theme radio buttons
    var themeMode = localStorage.getItem("themeMode") || "device";
    $("input[name='themeMode'][value='" + themeMode + "']").prop('checked', true);
    
    //attach event listeners to theme radio buttons
    $("input[name='themeMode']").on('change', function() {
        switchTheme(this.value);
    });

    // Map projection radio init
    var projection = localStorage.getItem('mapProjection') || 'globe';
    if (projection === 'flat') {
        $("#switchProjectionFlat").prop('checked', true);
    } else {
        $("#switchProjectionGlobe").prop('checked', true);
    }

    // Map centering radio init
    var centering = getDefaultMapCentering();
    if (centering === 'center') {
        $("#switchCenteringCenter").prop('checked', true);
    } else {
        $("#switchCenteringLeft").prop('checked', true);
    }
})
/**
 * global variable to store device settings
 */
let deviceSettings = {};
/**
 * save any setting to local storage
 */
function saveSetting(e) {
    e.currentTarget.classList.toggle('on');
    deviceSettings[e.currentTarget.dataset.store] = e.currentTarget.classList.contains('on');
    localStorage.setItem('deviceSettings', JSON.stringify(deviceSettings));
}
/**
 * init
 */
window.addEventListener('DOMContentLoaded', (event) => {
    const specialToggles = document.querySelectorAll("#settingsModal .checkbox");

    if(localStorage.getItem('deviceSettings')) {
        deviceSettings = JSON.parse(localStorage.getItem('deviceSettings'));
        Object.keys(deviceSettings).forEach(key => {
            if (document.querySelector(`[data-store="${key}"]`)) {
                if (deviceSettings[key] === true) {
                    document.querySelector(`[data-store="${key}"]`).classList.add('on');
                } else {
                    document.querySelector(`[data-store="${key}"]`).classList.remove('on');
                }
            }
        });
    } else {
        specialToggles.forEach(toggle => {
            deviceSettings[toggle.dataset.store] = toggle.classList.contains('on');
        });
        localStorage.setItem('deviceSettings', JSON.stringify(deviceSettings));
    }

    specialToggles.forEach(toggle => {
        toggle.addEventListener('click', (event) => {
            saveSetting(event);
        });
    });
});
