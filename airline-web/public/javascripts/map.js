let map = null
let markers
let baseMarkers = []
let contestedMarkers = []
let airportMapCircle
let championMapMode = false
let _toggleState_AllianceBaseMapView = false;

function initMap() {
    initStyles();

    map = new google.maps.Map(document.getElementById('map'), {
        center: { lat: 20, lng: 150.644 },
        zoom: 2,
        minZoom: 2,
        gestureHandling: 'greedy',
        styles: getMapStyles(),
        mapTypeId: getMapTypes(),
        restriction: {
            latLngBounds: { north: 85, south: -85, west: -180, east: 180 },
        }
    });

    map.addListener('zoom_changed', () => {
        const zoom = map.getZoom();
        Object.values(markers).forEach(marker => {
            marker.setVisible(isShowMarker(marker, zoom));
        });
    });

    map.addListener('maptypeid_changed', () => {
        const mapType = map.getMapTypeId();
        document.cookie = `currentMapTypes=${encodeURIComponent(mapType)}`;
    });

    //map controls
    const toggleChampionButton = htmlToElement(`
        <div id="toggleChampionButton" class="googleMapIcon" onclick="toggleChampionMap()" align="center"  style="margin-bottom: 10px;">
            <span class="alignHelper"></span>
            <img src="/assets/images/icons/crown.png" title='toggle champion' style="vertical-align: middle;"/>
        </div>`);

    const toggleAllianceBaseMapViewButton = htmlToElement(`
        <div id="toggleAllianceBaseMapViewButton" class="googleMapIcon" onclick="toggleAllianceBaseMapViewButton()" align="center" style="margin-bottom: 10px;">
            <span class="alignHelper"></span>
            <img src="/assets/images/icons/puzzle.png" title='Toggle alliance bases' style="vertical-align: middle;"/>
        </div>`);

    toggleAllianceBaseMapViewButton.index = 0;
    toggleChampionButton.index = 3;

    const mapHeight = document.getElementById('map').clientHeight;

    if (mapHeight > 500) {
        map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].push(toggleAllianceBaseMapViewButton);
        map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].push(toggleChampionButton);
    } else {
        map.controls[google.maps.ControlPosition.LEFT_BOTTOM].push(toggleAllianceBaseMapViewButton);
        map.controls[google.maps.ControlPosition.LEFT_BOTTOM].push(toggleChampionButton);
    }
}
/**
 * Map view init
 */
