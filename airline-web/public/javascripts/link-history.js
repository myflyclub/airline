var historyFlightMarkers = []
var historyFlightMarkerAnimation = null

function showLinkHistoryView() {
    fromLinkCanvas = $('#linksCanvas').is(":visible")
    $('.exitPaxMap').data("fromLinkCanvas", fromLinkCanvas)

	if (!$('#worldMapCanvas').is(":visible")) {
		showWorldMap()
	}

	AirlineMap.clearAllPaths() //clear all flight paths

    //populate control panel
	$("#linkHistoryControlPanel .transitAirlineList .table-row").remove()

	$("#linkHistoryControlPanel .routeList").empty()
	$("#linkHistoryControlPanel").data("showForward", true)
	var link = loadedLinksById[selectedLink]
	var forwardLinkDescription = "<div style='display: flex; align-items: center;' class='clickable selected' onclick='toggleLinkHistoryDirection(true, $(this))'>" + getAirportText(link.fromAirportCity, link.fromAirportCode) + "<img src='/assets/images/icons/arrow.png'>" + getAirportText(link.toAirportCity, link.toAirportCode) + "</div>"
    var backwardLinkDescription = "<div style='display: flex; align-items: center;' class='clickable' onclick='toggleLinkHistoryDirection(false, $(this))'>" + getAirportText(link.toAirportCity, link.toAirportCode) + "<img src='/assets/images/icons/arrow.png'>" + getAirportText(link.fromAirportCity, link.fromAirportCode) + "</div>"

    $("#linkHistoryControlPanel .routeList").append(forwardLinkDescription)
    $("#linkHistoryControlPanel .routeList").append(backwardLinkDescription)

    $("#linkHistoryControlPanel").show()

    $('#linkHistoryControlPanel').data('cycleDelta', 0)
	loadLinkHistory(selectedLink)
}

