// State management for History Search
const _searchEtagStore = {}
const _searchRouteData = {}
const _researchData = {}
let historySearchState = {};
const resetHistorySearchState = () => {
    historySearchState = {
        from: { type: null, id: null, text: '' },
        to: { type: null, id: null, text: '' },
        airline: { id: null, text: '' },
        alliance: { id: null, text: '' },
        capacity: null,
        capacityDelta: null
    };
};
resetHistorySearchState(); // Initial state reset

function showSearchCanvas(historyAirline) {
    var titlesContainer = $("#searchCanvas div.titlesContainer")
    positionTitles(titlesContainer)

    // Ensure worldMapCanvas is the active top-level canvas
    setActiveDiv($('#worldMapCanvas'))
    // Show search overlay, hide sidePanel
    showMapOverlay($('#searchCanvas'))
	$("#searchCanvas").css("display", "flex")

	$("#routeSearchResult").empty()
	if (isMobileDevice()) {
	   $('#searchCanvas .banner').hide()
	} else {
	   showBanner()
    }
	$("#historySearchResult .table-row").empty()
	$('#searchCanvas .searchContainer input').val('')
	$('#searchCanvas .searchContainer input').removeData("selectedId")
    // resetHistorySearchState(); // Reset state object every time search is opened

    updateNavigationArrows(titlesContainer)

    initializeHistorySearch()

    if (historyAirline && (typeof historyAirline === 'object' || historyAirline > 0)) {
        refreshSearchDiv('history');

        // Update state and UI for the provided airline
        const airlineId = typeof historyAirline === 'object' ? historyAirline.id : historyAirline;
        const airlineText = typeof historyAirline === 'object' ? getAirlineTextEntry(historyAirline, false) : historyAirline;
        historySearchState.airline = { id: airlineId, text: airlineText };
        $('#searchCanvas div.historySearch input.airline').data('selectedId', airlineId).val(airlineText);

        searchLinkHistory()
    } else if (historyAirline === 0) {
        refreshSearchDiv('research');
    } else {
        refreshSearchDiv('route');
    }
}