function addMarkers() {
    markers = undefined;
    const infoWindow = new google.maps.InfoWindow({
        maxWidth: 250
    });
    const originalOpacity = 0.7;
    let currentZoom = map.getZoom();

    infoWindow.addListener('closeclick', () => {
        if (infoWindow.marker) {
            infoWindow.marker.setOpacity(originalOpacity);
        }
    });

    const resultMarkers = {};
    for (let i = 0; i < airports.length; i++) {
        const airport = airports[i];
        const position = { lat: airport.latitude, lng: airport.longitude };
        const icon = getAirportIcon(airport);

        const marker = new google.maps.Marker({
            position: position,
            map: map,
            title: airport.name,
            airportName: airport.name,
            opacity: originalOpacity,
            airport: airport,
            icon: icon,
            originalIcon: icon, //so we can flip back and forth
        });

        let zIndex = airport.size * 10;
        zIndex += Math.min(10, Math.floor(airport.population / 1000000));
        zIndex += (airport.isOrangeAirport || airport.isGateway) ? 15 : 0;
        marker.setZIndex(zIndex); //major airport should have higher index

        marker.addListener('click', function () {
            infoWindow.close();
            if (infoWindow.marker && infoWindow.marker !== this) {
                infoWindow.marker.setOpacity(originalOpacity);
            }

            activeAirport = airport;

            if (activeAirline) {
                updateBaseInfo(airport.id);
            }

            // Get the openness span element
            const opennessSpan = getOpennessSpan(loadedCountriesByCode[airport.countryCode].openness, airport.size, airport.isDomesticAirport, airport.isGateway, true);
            const cycleData = (airportsLatestData && airportsLatestData.champions) ? airportsLatestData.champions[airport.id] : null;
            const rep = cycleData ? cycleData.reputation : "-";
            const travelRate = cycleData ? cycleData.travelRate : "-";

            // Update popup elements directly
            document.getElementById('airportPopupName').textContent = airport.name;
            document.getElementById('airportPopupIata').textContent = airport.iata;
            document.getElementById('airportPopupCity').innerHTML = `${airport.city}&nbsp;${getCountryFlagImg(airport.countryCode)}${opennessSpan}`; // opennessSpan is already an HTML string
            document.getElementById('airportPopupPopulation').textContent = commaSeparateNumber(airport.population);
            document.getElementById('airportPopupIncomeLevel').textContent = "$" + commaSeparateNumber(airport.income);
            document.getElementById('airportPopupMaxRunwayLength').innerHTML = airport.runwayLength + "m";
            document.getElementById('airportPopupSize').textContent = airport.size;
            document.getElementById('airportPopupTravelRate').innerHTML = travelRate + "%";
            document.getElementById('airportPopupReputation').innerHTML = rep;
            document.getElementById('airportPopupIcons').innerHTML = airport.hasOwnProperty("features") ? airport.features.map(updateFeatures).join('') : "";
            document.getElementById('airportPopupId').value = airport.id;
            document.getElementById('viewAirportButton').href = `/airport/${airport.iata}`;
            
            const popup = document.getElementById('airportPopup').cloneNode(true);
            popup.style.display = ''; // Use empty string to revert to default (or 'block')
            infoWindow.setContent(popup);
            infoWindow.open(map, this);
            infoWindow.marker = this;

            activeAirportPopupInfoWindow = infoWindow;

            const planButton = document.getElementById('planToAirportButton');
            if (activeAirline && activeAirline.headquarterAirport) {
                planButton.style.display = '';
            } else {
                planButton.style.display = 'none';
            }
        });

        marker.addListener('mouseover', function (event) {
            this.setOpacity(0.9);
        });
        marker.addListener('mouseout', function (event) {
            if (infoWindow.marker !== this) {
                this.setOpacity(originalOpacity);
            }
        });

        marker.setVisible(isShowMarker(marker, currentZoom));
        resultMarkers[airport.id] = marker;
    }
    //now assign it to markers to indicate that it's ready
    markers = resultMarkers;
}

function removeMarkers() {
    // markers is an object, so iterate over its values
    Object.values(markers).forEach(marker => {
        marker.setMap(null);
    });
    markers = {};
}

function getAirportIcon(airportInfo) {
    const mapElement = document.getElementById('map');
    const largeAirportMarkerIcon = mapElement.dataset.largeAirportMarker;
    const mediumAirportMarkerIcon = mapElement.dataset.mediumAirportMarker;
    const smallAirportMarkerIcon = mapElement.dataset.smallAirportMarker;
    const gatewayAirportMarkerIcon = mapElement.dataset.gatewayAirportMarker;
    const domesticAirportMarkerIcon = mapElement.dataset.domesticAirportMarker;

    let icon;
    if (airportInfo.isGateway) {
        icon = gatewayAirportMarkerIcon;
    } else if (airportInfo.isOrangeAirport) {
        icon = domesticAirportMarkerIcon;
    } else if (airportInfo.size <= 3) {
        icon = smallAirportMarkerIcon;
    } else if (airportInfo.size <= 6) {
        icon = mediumAirportMarkerIcon;
    } else {
        icon = largeAirportMarkerIcon;
    }
    return icon;
}

function isShowMarker(marker, zoom) {
    if (championMapMode && !marker.championIcon) {
        return false;
    }
    return (marker.isBase) || ((zoom >= 4) && (zoom + marker.airport.size / 2 >= 7.5)); //start showing size >= 7 at zoom 4
}

