function showCampaignModal() {
    updateCampaignTable()
    var $locationSearchInput = $('#campaignModal .searchInput input')

    $locationSearchInput.on('confirmSelection', function(e) {
        draftCampaign($('#campaignModal .searchInput input').data("selectedId"))
    })

    if (!campaignMap) {
        initCampaignMap()
    }

    updateAirlineDelegateStatus($('#campaignModal div.delegateStatus'), function(delegateInfo) {
            $('#campaignModal div.delegateSection').data("availableDelegates", delegateInfo.permanentAvailableCount)
        })
    $('#campaignModal div.delegateSection').data("delegatesRequired", 0)
    $('#campaignModal .campaignDetails').hide()
    $('#campaignModal .draftCampaign').hide()
    $('#campaignModal').data('closeCallback', updateCampaignSummary)
    $('#campaignModal').fadeIn(500)
}

function initCampaignMap() {
    const container = $('#campaignModal .campaignMap')[0]
    if (!container) return

    // Get the current style from the main map module
    const currentStyle = typeof getCurrentStyle === 'function' ? getCurrentStyle() : 'dark'
    const style = currentStyle === 'light' ? getCampaignMapLightStyle() : getCampaignMapDarkStyle()

    campaignMap = new maplibregl.Map({
        container: container,
        style: style,
        center: [0, 0],
        zoom: 2,
        interactive: true,
        scrollZoom: false,
        attributionControl: false
    })

    // Add navigation control
    campaignMap.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
}

function getCampaignMapDarkStyle() {
    return {
        version: 8,
        sources: {
            protomaps: {
                type: 'vector',
                tiles: [
                    'https://maps.myfly.club/mfc/{z}/{x}/{y}.mvt'
                ],
                maxzoom: 11,
                attribution: '<a href="https://protomaps.com">Protomaps</a> © <a href="https://openstreetmap.org">OpenStreetMap</a>'
            }
        },
        glyphs: 'https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf',
        layers: [
            { id: 'background', type: 'background', paint: { 'background-color': '#10141b' } },
            { id: 'earth', type: 'fill', source: 'protomaps', 'source-layer': 'earth', paint: { 'fill-color': '#1c232c' } },
            { id: 'water', type: 'fill', source: 'protomaps', 'source-layer': 'water', paint: { 'fill-color': '#10141b' } },
            { id: 'boundaries', type: 'line', source: 'protomaps', 'source-layer': 'boundaries', filter: ['==', ['get', 'pmap:kind'], 'country'], paint: { 'line-color': '#343c4a', 'line-width': 1 } },
            {
                id: 'labels',
                type: 'symbol',
                source: 'protomaps',
                'source-layer': 'places',
                filter: ['==', ['get', 'pmap:kind'], 'country'],
                layout: {
                    'text-field': ['coalesce', ['get', 'name:en'], ['get', 'name']],
                    'text-font': ['Noto Sans Regular'],
                    'text-size': 12,
                    'text-allow-overlap': true
                },
                paint: {
                    'text-color': '#8e96a0',
                    'text-halo-color': '#10141b',
                    'text-halo-width': 1
                }
            }
        ]
    }
}