function showBanner() {
    return; //is disabled

    $.ajax({
            type: 'GET',
            url: "/banner",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                if (result.bannerUrl) {
                    $('#searchCanvas .banner img').attr('src', result.bannerUrl + "=w" + $('#searchCanvas .banner').width())
                    $('#searchCanvas .banner').show()
                } else {
                    $('#searchCanvas .banner').hide()
                }
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

function initializeHistorySearch() {
    const searchCanvas = $('#searchCanvas');
    const titlesContainer = searchCanvas.find('div.titlesContainer');

    // Logic to sync airport info between tabs
    titlesContainer.off('mousedown.syncAirport click.syncAirport') // Clear previous handlers
        .on('mousedown.syncAirport', '.titleSelection', function() {
            const $clickedTitle = $(this);
            if (!$clickedTitle.hasClass('selected')) {
                const fromSearchType = $clickedTitle.siblings('.selected').data('searchType');
                titlesContainer.data('fromSearchType', fromSearchType);
            } else {
                titlesContainer.removeData('fromSearchType');
            }
        })
        .on('click.syncAirport', '.titleSelection', function() {
            const $clickedTitle = $(this);
            const fromSearchType = titlesContainer.data('fromSearchType');
            const toSearchType = $clickedTitle.data('searchType');

            if (fromSearchType && fromSearchType !== toSearchType) {
                const getInputs = (type) => {
                    if (!type) return null;
                    const containerSelector = (type === 'route' || type === 'research') ? '.searchFieldContainer' : '.historySearch';
                    return {
                        from: searchCanvas.find(`${containerSelector} .fromAirport`),
                        to: searchCanvas.find(`${containerSelector} .toAirport`)
                    };
                };

                const fromInputs = getInputs(fromSearchType);
                const toInputs = getInputs(toSearchType);

                if (fromInputs && toInputs) {
                    const fromId = fromInputs.from.data('selectedId');
                    if (fromId) {
                        toInputs.from.data('selectedId', fromId).val(fromInputs.from.val());
                    }
                    const toId = fromInputs.to.data('selectedId');
                    if (toId) {
                        toInputs.to.data('selectedId', toId).val(fromInputs.to.val());
                    }
                }
            }
            titlesContainer.removeData('fromSearchType');
        });

    // Handlers for capacity/delta inputs to update state
    searchCanvas.find('.historySearch input.capacity').on('input', function() {
        const value = $(this).val();
        historySearchState.capacity = value && !isNaN(value) ? parseInt(value) : null;
    });

    searchCanvas.find('.historySearch input.capacityDelta').on('input', function() {
        const value = $(this).val();
        historySearchState.capacityDelta = value && !isNaN(value) ? parseInt(value) : null;
    });

    // Sort function for result table
    searchCanvas.find('.historySearch .sortHeader .cell.clickable').off('click.toggleSort').on('click.toggleSort', function() {
        toggleTableSortOrder($(this), updateLinkHistoryTable);
    });
}

function refreshSearchDiv(searchTitleType) {
    if (searchTitleType === 'route') {
        $('#searchCanvas div.routeSearch').show();
        $('#searchCanvas div.routeSearch').siblings('.searchContainer').hide();
        $('#searchCanvas .searchFieldContainer').show();
    } else if (searchTitleType === 'history') {
        $('#searchCanvas div.historySearch').show();
        $('#searchCanvas div.historySearch').siblings('.searchContainer').hide();
        $('#searchCanvas .searchFieldContainer').hide();
    } else if (searchTitleType === 'research') {
        $('#searchCanvas div.research').show();
        $('#researchSearchResult').hide();
        $('#searchCanvas div.research').siblings('.searchContainer').hide();
        $('#searchCanvas .searchFieldContainer').show();
    }
}

function searchAction(fromAirportId, toAirportId) {
    var searchTitleType = $('#searchCanvas div.titlesContainer div.selected').data('searchType')
    if (searchTitleType === 'route') {
        searchRoute(fromAirportId, toAirportId)
    } else if (searchTitleType === 'research') {
        researchFlight(fromAirportId, toAirportId)
    }
}

function showResearchPreloaded(fromAirportId, toAirportId) {
    AirlineMap.removeTempPath();
    document.querySelector('#searchCanvas div.titlesContainer .selected').classList.remove('selected')
    document.querySelector('#searchCanvas div.titlesContainer [data-search-type="research"]').classList.add('selected')
    const fromId = fromAirportId || ($('#planLinkFromAirportId').val() > 0 ? $('#planLinkFromAirportId').val() : activeAirline.headquarterAirport.airportId);
    const toId = toAirportId || $('#airportPopupId').val();
    const from = getAirportByAttribute(fromId);
    const to = getAirportByAttribute(toId);
    showSearchCanvas(0);
    researchFlight(fromId, toId);

    //create a temp path
    var tempLink = {fromLatitude: from.latitude, fromLongitude: from.longitude, toLatitude: to.latitude, toLongitude: to.longitude}
    //set the temp path
    tempPath = AirlineMap.drawFlightPath(tempLink, '#3b94e6')
    AirlineMap.highlightPath(tempPath.path, false)

    document.querySelector('#searchCanvas .searchInput .fromAirport').value = from.iata;
    document.querySelector('#searchCanvas .searchInput .toAirport').value = to.iata;
    document.querySelector('#searchCanvas .searchInput .fromAirport').setAttribute('data-selectedid', fromId);
    document.querySelector('#searchCanvas .searchInput .toAirport').setAttribute('data-selectedid', toId);
}


async function searchRoute(fromAirportId, toAirportId) {
    if (!fromAirportId || !toAirportId) {
        return;
    }
    const url = `/search-route/${fromAirportId}/${toAirportId}`;
    const key = `route-${fromAirportId}-${toAirportId}`;
    const headers = {};
    if (_searchEtagStore[key]) headers['If-None-Match'] = _searchEtagStore[key];
    $('body .loadingSpinner').show();
    try {
        const res = await fetch(url, { headers });
        let searchResult;
        if (res.status === 304 && _searchRouteData[key]) {
            searchResult = _searchRouteData[key];
        } else {
            if (!res.ok) { console.log('AJAX error: ' + res.status); return; }
            const etag = res.headers.get('ETag');
            if (etag) _searchEtagStore[key] = etag;
            searchResult = await res.json();
            _searchRouteData[key] = searchResult;
        }
            const resultContainer = $("#routeSearchResult");
            resultContainer.empty();
            $("#searchCanvas .banner").hide();

            if (searchResult.length === 0) {
                resultContainer.append("<div class='ticketTitle p-4'>Sorry, no flights available.</div>");
                return;
            }

            $.each(searchResult, function(index, entry) {
                const flightLinks = entry.route.filter(link => link.transportType === 'FLIGHT');
                if (flightLinks.length === 0) {
                    return true; // continue to next entry in $.each
                }

                const startLink = flightLinks[0];
                const endLink = flightLinks[flightLinks.length - 1];
                const startDay = Math.floor(startLink.departure / (24 * 60));
                const totalCost = entry.route.reduce((acc, link) => acc + (link.price || 0), 0);

                let stopDescription;
                if (flightLinks.length === 1) {
                    stopDescription = "Direct Flight";
                } else if (flightLinks.length === 2) {
                    stopDescription = "1 Stop";
                } else {
                    stopDescription = `${flightLinks.length - 1} Stops`;
                }

                let remarksLabel = '';
                if (entry.remarks) {
                    entry.remarks.forEach(remark => {
                        if (remark === 'BEST_SELLER' || remark === 'BEST_DEAL') {
                            remarksLabel += `<div class='remark bg-darkGreen inline-block'>${remark.replace('_', ' ')}</div>`;
                        }
                    });
                }
                const priceColor = remarksLabel ? 'darkgreen' : 'inherit';


                const linksHtml = flightLinks.map((link, index) => {
                    const previousLink = index > 0 ? flightLinks[index - 1] : null;
                    const preGenericTransit = entry.route.find(r => r.toAirportIata === link.fromAirportIata && r.transportType === 'GENERIC_TRANSIT');
                    const postGenericTransit = entry.route.find(r => r.fromAirportIata === link.toAirportIata && r.transportType === 'GENERIC_TRANSIT');

                    let remarks = [];
                    if (preGenericTransit) remarks.push(`Depart from ${link.fromAirportIata}`);
                    if (postGenericTransit) remarks.push(`Arrive at ${link.toAirportIata}`);
                    if (previousLink) remarks.push(`+${getDurationText(link.departure - previousLink.arrival)} layover at ${link.fromAirportIata}`);

                    const linkDurationText = getDurationText(link.arrival - link.departure);
                    const remarksText = remarks.length > 0 ? `(${remarks.join(", ")})` : '';

                    return `
                        <div class='mb-2'>
                            <div class='flex-row py-2'>
                                <div class='w-half flex-align-center'>
                                    ${getAirlineLogoImg(link.airlineId)}<span class='summary ml-1'>${link.airlineName}</span>
                                </div>
                                <div class='w-half'>${linkDurationText} ${remarksText.length > 0 ? '<span class="remark">' + remarksText + '</span>' : ''}</div>
                            </div>
                            <div style='display: none;' class='linkDetails pb-2 flex-row'>
                                <div class='w-half'>
                                    <div class='summary inline-block' style='width: 75px;'>${link.flightCode}</div>
                                    <span class='summary'>${getAirlineTimeSlotText(link.departure, startDay)} - ${getAirlineTimeSlotText(link.arrival, startDay)}</span>
                                    <div class='font-bold'>$${link.price}</div>
                                    ${getLinkFeatureIconsDiv(link.features).prop('outerHTML')}
                                    ${getLinkReviewDiv(link.computedQuality, link.linkClass).prop('outerHTML')}
                                </div>
                                <div class='w-half'>
                                    <div class='flex-align-center'>
                                        ${getAirportText(link.fromAirportCity, link.fromAirportIata)}
                                        <img src='/assets/images/icons/arrow.png' class='mx-2'>
                                        ${getAirportText(link.toAirportCity, link.toAirportIata)}
                                    </div>
                                    <div><i class='opacity-80'>${link.airplaneModelName ? link.airplaneModelName : "-"}</i></div>
                                    ${link.operatorAirlineId ? `<div class='text-xs'>Operated by ${getAirlineLogoImg(link.operatorAirlineId)}${link.operatorAirlineName}</div>` : ''}
                                    ${preGenericTransit ? `<div>Depart from ${preGenericTransit.toAirportText}</div>` : ''}
                                    ${postGenericTransit ? `<div>Arrive at ${postGenericTransit.fromAirportText}</div>` : ''}
                                </div>
                            </div>
                        </div>
                    `;
                }).join('');

                const itineraryHtml = `
                    <div class='section itinerary flex-row items-center mb-4' onclick='toggleSearchLinkDetails($(this))'>
                        <div class='flex-grow pr-4'>
                            <div class='summary flex-row items-center pb-2 mb-2' style='border-bottom: 1px solid rgba(0,0,0,0.05);'>
                                <div class='w-half'>${getAirlineTimeSlotText(startLink.departure, startDay)} - ${getAirlineTimeSlotText(endLink.arrival, startDay)}</div>
                                <div class='w-half'>${getDurationText(endLink.arrival - startLink.departure)}</div>
                            </div>
                            ${linksHtml}
                        </div>
                        <div class='flex-col items-end' style='width: 100px; flex-shrink: 0;'>
                            <div class='price text-xl font-bold' style='color: ${priceColor};'>$ ${totalCost}</div>
                            ${remarksLabel}
                            <div class='mt-1 opacity-80'>${stopDescription}</div>
                        </div>
                    </div>
                `;
                resultContainer.append(itineraryHtml);
            });
    } catch (e) {
        console.error('Failed to search route:', e);
    } finally {
        $('body .loadingSpinner').hide();
    }
}


function searchLinkHistory() {
    const url = "/search-link-history";
    const searchData = {};

    if (historySearchState.from.id) {
        const key = historySearchState.from.type === 'airport' ? 'fromAirportId' : 'fromCountryCode';
        searchData[key] = historySearchState.from.id;
    }
    if (historySearchState.to.id) {
        const key = historySearchState.to.type === 'airport' ? 'toAirportId' : 'toCountryCode';
        searchData[key] = historySearchState.to.id;
    }
    if (historySearchState.airline.id) {
        searchData["airlineId"] = historySearchState.airline.id;
    }
    if (historySearchState.alliance.id) {
        searchData["allianceId"] = historySearchState.alliance.id;
    }
    if (historySearchState.capacity) {
        searchData["capacity"] = historySearchState.capacity;
    }
    if (historySearchState.capacityDelta) {
        searchData["capacityDelta"] = historySearchState.capacityDelta;
    }


    $.ajax({
        type: 'POST',
        url: url,
        contentType: 'application/json; charset=utf-8',
        data: JSON.stringify(searchData),
        dataType: 'json',
        success: function(searchResult) {
            $("#linkHistorySearchResult").empty()
            $("#searchCanvas .linkHistorySearchTable").data("entries", searchResult)
            updateLinkHistoryTable()
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



function updateLinkHistoryTable(sortProperty, sortOrder) {
	var linkHistoryTable = $("#searchCanvas .linkHistorySearchTable")
	linkHistoryTable.children("div.table-row").remove()

    var loadedData = linkHistoryTable.data('entries')
	//sort the list
	//loadedLinks.sort(sortByProperty(sortProperty, sortOrder == "ascending"))
	loadedData = sortPreserveOrder(loadedData, sortProperty, sortOrder == "ascending")


	$.each(loadedData, function(index, link) {
		var row = $("<div class='table-row'></div>")
		row.append("<div class='cell'>" + getCycleDeltaText(link.cycleDelta) + "</div>")
        row.append("<div class='cell'>" + getAirlineLogoImg(link.airlineId) + link.airlineName + "</div>")
		row.append("<div class='cell'>" + getCountryFlagImg(link.fromCountryCode) + getAirportText(link.fromAirportCity, link.fromAirportIata) + "</div>")
		row.append("<div class='cell'>" + getCountryFlagImg(link.toCountryCode) + getAirportText(link.toAirportCity, link.toAirportIata) + "</div>")
		row.append("<div class='cell'>" + link.airplaneModelName + "</div>")
		$("<div class='cell' align='right'></div>").appendTo(row).append(getCapacitySpan(link.capacity, link.frequency))
		$("<div class='cell' align='right'></div>").appendTo(row).append(getCapacityDeltaSpan(link.capacityDelta))
		$("<div class='cell'></div>").appendTo(row).text(toLinkClassValueString(link.price, '$'))
		$("<div class='cell'></div>").appendTo(row).append(getPriceDeltaSpan(link.priceDelta))


		linkHistoryTable.append(row)
	});

	if (loadedData.length == 0) {
		var row = $("<div class='table-row'><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div></div>")
		linkHistoryTable.append(row)
	}
}

function getCycleDeltaText(cycleDelta) {
    if (cycleDelta >= 0) {
        return "This wk"
    } else if (cycleDelta == 1) {
        return "Last wk"
    } else {
        return (cycleDelta * -1) + " wk ago"
    }
}

function getCapacitySpan(capacity, frequency) {
    var span = $("<span></span>")
    $('<span>' + capacity.total + '</span>').appendTo(span).prop('title', toLinkClassValueString(capacity))
    span.append('<span>(' + frequency + ')</span>')
    return span
}

function getCapacityDeltaSpan(capacityDelta) {
    var span = $("<span></span>")
    if (!capacityDelta.economy && !capacityDelta.business && !capacityDelta.first) {
        span.text('-')
    } else {
        span.append(getDeltaSpan(capacityDelta.total))
        span.prop('title', toLinkClassValueString(capacityDelta))
    }
    return span
}

function getDeltaSpan(delta) {
    var span = $('<span></span>')
    var displayValue
    if (delta < 0) {
        span.append('<img src="/assets/images/icons/12px/arrow-270-red.png">')
        displayValue = delta * -1
    } else {
        span.append('<img src="/assets/images/icons/12px/arrow-090.png">')
        displayValue = delta
    }
    span.append('<span>' + displayValue + '</span>')
    return span
}

function getPriceDeltaSpan(priceDelta) {
    var span = $("<span></span>")
    if (!priceDelta.economy && !priceDelta.business && !priceDelta.first) {
        span.text("-")
        return span
    }

    if (priceDelta.economy) {
        span.append(getDeltaSpan(priceDelta.economy))
    } else {
        span.append('<span>-</span>')
    }
    span.append("/")

    if (priceDelta.business) {
        span.append(getDeltaSpan(priceDelta.business))
    } else {
        span.append('<span>-</span>')
    }
    span.append("/")

    if (priceDelta.first) {
        span.append(getDeltaSpan(priceDelta.first))
    } else {
        span.append('<span>-</span>')
    }

    return span
}

function toggleTableSortOrder(sortHeader, updateTableFunction) {
	if (sortHeader.data("sort-order") == "ascending") {
		sortHeader.data("sort-order", "descending")
	} else {
		sortHeader.data("sort-order", "ascending")
	}

	sortHeader.siblings().removeClass("selected")
	sortHeader.addClass("selected")

	updateTableFunction(sortHeader.data("sort-property"), sortHeader.data("sort-order"))
}


var linkFeatureIconsLookup = {
    "WIFI" : { "description" : "WIFI", "icon" : "/assets/images/icons/wi-fi-zone.png"},
    "BEVERAGE_SERVICE" : { "description" : "Beverage and snack services", "icon" : "/assets/images/icons/cup.png"},
    "HOT_MEAL_SERVICE" : { "description" : "Hot meal services", "icon" : "/assets/images/icons/plate-cutlery.png"},
    "PREMIUM_DRINK_SERVICE" : { "description" : "Premium drink services", "icon" : "/assets/images/icons/glass.png"},
    "IFE" : { "description" : "In-flight entertainment", "icon" : "/assets/images/icons/media-player-phone-horizontal.png"},
    "GAME" : { "description" : "Video game system", "icon" : "/assets/images/icons/controller.png"},
    "POSH" : { "description" : "Luxurious", "icon" : "/assets/images/icons/diamond.png"},
    "POWER_OUTLET" : { "description" : "Power outlet", "icon" : "/assets/images/icons/plug.png"}
}

function toggleSearchLinkDetails(containerDiv) {
    if (containerDiv.find(".linkDetails:visible").length > 0) {
        containerDiv.find(".linkDetails").hide()
    } else {
        containerDiv.find(".linkDetails").show()
    }
}

function getLinkFeatureIconsDiv(features) {
    var featureIconsDiv = $("<div></div>")
    $.each(features, function(index, feature) {
        var featureInfo = linkFeatureIconsLookup[feature]
        var icon = $("<img src='" + featureInfo.icon + "' class='tooltip-attr' data-tooltip='" + featureInfo.description + "' style='margin: 2px;'>")
         featureIconsDiv.append(icon)
    })

    return featureIconsDiv
}

function getLinkReviewDiv(quality, classText) {

    var color
    var text
    if (quality >= 80) {
        text = "Excellent"
        color = "darkgreen"
    } else if (quality >= 70) {
        text = "Very good"
        color = "darkgreen"
    } else if (quality >= 60) {
        text = "Good"
        color = "darkgreen"
    } else if (quality >= 50) {
        text = "Average"
        color = "gold"
    } else if (quality >= 40) {
        text = "Poor"
        color = "darkorange"
    } else if (quality >= 30) {
        text = "Terrible"
        color = "crimson"
    } else {
        text = "Abysmal"
        color = "crimson"
    }
    var text = text + " (" + (quality / 10) + "/10)"
    return $("<div style='color: "  + color + "'>" + text + " " + classText + " seat</div>")
}

function getAirlineTimeSlotText(minutes, startDay) {
    var dayOfWeek = Math.floor(minutes / (24 * 60))
    var minuteOfHour = minutes % 60
    var hourOfDay = Math.floor(minutes % (24 * 60) / 60)
    var hourText = hourOfDay < 10 ? "0" + hourOfDay : hourOfDay
    var minuteText = minuteOfHour < 10 ? "0" + minuteOfHour : minuteOfHour
    if (startDay < dayOfWeek) {
        return hourText + ":" + minuteText + "(+" + (dayOfWeek - startDay) + ")"
    } else {
        return hourText + ":" + minuteText
    }
}


function getAirportTextEntry(entry) {
    var text = "";
    if (entry.airportCity) {
        text += entry.airportCity + ", " + loadedCountriesByCode[entry.countryCode].name
    }

    if (entry.airportIata) {
        text += " (" + entry.airportIata + ")"
    }

    if (entry.airportName) {
        text += " " + entry.airportName
    }

    return text
}

function getAirportShortText(entry) {
    var text = "";
    if (entry.airportCity) {
        text += entry.airportCity
    }

    if (entry.airportIata) {
        text += " (" + entry.airportIata + ")"
    }
    return text
}

function highlightText(text, phrase) {
    var index = text.toLowerCase().indexOf(phrase.toLowerCase());
    if (index >= 0) {
        var prefix = text.substring(0, index)
        var highlight = "<b>" + text.substring(index, index + phrase.length) + "</b>"
        var suffix = text.substring(index + phrase.length)
        return prefix + highlight + suffix;
    }
    return text;
}

function resetSearchInput(button) {
    var disablingInputs = button.closest('.searchContainer').find('input')
    disablingInputs.val('')
    disablingInputs.removeData("selectedId")
    resetHistorySearchState();
}

function searchButtonKeyPress(event, button) {
    if (event.keyCode == 13) { //enter
        button.click()
    }
}


function searchKeyDown(event, input) {
    if (event.keyCode == 9) { //tab, has to do it here otherwise input field would lose focus
        confirmSelection(input)
    }
}

function searchChange(input) {
    search(event, input);
    input.removeData("selectedId");

    // Clear from state object
    const searchType = input.closest('div.searchInput').data('searchType');
    const searchGroup = input.data('searchGroup');

    if (searchGroup === 'fromDestination' || searchGroup === 'toDestination') {
        const targetGroup = searchGroup === 'fromDestination' ? 'from' : 'to';
        if (historySearchState[targetGroup].type === searchType) {
            historySearchState[targetGroup] = { type: null, id: null, text: '' };
        }
    } else if (searchGroup === 'entity') {
        if (historySearchState[searchType]) {
            historySearchState[searchType] = { id: null, text: '' };
        }
    }
}

function searchKeyUp(event, input) {
    var resultContainer = input.closest('div.searchInput').siblings('div.searchResult')
    if (event.key === 'ArrowUp' || event.keyCode == 38) {
        navigateSearchResults(-1, resultContainer, 'div.searchResultEntry')
    } else if (event.key === 'ArrowDown' || event.keyCode == 40) {
        navigateSearchResults(1, resultContainer, 'div.searchResultEntry')
    } else if (event.key === 'Enter' || event.keyCode == 13) {
        confirmSelection(input)
    }
}

function searchFocusOut(input) {
    if (!input.data("selectedId")) { //have not select anything, revert to empty
        input.val("")
    }

    var resultContainer = input.closest('div.searchInput').siblings('div.searchResult')
    resultContainer.hide()
}

function confirmSelection(input) {
    const $input = input.closest('div.searchInput');
    const resultContainer = $input.siblings('div.searchResult');
    const searchType = $input.data('searchType');
    const selected = resultContainer.find('div.selected').data(searchType);

    if (selected) {
        var displayVal;
        var selectedId;
        if (searchType === "airport") {
            displayVal = getAirportShortText(selected);
            selectedId = selected.airportId;
        } else if (searchType === "country") {
            displayVal = getCountryTextEntry(selected);
            selectedId = selected.countryCode;
        } else if (searchType === "airline") {
            displayVal = getAirlineTextEntry(selected, false);
            selectedId = selected.airlineId;
        } else if (searchType === "alliance") {
            displayVal = getAllianceTextEntry(selected);
            selectedId = selected.allianceId;
        }

        input.val(displayVal);
        input.attr("data-selectedId", selectedId);
        input.data("selectedId", selectedId); // Keep for UI purposes
        input.trigger('confirmSelection');

        // Update state object
        const searchGroup = input.data('searchGroup'); // fromDestination, toDestination, entity
        if (searchGroup === 'fromDestination' || searchGroup === 'toDestination') {
            const targetGroup = searchGroup === 'fromDestination' ? 'from' : 'to';
            const otherInputInGroup = searchType === 'airport' ? 'country' : 'airport';

            historySearchState[targetGroup] = { type: searchType, id: selectedId, text: displayVal };

            // Visually clear the other input in the same group
            $input.closest('.searchCriterion').siblings('.searchCriterion').find(`input.${otherInputInGroup}`).val('');

        } else if (searchGroup === 'entity') { // airline or alliance
            const otherEntity = searchType === 'airline' ? 'alliance' : 'airline';
            historySearchState[searchType] = { id: selectedId, text: displayVal };
            historySearchState[otherEntity] = { id: null, text: '' }; // Clear other entity

            // Visually clear the other input in the same group
            $input.closest('.searchCriterion').siblings('.searchCriterion').find(`input.${otherEntity}`).val('');
        }
    }
    resultContainer.hide();
}

function clickSelection(selectionDiv) {
    selectionDiv.siblings("div.selected").removeClass("selected")
    selectionDiv.addClass("selected")
    var resultContainer = selectionDiv.closest(".searchResult")

    var input = resultContainer.siblings(".searchInput").find("input[type=text]")
    confirmSelection(input)
}

/**
 * Shared keyboard navigation for search result lists.
 * Works with both jQuery elements and vanilla DOM elements.
 * @param {number} indexChange - Direction to move (-1 for up, 1 for down)
 * @param {Element|jQuery} container - The results container
 * @param {string} itemSelector - CSS selector for result items (default: '.searchResultEntry')
 */
function navigateSearchResults(indexChange, container, itemSelector = '.searchResultEntry') {
    const isJQuery = container.jquery !== undefined;
    const items = isJQuery
        ? container.find(itemSelector).toArray()
        : container.querySelectorAll(itemSelector);

    if (items.length === 0) return;

    const selectedSelector = itemSelector + '.selected';
    const selected = isJQuery
        ? container.find(selectedSelector)[0]
        : container.querySelector(selectedSelector);

    let currentIndex = Array.from(items).indexOf(selected);
    if (currentIndex === -1) currentIndex = 0;

    currentIndex += indexChange;
    if (currentIndex < 0) currentIndex = 0;
    if (currentIndex >= items.length) currentIndex = items.length - 1;

    // Remove selected from all, add to new
    items.forEach(item => item.classList.remove('selected'));
    if (items[currentIndex]) {
        items[currentIndex].classList.add('selected');
    }
}

// Legacy wrapper for existing code
function changeSelection(indexChange, resultContainer) {
    navigateSearchResults(indexChange, resultContainer, 'div.searchResultEntry');
}

function numberInputFocusOut(input) {
    const value = input.val();
    const parsedValue = value && !isNaN(value) ? parseInt(value) : null;
    if (!parsedValue) {
        input.val('')
    }

    if (input.hasClass('capacity')) {
        historySearchState.capacity = parsedValue;
    } else if (input.hasClass('capacityDelta')) {
        historySearchState.capacityDelta = parsedValue;
    }
}

let searchState = {};

/**
 * Generic search configuration for different search types
 */
const searchConfigs = {
    airport: {
        dataSource: () => {
            // airports is GeoJSON - extract properties from features
            if (airports?.features) {
                return airports.features.map(f => f.properties);
            }
            return [];
        },
        searchFields: [
            { field: 'city', exactScore: 100, partialScore: 50 },
            { field: 'iata', exactScore: 200, partialScore: 80 },
            { field: 'icao', exactScore: 200, partialScore: 80 },
            { field: 'name', exactScore: 70, partialScore: 30 }
        ],
        resultMapper: (item) => ({
            airportId: item.id,
            airportCity: item.city,
            airportIata: item.iata,
            airportIcao: item.icao,
            airportName: item.name,
            countryCode: item.countryCode
        }),
        textEntryFunction: getAirportTextEntry,
        idField: 'airportId'
    },
    country: {
        dataSource: () => Object.entries(loadedCountriesByCode || {}).map(([code, country]) => ({
            countryCode: code,
            countryName: country.name
        })),
        searchFields: [
            { field: 'countryName', exactScore: 100, partialScore: 50 },
            { field: 'countryCode', exactScore: 200, partialScore: 80 }
        ],
        resultMapper: (item) => item,
        textEntryFunction: getCountryTextEntry,
        idField: 'countryCode'
    },
    airline: {
        dataSource: () => Rivals.airlines || [],
        searchFields: [
            { field: 'airlineCode', exactScore: 200, partialScore: 80 },
            { field: 'name', exactScore: 100, partialScore: 50 },
            { field: 'username', exactScore: 50, partialScore: 25 },
        ],
        resultMapper: (item) => ({
            airlineId: item.id || item.airlineId,
            airlineName: item.name,
            airlineCode: item.airlineCode,
            countryCode: item.countryCode
        }),
        textEntryFunction: (entry) => getAirlineTextEntry(entry, true),
        idField: 'airlineId'
    },
    alliance: {
        dataSource: () => Object.values(Alliance.loadedAlliancesById),
        searchFields: [
            { field: 'name', exactScore: 100, partialScore: 50 }
        ],
        resultMapper: (item) => ({
            allianceId: item.id,
            allianceName: item.name
        }),
        textEntryFunction: getAllianceTextEntry,
        idField: 'allianceId'
    }
};

/**
 * Generic search function that works with any configured search type
 * @param {string} searchType - The type of search (airport, country, airline, alliance)
 * @param {string} phrase - Search phrase
 * @returns {Array} Array of matching entries
 */
function searchCachedData(searchType, phrase) {
    const config = searchConfigs[searchType];
    if (!config) {
        console.error(`No search configuration found for type: ${searchType}`);
        return [];
    }

    const dataSource = config.dataSource();
    if (!dataSource || !Array.isArray(dataSource)) {
        console.error(`Data source not loaded or invalid for type: ${searchType}`);
        return [];
    }

    const searchTerm = phrase.toLowerCase().trim();
    const results = [];

    // Search through data source
    for (let i = 0; i < dataSource.length; i++) {
        const item = dataSource[i];
        let matches = false;
        let matchScore = 0;

        // Check each configured search field
        for (const fieldConfig of config.searchFields) {
            const fieldValue = item[fieldConfig.field];
            if (fieldValue && typeof fieldValue === 'string') {
                const fieldValueLower = fieldValue.toLowerCase();
                if (fieldValueLower.includes(searchTerm)) {
                    matches = true;
                    if (fieldValueLower === searchTerm) {
                        matchScore += fieldConfig.exactScore;
                    } else if (fieldValueLower.startsWith(searchTerm)) {
                        matchScore += fieldConfig.exactScore * 0.8;
                    } else {
                        matchScore += fieldConfig.partialScore;
                    }
                }
            }
        }

        if (matches) {
            // Add population score for airports
            if (searchType === 'airport' && item.population) {
                // Add a score based on population, scaled logarithmically
                // A multiplier of 30 brings a 20M population airport to a score of ~220
                const populationScore = Math.log10(item.population) * 25;
                matchScore += populationScore;
            }

            const mappedResult = config.resultMapper(item);
            mappedResult.matchScore = matchScore;
            results.push(mappedResult);
        }
    }

    // Sort by match score (highest first), then by primary field
    results.sort((a, b) => {
        if (b.matchScore !== a.matchScore) {
            return b.matchScore - a.matchScore;
        }
        // Use the first field as primary sort
        const primaryField = config.searchFields[0].field;
        const aValue = a[primaryField] || '';
        const bValue = b[primaryField] || '';
        return aValue.localeCompare(bValue);
    });

    return results.slice(0, 15); // Limit to 15 results
}

/**
 * Generic cached search UI handler
 * @param {string} searchType - The type of search
 * @param {jQuery} input - The search input element
 * @param {jQuery} resultContainer - The results container element
 * @param {string} phrase - The search phrase
 */
function performSearch(searchType, input, resultContainer, phrase) {
    input.parent().find(".spinner").show(0);
    
    // Use setTimeout to make it asynchronous and allow UI to update
    setTimeout(function() {
        try {
            // Clear previous results
            resultContainer.find("div.searchResultEntry, div.message").remove();
            
            // Search cached data
            const searchResults = searchCachedData(searchType, phrase);
            const config = searchConfigs[searchType];
            
            if (searchResults.length === 0) {
                resultContainer.append(`<div class='message'>No ${searchType}s found</div>`);
            } else {
                $.each(searchResults, function(index, entry) {
                    var textEntry = config.textEntryFunction(entry);
                    var text = highlightText(textEntry, phrase);
                    var searchResultDiv = $("<div class='searchResultEntry' onmousedown='clickSelection($(this))'>" + text + "</div>");
                    searchResultDiv.data(searchType, entry);
                    resultContainer.append(searchResultDiv);
                    if (index == 0) {
                        searchResultDiv.addClass("selected");
                    }
                });
            }
            
            resultContainer.show();
        } catch (error) {
            console.error(`Error searching cached ${searchType}s:`, error);
            resultContainer.append(`<div class='message'>Search error occurred</div>`);
            resultContainer.show();
        } finally {
            input.parent().find(".spinner").hide();
        }
    }, 10); // Small delay to allow UI to update
}

function search(event, input) {
    const resultContainer = input.closest('div.searchInput').siblings('div.searchResult');
    const searchType = input.closest('div.searchInput').data('searchType');
    const phrase = input.val();

    if (phrase.length < 3 && searchType !== 'country') {
        resultContainer.hide();
        return;
    }

    // Use cached search for all supported types
    if (searchConfigs[searchType]) {
        performSearch(searchType, input, resultContainer, phrase);
    } else {
        // A search type was used that is not configured in searchConfigs
        console.warn(`Unsupported search type: ${searchType}`);
        resultContainer.hide();
    }
}

function buildDemandsTable(demands, targetDiv) {
    document.querySelectorAll(`#researchSearchResult .table.${targetDiv} .table-row`).forEach(row => row.remove());
    demands.forEach(demand => {
        const row = document.createElement('div');
        row.className = 'table-row';
        row.innerHTML = `
            <div class='cell'>${demand.linkClass}</div>
            <div class='cell'>${demand.passengerType}</div>
            <div class='cell'>${demand.preferenceType}</div>
            <div class='cell'>$${commaSeparateNumber(demand.price)}</div>
            <div class='cell'>${commaSeparateNumber(demand.count)}</div>
        `;
        document.querySelector(`#researchSearchResult .table.${targetDiv}`).appendChild(row);
    });

    if (demands.length === 0) {
        const row = document.createElement('div');
        row.className = 'table-row';
        row.innerHTML = `
            <div class='cell'>-</div>
            <div class='cell'>-</div>
            <div class='cell'>-</div>
            <div class='cell'>-</div>
            <div class='cell'>-</div>
        `;
        document.querySelector(`#researchSearchResult .table.${targetDiv}`).appendChild(row);
    }
}


async function researchFlight(fromAirportId, toAirportId) {
    if (!fromAirportId || !toAirportId) {
        return;
    }
    const url = `/research-link/${fromAirportId}/${toAirportId}`;
    const key = `research-${fromAirportId}-${toAirportId}`;
    const headers = {};
    if (_searchEtagStore[key]) headers['If-None-Match'] = _searchEtagStore[key];
    $('body .loadingSpinner').show();
    try {
        const res = await fetch(url, { headers });
        let result;
        if (res.status === 304 && _researchData[key]) {
            result = _researchData[key];
        } else {
            if (!res.ok) { console.log('AJAX error: ' + res.status); return; }
            const etag = res.headers.get('ETag');
            if (etag) _searchEtagStore[key] = etag;
            result = await res.json();
            _researchData[key] = result;
        }
        populateResearchHeader(result);
        populateResearchLinksTable(result.links, result.consumptions, result.basePrice);
        populateResearchDemandTables(result);
        $('#researchSearchResult').show();
        assignAirlineColors(result.consumptions, "airlineId");
    } catch (e) {
        console.error('Failed to research flight:', e);
    } finally {
        $('body .loadingSpinner').hide();
    }
}

function populateResearchHeader(result) {
    const {
        fromAirportId, toAirportId, fromAirportText, toAirportText,
        fromAirportPopulation, fromAirportIncome, toAirportPopulation, toAirportIncome,
        fromAirportCountryCode, toAirportCountryCode, mutualRelationship,
        affinity, distance, flightType, directDemand, baselineDirectDemand
    } = result;

    const researchResult = $('#researchSearchResult');

    loadAirportImage(fromAirportId, researchResult.find('img.fromAirport'));
    loadAirportImage(toAirportId, researchResult.find('img.toAirport'));

    researchResult.find(".fromAirportText").text(fromAirportText).attr("href", `/airport/${fromAirportCountryCode}`);
    researchResult.find(".fromAirport .population").text(commaSeparateNumber(fromAirportPopulation));
    researchResult.find(".fromAirport .income").text("$" + commaSeparateNumber(fromAirportIncome));

    researchResult.find(".toAirportText").text(toAirportText).attr("href", `/airport/${toAirportCountryCode}`);
    researchResult.find(".toAirport .population").text(commaSeparateNumber(toAirportPopulation));
    researchResult.find(".toAirport .income").text("$" + commaSeparateNumber(toAirportIncome));

    researchResult.find(".relationship").html(`${getCountryFlagImg(fromAirportCountryCode)}&nbsp;vs&nbsp;${getCountryFlagImg(toAirportCountryCode)}${getCountryRelationshipDescription(mutualRelationship)}`);
    researchResult.find(".affinities").text(affinity);
    researchResult.find(".distance").text(distance);
    researchResult.find(".flightType").text(flightType);
    researchResult.find(".demand").text(toLinkClassValueString(directDemand));
    researchResult.find(".baselineDemand").text(toLinkClassValueString(baselineDirectDemand));
}

function populateResearchLinksTable(links, consumptions, basePrice) {
    const linksTable = $('#researchSearchResult .table.links');
    linksTable.find(".table-row").remove(); // Clear existing rows

    if (links.length === 0) {
        const emptyRow = `
            <div class='table-row'>
                <div class='cell'>-</div>
                <div class='cell'>-</div>
                <div class='cell'>-</div>
                <div class='cell'>-</div>
                <div class='cell'>-</div>
                <div class='cell'>-</div>
            </div>`;
        linksTable.append(emptyRow);
        return;
    }

    links.forEach((link, index) => {
        const consumption = consumptions[index];
        const loadFactor = link.capacity.total > 0 ? Math.round(consumption.soldSeats * 100 / link.capacity.total) : 0;
        const rowHtml = `
            <div class='table-row'>
                <div class='cell'>${getAirlineLogoImg(link.airlineId)} ${link.airlineName}</div>
                <div class='cell'>${toLinkPercentOfBasePrices(link.price, basePrice)}</div>
                <div class='cell'>${toLinkClassValueString(link.capacity)}</div>
                <div class='cell'>${link.frequency}</div>
                <div class='cell'>${link.computedQuality}</div>
                <div class='cell'>${loadFactor}%</div>
            </div>`;
        linksTable.append(rowHtml);
    });
}

function populateResearchDemandTables(result) {
    const { fromAirportIata, toAirportIata, fromDemands, toDemands } = result;

    $('#researchSearchResult .fromDemandHeading').text(`Demand from ${fromAirportIata} to ${toAirportIata}`);
    buildDemandsTable(fromDemands, "fromDemand");

    $('#researchSearchResult .toDemandHeading').text(`Demand to ${fromAirportIata} from ${toAirportIata}`);
    buildDemandsTable(toDemands, "toDemand");
}

function getCountryTextEntry(country) {
   return country.countryName + "(" + country.countryCode + ")"
}

function getZoneTextEntry(zone) {
    return zone.zoneName + "(" + zone.zone + ")"
}

function getAirlineTextEntry(airline, showPreviousNames) {
    var name = airline.airlineName ? airline.airlineName : airline.name //some inconsistencies...
    var result = name + "(" + airline.airlineCode + ")"
    if (showPreviousNames && airline.previousNames && airline.previousNames.length > 0) {
        result += (" formerly: " + airline.previousNames.join(", "))
    }
    return result
}

function getAllianceTextEntry(alliance) {
    return alliance.allianceName
}

function positionTitles(titlesContainer) {
    titlesContainer.show();
    var titleSelections = titlesContainer.children('div.titleSelection')
    titleSelections.addClass('clickable')

    titleSelections.off("click.select");
    titleSelections.on("click.select", function(){
      $(this).siblings().removeClass('selected')
      $(this).addClass('selected')
      updateNavigationArrows(titlesContainer, true)
    });
}

function titleNavigate(arrow, indexChange) {
    var titlesContainer = arrow.closest('.titlesContainer')
    var selectedDiv = titlesContainer.find('.titleSelection.selected')
    var selectedIndex = titlesContainer.find('.titleSelection').index(selectedDiv)
    var newIndex = selectedIndex + indexChange

    var newSelectedDiv = titlesContainer.find('.titleSelection')[newIndex]
    $(newSelectedDiv).trigger('click')
}

function updateNavigationArrows($titlesContainer, animated) {
    var $selectedDiv = $titlesContainer.find('.titleSelection.selected')
    var selectedIndex = $titlesContainer.find('.titleSelection').index($selectedDiv)
    var selectionLength = $titlesContainer.find('.titleSelection').length


    if (selectionLength <= 1) {
        $titlesContainer.find('div.left').hide()
        $titlesContainer.find('div.right').hide()
    } else {
        var duration = updateNavigationArrows ? 500 : 0

        if (selectedIndex <= 0) {
            $titlesContainer.find('div.left').fadeOut(duration)
        } else {
            $titlesContainer.find('div.left').fadeIn(duration)
        }

        if (selectedIndex >= selectionLength - 1) {
            $titlesContainer.find('div.right').fadeOut(duration)
        } else {
            $titlesContainer.find('div.right').fadeIn(duration)
        }
    }
}