function toggleChampionMap() {
    const zoom = map.getZoom();
    championMapMode = !championMapMode;
    const champions = airportsLatestData.champions;

    // markers is an object, so iterate over its values
    Object.values(markers).forEach(marker => {
        if (championMapMode) {
            if (champions && champions[marker.airport.id]) {
                marker.previousIcon = marker.icon;
                marker.previousTitle = marker.title;

                const champ = champions[marker.airport.id];
                const championIcon = '/airlines/' + champ.championAirlineId + '/logo';
                marker.championIcon = championIcon;
                marker.setIcon(championIcon);
                let title = marker.title + " - " + champ.championAirlineName;

                google.maps.event.clearListeners(marker, 'mouseover');
                google.maps.event.clearListeners(marker, 'mouseout');

                if (champ.contested) {
                    addContestedMarker(marker);
                    title += " (contested by " + champ.contested + ")";
                }
                marker.setTitle(title);
            } else {
                marker.setVisible(false);
            }
        } else {
            if (marker.championIcon) {
                marker.setTitle(marker.previousTitle);
                marker.setIcon(marker.previousIcon);
            }
            while (contestedMarkers.length > 0) {
                const contestedMarker = contestedMarkers.pop();
                contestedMarker.setMap(null);
            }
            marker.setVisible(isShowMarker(marker, zoom));
            updateAirportMarkers(activeAirline);
        }
    });
}

function addContestedMarker(airportMarker) {
    const contestedMarker = new google.maps.Marker({
        position: airportMarker.getPosition(),
        map,
        title: "Contested",
        icon: { anchor: new google.maps.Point(-5, 15), url: "/assets/images/icons/fire.svg" },
        zIndex: 500
    });
    //marker.setVisible(isShowMarker(airportMarker, zoom))
    contestedMarker.bindTo("visible", airportMarker);
    contestedMarker.setZIndex(airportMarker.getZIndex() + 1);
    contestedMarkers.push(contestedMarker);
}

function updateAirportBaseMarkers(newBaseAirports, relatedFlightPaths) {
    //reset baseMarkers
    baseMarkers.forEach(marker => {
        marker.setIcon(marker.originalIcon);
        marker.isBase = false;
        marker.setVisible(isShowMarker(marker, map.getZoom()));
        marker.baseInfo = undefined;
        google.maps.event.clearListeners(marker, 'mouseover');
        google.maps.event.clearListeners(marker, 'mouseout');
    });
    baseMarkers = [];

    const mapElement = document.getElementById('map');
    const headquarterMarkerIcon = mapElement.dataset.headquarterMarker;
    const baseMarkerIcon = mapElement.dataset.baseMarker;
    const baseAllianceIcon = mapElement.dataset.baseAllianceMarker;
    const baseAllianceHQIcon = mapElement.dataset.baseAlliancehqMarker;

    newBaseAirports.forEach(baseAirport => {
        if (!baseAirport) return;
        const marker = markers[baseAirport.airportId];
        if (!marker) return; // Fix: Added check for marker existence

        if (baseAirport.airlineId !== activeAirline.id) {
            marker.setIcon(baseAirport.headquarter ? baseAllianceHQIcon : baseAllianceIcon);
        } else {
            marker.setIcon(baseAirport.headquarter ? headquarterMarkerIcon : baseMarkerIcon);
        }

        marker.setZIndex(999);
        marker.isBase = true;
        marker.setVisible(true);
        marker.baseInfo = baseAirport;
        
        marker.addListener('mouseover', function (event) {
            // relatedFlightPaths is an object
            Object.values(relatedFlightPaths).forEach(pathEntry => {
                const path = pathEntry.path;
                const link = pathEntry.path.link;
                
                // Store original opacity directly on the path object
                if (path.originalOpacity === undefined) {
                    path.originalOpacity = path.strokeOpacity;
                }
                
                if (link.fromAirportId !== baseAirport.airportId || link.airlineId !== baseAirport.airlineId) {
                    path.setOptions({ strokeOpacity: 0.1 });
                } else {
                    path.setOptions({ strokeOpacity: 0.8 });
                }
            });
        });
        
        marker.addListener('mouseout', function (event) {
            // relatedFlightPaths is an object
            Object.values(relatedFlightPaths).forEach(pathEntry => {
                const path = pathEntry.path;
                const originalOpacity = path.originalOpacity; // Read directly from path
                if (originalOpacity !== undefined) {
                    path.setOptions({ strokeOpacity: originalOpacity });
                    // Optionally clear it if you want it reset next time
                    // path.originalOpacity = undefined; 
                }
            });
        });

        baseMarkers.push(marker);
    });

    return baseMarkers;
}