function loadLinkHistory(linkId) {
    // Clear history paths (managed by map module state)
    AirlineMap.clearHistoryPaths()

	var linkInfo = loadedLinksById[linkId]
    var airlineNamesById = {}
    var cycleDelta = $('#linkHistoryControlPanel').data('cycleDelta')
    $("#linkHistoryControlPanel .transitAirlineList").empty()

    var url = "/airlines/" + activeAirline.id + "/related-link-consumption/" + linkId + "?cycleDelta=" + cycleDelta +
    "&economy=" + $("#linkHistoryControlPanel .showEconomy").is(":checked") +
    "&business=" + $("#linkHistoryControlPanel .showBusiness").is(":checked") +
    "&first=" + $("#linkHistoryControlPanel .showFirst").is(":checked")

    $.ajax({
        type: 'GET',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(linkHistory) {
            var forwardTransitPaxByAirlineId = {}
            var backwardTransitPaxByAirlineId = {}

            if (!jQuery.isEmptyObject(linkHistory)) {
                $.each(linkHistory.relatedLinks, function(step, relatedLinksOnStep) {
                    $.each(relatedLinksOnStep, function(key, relatedLink) {
                        AirlineMap.drawLinkHistoryPath(relatedLink, false, linkId, step)
                        if (linkInfo.fromAirportId != relatedLink.fromAirportId || linkInfo.toAirportId != relatedLink.toAirportId || linkInfo.airlineId != linkInfo.airlineId) { //transit should not count the selected link
                            airlineNamesById[relatedLink.airlineId] = relatedLink.airlineName
                            if (!forwardTransitPaxByAirlineId[relatedLink.airlineId]) {
                                forwardTransitPaxByAirlineId[relatedLink.airlineId] = relatedLink.passenger
                            } else {
                                forwardTransitPaxByAirlineId[relatedLink.airlineId] = forwardTransitPaxByAirlineId[relatedLink.airlineId] + relatedLink.passenger
                            }
                        }
                    })
                })
                $.each(linkHistory.invertedRelatedLinks, function(step, relatedLinksOnStep) {
                    $.each(relatedLinksOnStep, function(key, relatedLink) {
                        AirlineMap.drawLinkHistoryPath(relatedLink, true, linkId, step)
                        if (linkInfo.fromAirportId != relatedLink.toAirportId || linkInfo.toAirportId != relatedLink.fromAirportId || linkInfo.airlineId != linkInfo.airlineId) { //transit should not count the selected link
                            airlineNamesById[relatedLink.airlineId] = relatedLink.airlineName
                            if (!backwardTransitPaxByAirlineId[relatedLink.airlineId]) {
                                backwardTransitPaxByAirlineId[relatedLink.airlineId] = relatedLink.passenger
                            } else {
                                backwardTransitPaxByAirlineId[relatedLink.airlineId] = backwardTransitPaxByAirlineId[relatedLink.airlineId] + relatedLink.passenger
                            }
                        }
                    })
                })
                var forwardItems = Object.keys(forwardTransitPaxByAirlineId).map(function(key) {
                  return [key, forwardTransitPaxByAirlineId[key]];
                });
                var backwardItems = Object.keys(backwardTransitPaxByAirlineId).map(function(key) {
                  return [key, backwardTransitPaxByAirlineId[key]];
                });
                //now sort them
                forwardItems.sort(function(a, b) {
                    return b[1] - a[1]
                })
                backwardItems.sort(function(a, b) {
                    return b[1] - a[1]
                })
                //populate the top 5 transit airline table
                forwardItems = $(forwardItems).slice(0, 5)
                backwardItems = $(backwardItems).slice(0, 5)
                $.each(forwardItems, function(index, entry) { //entry : airlineId, pax counts
                    var tableRow = $("<div class='table-row' style='display: none;'></div>")
                    tableRow.addClass("forward")
                    var airlineId = entry[0]
                    tableRow.append("<div class='cell' style='width: 70%'>" + getAirlineSpan(airlineId, airlineNamesById[airlineId]) + "</div>")
                    tableRow.append("<div class='cell' style='width: 30%'>" + entry[1] + "</div>")

                    $("#linkHistoryControlPanel .transitAirlineList").append(tableRow)
                })
                $.each(backwardItems, function(index, entry) { //entry : airlineId, pax counts
                    var tableRow = $("<div class='table-row' style='display: none;'></div>")
                    tableRow.addClass("backward")
                    var airlineId = entry[0]
                    tableRow.append("<div class='cell' style='width: 70%'>" + getAirlineSpan(airlineId, airlineNamesById[airlineId]) + "</div>")
                    tableRow.append("<div class='cell' style='width: 30%'>" + entry[1] + "</div>")

                    $("#linkHistoryControlPanel .transitAirlineList").append(tableRow)
                })

            }
            showLinkHistory(fromLinkCanvas)
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


function toggleLinkHistoryDirection(showForward, routeDiv) {
    routeDiv.siblings().removeClass("selected")
    routeDiv.addClass("selected")

    $("#linkHistoryControlPanel").data("showForward", showForward)
    showLinkHistory()
}

function hideLinkHistoryView() {
    // Clear history paths via map module
    AirlineMap.clearHistoryPaths()

	AirlineMap.clearHistoryFlightMarkers()
	updateLinksInfo() //redraw all flight paths

	$("#linkHistoryControlPanel").hide()

	if ($('.exitPaxMap').data("fromLinkCanvas")) {
	    showLinksCanvas()
	}
}


function clearHistoryFlightMarkers() {
    $.each(historyFlightMarkers, function(index, markersOnAStep) {
        $.each(markersOnAStep, function(index, marker) {
            if (marker.remove) {
                marker.remove() // MapLibre marker
            }
        })
    })
    historyFlightMarkers = []

    if (historyFlightMarkerAnimation) {
        window.clearInterval(historyFlightMarkerAnimation)
        historyFlightMarkerAnimation = null
    }
}

function showLinkHistory() {
    var showAlliance = $("#linkHistoryControlPanel .showAlliance").is(":checked")
    var showOther = $("#linkHistoryControlPanel .showOther").is(":checked")
    var showForward = $("#linkHistoryControlPanel").data("showForward")
    var showAnimation = $("#linkHistoryControlPanel .showAnimation").is(":checked")

    var cycleDelta = $("#linkHistoryControlPanel").data('cycleDelta')
    $("#linkHistoryControlPanel .cycleDeltaText").text(cycleDelta * -1 + 1)
    var disablePrev = false
    var disableNext= false
    if (cycleDelta <= -29) {
        disablePrev = true
    } else if (cycleDelta >= 0) {
        disableNext = true
    }

    $("#linkHistoryControlPanel img.prev").prop("onclick", null).off("click");
    if (disablePrev) {
        $('#linkHistoryControlPanel img.prev').attr("src", "/assets/images/icons/arrow-180-grey.png")
        $('#linkHistoryControlPanel img.prev').removeClass('clickable')
    } else {
        $('#linkHistoryControlPanel img.prev').attr("src", "/assets/images/icons/arrow-180.png")
        $('#linkHistoryControlPanel img.prev').addClass('clickable')
        $("#linkHistoryControlPanel img.prev").click(function() {
            $("#linkHistoryControlPanel").data('cycleDelta', $("#linkHistoryControlPanel").data('cycleDelta') - 1)
            loadLinkHistory(selectedLink)
        })
    }

    $("#linkHistoryControlPanel img.next").prop("onclick", null).off("click");
    if (disableNext) {
        $('#linkHistoryControlPanel img.next').attr("src", "/assets/images/icons/arrow-grey.png")
        $('#linkHistoryControlPanel img.next').removeClass('clickable')
        $("#linkHistoryControlPanel img.next").prop("onclick", null).off("click");
    } else {
        $('#linkHistoryControlPanel img.next').attr("src", "/assets/images/icons/arrow.png")
        $('#linkHistoryControlPanel img.next').addClass('clickable')
        $("#linkHistoryControlPanel img.next").click(function() {
            $("#linkHistoryControlPanel").data('cycleDelta', $("#linkHistoryControlPanel").data('cycleDelta') + 1)
            loadLinkHistory(selectedLink)
        })
    }

    $("#linkHistoryControlPanel .transitAirlineList .table-row").hide()
    if (showForward) {
        $("#linkHistoryControlPanel .transitAirlineList .table-row.forward").show()
    } else {
        $("#linkHistoryControlPanel .transitAirlineList .table-row.backward").show()
    }

    clearHistoryFlightMarkers()

    // Update history paths visibility based on filter settings
    $.each(historyPaths, function(key, historyPath) {
        var shouldShow = ((showForward && !historyPath.inverted) || (!showForward && historyPath.inverted)) &&
            (historyPath.thisAirlinePassengers > 0 ||
             (showAlliance && historyPath.thisAlliancePassengers > 0) ||
             (showOther && historyPath.otherAirlinePassengers > 0))

        if (shouldShow) {
            var totalPassengers = historyPath.thisAirlinePassengers + historyPath.thisAlliancePassengers + historyPath.otherAirlinePassengers
            if (totalPassengers < 100 && !historyPath.watched) {
                historyPath.opacity = 0.2 + totalPassengers / 100 * (0.8 - 0.2)
            } else {
                historyPath.opacity = 0.8
            }

            // Set color based on passenger type
            if (historyPath.thisAirlinePassengers > 0) {
                historyPath.color = "#DC83FC"
            } else if (showAlliance && historyPath.thisAlliancePassengers > 0) {
                historyPath.color = "#E28413"
            } else {
                historyPath.color = "#888888"
            }

            historyPath.visible = true
        } else {
            historyPath.visible = false
        }
    })

    // Refresh history routes display via the map module
    refreshHistoryRoutesDisplay()
}

var historyPopupHandlersInitialized = false

function initHistoryPopupHandlers() {
    if (historyPopupHandlersInitialized || !map) return

    // Ensure the history routes layers exist
    AirlineMap.ensureHistoryRoutesLayers()

    const clickLayerId = AirlineMap.getHistoryRoutesClickLayerId()
    if (!clickLayerId) return

    // Add popup on hover
    map.on('mouseenter', clickLayerId, function(e) {
        map.getCanvas().style.cursor = 'pointer'

        if (e.features.length > 0) {
            const props = e.features[0].properties

            $("#linkHistoryPopupFrom").html(getCountryFlagImg(props.fromCountryCode) + getAirportText(props.fromAirportCity, props.fromAirportCode))
            $("#linkHistoryPopupTo").html(getCountryFlagImg(props.toCountryCode) + getAirportText(props.toAirportCity, props.toAirportCode))
            $("#linkHistoryThisAirlinePassengers").text(props.thisAirlinePassengers)

            var showAlliance = $("#linkHistoryControlPanel .showAlliance").is(":checked")
            var showOther = $("#linkHistoryControlPanel .showOther").is(":checked")

            if (showAlliance) {
                $("#linkHistoryThisAlliancePassengers").text(props.thisAlliancePassengers)
                $("#linkHistoryThisAlliancePassengers").closest(".table-row").show()
            } else {
                $("#linkHistoryThisAlliancePassengers").closest(".table-row").hide()
            }
            if (showOther) {
                $("#linkHistoryOtherAirlinePassengers").text(props.otherAirlinePassengers)
                $("#linkHistoryOtherAirlinePassengers").closest(".table-row").show()
            } else {
                $("#linkHistoryOtherAirlinePassengers").closest(".table-row").hide()
            }

            var popup = $("#linkHistoryPopup").clone()
            popup.show()

            new maplibregl.Popup({ closeButton: false, closeOnClick: false })
                .setLngLat(e.lngLat)
                .setDOMContent(popup[0])
                .addTo(map)
        }
    })

    map.on('mouseleave', clickLayerId, function() {
        map.getCanvas().style.cursor = ''
        // Remove popup
        const popups = document.getElementsByClassName('maplibregl-popup')
        if (popups.length) {
            popups[0].remove()
        }
    })

    historyPopupHandlersInitialized = true
}

function refreshHistoryRoutesDisplay() {
    // Initialize popup handlers once
    initHistoryPopupHandlers()

    // Delegate rendering to the map module
    AirlineMap.showLinkHistory()
}