function getCampaignMapLightStyle() {
    return {
        version: 8,
        sources: {
            protomaps: {
                type: 'vector',
                tiles: [
                    'https://maps.myfly.club/mfc/{z}/{x}/{y}.mvt'
                ],
                maxzoom: 11,
                attribution: '<a href="https://protomaps.com">Protomaps</a> © <a href="https://openstreetmap.org">OpenStreetMap</a>'
            }
        },
        glyphs: 'https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf',
        layers: [
            { id: 'background', type: 'background', paint: { 'background-color': '#ccd3d3' } },
            { id: 'earth', type: 'fill', source: 'protomaps', 'source-layer': 'earth', paint: { 'fill-color': '#f0f0f0' } },
            { id: 'water', type: 'fill', source: 'protomaps', 'source-layer': 'water', paint: { 'fill-color': '#ccd3d3' } },
            { id: 'boundaries', type: 'line', source: 'protomaps', 'source-layer': 'boundaries', filter: ['==', ['get', 'pmap:kind'], 'country'], paint: { 'line-color': '#ffffff', 'line-width': 1 } },
            {
                id: 'labels',
                type: 'symbol',
                source: 'protomaps',
                'source-layer': 'places',
                filter: ['==', ['get', 'pmap:kind'], 'country'],
                layout: {
                    'text-field': ['coalesce', ['get', 'name:en'], ['get', 'name']],
                    'text-font': ['Noto Sans Regular'],
                    'text-size': 12,
                    'text-allow-overlap': true
                },
                paint: {
                    'text-color': '#5c5c5c',
                    'text-halo-color': '#ffffff',
                    'text-halo-width': 1
                }
            }
        ]
    }
}

function toggleDraftCampaign() {
    $('#campaignModal .campaignDetails').hide()
    $('#campaignModal .searchInput .airport').val('')

    $('#campaignModal .draftCampaign').fadeIn(500)

}

function changeCampaignDelegateCount(delta) {
    changeTaskDelegateCount($('#campaignModal .delegateSection'), delta, function(delegateCount) {
        if (delegateCount <= 0) {
            disableButton($('#campaignModal .campaignDetails .save'), "must assign at least one delegate")
        } else {
            enableButton($('#campaignModal .campaignDetails .save'))
        }
        $('#campaignModal .campaignDetails .cost').text('$' + commaSeparateNumber(delegateCount * $('#campaignModal .campaignDetails').data('costPerDelegate')))
    })
}


var loadedCampaigns = []



function updateCampaignTable() {
    $.ajax({
        type: 'GET',
        url: "/airlines/" + activeAirline.id + "/campaigns?fullLoad=true",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            loadedCampaigns = result
            refreshCampaignTable()
            var selectedSortHeader = $('#campaignModal .campaignTableHeader .cell.selected')
		    refreshCampaignTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'))
        },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
    });

}

function refreshCampaignTable(sortProperty, sortOrder) {
	var $campaignTable = $("#campaignModal .campaignTable")
	$campaignTable.children("div.table-row").remove()

	//sort the list
	//loadedLinks.sort(sortByProperty(sortProperty, sortOrder == "ascending"))
	loadedCampaigns = sortPreserveOrder(loadedCampaigns, sortProperty, sortOrder == "ascending")

    var selectedCampaign = $('#campaignModal').data('selectedCampaign')
	$.each(loadedCampaigns, function(index, campaign) {
		var row = $("<div class='table-row clickable' onclick='selectCampaign($(this))'></div>")
		row.data("campaign", campaign)

		row.append("<div class='cell'>" + getCountryFlagImg(campaign.principalAirport.countryCode) + getAirportText(campaign.principalAirport.city, campaign.principalAirport.iata) + "</div>")
		row.append("<div class='cell'>" + campaign.radius + "</div>")
		row.append("<div class='cell'>" + campaign.level + "</div>")
		row.append("<div class='cell'>" + campaign.population + "</div>")
		row.append("<div class='cell'>" + campaign.area.length + "</div>")

		if (selectedCampaign && selectedCampaign.id == campaign.id) {
			row.addClass("selected")
		}

		$campaignTable.append(row)
	});

	if (loadedCampaigns.length == 0) {
	    $campaignTable.append("<div class='table-row'><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div></div>")

	    $('#campaignModal .addCampaign').addClass('glow')
	} else {
	    $('#campaignModal .addCampaign').removeClass('glow')
	}
}
function selectCampaign(row) {
    //update table
	row.siblings().removeClass("selected")
	row.addClass("selected")
	var campaign = row.data('campaign')
	$('#campaignModal').data('selectedCampaign', campaign)
	$('#campaignModal .campaignMap').data('radius', campaign.radius)
    $('#campaignModal .campaignMap').data('selectedAirportId', campaign.principalAirport.id)

    var $delegateSection = $('#campaignModal div.delegateSection')
    var delegates = campaign.delegates
    delegates.sort(function(a, b) { //sort, the most senior comes first
        return a.startCycle - b.startCycle
    })
    $delegateSection.data('originalDelegates', delegates)
    $delegateSection.data('assignedDelegateCount', delegates.length)

    $('#campaignModal .campaignDetails .create').hide()
    $('#campaignModal .campaignDetails .update').show()
    $('#campaignModal .campaignDetails .delete').show()

	refreshCampaign()
}