function updateAirportMarkers(airline) { //set different markers for head quarter and bases
    if (!markers) { //markers not ready yet, wait
        setTimeout(() => updateAirportMarkers(airline), 500);
    } else {
        if (airline) {
            updateAirportBaseMarkers(airline.baseAirports, flightPaths);
        } else {
            updateAirportBaseMarkers([], flightPaths); // Pass empty array and flightPaths
        }
    }
}

//airport links view
function toggleAirportLinksView() {
    clearAirportLinkPaths(); //clear previous ones if exist
    deselectLink();
    toggleAirportLinks(activeAirport);
}

function closeAirportInfoPopup() {
    if (activeAirportPopupInfoWindow) {
        activeAirportPopupInfoWindow.close(map);
        if (activeAirportPopupInfoWindow.marker) {
            activeAirportPopupInfoWindow.marker.setOpacity(0.7);
        }
        activeAirportPopupInfoWindow = undefined;
    }
}

function drawAirportLinkPath(localAirport, details) {
    const remoteAirport = details.remoteAirport;
    const from = new google.maps.LatLng({ lat: localAirport.latitude, lng: localAirport.longitude });
    const to = new google.maps.LatLng({ lat: remoteAirport.latitude, lng: remoteAirport.longitude });
    const pathKey = remoteAirport.id;

    const totalCapacity = details.capacity.total;
    let opacity;
    if (totalCapacity < 2000) {
        opacity = 0.2 + (totalCapacity / 2000) * 0.6;
    } else {
        opacity = 0.8;
    }

    const airportLinkPath = new google.maps.Polyline({
        geodesic: true,
        strokeColor: "#DC83FC",
        strokeOpacity: opacity,
        strokeWeight: 2,
        path: [from, to],
        zIndex: 1100,
        map: map,
    });

    const fromAirport = getAirportText(localAirport.city, localAirport.iata);
    const toAirport = getAirportText(remoteAirport.city, remoteAirport.iata);

    const shadowPath = new google.maps.Polyline({
        geodesic: true,
        strokeColor: "#DC83FC",
        strokeOpacity: 0.0001,
        strokeWeight: 25,
        path: [from, to],
        zIndex: 401,
        fromAirport: fromAirport,
        fromCountry: localAirport.countryCode,
        toAirport: toAirport,
        toCountry: remoteAirport.countryCode,
        details: details,
        map: map,
    });
    polylines.push(airportLinkPath);
    polylines.push(shadowPath);

    airportLinkPath.shadowPath = shadowPath;

    let infowindow;
    shadowPath.addListener('mouseover', function (event) {
        highlightPath(airportLinkPath, false);
        
        document.getElementById('airportLinkPopupFrom').innerHTML = getCountryFlagImg(this.fromCountry) + this.fromAirport;
        document.getElementById('airportLinkPopupTo').innerHTML = getCountryFlagImg(this.toCountry) + this.toAirport;
        document.getElementById('airportLinkPopupCapacity').textContent = toLinkClassValueString(this.details.capacity) + "(" + this.details.frequency + ")";
        
        const operatorsContainer = document.getElementById('airportLinkOperators');
        operatorsContainer.innerHTML = ''; // Clear previous operators
        
        this.details.operators.forEach(operator => {
            const operatorDiv = document.createElement('div');
            operatorDiv.innerHTML = getAirlineLogoSpan(operator.airlineId, operator.airlineName) +
                                   `<span>${operator.frequency}&nbsp;flight(s) weekly&nbsp;${toLinkClassValueString(operator.capacity)}</span>`;
            operatorsContainer.appendChild(operatorDiv);
        });

        infowindow = new google.maps.InfoWindow({
            maxWidth: 400
        });
        
        const popup = document.getElementById('airportLinkPopup').cloneNode(true);
        popup.style.display = ''; // Show
        infowindow.setContent(popup);
        infowindow.setPosition(event.latLng);
        infowindow.open(map);
    });
    
    shadowPath.addListener('mouseout', function (event) {
        unhighlightPath(airportLinkPath);
        if (infowindow) {
            infowindow.close();
        }
    });

    airportLinkPaths[pathKey] = airportLinkPath;
}

function clearMarkerEntry(markerEntry) {
	//remove all animation intervals
	window.clearInterval(markerEntry.animation)

	//remove all markers
	$.each(markerEntry.markers, function(key, marker) {
		marker.setMap(null)
	})
}

function clearPathEntry(pathEntry) {
	pathEntry.path.setMap(null)
	pathEntry.shadow.setMap(null)
}

function clearAllPaths() {
	//remove all links from UI first
	$.each(flightPaths, function( key, pathEntry ) {
		clearPathEntry(pathEntry)
	})

	flightPaths = {}

	$.each(polylines, function(index, polyline) {
		if (polyline.getMap() != null) {
			polyline.setMap(null)
		}
	})

	polylines = polylines.filter(function(polyline) {
	    return polyline.getMap() != null
	})
}

function clearAirportLinkPaths() {
    // airportLinkPaths is an object
    Object.values(airportLinkPaths).forEach(airportLinkPath => {
        airportLinkPath.setMap(null);
        airportLinkPath.shadowPath.setMap(null);
    });
    airportLinkPaths = {};
}

function hideAirportLinksView() {
    clearAirportLinkPaths();
    updateLinksInfo(); //redraw all flight paths
    document.getElementById('topAirportLinksPanel').style.display = 'none';
}

function addAirlineSpecificMapControls(map) {
    // Uses the htmlToElement helper function
    const toggleHeatmapButton = htmlToElement(`
        <div id="toggleMapHeatmapButton" class="googleMapIcon" onclick="toggleHeatmap()" align="center"  style="margin-bottom: 10px;">
            <span class="alignHelper"></span>
            <img src="/assets/images/icons/table-heatmap.png" title='toggle heatmap' style="vertical-align: middle;"/>
        </div>`);
    
    toggleHeatmapButton.index = 4;

    const mapHeight = document.getElementById('map').clientHeight;

    if (mapHeight > 500) {
        map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].insertAt(3, toggleHeatmapButton);
     } else {
        map.controls[google.maps.ControlPosition.LEFT_BOTTOM].insertAt(3, toggleHeatmapButton);
    }
}

function drawFlightPath(link, linkColor) {

   if (!linkColor) {
	   linkColor = getLinkColor(link.profit, link.revenue)
   }
   var flightPath = new google.maps.Polyline({
     path: [{lat: link.fromLatitude, lng: link.fromLongitude}, {lat: link.toLatitude, lng: link.toLongitude}],
     geodesic: true,
     strokeColor: linkColor,
     strokeOpacity: pathOpacityByStyle[currentStyles].normal,
     strokeWeight: 2,
     frequency : link.frequency,
     modelId : link.modelId,
     link : link,
     zIndex: 90
   });

   var icon = "/assets/images/icons/airplane.png"

   flightPath.setMap(map)
   polylines.push(flightPath)

   var shadowPath = new google.maps.Polyline({
	     path: [{lat: link.fromLatitude, lng: link.fromLongitude}, {lat: link.toLatitude, lng: link.toLongitude}],
	     geodesic: true,
	     map: map,
	     strokeColor: getLinkColor(link.profit, link.revenue),
	     strokeOpacity: 0.001,
	     strokeWeight: 15,
	     zIndex: 100
	   });

   var resultPath = { path : flightPath, shadow : shadowPath }
   if (link.id) {
	  shadowPath.addListener('click', function() {
	   		selectLinkFromMap(link.id, false)
	  });
	  flightPaths[link.id] = resultPath
   }

   return resultPath
}

function refreshFlightPath(link, forceRedraw) {
	if (flightPaths[link.id]) {
		var path = flightPaths[link.id].path
		if (forceRedraw || path.frequency != link.frequency || path.modelId != link.modelId) { //require marker change
			path.frequency = link.frequency
			path.modelId = link.modelId
		}
		path.setOptions({ strokeColor : getLinkColor(link.profit, link.revenue), strokeOpacity : pathOpacityByStyle[currentStyles].normal })
	}
}