function draftCampaign(selectedAirportId) {
    $('#campaignModal #campaignTable .table-row.selected').removeClass('selected')
    $('#campaignModal .campaignMap').data('radius', MIN_CAMPAIGN_RADIUS)
    $('#campaignModal .campaignMap').data('selectedAirportId', selectedAirportId)
    $('#campaignModal').removeData('selectedCampaign')

    var $delegateSection = $('#campaignModal div.delegateSection')
    $delegateSection.data('originalDelegates', [])
    $delegateSection.data('assignedDelegateCount', 0)
    disableButton($('#campaignModal .campaignDetails .save'), "must assign at least one delegate")

    $('#campaignModal .campaignDetails .create').show()
    $('#campaignModal .campaignDetails .update').hide()
    $('#campaignModal .campaignDetails .delete').hide()

    refreshCampaign()
}

function createCampaign() {
    var $delegateSection = $('#campaignModal .delegateSection')
    var assignedDelegateCount = $delegateSection.data('assignedDelegateCount')
    $.ajax({
        type: 'POST',
        url: "/airlines/" + activeAirline.id + "/campaigns",
        contentType: 'application/json; charset=utf-8',
        data:  JSON.stringify({
         'delegateCount' : assignedDelegateCount,
         'airportId' : $("#campaignModal .campaignMap").data('selectedAirportId'),
         'radius' : $('#campaignModal .campaignMap').data('radius')
         }) ,
        dataType: 'json',
        success: function(result) {
            closeCampaignDetails(true)
            updateCampaignTable()
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function updateCampaign() {
    var $delegateSection = $('#campaignModal .delegateSection')
    var assignedDelegateCount = $delegateSection.data('assignedDelegateCount')
    $.ajax({
        type: 'POST',
        url: "/airlines/" + activeAirline.id + "/campaigns",
        contentType: 'application/json; charset=utf-8',
        data:  JSON.stringify({
         'delegateCount' : assignedDelegateCount,
         'radius' : $('#campaignModal .campaignMap').data('radius'),
         'campaignId' : $('#campaignModal').data('selectedCampaign').id
         }) ,
        dataType: 'json',
        success: function(result) {
            closeCampaignDetails(true)
            updateCampaignTable()
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}



function deleteCampaign() {
    var airport = $('#campaignModal').data('selectedCampaign').principalAirport
    promptConfirm("Do you want to delete this campaign at " + getAirportText(airport.city, airport.iata), function() {
        $.ajax({
            type: 'DELETE',
            url: "/airlines/" + activeAirline.id + "/campaigns/" + $('#campaignModal').data('selectedCampaign').id,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                closeCampaignDetails(true)
                updateCampaignTable()
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
        }
    )
}



var campaignMap
var campaignMapMarkers = []
var campaignMapCircle = null
var campaignPopup = null

function populateCampaignMap(principalAirport, campaignArea, candidateArea, radius) {
    // Clear existing markers and circle
    campaignMapMarkers.forEach(marker => marker.remove())
    campaignMapMarkers = []

    if (campaignMapCircle && campaignMap.getSource('campaign-circle')) {
        campaignMap.removeLayer('campaign-circle-fill')
        campaignMap.removeLayer('campaign-circle-outline')
        campaignMap.removeSource('campaign-circle')
    }

    // Calculate zoom level to fit the radius
    // MapLibre uses different zoom calculation than Google Maps
    const radiusKm = (radius + 200) * 1.2
    const zoom = getMapLibreZoomForRadius(radiusKm, principalAirport.latitude)

    campaignMap.flyTo({
        center: [principalAirport.longitude, principalAirport.latitude],
        zoom: zoom,
        duration: 500
    })

    // Add circle as GeoJSON source
    const circleGeoJSON = createCircleGeoJSON(
        principalAirport.longitude,
        principalAirport.latitude,
        radius * 1000 // convert to meters
    )

    campaignMap.addSource('campaign-circle', {
        type: 'geojson',
        data: circleGeoJSON
    })

    campaignMap.addLayer({
        id: 'campaign-circle-fill',
        type: 'fill',
        source: 'campaign-circle',
        paint: {
            'fill-color': '#32CF47',
            'fill-opacity': 0.3
        }
    })

    campaignMap.addLayer({
        id: 'campaign-circle-outline',
        type: 'line',
        source: 'campaign-circle',
        paint: {
            'line-color': '#32CF47',
            'line-width': 2,
            'line-opacity': 0.5
        }
    })

    campaignMapCircle = true

    // Add airport markers
    populateCampaignAirportMarkers(campaignArea, true)
    populateCampaignAirportMarkers(candidateArea, false)
}

function createCircleGeoJSON(lng, lat, radiusMeters) {
    const points = 64
    const coords = []
    const earthRadius = 6371000 // meters

    for (let i = 0; i <= points; i++) {
        const angle = (i / points) * 2 * Math.PI
        const dx = radiusMeters * Math.cos(angle)
        const dy = radiusMeters * Math.sin(angle)

        const newLat = lat + (dy / earthRadius) * (180 / Math.PI)
        const newLng = lng + (dx / earthRadius) * (180 / Math.PI) / Math.cos(lat * Math.PI / 180)

        coords.push([newLng, newLat])
    }

    return {
        type: 'Feature',
        geometry: {
            type: 'Polygon',
            coordinates: [coords]
        }
    }
}

function getMapLibreZoomForRadius(radiusKm, latitude) {
    // Calculate zoom level to fit a given radius in the map view
    const mapWidth = $('#campaignModal .campaignMap').width() || 300
    const metersPerPixel = radiusKm * 1000 * 2 / mapWidth
    const zoom = Math.log2(40075016.686 * Math.cos(latitude * Math.PI / 180) / metersPerPixel / 256)
    return Math.min(Math.max(zoom - 0.5, 1), 15)
}

function populateCampaignAirportMarkers(airports, hasCoverage) {
    $.each(airports, function(index, airport) {
        // Create marker element
        const el = document.createElement('div')
        el.className = 'campaign-airport-marker'
        el.style.width = '20px'
        el.style.height = '20px'
        el.style.backgroundSize = 'contain'
        el.style.backgroundRepeat = 'no-repeat'
        el.style.cursor = 'pointer'

        if (hasCoverage) {
            el.style.backgroundImage = `url(${getAirportIcon(airport)})`
        } else {
            el.style.backgroundImage = `url(${$("#map").data("disabledAirportMarker")})`
            el.style.opacity = '0.5'
        }

        const marker = new maplibregl.Marker({ element: el })
            .setLngLat([airport.longitude, airport.latitude])
            .addTo(campaignMap)

        // Add hover popup
        el.addEventListener('mouseenter', function() {
            $("#campaignAirportPopup .airportName").text(getAirportText(airport.city, airport.iata))
            $("#campaignAirportPopup .airportPopulation").text(airport.population)

            const popupContent = $("#campaignAirportPopup").clone()
            popupContent.show()

            if (campaignPopup) {
                campaignPopup.remove()
            }

            campaignPopup = new maplibregl.Popup({
                closeButton: false,
                closeOnClick: false,
                offset: 10
            })
                .setLngLat([airport.longitude, airport.latitude])
                .setDOMContent(popupContent[0])
                .addTo(campaignMap)
        })

        el.addEventListener('mouseleave', function() {
            if (campaignPopup) {
                campaignPopup.remove()
                campaignPopup = null
            }
        })

        campaignMapMarkers.push(marker)
    })
}

var MIN_CAMPAIGN_RADIUS = 100
var MAX_CAMPAIGN_RADIUS = 1000
function changeCampaignRadius(delta) {
    currentRadius = $('#campaignModal .campaignMap').data('radius')
    var newRadius = currentRadius + delta
    if (newRadius >= MIN_CAMPAIGN_RADIUS && newRadius <= MAX_CAMPAIGN_RADIUS) {
        $('#campaignModal .campaignMap').data('radius', newRadius)
        refreshCampaign()
    }
}

function updateCampaignRadiusControl(radius) {
    if (radius > MIN_CAMPAIGN_RADIUS) {
        enableButton($("#campaignModal .radiusControl .decrease"))
    }
    if (radius < MAX_CAMPAIGN_RADIUS) {
        enableButton($("#campaignModal .radiusControl .increase"))
    }

    if (radius <= MIN_CAMPAIGN_RADIUS) {
        disableButton($("#campaignModal .radiusControl .decrease"), "Min campaign radius at " + MIN_CAMPAIGN_RADIUS + " km" )
    } else if (radius >= MAX_CAMPAIGN_RADIUS) {
        disableButton($("#campaignModal .radiusControl .increase"), "Max campaign radius at " + MAX_CAMPAIGN_RADIUS + " km" )
    }
}



function refreshCampaign() {
    var radius = $('#campaignModal .campaignMap').data('radius')
    var selectedAirportId = $('#campaignModal .campaignMap').data('selectedAirportId')
    updateCampaignRadiusControl(radius)
    $('#campaignModal span.radius').text(radius)
    $.ajax({
        type: 'GET',
        url: "/airlines/" + activeAirline.id + "/campaign-airports/" + selectedAirportId + "?radius=" + radius,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            populateCampaignMap(result.principalAirport, result.area, result.candidateArea, radius)
            updateCampaignDetails(result)
            $('#campaignModal .draftCampaign').hide()
            $('#campaignModal .campaignDetails').fadeIn(500)
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function updateCampaignDetails(campaign) {
    var $delegateSection = $('#campaignModal div.delegateSection')
    $('#campaignModal .campaignDetails .principalAirport').text(getAirportText(campaign.principalAirport.city, campaign.principalAirport.iata))
    $('#campaignModal .campaignDetails .population').text(commaSeparateNumber(campaign.population))
    $('#campaignModal .campaignDetails .airports').text(campaign.area.length)
    var delegateLevel = 0
    $.each($delegateSection.data('originalDelegates'), function(index, delegate) {
        delegateLevel += delegate.level
    })

    $('#campaignModal .campaignDetails .delegateLevel').text(delegateLevel)
    $('#campaignModal .campaignDetails').data('costPerDelegate', campaign.costPerDelegate)
    $('#campaignModal .campaignDetails .loyaltyBonus').text(campaign.bonus.loyalty)

    //update delegate section
    var $delegateSection = $('#campaignModal div.delegateSection')
    $('#campaignModal .campaignDetails .cost').text('$' + commaSeparateNumber($delegateSection.data('assignedDelegateCount') * campaign.costPerDelegate))
    refreshAssignedDelegates($delegateSection.data('assignedDelegateCount'), '#4a9eed', $delegateSection.find('.assignedDelegatesIcons'))


}

function closeCampaignDetails(updated) {
    if (updated) {
        updateAirlineDelegateStatus($('#officeCanvas .delegateStatus'))
    }
    $('#campaignModal .campaignDetails').fadeOut(500)
}