function getLinkColor(profit, revenue) {
   if (profit !== undefined) {
	   var maxProfitFactor = 0.5
	   var minProfitFactor = -0.5
	   var profitFactor
	   if (revenue > 0) {
		   profitFactor = profit / revenue
	   } else if (profit < 0) { //revenue 0, losing money
		   profitFactor = minProfitFactor
	   } else {
		   profitFactor = 0
	   }

	   if (profitFactor > maxProfitFactor) {
		   profitFactor = maxProfitFactor
	   } else if (profitFactor < minProfitFactor) {
		   profitFactor = minProfitFactor
	   }
	   var redHex
	   if (profitFactor > 0) {
		   redHex = 220 * (1 - (profitFactor / maxProfitFactor))
	   } else {
		   redHex = 220
	   }
	   var greenHex
	   if (profitFactor < 0) {
		   greenHex = 220 * (1 + (profitFactor / maxProfitFactor))
	   } else {
		   greenHex = 220
	   }
	   if (currentStyles === "light") {
	      redHex -= 50
	      greenHex -= 50
	   }
	   if (redHex < 0) redHex = 0
	   if (greenHex < 0) greenHex = 0


	   var redHexString = parseInt(redHex).toString(16)
	   if (redHexString.length == 1) { redHexString = "0" + redHexString }
	   var greenHexString = parseInt(greenHex).toString(16)
	   if (greenHexString.length == 1) { greenHexString = "0" + greenHexString }
	   return colorHex = "#" + redHexString + greenHexString + "20"
   } else  { //no history yet
	   return "#DCDC20"
   }
}

function highlightPath(path, refocus) {
	refocus = refocus || false
	//focus to the from airport
	if (refocus) {
		map.setCenter(path.getPath().getAt(0))
	}


	if (!path.highlighted) { //only highlight again if it's not already done so
	    var originalColorString = path.strokeColor
		//keep track of original values so we can revert...shouldn't there be a better way to just get all options all at once?
		path.originalColor = originalColorString
		path.originalStrokeWeight = path.strokeWeight
		path.originalZIndex = path.zIndex
		path.originalStrokeOpacity = path.strokeOpacity

		path.setOptions({ strokeOpacity : pathOpacityByStyle[currentStyles].highlight })
		var totalFrames = 20

		var rgbHexValue = parseInt(originalColorString.substring(1), 16);
		var currentRgb = { r : rgbHexValue >> (4 * 4), g : rgbHexValue >> (2 * 4) & 0xff, b : rgbHexValue & 0xff }
		var highlightColor = { r : 0xff, g : 0xff, b : 0xff}
		var colorStep = { r : (highlightColor.r - currentRgb.r) / totalFrames, g : (highlightColor.g - currentRgb.g) / totalFrames, b : (highlightColor.b - currentRgb.b) / totalFrames }
		var currentFrame = 0
		var animation = window.setInterval(function() {
			if (currentFrame < totalFrames) { //transition to highlight color
				currentRgb = { r : currentRgb.r + colorStep.r, g : currentRgb.g + colorStep.g, b : currentRgb.b + colorStep.b }
			} else { //transition back to original color
				currentRgb = { r : currentRgb.r - colorStep.r, g : currentRgb.g - colorStep.g, b : currentRgb.b - colorStep.b }
			}
			//convert currentRgb back to hexstring
			var redHex = Math.round(currentRgb.r).toString(16)
			if (redHex.length < 2) {
				redHex = "0" + redHex
			}
			var greenHex = Math.round(currentRgb.g).toString(16)
			if (greenHex.length < 2) {
				greenHex = "0" + greenHex
			}
			var blueHex = Math.round(currentRgb.b).toString(16)
			if (blueHex.length < 2) {
				blueHex = "0" + blueHex
			}

			var colorHexString = "#" + redHex + greenHex + blueHex
			path.setOptions({ strokeColor : colorHexString , strokeWeight : 4, zIndex : 91})

			currentFrame = (currentFrame + 1) % (totalFrames * 2)

		}, 50)
		path.animation = animation

		path.highlighted = true
	}

}
function unhighlightPath(path) {
	window.clearInterval(path.animation)
	path["animation"] = undefined
	path.setOptions({ strokeColor : path.originalColor , strokeWeight : path.originalStrokeWeight, zIndex : path.originalZIndex, strokeOpacity : path.originalStrokeOpacity})

	delete path.highlighted
}

/**
 * deselect a currently selected link, perform both UI and underlying data changes
 * @returns
 */
function deselectLink() {
	if (selectedLink) {
		unhighlightLink(selectedLink)
		selectedLink = undefined
	}
	removeTempPath()
	$("#sidePanel").fadeOut(200)
}

/**
 * Perform UI changes for unhighlighting currently highlighted link
 * @param linkId
 * @returns
 */
function unhighlightLink() {
	$.each(flightPaths, function(linkId, path) {
		if (path.path.highlighted) {
			unhighlightPath(path.path)
		}
	})

}

/**
 * Performs UI changes to highlight a link
 */
function highlightLink(linkId, refocus) {
	if (tempPath) {
		removeTempPath(tempPath)
	}

	//highlight the selected link's flight path
	highlightPath(flightPaths[linkId].path, refocus)

	//highlight the corresponding list item
//	var selectedListItem = $("#linkList a[data-link-id='" + linkId + "']")
//	selectedListItem.addClass("selected")
}

//alliance 

function showAllianceMap() {
	clearAllPaths()
	deselectLink()

	var alliancePaths = []

	$.ajax({
		type: 'GET',
		url: "/alliances/" + selectedAlliance.id + "/details",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
				$.each(result.links, function(index, link) {
					alliancePaths.push(drawAllianceLink(link))
				})
				var allianceBases = []
				 $.each(result.members, function(index, airline) {
				    if (airline.role != "APPLICANT") {
				        $.merge(allianceBases, airline.bases)
                    }
                })

				var airportMarkers = updateAirportBaseMarkers(allianceBases, alliancePaths)
				//now add extra listener for alliance airports
				$.each(airportMarkers, function(key, marker) {
                        marker.addListener('mouseover', function(event) {
                            closeAlliancePopups()
                            var baseInfo = marker.baseInfo
                            $("#allianceBasePopup .city").html(getCountryFlagImg(baseInfo.countryCode) + "&nbsp;" + baseInfo.city)
                            $("#allianceBasePopup .airportName").text(baseInfo.airportName)
                            $("#allianceBasePopup .iata").html(baseInfo.airportCode)
                            $("#allianceBasePopup .airlineName").html(getAirlineLogoImg(baseInfo.airlineId) + "&nbsp;" + baseInfo.airlineName)
                            $("#allianceBasePopup .baseScale").html(baseInfo.scale)

                            var infoWindow = new google.maps.InfoWindow({ maxWidth : 1200});
                            var popup = $("#allianceBasePopup").clone()
                            popup.show()
                            infoWindow.setContent(popup[0])
                            //infoWindow.setPosition(event.latLng);
                            infoWindow.open(map, marker);
                            map.allianceBasePopup = infoWindow
                        })
                        marker.addListener('mouseout', function(event) {
                            closeAlliancePopups()
                        })
                    })


				switchMap();
				$("#worldMapCanvas").data("initCallback", function() { //if go back to world map, re-init the map
				        map.controls[google.maps.ControlPosition.TOP_CENTER].clear()
				        clearAllPaths()
                        updateAirportMarkers(activeAirline)
                        updateLinksInfo() //redraw all flight paths
                        closeAlliancePopups()
                })

				window.setTimeout(addExitButton , 1000); //delay otherwise it doesn't push to center
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    },
	    beforeSend: function() {
            $('body .loadingSpinner').show()
        },
        complete: function(){
            $('body .loadingSpinner').hide()
        }
	});
}

function addExitButton() {
    if (map.controls[google.maps.ControlPosition.TOP_CENTER].getLength() > 0) {
        map.controls[google.maps.ControlPosition.TOP_CENTER].clear()
    }
    map.controls[google.maps.ControlPosition.TOP_CENTER].push(createMapButton(map, 'Exit Alliance Flight Map', 'hideAllianceMap()', 'hideAllianceMapButton')[0]);
}

function drawAllianceLink(link) {
	var from = new google.maps.LatLng({lat: link.fromLatitude, lng: link.fromLongitude})
	var to = new google.maps.LatLng({lat: link.toLatitude, lng: link.toLongitude})
	//var pathKey = link.id
	
	var strokeColor = airlineColors[link.airlineId]
	if (!strokeColor) {
		strokeColor = "#DC83FC"
	}

    var maxOpacity = 0.7
    var minOpacity = 0.1
    var standardCapacity = 10000
    var strokeOpacity
	if (link.capacity.total < standardCapacity) {
        strokeOpacity = minOpacity + link.capacity.total / standardCapacity * (maxOpacity - minOpacity)
    } else {
        strokeOpacity = maxOpacity
    }
		
	var linkPath = new google.maps.Polyline({
			 geodesic: true,
		     strokeColor: strokeColor,
		     strokeOpacity: strokeOpacity,
		     strokeWeight: 2,
		     path: [from, to],
		     zIndex : 90,
		     link : link
		});
		
	var fromAirport = getAirportText(link.fromAirportCity, link.fromAirportCode)
	var toAirport = getAirportText(link.toAirportCity, link.toAirportCode)
	
	shadowPath = new google.maps.Polyline({
		 geodesic: true,
	     strokeColor: strokeColor,
	     strokeOpacity: 0.0001,
	     strokeWeight: 25,
	     path: [from, to],
	     zIndex : 100,
	     fromAirport : fromAirport,
	     fromCountry : link.fromCountryCode, 
	     toAirport : toAirport,
	     toCountry : link.toCountryCode,
	     capacity : link.capacity.total,
	     airlineName : link.airlineName,
	     airlineId : link.airlineId
	});
	
	linkPath.shadowPath = shadowPath
	

	shadowPath.addListener('mouseover', function(event) {
	    if (!map.allianceBasePopup) { //only do this if it is not hovered over base icon. This is a workaround as zIndex does not work - hovering over base icon triggers onmouseover event on the link below the icon
            $("#linkPopupFrom").html(getCountryFlagImg(this.fromCountry) + "&nbsp;" + this.fromAirport)
            $("#linkPopupTo").html(getCountryFlagImg(this.toCountry) + "&nbsp;" + this.toAirport)
            $("#linkPopupCapacity").html(this.capacity)
            $("#linkPopupAirline").html(getAirlineLogoImg(this.airlineId) + "&nbsp;" + this.airlineName)


            var infowindow = new google.maps.InfoWindow({
                 maxWidth : 1200});

            var popup = $("#linkPopup").clone()
            popup.show()
            infowindow.setContent(popup[0])

            infowindow.setPosition(event.latLng);
            infowindow.open(map);
            map.allianceLinkPopup = infowindow
        }
	})		
	shadowPath.addListener('mouseout', function(event) {
        closeAllianceLinkPopup()
	})
	
	linkPath.setMap(map)
	linkPath.shadowPath.setMap(map)
	polylines.push(linkPath)
	polylines.push(linkPath.shadowPath)

    var resultPath = { path : linkPath, shadow : shadowPath } //kinda need this so it has consistent data structure as the normal flight paths
    return resultPath
}

function showAllianceMemberDetails(allianceMember) {
    $("#allianceMemberModal").data("airlineId", allianceMember.airlineId)

    $("#allianceMemberModal .airlineName").html(getAirlineLogoImg(allianceMember.airlineId) + allianceMember.airlineName)
    $("#allianceMemberModal .allianceMemberStatus").text(allianceMember.allianceRole)
    updateAirlineBaseList(allianceMember.airlineId, $("#allianceMemberModal .baseList"))
    $("#allianceMemberModal").fadeIn(200)
}

function closeAlliancePopups() {
    if (map.allianceBasePopup) {
        map.allianceBasePopup.close()
        map.allianceBasePopup.setMap(null)
        map.allianceBasePopup = undefined
    }
    closeAllianceLinkPopup()
}

function closeAllianceLinkPopup() {
    if (map.allianceLinkPopup) {
        map.allianceLinkPopup.close()
        map.allianceLinkPopup.setMap(null)
        map.allianceLinkPopup = undefined
    }
}

function hideAllianceMap() {
    map.controls[google.maps.ControlPosition.TOP_CENTER].clear()
    clearAllPaths()
    updateAirportBaseMarkers([]) //revert base markers
    closeAlliancePopups()
    setActiveDiv($("#allianceCanvas"))
}