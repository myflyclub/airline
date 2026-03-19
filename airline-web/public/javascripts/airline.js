var flightPaths = {} //key: link id, value : { path, shadow }

var tempPath //temp path for new link creation
var loadedLinks = []
var loadedLinksById = {}
var linksTableSummaryState = false
var currentAnimationStatus = false
var currentAirlineAllianceMembers = []
var selectedLinkIds = new Set()
var linkColors = JSON.parse(localStorage.getItem('linkColors')) || {}
const CLASSES = ['economy', 'business', 'first'];

$( document ).ready(function() {
    $('#linkEventModal .filterCheckboxes input:checkbox').change(function() {
        $("#linkEventModal .linkEventHistoryTable .table-row").hide() //hide all first
        $('#linkEventModal .filterCheckboxes input:checkbox').each(function() { //have to iterate them, as they are not mutually exclusive...
           var filterType = $(this).data('filter')
           if ($(this).prop('checked')) {
               $("#linkEventModal .linkEventHistoryTable .table-row.filter-" + filterType).show()
           }
        });
    })
})

/**
 *
 * @param {integer} airlineId
 * @returns
 */
function updateAirlineInfo(airlineId) {
	return $.ajax({
		type: 'GET',
		url: "/airlines/" + airlineId + "?extendedInfo=true",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(airline) {
	    	refreshTopBar(airline)
	    	$(".currentAirline").html(getAirlineLogoImg(airline.id) + airline.name)

	    	if (airline.headquarterAirport) {
                        $("#currentAirlineCountry").html("<img class='flag' src='/assets/images/flags/" + airline.headquarterAirport.countryCode + ".svg' />")
	    	} else {
                        $("#currentAirlineCountry").empty()
	    	}
	    	activeAirline = airline
            const airlineType = airline.type.replace(" ", "") || 'standard'
            document.body.classList.add(`airlineType-${airlineType}`);
	    	updateAirlineLogo()
            if (window.AirlineMap) {
                updateLinksInfo();
                AirlineMap.updateAirportMarkers(airline);
            }
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function updateAirlineLogo() {
	$('.airlineLogo').attr('src', '/airlines/' + activeAirline.id + "/logo?dummy=" + Math.random())
}

function refreshTopBar(airline) {
    changeColoredElementValue($(".balance"), airline.balance)
    var currentAP = airline.actionPoints != null ? airline.actionPoints : 0.0
    $(".actionPoints").text("⚡ " + currentAP.toFixed(1))

    $(".actionPoints").attr('data-tooltip', computeApTooltip(currentAP, airline.delegatesInfo))
	$(".reputationValue").text(airline.reputation)
	$(".reputationStars").empty()

	//mobile
	$(".reputation.label").text(airline.reputation)

	//desktop
    const airlineNext = airline.gradeCeiling > 10e12 ? "∞" : commaSeparateNumber(airline.gradeCeiling)
	const reputationText = "Reputation: " + airline.reputation.toFixed(0) + " (" + airline.gradeDescription + ") Next Grade: " + airlineNext
	var $starBar = $(getGradeStarsImgs(airline.gradeLevel - 3))
	$(".reputationStars").append($starBar)
	addTooltip($("#topReputationStars"), reputationText, {'top' : 0, 'width' : '350px', 'white-space' : 'nowrap'})

    oilPrices === null && loadOilPrices();
}

async function selectAirline(airlineId) {
	initWebSocket(airlineId)
	await updateAirlineInfo(airlineId)
	loadAndWatchAirlineNotes(airlineId)
}

function selectHeadquarters(airportId) {
    if (!activeAirline.headquarterAirport) {
        $.ajax({
                type: 'GET',
                url: "/airlines/" + activeAirline.id + "/profiles?airportId=" + airportId ,
                contentType: 'application/json; charset=utf-8',
                dataType: 'json',
                success: function(profiles) {
                    updateProfiles(profiles)
                    showWorldMap()
                },
                error: function(jqXHR, textStatus, errorThrown) {
                        console.log(JSON.stringify(jqXHR));
                        console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
                }
            });
    }
}

function buildBase(isHeadquarter, scale) {
	scale = scale || 1
	var url = "/airlines/" + activeAirline.id + "/bases/" + activeAirportId
	var baseData = {
			"airportId" : parseInt(activeAirportId),
			"airlineId" : activeAirline.id,
			"scale" : scale,
			"headquarter" : isHeadquarter}
	$.ajax({
		type: 'PUT',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    data: JSON.stringify(baseData),
	    dataType: 'json',
	    success: function() {
            updateAirlineInfo(activeAirline.id)
	    	showWorldMap()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function deleteBase() {
	var url = "/airlines/" + activeAirline.id + "/bases/" + activeAirportId

	$.ajax({
		type: 'DELETE',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function() {
	    	updateAirlineInfo(activeAirline.id)
	    	showWorldMap()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function downgradeBase() {
	var url = "/airlines/" + activeAirline.id + "/downgradeBase/" + activeAirportId

	$.ajax({
		type: 'POST',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    success: function() {
	    	updateAirlineInfo(activeAirline.id)
	    	showWorldMap()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

//remove and re-add all the links
function updateLinksInfo() {
    if (window.AirlineMap) {
        AirlineMap.clearAllPaths()
    }

    if (activeAirline) {
        $.ajax({
            type: 'GET',
            url: "/airlines/" + activeAirline.id + "/links-details",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function (data) {
                if (window.AirlineMap) {
                    if (data.type === 'FeatureCollection' && data.features) {
                        AirlineMap.setRoutesFromGeoJSON(data);
                    } else {
                        $.each(data, function (key, link) { AirlineMap.drawFlightPath(link) });
                    }
                    AirlineMap.updateAirportMarkers(activeAirline);
                }
                updateLoadedLinks(data);
            },
            error: function (jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }
}

//refresh links without removal/addition
function refreshLinks(forceRedraw) {
    $.ajax({
        type: 'GET',
        url: "/airlines/" + activeAirline.id + "/links-details",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (data) {
            if (data.type === 'FeatureCollection' && data.features) {
                AirlineMap.setRoutesFromGeoJSON(data);
            } else {
                $.each(data, function (key, link) { refreshFlightPath(link, forceRedraw) });
            }
            updateLoadedLinks(data);
        },
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

let currentLinkConsumptions = null
let currentLinkRouteOps = []
let quickPriceDefaults = {}

function computePriceFloor(paxClass, defaultPrice, otherLinks) {
    let lowestPrice = defaultPrice
    otherLinks.forEach(link => {
        if (link.price && link.price[paxClass] < lowestPrice) {
            lowestPrice = link.price[paxClass]
        }
    })
    return Math.ceil(lowestPrice * 0.65)
}

function saveCurrentLinkPrice() {
    var airlineId = activeAirline.id
    var linkId = $('#actionLinkId').val()
    var hasCompetitor = currentLinkRouteOps.length > 1
    var prices = {}

    $('.quick-price-input').each(function() {
        var paxClass = $(this).data('class')
        var val = parseInt($(this).val())
        if (isNaN(val) || val < 1) {
            $(this).val(quickPriceDefaults[paxClass])
            val = quickPriceDefaults[paxClass]
        } else {
            var floor = computePriceFloor(paxClass, quickPriceDefaults[paxClass], currentLinkRouteOps)
            if (hasCompetitor && val < floor) {
                $(this).val(floor)
                val = floor
            } else {
                $(this).val(Math.floor(val))
                val = Math.floor(val)
            }
        }
        prices[paxClass] = val
    })

    $.ajax({
        type: 'PATCH',
        url: '/airlines/' + airlineId + '/links/' + linkId + '/price',
        data: JSON.stringify(prices),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            quickPriceDefaults = {economy: result.economy, business: result.business, first: result.first}
            $('#quickPriceSave').hide()
            $('#quickPriceMessage').html('<span style="color:#4caf50">Prices updated</span>')
        },
        error: function() {
            $('#quickPriceMessage').html('<span style="color:#f44336">Error: could not update prices</span>')
        }
    })
}

function refreshLinkDetails(linkId) {
	var airlineId = activeAirline.id

	$("#linkCompetitons .data-row").remove()
	$("#actionLinkId").val(linkId)

	currentLinkConsumptions = null // Clear stale chart data immediately so old graphs don't persist
	plotLinkCharts([], $("#linkDetails #switchMonth").is(':checked') ? plotUnitEnum.MONTH : plotUnitEnum.QUARTER)

	const notesLink = document.getElementById('linkNotes');
    const linkNote = notes.linkNotes.find(note => note.id === linkId);
    notesLink.value = linkNote?.note || '';

	//load link
	$.ajax({
		type: 'GET',
		url: "/airlines/" + airlineId + "/links/" + linkId,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(link) {
	    	if (linkId !== selectedLink) return //stale response - user has selected a different link
	    	$("#linkFromAirport").attr("href", "/airport/" + link.fromAirportCode).html(getCountryFlagImg(link.fromCountryCode, "15px") + link.fromAirportCity + "<i class='pl-1 iata'>" + link.fromAirportCode + "</i>")
	    	$("#linkToAirport").attr("href", "/airport/" + link.toAirportCode).html(getCountryFlagImg(link.toCountryCode, "15px") + link.toAirportCity + "<i class='pl-1 iata'>" + link.toAirportCode + "</i>")
	    	$("#linkFlightCode").text(link.flightCode)
	    	if (link.assignedAirplanes && link.assignedAirplanes.length > 0) {
	    		$('#linkAirplaneModel').text(link.assignedAirplanes[0].airplane.name)
	    	} else {
	    		$('#linkAirplaneModel').text("")
	    	}
	    	quickPriceDefaults = {economy: link.price.economy, business: link.price.business, first: link.price.first}
	    	$('#quickPriceEconomy').val(link.price.economy)
	    	$('#quickPriceBusiness').val(link.price.business)
	    	$('#quickPriceFirst').val(link.price.first)
	    	$('#quickPriceSave').hide()
	    	$('#quickPriceMessage').text('')
	    	$('.quick-price-input').off('.quickPrice').on('input.quickPrice', function() {
	    	    $('#quickPriceSave').show()
	    	    $('#quickPriceMessage').text('')
	    	})
	    	$("#linkDistance").text(link.distance + " km")
	    	$("#linkDuration").text(toHoursAndMinutes(link.duration).hours + "hr " + toHoursAndMinutes(link.duration).minutes + "min ")
	    	$("#linkQuality").html(getGradeStarsImgs(Math.min(10, Math.round(link.computedQuality / 10))) + " (" + link.computedQuality + ")")
	    	$("#linkCurrentCapacity").html(toLinkClassDiv(link.capacity))
	    	if (link.future) {
	    	    $("#linkCurrentDetails .future .capacity").html(toLinkClassDiv(link.future.capacity))
	    	    $("#linkCurrentDetails .future").show()
	    	} else {
	    	    $("#linkCurrentDetails .future").hide()
	    	}
	    	$("#linkCurrentDetails").show()
            $("#editLinkButton").attr("onclick", `planLink(${link.fromAirportId}, ${link.toAirportId})`);
	    	$("#linkToAirportId").val(link.toAirportId)
	    	$("#linkFromAirportId").val(link.fromAirportId)

	    	//load competition
	    	$.ajax({
	    		type: 'GET',
	    		url: "/airports/" + link.fromAirportId + "/to/" + link.toAirportId,
	    	    contentType: 'application/json; charset=utf-8',
	    	    dataType: 'json',
	    	    success: function(linkConsumptions) {
	    	    	currentLinkRouteOps = linkConsumptions
	    	    	$("#linkCompetitons .data-row").remove()
	    	    	$.each(linkConsumptions, function(index, linkConsumption) {
    	    			var row = $("<div class='table-row data-row clickable' onclick='navigateTo(\"/rivals/" + linkConsumption.id + "\")'><div style='display: table-cell;'>" + linkConsumption.airlineName
                                  		    	    				+ "</div><div style='display: table-cell;'>" + toLinkClassValueString(linkConsumption.price, "$")
                                  		    	    				+ "</div><div style='display: table-cell; text-align: right;'>" + toLinkClassValueString(linkConsumption.capacity)
                                  		    	    				+ "</div><div style='display: table-cell; text-align: right;'>" + linkConsumption.quality
                                  		    	    				+ "</div><div style='display: table-cell; text-align: right;'>" + linkConsumption.frequency + "</div></div>")
                        if (linkConsumption.airlineId == airlineId) {
                            $("#linkCompetitons .table-header").after(row) //self is always on top
                        } else {
                            $("#linkCompetitons").append(row)
                        }

	    	    	})

	    	    	if ($("#linkCompetitons .data-row").length == 0) {
	    	    		$("#linkCompetitons").append("<div class='table-row data-row'><div style='display: table-cell;'>-</div><div style='display: table-cell;'>-</div><div style='display: table-cell;'>-</div><div style='display: table-cell;'>-</div><div style='display: table-cell;'>-</div></div>")
	    	    	}
	    	    	$("#linkCompetitons").show()

	    	    	assignAirlineColors(linkConsumptions, "airlineId")
	    	    	plotPie(linkConsumptions, null, "linkCompetitionsPie", "airlineName", "soldSeats")
	    	    },
	            error: function(jqXHR, textStatus, errorThrown) {
	    	            console.log(JSON.stringify(jqXHR));
	    	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    	    }
	    	});

	    	$('#linkEventModal').data('link', link)

	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});

    var plotUnit = $("#linkDetails #switchMonth").is(':checked') ? plotUnitEnum.MONTH : plotUnitEnum.QUARTER
	var cycleCount = plotUnitEnum.YEAR.maxWeek

	//load history
	$.ajax({
		type: 'GET',
		url: "/airlines/" + airlineId + "/link-consumptions/" + linkId + "?cycleCount=" + cycleCount,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(linkConsumptions) {
	    	if (linkId !== selectedLink) return //stale response - user has selected a different link
	    	if (jQuery.isEmptyObject(linkConsumptions)) {
	    	    currentLinkConsumptions = null
	    		$("#linkHistoryPrice").text("-")
		    	$("#linkHistoryCapacity").text("-")
		    	$("#linkLoadFactor").text("-")
		    	$("#linkProfit").text("-")
		    	$("#linkRevenue").text("-")
		    	$("#linkFuelCost").text("-")
		    	$("#linkFuelTax").text("-")
		    	$("#linkCrewCost").text("-")
		    	$("#linkAirportFees").text("-")
		    	$("#linkDepreciation").text("-")
		    	$("#linkCompensation").text("-")
		    	$("#linkLoungeCost").text("-")
		    	$("#linkServiceSupplies").text("-")
		    	$("#linkMaintenance").text("-")
		    	$("#linkOtherCosts").text("-")
		    	$("#linkDelays").text("-")
		    	$("#linkCancellations").text("-")

		    	disableButton($("#linkDetails .button.viewLinkHistory"), "Passenger Map is not yet available for this route - please wait for the simulation (time estimation on top left of the screen).")
		    	disableButton($("#linkDetails .button.viewLinkComposition"), "Passenger Survey is not yet available for this route - please wait for the simulation (time estimation on top left of the screen).")
		    	disableButton($("#linkDetails .button.viewLinkEvent"), "Event history is not yet available for this route - please wait for the simulation (time estimation on top left of the screen).")
	    	} else {
	    		currentLinkConsumptions = linkConsumptions
	    		updateLinkHistory(linkConsumptions, true)
		    	enableButton($("#linkDetails .button.viewLinkHistory"))
		    	enableButton($("#linkDetails .button.viewLinkComposition"))
		    	enableButton($("#linkDetails .button.viewLinkEvent"))
	    	}
            plotLinkCharts(linkConsumptions, plotUnit)
            $('#linkEventChart').data('linkConsumptions', linkConsumptions)
	    	$("#linkHistoryDetails").show()
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
	setActiveDiv($("#linkDetails"))
	hideActiveDiv($("#extendedPanel #airplaneModelDetails"))
	showMapOverlay($('#sidePanel'));
}

/**
 * Is click event. Uses currentLinkConsumptions global variable
 *
 * @param {Boolean} useAverage
 * @returns
 */
function updateLinkHistory() {
  if (!currentLinkConsumptions || currentLinkConsumptions.length === 0) return;
  var useAverage = document.getElementById("switchlinkCurrentDetailsYear").checked

  let data;
  let startCycle, endCycle;

  if (useAverage) {
    // Use up to the last 48 weeks
    const consumptions = currentLinkConsumptions.slice(0, 48);
    startCycle = consumptions[consumptions.length - 1].cycle;
    endCycle = consumptions[0].cycle;

    // Helper to average numeric fields
    const avg = (arr, key) => {
        const reduction = arr.reduce((sum, c) => sum + (c[key] || 0), 0) / arr.length;
        return Math.round(reduction);
    }


    // Average nested objects (capacity, soldSeats)
    const avgNested = (arr, parentKey) => {
      const keys = Object.keys(arr[0][parentKey]);
      const result = {};
      keys.forEach((k) => {
        const reduction = arr.reduce((sum, c) => sum + (c[parentKey][k] || 0), 0) / arr.length;
        result[k] = Math.round(reduction);
      });
      return result;
    };

    data = {
      price: avg(consumptions, "price"),
      capacity: avgNested(consumptions, "capacity"),
      soldSeats: avgNested(consumptions, "soldSeats"),
      profit: avg(consumptions, "profit"),
      revenue: avg(consumptions, "revenue"),
      fuelCost: avg(consumptions, "fuelCost"),
      fuelTax: avg(consumptions, "fuelTax"),
      crewCost: avg(consumptions, "crewCost"),
      airportFees: avg(consumptions, "airportFees"),
      depreciation: avg(consumptions, "depreciation"),
      delayCompensation: avg(consumptions, "delayCompensation"),
      loungeCost: avg(consumptions, "loungeCost"),
      inflightCost: avg(consumptions, "inflightCost"),
      maintenanceCost: avg(consumptions, "maintenanceCost"),
      minorDelayCount: avg(consumptions, "minorDelayCount"),
      majorDelayCount: avg(consumptions, "majorDelayCount"),
      cancellationCount: avg(consumptions, "cancellationCount"),
    };
  } else {
    data = currentLinkConsumptions[0];
    startCycle = endCycle = data.cycle;
  }

  $("#linkHistoryPrice").text(toLinkClassValueString(data.price, "$"));
  $("#linkHistoryCapacity").text(toLinkClassValueString(data.capacity));

  const loadFactor = {
    economy:
      data.capacity.economy > 0
        ? parseInt((data.soldSeats.economy / data.capacity.economy) * 100)
        : "-",
    business:
      data.capacity.business > 0
        ? parseInt((data.soldSeats.business / data.capacity.business) * 100)
        : "-",
    first:
      data.capacity.first > 0
        ? parseInt((data.soldSeats.first / data.capacity.first) * 100)
        : "-",
  };

  $("#linkLoadFactor").text(toLinkClassValueString(loadFactor, "", "%"));
  $("#linkProfit").text("$" + commaSeparateNumber(data.profit));
  $("#linkRevenue").text("$" + commaSeparateNumber(data.revenue));
  $("#linkFuelCost").text("$" + commaSeparateNumber(data.fuelCost));
  $("#linkFuelTax").text("$" + commaSeparateNumber(data.fuelTax));
  $("#linkCrewCost").text("$" + commaSeparateNumber(data.crewCost));
  $("#linkAirportFees").text("$" + commaSeparateNumber(data.airportFees));
  $("#linkDepreciation").text("$" + commaSeparateNumber(data.depreciation));
  $("#linkCompensation").text("$" + commaSeparateNumber(data.delayCompensation));
  $("#linkLoungeCost").text("$" + commaSeparateNumber(data.loungeCost));
  $("#linkServiceSupplies").text("$" + commaSeparateNumber(data.inflightCost));
  $("#linkMaintenance").text("$" + commaSeparateNumber(data.maintenanceCost));

  if (data.minorDelayCount == 0 && data.majorDelayCount == 0) {
    $("#linkDelays").removeClass("warning").text("-");
  } else {
    $("#linkDelays")
      .addClass("warning")
      .text(
        Math.round(data.minorDelayCount) +
          " minor " +
          Math.round(data.majorDelayCount) +
          " major"
      );
  }

  if (data.cancellationCount == 0) {
    $("#linkCancellations").removeClass("warning").text("-");
  } else {
    $("#linkCancellations")
      .addClass("warning")
      .text(Math.round(data.cancellationCount));
  }

  if (startCycle === endCycle) {
    $("#linkHistoryDate").text(getGameDate(startCycle, "WEEKLY", true) + ":");
  } else {
    $("#linkHistoryDate").text(
        getGameDate(endCycle, "YEAR", true) + " Average:"
    );
  }
}

function plotLinkCharts(linkConsumptions, plotUnit) {
    plotLinkProfit(linkConsumptions, "linkProfitChart", plotUnit)
	plotLinkConsumption(linkConsumptions, "linkRidershipChart", "linkRevenueChart", "linkPriceChart", plotUnit)
}

function refreshLinkCharts() {
    var plotUnit = $("#linkDetails #switchMonth").is(':checked') ? plotUnitEnum.MONTH : plotUnitEnum.QUARTER
	plotLinkCharts(currentLinkConsumptions, plotUnit)
}

function fadeOutMarker(marker, animationInterval) {
    var opacity = 1.0
    var animation = window.setInterval(function () {
        if (opacity <= 0) {
            marker.setMap(null)
            marker.setOpacity(1)
            window.clearInterval(animation)
        } else {
            marker.setOpacity(opacity)
            opacity -= 0.1
        }
    }, animationInterval)
}


function planToAirport(toAirportId, toAirportName) {
	$('#planLinkToAirportId').val(toAirportId)

	if (!$('#planLinkFromAirportId').val()) { //set the HQ by default for now
		$('#planLinkFromAirportId').val(activeAirline.headquarterAirport.airportId)
	}
	if ($('#planLinkFromAirportId').val() && $('#planLinkToAirportId').val()) {
		planLink($('#planLinkFromAirportId').val(), $('#planLinkToAirportId').val())
	}
}

//i.e. plan route
function planLink(fromAirport, toAirport, isRefresh) {
    checkTutorial("planLink")
	var airlineId = activeAirline.id

	$("#planLinkFromAirportId").val(fromAirport)
	$("#planLinkToAirportId").val(toAirport)
    setActiveDiv($('#planLinkDetails'))
    $('#planLinkDetails .warning').hide()

    var loadPlanLink = function() {
        var url = "/airlines/" + airlineId + "/plan-link"
        $.ajax({
            type: 'POST',
            url: url,
            data: { 'airlineId' : parseInt(airlineId), 'fromAirportId': parseInt(fromAirport), 'toAirportId' : parseInt(toAirport)} ,
            dataType: 'json',
            success: function(linkInfo) {
                updatePlanLinkInfo(linkInfo, isRefresh)
                if (!isRefresh) {
                    showMapOverlay($('#sidePanel'));
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

    if (!isRefresh) {
        $('#sidePanel').fadeOut(200, loadPlanLink)
    } else {
        loadPlanLink()
    }


}

var planLinkInfo = null
var planLinkInfoByModel = {}
var existingLink

function updatePlanLinkInfo(linkInfo, isRefresh) {
	$('#planLinkFromAirportName').attr("href", `/airport/${linkInfo.fromAirportCode}`).html(getCountryFlagImg(linkInfo.fromCountryCode) + linkInfo.fromAirportCity + "<i class='pl-2 iata'>" + linkInfo.fromAirportCode + "</i>")
	planLinkInfo = linkInfo

	if (activeAirline.baseAirports.length > 1) { //only allow changing from airport if this is a new link and there are more than 1 base
		$('#planLinkFromAirportEditIcon').show()
		//fill the from list
		$('#planLinkFromAirportSelect').empty()
		$.each(activeAirline.baseAirports, function(index, base) {
			var airportId = base.airportId
			var cityName = base.city
			var airportCode = base.airportCode
			var option = $("<option></option>").attr("value", airportId).text(getAirportText(cityName, airportCode))

			if ($('#planLinkFromAirportId').val() == airportId) {
				option.prop("selected", true)
			}
			option.appendTo($("#planLinkFromAirportSelect"))
		});
	} else {
		$('#planLinkFromAirportEditIcon').hide()
	}
	$("#planLinkFromAirportSelect").hide() //do not show the list yet

	$('#planLinkToAirportName').attr("href", `/airport/${linkInfo.toAirportCode}`).html(getCountryFlagImg(linkInfo.toCountryCode) + linkInfo.toAirportCity + "<i class='pl-2 iata'>" + linkInfo.toAirportCode + "</i>")
    $('.planToIata').text(linkInfo.toAirportCode)
    $('.planFromIata').text(linkInfo.fromAirportCode)
	$('#planLinkMutualRelationship').html(getCountryFlagImg(linkInfo.fromCountryCode) + " ⇄ " + getCountryFlagImg(linkInfo.toCountryCode) + getCountryRelationshipDescription(linkInfo.mutualRelationship))
	$('#planLinkAffinity').text(linkInfo.affinity)

	var relationship = linkInfo.toCountryRelationship
    var relationshipSpan = getAirlineRelationshipDescriptionSpan(relationship.total)
    $("#planLinkToCountryRelationship .total").html(relationshipSpan)

    var $relationshipDetailsIcon = $("#planLinkToCountryRelationship .detailsIcon")
    $relationshipDetailsIcon.data("countryCode", linkInfo.toCountryCode)
    $relationshipDetailsIcon.show()

    var cc = linkInfo.toCountryCode
    if (!loadedCountriesByCode[cc]) loadedCountriesByCode[cc] = {}
    loadedCountriesByCode[cc].countryRelationship = relationship
    loadedCountriesByCode[cc].CountryTitle = linkInfo.toCountryTitle

    var title = linkInfo.toCountryTitle
    updateAirlineTitle(title, $("#planLinkToCountryTitle img.airlineTitleIcon"), $("#planLinkToCountryTitle .airlineTitle"))

	$('#planLinkDistance').html(linkInfo.distance + " km")
	$('.planLinkFlightType').html(linkInfo.flightType)

    var $breakdown = $("#planLinkDetails .directDemandBreakdown")
    $breakdown.find(".fromAirport .airportLabel").empty()
    $breakdown.find(".fromAirport .airportLabel").append(getAirportSpan({ "iata" : linkInfo.fromAirportCode, "countryCode" : linkInfo.fromCountryCode, "city" : linkInfo.fromAirportCity}))
    $breakdown.find(".toAirport .airportLabel").empty()
    $breakdown.find(".toAirport .airportLabel").append(getAirportSpan({ "iata" : linkInfo.toAirportCode, "countryCode" : linkInfo.toCountryCode, "city" : linkInfo.toAirportCity}))

    // Sum up all preferences for each passenger type for "from" direction
    let fromTravelerTotal = sumPreferencesByType(linkInfo.fromDemandDetails, "Traveler")
    let fromBusinessTotal = sumPreferencesByType(linkInfo.fromDemandDetails, "Business")
    let fromTouristTotal = sumPreferencesByType(linkInfo.fromDemandDetails, "Tourist")
    $breakdown.find(".fromAirport .travelerDemand").text(toLinkClassValueString(fromTravelerTotal))
    $breakdown.find(".fromAirport .businessDemand").text(toLinkClassValueString(fromBusinessTotal))
    $breakdown.find(".fromAirport .touristDemand").text(toLinkClassValueString(fromTouristTotal))

    // Sum up all preferences for each passenger type for "to" direction
    let toTravelerTotal = sumPreferencesByType(linkInfo.toDemandDetails, "Traveler")
    let toBusinessTotal = sumPreferencesByType(linkInfo.toDemandDetails, "Business")
    let toTouristTotal = sumPreferencesByType(linkInfo.toDemandDetails, "Tourist")

    $breakdown.find(".toAirport .travelerDemand").text(toLinkClassValueString(toTravelerTotal))
    $breakdown.find(".toAirport .businessDemand").text(toLinkClassValueString(toBusinessTotal))
    $breakdown.find(".toAirport .touristDemand").text(toLinkClassValueString(toTouristTotal))


	$("#planLinkCompetitors .data-row").remove()

	linkInfo.otherLinks.sort(function(a, b) {
        return b.capacity.total - a.capacity.total;
    });
	$.each(linkInfo.otherLinks, function(index, linkConsumption) {
        let loadFactorPercentage = Math.round(linkConsumption.soldSeats * 100 / linkConsumption.capacity.total)
        $("#planLinkCompetitors").append("<div class='table-row data-row'><div style='display: table-cell;'>" + getAirlineLogoImg(linkConsumption.airlineId) + getAirlineLabelSpan(linkConsumption.airlineId, linkConsumption.airlineName)
                                       + "</div><div style='display: table-cell;'>" + toLinkClassValueString(linkConsumption.price, "$")
                                       + "</div><div style='display: table-cell; text-align:right;'>" + toLinkClassValueString(linkConsumption.capacity)
                                       + "</div><div style='display: table-cell; text-align:right;'>" + linkConsumption.frequency
                                       + "</div><div style='display: table-cell; text-align:right;'>" + linkConsumption.quality
                                       + "</div><div style='display: table-cell; text-align:right;'>" + loadFactorPercentage + "</div></div>")
	})

	if ($("#planLinkCompetitors .data-row").length < 6) { //then additional info
	    linkInfo.otherViaLocalTransitLinks.sort(function(a, b) {
            return b.capacity.total - a.capacity.total;
        });
	    $.each(linkInfo.otherViaLocalTransitLinks, function(index, linkConsumption) { //reachable by 1 local transit
            let loadFactorPercentage = Math.round(linkConsumption.soldSeats * 100 / linkConsumption.capacity.total)
            var $row = $("<div class='table-row data-row' style='opacity: 60%'><div style='display: table-cell;'>" + getAirlineSpan(linkConsumption.airlineId, linkConsumption.airlineName)
                            + "</div><div style='display: table-cell;'>" + toLinkClassValueString(linkConsumption.price, "$")
                                       + "</div><div style='display: table-cell; text-align:right;'>" + toLinkClassValueString(linkConsumption.capacity)
                                       + "</div><div style='display: table-cell; text-align:right;'>" + linkConsumption.frequency
                                       + "</div><div style='display: table-cell; text-align:right;'>" + linkConsumption.quality
                                       + "</div><div style='display: table-cell; text-align:right;'>" + loadFactorPercentage + "</div></div>")
            let phrases = []
            if (linkConsumption.altFrom) {
                phrases.push("Depart from " + linkConsumption.altFrom)
            }
            if (linkConsumption.altTo) {
                phrases.push("Arrive at " + linkConsumption.altTo)
            }
            $row.attr('title', phrases.join('; '))
            $("#planLinkCompetitors").append($row)
		})
	}

	let averageLoadFactor = {economy: "-", business: "-", first: "-"}
    if(currentLinkConsumptions !== null){
        const lastLinkConsumptions = currentLinkConsumptions.slice(0, 10)
        averageLoadFactor = getLoadFactorsFor({
            soldSeats: {
                economy: averageFromSubKey(lastLinkConsumptions, "soldSeats", "economy"),
                business: averageFromSubKey(lastLinkConsumptions, "soldSeats", "business"),
                first: averageFromSubKey(lastLinkConsumptions, "soldSeats", "first"),
            },
            capacity: {
                economy: averageFromSubKey(lastLinkConsumptions, "capacity", "economy"),
                business: averageFromSubKey(lastLinkConsumptions, "capacity", "business"),
                first: averageFromSubKey(lastLinkConsumptions, "capacity", "first"),
            },
        });
    }
    $("#planLFEconomy").text(averageLoadFactor.economy+"%")
    $("#planLFBusiness").text(averageLoadFactor.business+"%")
    $("#planLFFirst").text(averageLoadFactor.first+"%")

	if ($("#planLinkCompetitors .data-row").length == 0) {
		$("#planLinkCompetitors").append("<div class='table-row data-row'><div style='display: table-cell;'>-</div><div style='display: table-cell;'>-</div><div style='display: table-cell;'>-</div><div style='display: table-cell;'>-</div><div style='display: table-cell;'>-</div></div>")
	}

    document.querySelector("#planLinkQuality").innerHTML = getGradeStarsImgs(Math.min(10, Math.round(linkInfo.quality / 10)), 12)

	const fromQuality = document.getElementById("planLinkExpectedToQuality")
	fromQuality.getElementsByClassName("firstQuality")[0].innerHTML = getGradeStarsImgs(Math.round(linkInfo.toExpectedQuality.F / 10), 12)
	fromQuality.getElementsByClassName("businessQuality")[0].innerHTML = getGradeStarsImgs(Math.round(linkInfo.toExpectedQuality.J / 10), 12)
	fromQuality.getElementsByClassName("economyQuality")[0].innerHTML = getGradeStarsImgs(Math.round(linkInfo.toExpectedQuality.Y / 10), 12)
	fromQuality.getElementsByClassName("discountQuality")[0].innerHTML = getGradeStarsImgs(Math.round(linkInfo.toExpectedQuality.D / 10), 12)

	const toQuality = document.getElementById("planLinkExpectedFromQuality")
    toQuality.getElementsByClassName("firstQuality")[0].innerHTML = getGradeStarsImgs(Math.round(linkInfo.fromExpectedQuality.F / 10), 12)
    toQuality.getElementsByClassName("businessQuality")[0].innerHTML = getGradeStarsImgs(Math.round(linkInfo.fromExpectedQuality.J / 10), 12)
    toQuality.getElementsByClassName("economyQuality")[0].innerHTML = getGradeStarsImgs(Math.round(linkInfo.fromExpectedQuality.Y / 10), 12)
    toQuality.getElementsByClassName("discountQuality")[0].innerHTML = getGradeStarsImgs(Math.round(linkInfo.fromExpectedQuality.D / 10), 12)

	if (linkInfo.estimatedDifficulty > 0) {
        $('#planLinkEstimatedDifficultyRow').show()
        $('#planLinkEstimatedDifficulty').text(linkInfo.estimatedDifficulty.toFixed(2))
    } else {
        $('#planLinkEstimatedDifficulty').text('-')
    }

    if (linkInfo.cost !== 0) {
        $('#planLinkSetupCostRow').show()
        $('#planLinkSetupCost').text("$" + commaSeparateNumber(linkInfo.cost))
    } else {
        $('#planLinkSetupCostRow').hide()
    }

    if (tempPath) { //remove previous plan link if it exists
		removeTempPath()
	}
	//unhighlight the existing path if any
	if (selectedLink) {
		AirlineMap.unhighlightLink(selectedLink)
		if (!linkInfo.existingLink || linkInfo.existingLink.id != selectedLink) {
			AirlineMap.deselectLink()
		}
	}

	if (!linkInfo.existingLink || !flightPaths[linkInfo.existingLink.id]) { //new link or link show visible (other views)
		//create a temp path
		var tempLink = {fromLatitude : linkInfo.fromAirportLatitude, fromLongitude : linkInfo.fromAirportLongitude, toLatitude : linkInfo.toAirportLatitude, toLongitude : linkInfo.toAirportLongitude}
		//set the temp path
		tempPath = AirlineMap.drawFlightPath(tempLink, '#2658d3')
		AirlineMap.highlightPath(tempPath.path, false)
	} else {
		AirlineMap.highlightLink(linkInfo.existingLink.id, false)
	}

	var initialPrice = {}
	if (!linkInfo.existingLink) {
	    initialPrice.economy = linkInfo.suggestedPrice.TouristFrom.economy
	    initialPrice.business = linkInfo.suggestedPrice.TouristFrom.business
	    initialPrice.first = linkInfo.suggestedPrice.TouristFrom.first

		$('#addLinkButton').show()
		$('#deleteLinkButton').hide()
		$('#updateLinkButton').hide()
	} else {
	    initialPrice.economy = linkInfo.existingLink.price.economy
        initialPrice.business = linkInfo.existingLink.price.business
        initialPrice.first = linkInfo.existingLink.price.first
		$('#addLinkButton').hide()
		if (linkInfo.deleteRejection) {
			$('#deleteLinkButton').hide()
		} else {
			$('#deleteLinkButton').show()
			// console.log(linkInfo)
			if (linkInfo.deleteLinkRefund && linkInfo.deleteLinkRefund > 0) {
			    $('#deleteLinkButton').attr('onclick',`promptConfirm("Delete this route? You will receive ${linkInfo.deleteLinkRefund} action points.", deleteLink)`)
			} else {
			    $('#deleteLinkButton').attr('onclick',`promptConfirm("Delete this route?", deleteLink)`)
			}
		}
		$('#updateLinkButton').show()
	}
    const PRICE_INPUT_SELECTOR = '#planLinkEconomyPrice, #planLinkBusinessPrice, #planLinkFirstPrice';
    const INPUT_IDLE_MS = 400;

    const classToSelector = {
        economy: '#planLinkEconomyPrice',
        business: '#planLinkBusinessPrice',
        first: '#planLinkFirstPrice'
    };

    // Initialize values
    Object.entries(classToSelector).forEach(([paxClass, selector]) => {
        $(selector).val(initialPrice[paxClass]);
    });

    const hasCompetitor = linkInfo.otherLinks.length > 1;
    let inputIdleTimer = null;

    function normalizePriceInput($input) {
        const paxClass = $input.data('class');
        const defaultPrice = initialPrice[paxClass];
        const floorPrice = computePriceFloor(paxClass, defaultPrice, linkInfo.otherLinks);

        const raw = Number($input.val());
        let normalized = defaultPrice;

        if (!Number.isNaN(raw)) {
            if (raw < 1) {
                normalized = defaultPrice;
            } else if (hasCompetitor && raw < floorPrice) {
                normalized = floorPrice;
            } else {
                normalized = Math.floor(raw);
            }
        }

        $input.val(normalized);
    }

    function refreshPricingAndDemand() {
        updatePricePercentage();
        calculateDemand();
    }

    const $priceInputs = $(PRICE_INPUT_SELECTOR);

    // Remove old handlers and bind namespaced ones
    $priceInputs
        .off('.priceChange')
        .on('input.priceChange', function () {
            const $input = $(this);
            clearTimeout(inputIdleTimer);

            inputIdleTimer = setTimeout(() => {
                normalizePriceInput($input);
                refreshPricingAndDemand();
            }, INPUT_IDLE_MS);
        })
        .on('focusout.priceChange', function () {
            clearTimeout(inputIdleTimer);
            normalizePriceInput($(this));
            refreshPricingAndDemand();
        });

    //reset/display warnings
    $("#planLinkDetails .warningList").empty()
    if (linkInfo.warnings) {
        $.each(linkInfo.warnings, function(index, warning) {
            $("#planLinkDetails .warningList").append("<div class='warning'><img src='/assets/images/icons/exclamation-red-frame.png'>&nbsp;" + warning + "</div>")
        })
    }

    $('#planLinkDetails .titleCue').removeClass('glow')
    if (linkInfo.rejection) {
        document.getElementById('linkRejectionReason').textContent = linkInfo.rejection.description
        if (linkInfo.rejection.type === "TITLE_REQUIREMENT") {
            $('#planLinkDetails .titleCue').addClass('glow')
        }
        $('.linkRejection').show()
        $('#addLinkButton').hide()
        $('#updateLinkButton').hide()
        $('#deleteLinkButton').hide()
        $('#planLinkExtendedDetails').hide()
        $('#planLinkModelRow').hide()
        $('#extendedPanel').hide()
        updatePricePercentage();
        calculateDemand();
        return
    } else {
        $('.linkRejection').hide()
        $('#planLinkModelRow').show()
    }


	//populate airplane model drop down
	var explicitlySelectedModelId = $("#planLinkModelSelect").data('explicitId')
	$("#planLinkModelSelect").removeData('explicitId')

	//or if refresh, just use whatever selected previously
	if (isRefresh) {
	    explicitlySelectedModelId = $('#planLinkModelSelect').find(":selected").attr('value')
    }

	$("#planLinkModelSelect").children('option').remove()

	planLinkInfoByModel = {}

	//find which model is assigned to the existing link (if exist)
	var assignedModelId
	var selectedModelId

	if (explicitlySelectedModelId) { //if there was a explicitly selected model, for example from buying a new plane
		selectedModelId = explicitlySelectedModelId;
	}

	if (linkInfo.existingLink) {
		$.each(linkInfo.modelPlanLinkInfo, function(key, modelPlanLinkInfo) {
			if (modelPlanLinkInfo.isAssigned) { //higher precedence
				assignedModelId = modelPlanLinkInfo.modelId
				if (!selectedModelId) {
					selectedModelId = assignedModelId
				}
				return false
			}
		});
	}

	if (!selectedModelId) {
		$.each(linkInfo.modelPlanLinkInfo, function(key, modelPlanLinkInfo) {
			if (modelPlanLinkInfo.airplanes.length > 0) { //select the first one with available planes
				selectedModelId = modelPlanLinkInfo.modelId
				return false
			}
		})
	}

	$.each(linkInfo.modelPlanLinkInfo, function(key, modelPlanLinkInfo) {
		if (modelPlanLinkInfo.airplanes.length > 0) {
			modelPlanLinkInfo.owned = true
		} else {
			modelPlanLinkInfo.owned = false
		}
	})

	linkInfo.modelPlanLinkInfo = sortPreserveOrder(linkInfo.modelPlanLinkInfo, "capacity", true)
	linkInfo.modelPlanLinkInfo = sortPreserveOrder(linkInfo.modelPlanLinkInfo, "owned", false)

	if (!selectedModelId) { //nothing available, select the first one in the list
		if (linkInfo.modelPlanLinkInfo.length > 0) { //select the first one with available planes
			selectedModelId = linkInfo.modelPlanLinkInfo[0].modelId
		}
	}

	$.each(linkInfo.modelPlanLinkInfo, function(key, modelPlanLinkInfo) {
		var modelId = modelPlanLinkInfo.modelId
		var modelname = modelPlanLinkInfo.modelName

		var option = $("<option></option>").attr("value", modelId).text(modelname + " (" + modelPlanLinkInfo.maxFrequency + ")")
		if (modelPlanLinkInfo.airplanes.length > 0) {
		    option.addClass("highlight-text")
		}

		option.appendTo($("#planLinkModelSelect"))

		if (selectedModelId == modelId) {
			option.prop("selected", true)
			updateModelInfo(modelId)
		}

		planLinkInfoByModel[modelId] = modelPlanLinkInfo
	});

	if (linkInfo.modelPlanLinkInfo.length == 0) {
		$("#planLinkModelSelect").next($(".warning")).remove()
		$("#planLinkModelSelect").after("<span class='label warning'>No airplane model can fly to this destination</span>")
		$("#planLinkModelSelect").hide()

		hideActiveDiv($("#extendedPanel #airplaneModelDetails"))
	} else {
		$("#planLinkModelSelect").next($(".warning")).remove()
		$("#planLinkModelSelect").show()

		setActiveDiv($("#extendedPanel #airplaneModelDetails"))
	}

	updatePlanLinkInfoWithModelSelected(selectedModelId, assignedModelId, isRefresh)
	updatePricePercentage();
	calculateDemand();
	$("#planLinkDetails div.value").show()
}

function calculateDemand() {
    const totalDemand = {
      economy: 0,
      business: 0,
      first: 0,
    };
    const currentPrices = {
        economy: parseFloat($('#planLinkEconomyPrice').val()),
        business: parseFloat($('#planLinkBusinessPrice').val()),
        first: parseFloat($('#planLinkFirstPrice').val()),
    }

    const allDemandDetails = [...planLinkInfo.fromDemandDetails, ...planLinkInfo.toDemandDetails];

    allDemandDetails.forEach(demandEntry => {
        const linkClass = demandEntry.linkClass;
		const linkClassAdjusted = linkClass === "discountEconomy" ? "economy" : linkClass;
        const currentPrice = currentPrices[linkClassAdjusted];

        if (currentPrice <= demandEntry.price) {
            totalDemand[linkClassAdjusted] += demandEntry.count;
        }
    });

    $('#planLinkDirectDemand').text(toLinkClassValueString(totalDemand))
}

function resetPrice() {
	updatePrice(
	    newPercentage = {
                        economy: 1,
                        business: 1,
                        first: 1
                    }, "all")
}

function updatePrice(percentage, classType = "all") {
    const economyInput = $('#planLinkEconomyPrice');
    const businessInput = $('#planLinkBusinessPrice');
    const firstInput = $('#planLinkFirstPrice');

    if (classType === "economy" || classType === "all") {
        economyInput.val(Math.round(planLinkInfo.suggestedPrice.TouristFrom.economy * percentage.economy));
    }
    if (classType === "business" || classType === "all") {
        businessInput.val(Math.round(planLinkInfo.suggestedPrice.TouristFrom.business * percentage.business));
    }
    if (classType === "first" || classType === "all") {
        firstInput.val(Math.round(planLinkInfo.suggestedPrice.TouristFrom.first * percentage.first));
    }

    updatePricePercentage();
    calculateDemand();
}


function increasePrice(classType = "all") {
    if (classType === "all") {
        CLASSES.forEach((paxClass) => {
            changeClassPrice(paxClass, 0.05);
        });
    } else {
        changeClassPrice(classType, 0.05);
    }
}

function decreasePrice(classType = "all") {
    if (classType === "all") {
        CLASSES.forEach((paxClass) => {
            changeClassPrice(paxClass, -0.05);
        });
    } else {
        changeClassPrice(classType, -0.05);
    }
}

function changeClassPrice(paxClass, percent) {
    const currentPrice = document.getElementById(`planLink${capitalizeFirstLetter(paxClass)}Price`).value ?? 0;
    const defaultPrice = planLinkInfo.suggestedPrice.TouristFrom[paxClass];
    const hasCompetitor = planLinkInfo.otherLinks.length > 1;
    const currentPercentage = parseFloat(currentPrice || 0) * 20 / defaultPrice / 20;

    const priceFloor = computePriceFloor(paxClass, defaultPrice, planLinkInfo.otherLinks)

    const newPercentage = Math.max(0, currentPercentage + percent);
    if (hasCompetitor && defaultPrice * newPercentage < priceFloor) {
        updatePrice({ [paxClass]: priceFloor / defaultPrice }, paxClass);
    } else {
        updatePrice({ [paxClass]: newPercentage }, paxClass);
    }
}

function updatePricePercentage(){
    $('#planMarkupEconomy').text(($('#planLinkEconomyPrice').val()/planLinkInfo.suggestedPrice.TouristFrom.economy*100).toFixed(0)+"%")
    $('#planMarkupBusiness').text(($('#planLinkBusinessPrice').val()/planLinkInfo.suggestedPrice.TouristFrom.business*100).toFixed(0)+"%")
    $('#planMarkupFirst').text(($('#planLinkFirstPrice').val()/planLinkInfo.suggestedPrice.TouristFrom.first*100).toFixed(0)+"%")
}

function sumPreferencesByType(demandDetails, passengerType) {
    let totals = {
        "economy" : 0,
        "business" : 0,
        "first" : 0,
        "discountEconomy" : 0
    }

    demandDetails.forEach(detail => {
        if (detail.passengerType === passengerType) {
            totals[detail.linkClass] += detail.count
        }
    })

    return totals
}

function updateFrequencyBar(frequencyBar, valueContainer, airplane, currentFrequency) {
    var availableFrequency = Math.floor(airplane.availableFlightMinutes / planLinkInfoByModel[airplane.modelId].flightMinutesRequired)
    var maxFrequency = availableFrequency + currentFrequency
    if (currentFrequency == 0) { //set 1 as min
        valueContainer.val(1)
    }
    generateImageBar(frequencyBar.data("emptyIcon"), frequencyBar.data("fillIcon"), maxFrequency, frequencyBar, valueContainer, null, null, updateTotalValues)
}

function updatePlanLinkInfoWithModelSelected(newModelId, assignedModelId, isRefresh) {
    selectedModelId = newModelId //modify the global one
    selectedModel = loadedModelsById[newModelId]
	if (selectedModelId) {
		var thisModelPlanLinkInfo = planLinkInfoByModel[selectedModelId]

		$('#planLinkAirplaneSelect').data('badConditionThreshold', gameConstants.aircraft.conditionBad)

		thisModelPlanLinkInfo.airplanes.sort(function(a, b) {
		    var result = b.frequency - a.frequency
		    if (result != 0) {
		        if (b.frequency == 0 || a.frequency == 0) { //if either one is not assigned to this route at all, then return result ie also higher precedence to compare if airplane is assigned
		            return result
		        }
		    }

		    return a.airplane.condition - b.airplane.condition //otherwise: both assigned or both not assigned, then return lowest condition ones first
		})

        $('#planLinkAirplaneSelect').empty()

		$.each(thisModelPlanLinkInfo.airplanes, function(key, airplaneEntry) {
//			var option = $("<option></option>").attr("value", airplane.airplaneId).text("#" + airplane.airplaneId)
//			option.appendTo($("#planLinkAirplaneSelect"))

			//check existing UI changes if just a refresh
			if (isRefresh) {
			    var $existingAirplaneRow = $("#planLinkDetails .frequencyDetail .airplaneRow[data-airplaneId='" + airplaneEntry.airplane.id + "']")
			    //UI values merge into the airplane/frequency info as we want to preserve previously UI change on refresh
    			mergeAirplaneEntry(airplaneEntry, $existingAirplaneRow)
			}

			var airplane = airplaneEntry.airplane
			airplane.isAssigned = airplaneEntry.frequency >  0
			var div =  $('<div class="clickable airplaneButton" onclick="toggleAssignedAirplane(this)" style="float: left;"></div>')
			div.append(getAssignedAirplaneIcon(airplane))
			div.data('airplane', airplane)
			div.data('existingFrequency', airplaneEntry.frequency)

			$('#planLinkAirplaneSelect').append(div)
		})
		if (thisModelPlanLinkInfo.airplanes.length == 0) {
		    $('#planLinkDetails .noAirplaneHelp').show()
		} else {
		    $('#planLinkDetails .noAirplaneHelp').hide()
		}
		toggleUtilizationRate($('#planLinkAirplaneSelect'), $('#planLinkExtendedDetails .toggleUtilizationRateBox'))
		toggleCondition($('#planLinkAirplaneSelect'), $('#planLinkExtendedDetails .toggleConditionBox'))


		$('#planLinkDuration').text(getDurationText(thisModelPlanLinkInfo.duration))

		if (!isRefresh) { //for refresh, do not reload the existing link, otherwise refresh on config change would show the new values in confirmation dialog etc
		    existingLink = planLinkInfo.existingLink
        }

		if (existingLink) {
			$("#planLinkServiceLevel").val(existingLink.rawQuality / 20)
		} else {
			$("#planLinkServiceLevel").val(1)
		}

		updateFrequencyDetail(thisModelPlanLinkInfo)

		var serviceLevelBar = $("#serviceLevelBar")
		generateImageBar(serviceLevelBar.data("emptyIcon"), serviceLevelBar.data("fillIcon"), 5, serviceLevelBar, $("#planLinkServiceLevel"))
		$("#planLinkExtendedDetails").show()
	} else {
		$("#planLinkExtendedDetails").hide()
	}
}
//Merge UI change (though temp) to the data loaded from api service
//This is used for refresh which we want to reload but keep temp changes from UI
function mergeAirplaneEntry(airplaneEntry, $airplaneRow) {
    var frequencyFromUi = $airplaneRow.length ? parseInt($airplaneRow.find(".frequency").val()) : 0
    var frequencyDelta = frequencyFromUi - airplaneEntry.frequency
    if (frequencyDelta != 0) { //then UI value is not the same as the original. Do adjustments
        airplaneEntry.frequency = frequencyFromUi
        var airplane = airplaneEntry.airplane
        airplane.availableFlightMinutes = airplane.availableFlightMinutes - (planLinkInfoByModel[airplane.modelId].flightMinutesRequired * frequencyDelta)
    }
}

function updateFrequencyDetail(info) {
    var airplaneEntries = info.airplanes
    $("#planLinkDetails .frequencyDetail .table-row").remove()

    var isEmpty = true
    $.each(airplaneEntries, function(index, airplaneEntry) {
        if (airplaneEntry.frequency > 0) { //only draw for those that are assigned to this link
            addAirplaneRow($("#planLinkDetails .frequencyDetail"), airplaneEntry.airplane, airplaneEntry.frequency)
            isEmpty = false
        }
    })
    if (isEmpty) {
        $("#planLinkDetails .frequencyDetail").append("<div class='table-row empty'><div class='cell'></div><div class='cell'>-</div><div class='cell'>-</div></div>")
    }

    updateTotalValues()
}



function addAirplaneRow(container, airplane, frequency) {
    var airplaneRow = $("<div class='table-row airplaneRow'></div>") //airplane bar contains - airplane icon, configuration, frequency
	let spaceMultipliers = {}
	gameConstants.linkClassValues.forEach(linkClass => {
		spaceMultipliers[linkClass.name] = linkClass.spaceMultiplier
	})

    var configurationDiv = $(`<div class='configuration' id='configuration-${airplane.id}' style="height:16px; width: 60px;"></div>`)
    var airplaneUpdateCallback = function(configurationDiv, airplaneId) {
        return function() {
            $.ajax({
                    type: 'GET',
                    url: "/airlines/" + activeAirline.id + "/airplanes/" + airplaneId,
                    contentType: 'application/json; charset=utf-8',
                    dataType: 'json',
                    success: function(result) {
                        var updatedAirplane = result
                        //should not redraw the whole airplaneRow as the unsaved frequency change will be reverted
                        plotSeatConfigurationBar(`configuration-${airplaneId}`, updatedAirplane.configuration, updatedAirplane.capacity, spaceMultipliers, true, "10px")
                        airplaneRow.data("airplane", updatedAirplane)
                        updateTotalValues()
                    },
                    error: function(jqXHR, textStatus, errorThrown) {
                            console.log(JSON.stringify(jqXHR));
                            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
                    }
                });
        }
    }

    var airplaneCellOuter = $('<div class="cell"></div>')
    var airplaneCell = $("<div style='display: flex; align-items: center;'></div>")
    airplaneCellOuter.append(airplaneCell)

    var onclickFunction = 'loadOwnedAirplaneDetails(' + airplane.id + ', null, $(this).data(\'airplaneUpdateCallback\'), true)'
    var airplaneInspectIcon = $('<div class="clickable-no-highlight px-1 py-05" onclick="' + onclickFunction + '"></div>')
    airplaneInspectIcon.data("airplaneUpdateCallback", airplaneUpdateCallback(configurationDiv, airplane.id))
    airplaneInspectIcon.append($('<img src="/assets/images/icons/airplane-magnifier.png" title="Inspect airplane #' + airplane.id + '">'))
    airplaneCell.append(airplaneInspectIcon)

    var airplaneRemovalIcon = $('<div class="clickable-no-highlight px-1 py-05" onclick="removeAirplaneFromLink(' + airplane.id + ')"></div>')
    airplaneRemovalIcon.append($('<img src="/assets/images/icons/airplane-minus.png" title="Unassign airplane #' + airplane.id + '">'))
    airplaneCell.append(airplaneRemovalIcon)

//    airplaneCell.append($("<span>#" + airplane.id + "</span>"))

    var sharedLinkCount = 0
    $.each(airplane.linkAssignments, function(linkId, frequency) {
        if (linkId != selectedLink) {
            sharedLinkCount ++
        }
    })
    if (sharedLinkCount > 0) {
        airplaneCell.append($('<img src="/assets/images/icons/information.svg" class="px-1 py-05 info svg" title="Shared with ' + sharedLinkCount + ' other route(s)">'))
    }

    if (!airplane.isReady) {
        airplaneCell.append($('<img src="/assets/images/icons/construction.png" title="Under construction">'))
    }

    airplaneRow.append(airplaneCellOuter)


    var configurationCell = $("<div class='cell'></div>")
    configurationCell.append(configurationDiv)
    airplaneRow.append(configurationCell)

    var frequencyBar = $("<div class='frequencyBar cell' data-empty-icon='/assets/images/icons/round-dot-grey.svg' data-fill-icon='/assets/images/icons/round-dot-green.svg'></div>")
    airplaneRow.append(frequencyBar)

    var valueContainer = $("<input class='frequency' type='hidden'>") //so changing the frequency bar would write the new value back to this ...is this necessary? since there's a callback function now...
    valueContainer.val(frequency)
    airplaneRow.append(valueContainer)
    airplaneRow.data("airplane", airplane)
    airplaneRow.attr('data-airplaneId', airplane.id) //for easier jquery selector

    container.append(airplaneRow)
    updateFrequencyBar(frequencyBar, valueContainer, airplane, frequency)
    plotSeatConfigurationBar(`configuration-${airplane.id}`, airplane.configuration, airplane.capacity, spaceMultipliers, true, "10px")
}

function addAirplaneToLink(airplane, frequency) {
    $("#planLinkDetails .frequencyDetail .table-row.empty").remove()
    addAirplaneRow($("#planLinkDetails .frequencyDetail"), airplane, frequency)
    updateTotalValues()
}

function removeAirplaneFromLink(airplaneId) {
    $("#planLinkDetails .frequencyDetail .airplaneRow").each(function(index, row){
        if ($(row).data("airplane").id == airplaneId) {
            $(row).remove()
        }
    })
    if ($("#planLinkDetails .frequencyDetail .airplaneRow").length == 0) {
        $("#planLinkDetails .frequencyDetail").append("<div class='table-row empty'><div class='cell'></div><div class='cell'>-</div><div class='cell'>-</div></div>")
    }

    updateTotalValues()

    //update the available airplane list
    $('#planLinkAirplaneSelect .airplaneButton').each(function(index, airplaneIcon){
      var airplane = $(airplaneIcon).data('airplane')
      if (airplane.id == airplaneId) {
        airplane.isAssigned = false
        $(airplaneIcon).find('img').replaceWith(getAssignedAirplaneImg(airplane))
      }
    })
}


//Get capacity based on current UI status
function getPlanLinkCapacity() {
    var currentFrequency = 0 //airplanes that are ready
    var currentCapacity = { "economy" : 0, "business" : 0, "first" : 0}

    var futureFrequency = 0 //airplanes that are ready + under construction
    var futureCapacity = { "economy" : 0, "business" : 0, "first" : 0}
    var hasUnderConstructionAirplanes = false

    $("#planLinkDetails .frequencyDetail .airplaneRow").each(function(index, airplaneRow) {
       frequency = parseInt($(airplaneRow).find(".frequency").val())
       configuration = $(airplaneRow).data("airplane").configuration

       futureFrequency += frequency
       futureCapacity.economy += configuration.economy * frequency
       futureCapacity.business += configuration.business * frequency
       futureCapacity.first += configuration.first * frequency

       if ($(airplaneRow).data("airplane").isReady) {
           currentFrequency += frequency
           currentCapacity.economy += configuration.economy * frequency
           currentCapacity.business += configuration.business * frequency
           currentCapacity.first += configuration.first * frequency
       } else {
            hasUnderConstructionAirplanes = true
       }
    })

    if (hasUnderConstructionAirplanes) {
        return { "current" : { "capacity" : currentCapacity, "frequency" : currentFrequency }, "future" : { "capacity" : futureCapacity, "frequency" : futureFrequency }}
    } else {
        return { "current" : { "capacity" : currentCapacity, "frequency" : currentFrequency }}
    }
}


// Update total frequency and capacity
function updateTotalValues() {
    var planCapacity = getPlanLinkCapacity()
    var currentCapacity = planCapacity.current.capacity
    var futureFrequency = planCapacity.future ? planCapacity.future.frequency : planCapacity.current.frequency
    var futureCapacity = planCapacity.future ? planCapacity.future.capacity : planCapacity.current.capacity

    $(".frequencyDetailTotal .total").text(futureFrequency)

    $('#planLinkCapacity').text(toLinkClassValueString(currentCapacity))
    if (planCapacity.future) {
        $("#planLinkDetails .future .capacity").text(toLinkClassValueString(futureCapacity))
        $("#planLinkDetails .future").show()
    } else {
        $("#planLinkDetails .future").hide()
    }


    $('#planLinkAirplaneSelect').removeClass('glow')
    $('.noAirplaneHelp').removeClass('glow')
    if (futureFrequency == 0) {
         disableButton($("#planLinkDetails .modifyLink"), "Must assign airplanes and frequency")

        var thisModelPlanLinkInfo = planLinkInfoByModel[selectedModelId]
        if (thisModelPlanLinkInfo.airplanes.length == 0) {
            $('.noAirplaneHelp').addClass('glow')
        } else {
            $('#planLinkAirplaneSelect').addClass('glow')
        }
    } else {
        enableButton($("#planLinkDetails .modifyLink"))
    }
    getLinkStaffingInfo()

    getLinkNegotiation(function(result) {
        if (result.negotiationInfo.finalRequirementValue > 0) {
            $('#planLinkEstimatedDifficultyRow').show()
            difficultyLookup = result.negotiationInfo.finalRequirementValue.toFixed(2)
            $('#planLinkEstimatedDifficulty').text(difficultyLookup)
        } else {
            $('#planLinkEstimatedDifficultyRow').hide()
            difficultyLookup = 0
            if (futureFrequency > 0) { //otherwise it might just overwrite estimated difficulty on new link
                $('#planLinkEstimatedDifficulty').text('-')
            }
        }
        if (result.negotiationInfo.actionPointRefund && result.negotiationInfo.actionPointRefund !== 0) {
            $('.planLinkDelegateRefundRow').show()
            $('.planLinkDelegateRefund').text(result.negotiationInfo.actionPointRefund)
        } else {
            $('.planLinkDelegateRefundRow').hide()
            $('.planLinkDelegateRefund').text('')
        }
    })
}


function getAssignedAirplaneIcon(airplane) {
	var badConditionThreshold = $('#planLinkAirplaneSelect').data('badConditionThreshold')
	return getAirplaneIcon(airplane, badConditionThreshold, airplane.isAssigned)
}

function getAssignedAirplaneImg(airplane) {
	var badConditionThreshold = $('#planLinkAirplaneSelect').data('badConditionThreshold')
	return getAirplaneIconImg(airplane, badConditionThreshold, airplane.isAssigned)
}


function toggleAssignedAirplane(iconSpan) {
	var airplane = $(iconSpan).data('airplane')
	var existingFrequency =  $(iconSpan).data('existingFrequency')
	if (airplane.isAssigned) {
		airplane.isAssigned = false
	} else {
		airplane.isAssigned = true
	}
	$(iconSpan).find('img').replaceWith(getAssignedAirplaneImg(airplane))

	if (airplane.isAssigned) { //add to the airplane frequency detail
        addAirplaneToLink(airplane, existingFrequency)
	} else { //remove from the airplane frequency detail
	    removeAirplaneFromLink(airplane.id)
	}
}

function getAssignedAirplaneFrequencies() {
	var assignedAirplaneFrequencies = {} //key airplaneId, value frequeuncy
	$('#planLinkDetails .frequencyDetail').find('.airplaneRow').each(function(index, airplaneRow) {
		var airplane = $(airplaneRow).data("airplane")
        assignedAirplaneFrequencies[airplane.id] = parseInt($(airplaneRow).find('.frequency').val())
	})

	return assignedAirplaneFrequencies
}

function createLink() {
	if ($("#planLinkFromAirportId").val() && $("#planLinkToAirportId").val()) {
		var airlineId = activeAirline.id
		var url = "/airlines/" + airlineId + "/links"
	    var linkData = {
			"fromAirportId" : parseInt($("#planLinkFromAirportId").val()),
			"toAirportId" : parseInt($("#planLinkToAirportId").val()),
			airplanes : getAssignedAirplaneFrequencies(),
			"airlineId" : airlineId,
			"price" : { "economy" : parseInt($("#planLinkEconomyPrice").val()), "business" : parseInt($("#planLinkBusinessPrice").val()), "first" : parseInt($("#planLinkFirstPrice").val())},
			"model" : parseInt($("#planLinkModelSelect").val()),
			"rawQuality" : parseInt($("#planLinkServiceLevel").val()) * 20,
			"assignedDelegates" : assignedActionPoints }
		$.ajax({
			type: 'PUT',
			url: url,
		    data: JSON.stringify(linkData),
		    contentType: 'application/json; charset=utf-8',
		    dataType: 'json',
		    success: function(savedLink) {
		    	var isSuccessful
		    	closeModal($('#linkConfirmationModal'))
                if (savedLink.negotiationResult) {
                    isSuccessful = savedLink.negotiationResult.isSuccessful
                    if (isSuccessful) {
                        negotiationAnimation(savedLink, refreshSavedLink, savedLink)
                    } else {
                        negotiationAnimation(savedLink, updateAirlineInfo, activeAirline.id)
                    }
                } else {
                    refreshSavedLink(savedLink)
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
}

function deleteLink() {
	var linkId = $('#actionLinkId').val()
	$.ajax({
		type: 'DELETE',
		url: "/airlines/" + activeAirline.id + "/links/" + linkId,
	    success: function() {
	    	$("#linkDetails").fadeOut(200)
	    	updateLinksInfo()
	    	updateAirlineInfo(activeAirline.id)
	    	AirlineMap.deselectLink()

	    	if ($('#linksCanvas').is(':visible')) { //reload the links table then
		    	loadLinksTable(null, true)
    		}
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function getLinkStaffingInfo() {
    var airlineId = activeAirline.id
    var url = "/airlines/" + airlineId + "/link-overtime-compensation"
    //console.log("selected " + $("#planLinkAirplaneSelect").val())
    var linkData = {
        "fromAirportId" : parseInt($("#planLinkFromAirportId").val()),
        "toAirportId" : parseInt($("#planLinkToAirportId").val()),
        airplanes : getAssignedAirplaneFrequencies(),
        "airlineId" : airlineId,
        "relationship" : planLinkInfo.mutualRelationship,
        "price" : { "economy" : parseInt($("#planLinkEconomyPrice").val()), "business" : parseInt($("#planLinkBusinessPrice").val()), "first" : parseInt($("#planLinkFirstPrice").val())},
        "model" : parseInt($("#planLinkModelSelect").val()),
        "rawQuality" : parseInt($("#planLinkServiceLevel").val()) * 20,
        "assignedDelegates" : assignedActionPoints }
    $.ajax({
        type: 'POST',
        url: url,
        data: JSON.stringify(linkData),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            if (result.extraOvertimeCompensation > 0) {
                $('#planLinkDetails .overtimeCompensation .amount').text(result.extraOvertimeCompensation)
                $('#planLinkDetails .overtimeCompensation').show()
            } else {
                $('#planLinkDetails .overtimeCompensation').hide()
            }

            $('#planLinkDetails .staffRequired').text(result.staffBreakdown.total)

            $('#linkStaffBreakdownTooltip .flightType').text(result.flightType)
            $('#linkStaffBreakdownTooltip .basic').text(result.staffBreakdown.basic)
            var frequencyStaff = result.staffBreakdown.frequency.toFixed(1)
            $('#linkStaffBreakdownTooltip .frequency').text(frequencyStaff)
            var capacityStaff = result.staffBreakdown.capacity.toFixed(1)
            $('#linkStaffBreakdownTooltip .capacity').text(capacityStaff)
            $('#linkStaffBreakdownTooltip .modifier').text(result.staffBreakdown.modifier == 1 ? "-" : result.staffBreakdown.modifier)

            var totalText = result.staffBreakdown.basic + " + " + frequencyStaff + " + " + capacityStaff
            if (result.staffBreakdown.modifier != 1) {
                totalText = "(" + totalText + ") * " + result.staffBreakdown.modifier
            }
            totalText += " = " + result.staffBreakdown.total
            $('#linkStaffBreakdownTooltip .total').text(totalText)

        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function cancelEditLink() {
	if (tempPath) { //create new link
		removeTempPath();
		$('#sidePanel').fadeOut(200);
	} else { //simply go back to linkDetails of the current link (exit edit mode)
		setActiveDiv($('#linkDetails'))
	}
	hideActiveDiv($("#airplaneModelDetails"))
    hideActiveDiv($("#linkDetails"))
}

function removeTempPath() {
	if (tempPath) {
		AirlineMap.unhighlightPath(tempPath.path)
		AirlineMap.clearPathEntry(tempPath)
		tempPath = undefined
	}
}

function showLinksCanvas(selectedLink = null, isReload = true) {
    var selectLinkId = selectedLink ? parseInt(selectedLink) : null
    loadLinksTable(function() {
        if (selectLinkId) {
            var row = $("#linksCanvas #linksTable .table-row[data-link-id='" + selectLinkId + "']")
            if (row.length > 0) {
                selectLinkFromTable(row, selectLinkId)
                row[0].scrollIntoView({ behavior: 'smooth', block: 'nearest' })
            }
        }
    })
	setActiveDiv($('#linksCanvas'));
	if (selectedLink === null) {
	    $('#sidePanel').fadeOut(200);
    }
	$('#sidePanel').appendTo($('#linksCanvas'))
}

function computeLinkDerivedProperties(links) {
    $.each(links, function(key, link) {
        if (link.currentStaffRequired == null) link.currentStaffRequired = 0
        link.profitMargin = link.revenue > 0 ? link.profit / link.revenue : 0
        link.profitPerStaff = link.currentStaffRequired > 0 ? Math.round(link.profit / link.currentStaffRequired) : 0
        link.totalCapacity = link.capacity ? link.capacity.economy + link.capacity.business + link.capacity.first : 0
        link.totalCapacityHistory = link.capacityHistory ? link.capacityHistory.economy + link.capacityHistory.business + link.capacityHistory.first : 0
        link.totalPassengers = link.passengers ? link.passengers.economy + link.passengers.business + link.passengers.first : 0
        const cancelledTotal = link.cancelledSeats ? link.cancelledSeats.total : 0
        link.totalLoadFactor = link.totalCapacityHistory > 0 ? Math.round(link.totalPassengers / (link.totalCapacityHistory - cancelledTotal) * 100) : 0
        link.model = (link.assignedAirplanes && link.assignedAirplanes.length > 0) ? link.assignedAirplanes[0].airplane.name : "-"
    })
}

function loadLinksTable(onComplete, forceRefresh) {
    const linksTable = $('#linksCanvas #linksTable');
    if (!linksTable.data('delegation-set')) {
        linksTable.on('click', '.table-row', function(e) {
            if ($(e.target).is('input')) return;
            selectLinkFromTable($(this), $(this).data('link-id'));
        });
        linksTable.on('click', '.link-checkbox', function(e) {
            e.stopPropagation();
            toggleLinkSelection($(this).closest('.table-row').data('link-id'), $(this));
        });
        linksTable.data('delegation-set', true);
    }

    function renderTable() {
        const sel = $('#linksTableSortHeader .cell.selected');
        updateLinksTable(sel.data('sort-property'), sel.data('sort-order'));
        if (onComplete) onComplete();
    }

    if (!forceRefresh && loadedLinks && loadedLinks.length > 0) {
        renderTable();
        return;
    }

	$.ajax({
		type: 'GET',
		url: "/airlines/" + activeAirline.id + "/links-details",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(data) {
	    	updateLoadedLinks(data);
	    	renderTable();
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function toggleLinksTableSortOrder(sortHeader) {
	if (sortHeader.data("sort-order") == "ascending") {
		sortHeader.data("sort-order", "descending")
	} else {
		sortHeader.data("sort-order", "ascending")
	}

	sortHeader.siblings().removeClass("selected")
	sortHeader.addClass("selected")

	updateLinksTable(sortHeader.data("sort-property"), sortHeader.data("sort-order"))
}

function toggleLinksTableAverage() {
    linksTableSummaryState = linksTableSummaryState === 'average' ? false : 'average'
    updateLinksTable()
}

function toggleLinksTableMedian() {
    linksTableSummaryState = linksTableSummaryState === 'median' ? false : 'median'
    updateLinksTable()
}

function addSummaryRow(links) {
    if (!linksTableSummaryState) return

    // allow passing an explicit list of links (e.g. filteredLinks). Fallback to loadedLinks
    const data = Array.isArray(links) ? links : loadedLinks

    const linkColumnConfigs = [
        { label: prettyLabel(linksTableSummaryState) }, // From Airport
        {}, // To Airport
        {}, // Model
        { getValue: (link) => link.distance, format: (val) => val.toFixed(0) + "km" },
        { getValue: (link) => link.totalCapacity, format: (val) => val.toFixed(0) }, // Capacity
        { getValue: (link) => link.totalPassengers, format: (val) => val.toFixed(0) },
        { getValue: (link) => link.totalLoadFactor, format: (val) => val.toFixed(0) + '%' },
        { getValue: (link) => link.computedQuality > 0 ? link.computedQuality : '-', format: (val) => val.toFixed(0) },
        { getValue: (link) => Math.round(link.satisfaction * 100), format: (val) => val.toFixed(0) + '%' },
        { getValue: (link) => link.revenue, format: (val) => '$' + commaSeparateNumber(val.toFixed(0)) },
        { getValue: (link) => link.profit, format: (val) => '$' + commaSeparateNumber(val.toFixed(0)) },
        { getValue: (link) => link.profitMargin, format: (val) => (val * 100).toFixed(2) + "%" },
        { getValue: (link) => link.currentStaffRequired, format: (val) => val.toFixed(1) },
        { getValue: (link) => link.profitPerStaff, format: (val) => '$' + commaSeparateNumber(val.toFixed(0)) }
    ];
    addTableSummaryRow("#linksCanvas #linksTable", data, linkColumnConfigs, linksTableSummaryState);
}

function updateLinksTable(sortProperty, sortOrder) {
	var linksTable = $("#linksCanvas #linksTable")
	linksTable.children("div.table-row").remove()

	//sort the list
	loadedLinks = sortPreserveOrder(loadedLinks, sortProperty, sortOrder == "ascending")

    const state = tableFilterState.getTableState('links');

    const filteredLinks = [];
    $.each(loadedLinks, function(index, link) {
        let isFiltered = false;
        Object.entries(state.selectedColumnFilter).forEach(([property, filterValues]) => {
            if (!Array.isArray(filterValues) || filterValues.length < 1) {
                return;
            }
            if (!filterValues.includes(String(link[property]))) {
                isFiltered = true;
            }
        });
        if (!isFiltered) {
            filteredLinks.push(link);
        }
    });

    // show summary for the filtered set
    filteredLinks.length > 0 && addSummaryRow(filteredLinks)

    const rowsHtml = [];
    filteredLinks.forEach((link) => {
        const quality = link.computedQuality > 0 ? link.computedQuality : "-"
        const bgStyle = linkColors[link.id] ? ` style="background-color:${linkColors[link.id]}"` : '';
        const selectedClass = selectedLink == link.id ? ' selected' : '';
        const checkedAttr = selectedLinkIds.has(link.id) ? ' checked' : '';
        rowsHtml.push(
            `<div class='table-row clickable${selectedClass}' data-link-id='${link.id}'${bgStyle}>` +
            `<div class='cell'><input type='checkbox' class='link-checkbox'${checkedAttr}></div>` +
            `<div class='cell'>${getCountryFlagImg(link.fromCountryCode)}${getAirportText(link.fromAirportCity, link.fromAirportCode)}</div>` +
            `<div class='cell'>${getCountryFlagImg(link.toCountryCode)}${getAirportText(link.toAirportCity, link.toAirportCode)}</div>` +
            `<div class='cell'>${link.model}</div>` +
            `<div class='cell' align='right'>${link.distance}km</div>` +
            `<div class='cell' align='right'>${link.totalCapacity}(${link.frequency})</div>` +
            `<div class='cell' align='right'>${link.totalPassengers}</div>` +
            `<div class='cell' align='right'>${link.totalLoadFactor}%</div>` +
            `<div class='cell' align='right'>${quality}</div>` +
            `<div class='cell' align='right'>${Math.round(link.satisfaction * 100)}%</div>` +
            `<div class='cell' align='right'>$${commaSeparateNumber(link.revenue)}</div>` +
            `<div class='cell' align='right'>$${commaSeparateNumber(link.profit)}</div>` +
            `<div class='cell' align='right'>${(link.profitMargin * 100).toFixed(2)}%</div>` +
            `<div class='cell' align='right'>${link.currentStaffRequired.toFixed(1)}</div>` +
            `<div class='cell' align='right'>$${commaSeparateNumber(link.profitPerStaff)}</div>` +
            `</div>`
        );
    });
    linksTable.append(rowsHtml.join(''));

	if (loadedLinks.length == 0) {
		$('#linksCanvas .noLinkTips').show();
	} else {
		$('#linksCanvas .noLinkTips').hide();
	}
}

function selectLinkFromMap(linkId, refocus) {
	refocus = refocus || false
	AirlineMap.unhighlightLink(selectedLink)
	selectedLink = linkId
	AirlineMap.highlightLink(linkId, refocus)

	//update link details panel
	refreshLinkDetails(linkId)
}

function selectLinkFromTable(row, linkId) {
	selectedLink = linkId
	//update table
	row.siblings().removeClass("selected")
	row.addClass("selected")

	//update link details panel
	refreshLinkDetails(linkId)
}

function updateLoadedLinks(links) {
    if (links && links.type === 'FeatureCollection' && links.features) {
        links = links.features.map(f => f.properties);
    }
	var previousOrder = {}
	if (loadedLinks) {
		$.each(loadedLinks, function(index, link) {
			previousOrder[link.id] = index
		})
		$.each(links, function(index, link) {
			link.previousOrder = previousOrder[link.id]
		})
		loadedLinks = links;
		loadedLinks.sort(sortByProperty("previousOrder"), true)
	} else {
		loadedLinks = links;
	}

	loadedLinksById = {}
	$.each(links, function(index, link) {
		loadedLinksById[link.id] = link
	});

	computeLinkDerivedProperties(loadedLinks);

	var availableOptions = {
		"fromAirportCode": {},
		"toAirportCode": {},
		"modelId": {},
		"distance": {},
	};
	$.each(loadedLinks, function(index, link) {
		availableOptions.fromAirportCode[link.fromCountryCode] = availableOptions.fromAirportCode[link.fromCountryCode] || {};
		availableOptions.fromAirportCode[link.fromCountryCode][link.fromAirportCode] = `${link.fromAirportCity} (${link.fromAirportCode})`;
		availableOptions.toAirportCode[link.toCountryCode] = availableOptions.toAirportCode[link.toCountryCode] || {};
		availableOptions.toAirportCode[link.toCountryCode][link.toAirportCode] = `${link.toAirportCity} (${link.toAirportCode})`;
		availableOptions.modelId[link.modelId] = link.modelName;
		availableOptions.distance[link.distance] = Number(link.distance);
	});
	updateColumnFilterOptions(availableOptions, 'links');
}

function showLinkComposition(linkId) {
	var url = "/airlines/" + activeAirline.id + "/link-composition/" + linkId

	$.ajax({
		type: 'GET',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	        updateSatisfaction(result)

	    	updateTopCountryComposition(result.homeCountry, "#passengerCompositionByHomeCountryTable")
	    	updateTopCountryComposition(result.destinationCountry, "#passengerCompositionByDestinationCountryTable")
	    	updatePassengerTypeComposition(result.paxTypeSatisfaction)
	    	updatePreferredClassComposition(result.linkClassSatisfaction)
	    	updatePreferenceTypeComposition(result.preferenceSatisfaction)
            updateTopAirportComposition($('#linkCompositionModal div.topHomeAirports'), result.homeAirports)
            updateTopAirportComposition($('#linkCompositionModal div.topDestinationAirports'), result.destinationAirports)
            $('#linkCompositionModal').fadeIn(200)

            plotPie(result.homeCountry, null , "passengerCompositionByHomeCountryPie", "countryName", "passengerCount", false)
            plotPie(result.destinationCountry, null , "passengerCompositionByDestinationCountryPie", "countryName", "passengerCount", false)
            plotPie(result.paxTypeSatisfaction, null , "passengerCompositionByPassengerTypePie", "title", "passengerCount", false)
            plotPie(result.linkClassSatisfaction, null , "passengerCompositionByPreferredClassPie", "title", "passengerCount", false)
            plotPie(result.preferenceSatisfaction, null , "passengerCompositionByPreferenceTypePie", "title", "passengerCount", false)

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


function showLinkEventHistory(linkId) {
    $('#linkEventModal .chart').hide()
    $('#linkRivalHistoryChart').show()

    //always default to all airlines (instead of self)
    $("#switchLinkEventRival").prop('checked', true)
    $("#switchLinkEventSelf").prop('checked', false)

    var link = $("#linkEventModal").data("link")
    $("#linkEventModal .title").html("<div style='display: inline-flex; align-items: center;'>"
    + getCountryFlagImg(link.fromCountryCode)
    + getAirportText(link.fromAirportCity, link.fromAirportCode)
    + "<img src='/assets/images/icons/arrow.png' style='margin: 0 5px;'>"
    + getCountryFlagImg(link.toCountryCode)
    + getAirportText(link.toAirportCity, link.toAirportCode) + "</div>")
    $('#linkEventModal .fromAirportCode').text(link.fromAirportCode)
    $('#linkEventModal .toAirportCode').text(link.toAirportCode)
    $('#linkEventModal .bothAirportCode').append(link.fromAirportCode + link.toAirportCode)
    $("#linkEventModal div.filterCheckboxes input:checkbox").prop('checked', true)

    var linkConsumptions = $($('#linkEventChart').data('linkConsumptions')).toArray().slice(0, 8 * 13)

    var chart = plotLinkEvent(linkConsumptions, 'linkEventChart',
        function(hoverCycle) {
            var $linkEventTableContainer = $("#linkEventModal .linkEventHistoryTableContainer")
            $linkEventTableContainer.find(".table-row").removeClass('selected')
            var $matchingRows = $linkEventTableContainer.find(".table-row[data-cycle='" + hoverCycle + "']")
            $matchingRows.addClass('selected')
            if ($matchingRows.length > 0) {
                scrollToRow($matchingRows[0], $linkEventTableContainer)
            }
        },
        function() { //chartout
             $("#linkEventModal .linkEventHistoryTableContainer .table-row").removeClass('selected')
        })
    $("#linkEventChart").data("chart", chart) //record back to the container

    //load rival comparison
    $.ajax({
        		type: 'GET',
        		url: "/airlines/" + activeAirline.id + "/link-related-rival-history/" + linkId + "?cycleCount=" + linkConsumptions.length,
        	    contentType: 'application/json; charset=utf-8',
        	    dataType: 'json',
        	    success: function(result) {
                    var chart = plotRivalHistory(result, 'linkRivalHistoryChart',
                        function(hoverCycle) {
                            var $linkEventTableContainer = $("#linkEventModal .linkEventHistoryTableContainer")
                            $linkEventTableContainer.find(".table-row").removeClass('selected')
                            var $matchingRows = $linkEventTableContainer.find(".table-row[data-cycle='" + hoverCycle + "']")
                            $matchingRows.addClass('selected')
                            if ($matchingRows.length > 0) {
                                var row = $matchingRows[0]
                                var baseOffset = $linkEventTableContainer.find(".table-row")[0].offsetTop //somehow first row is not 0...
                                var realOffset = row.offsetTop - baseOffset
                                $linkEventTableContainer.stop(true, true) //stop previous animation
                                $linkEventTableContainer.animate ({scrollTop: realOffset}, "fast");
                            }
                        },
                        function() { //chartout
                             $("#linkEventModal .linkEventHistoryTableContainer .table-row").removeClass('selected')
                        }
                    )
                    $("#linkRivalHistoryChart").data("chart", chart) //record back to the container
        	    },
                error: function(jqXHR, textStatus, errorThrown) {
        	            console.log(JSON.stringify(jqXHR));
        	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        	    }
        	});

    var url = "/airlines/" + activeAirline.id + "/link-related-event-history/" + linkId + "?cycleCount=" + linkConsumptions.length
    $.ajax({
    		type: 'GET',
    		url: url,
    	    contentType: 'application/json; charset=utf-8',
    	    dataType: 'json',
    	    success: function(result) {
                var linkEventTable = $("#linkEventModal .linkEventHistoryTable")
                linkEventTable.children("div.table-row").remove()

                result = result.sort(function (a, b) {
                    return b.cycle - a.cycle
                });

                $.each(result, function(index, entry) {
                    var row = $("<div class='table-row clickable'></div>")
                    row.attr("data-cycle", entry.cycle)
                    row.data("index", index)
                    row.append("<div class='cell'>" + getCycleDeltaText(entry.cycleDelta) + "</div>")
                    if (entry.airlineId) {
                        row.append("<div class='cell'>" + getAirlineLogoImg(entry.airlineId) + entry.airlineName + "</div>")
                    } else {
                        row.append("<div class='cell'>-</div>")
                    }

                    var $descriptionCell = $("<div class='cell'>" + entry.description + "</div>")
                    if (entry.descriptionCountryCode) {
                        $descriptionCell.prepend(getCountryFlagImg(entry.descriptionCountryCode))
                    }
                    row.append($descriptionCell)
                    if (entry.capacity) {
                        $("<div class='cell' align='right'></div>").appendTo(row).append(getCapacitySpan(entry.capacity, entry.frequency))
                    } else {
                        row.append("<div class='cell'>-</div>")
                    }

                    if (entry.capacityDelta) {
                        $("<div class='cell' align='right'></div>").appendTo(row).append(getCapacityDeltaSpan(entry.capacityDelta))
                    } else {
                        row.append("<div class='cell'>-</div>")
                    }

                    if (entry.price) {
                        $("<div class='cell'></div>").appendTo(row).text(toLinkClassValueString(entry.price, '$'))
                    } else {
                        row.append("<div class='cell'>-</div>")
                    }
                    if (entry.priceDelta) {
                        $("<div class='cell'></div>").appendTo(row).append(getPriceDeltaSpan(entry.priceDelta))
                    } else {
                        row.append("<div class='cell'>-</div>")
                    }
                    row.mouseenter(function() {
                        toggleLinkEventBar($('#linkEventModal .chart:visible').data('chart'), entry.cycle, true)
                    })
                    if (entry.matchFrom) {
                        row.addClass('filter-fromAirport')
                    }
                    if (entry.matchTo) {
                        row.addClass('filter-toAirport')
                    }
                    if (entry.matchFrom && entry.matchTo) {
                        row.addClass('filter-bothAirport')
                    }
                    if (!entry.matchFrom && !entry.matchTo) {
                        row.addClass('filter-other')
                    }
                    linkEventTable.append(row)
                });

                if (result.length == 0) {
                    var row = $("<div class='table-row'><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div></div>")
                    linkEventTable.append(row)
                }

                linkEventTable.mouseleave(function() {
                  toggleLinkEventBar($('#linkEventModal .chart:visible').data('chart'), -1, false)
                })
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

    $('#linkEventModal').fadeIn(200)
}

function showLinkRivalHistory(linkId) {
	var url = "/airlines/" + activeAirline.id + "/link-rival-history/" + linkId + "?cycleCount=30"

	$.ajax({
		type: 'GET',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	plotRivalHistoryChart(result.overlappingLinks, "rivalEconomyPriceChart", "economy", "price", "$", activeAirline.id)
	    	plotRivalHistoryChart(result.overlappingLinks, "rivalBusinessPriceChart", "business", "price", "$", activeAirline.id)
	    	plotRivalHistoryChart(result.overlappingLinks, "rivalFirstPriceChart", "first", "price", "$", activeAirline.id)
	    	plotRivalHistoryChart(result.overlappingLinks, "rivalEconomyCapacityChart", "economy", "capacity", "", activeAirline.id)
            plotRivalHistoryChart(result.overlappingLinks, "rivalBusinessCapacityChart", "business", "capacity", "", activeAirline.id)
            plotRivalHistoryChart(result.overlappingLinks, "rivalFirstCapacityChart", "first", "capacity", "", activeAirline.id)

	    	$('#linkRivalHistoryModal').fadeIn(200)
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

function switchLinkEventChart($chartContainer) {
    $("#linkEventModal .chart").hide()
    $chartContainer.show()
}

function showLinkRivalDetails(linkId) {
	var url = "/airlines/" + activeAirline.id + "/link-rival-details/" + linkId

	$.ajax({
		type: 'GET',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	        updateRivalTables(result)

	    	$('#linkRivalDetailsModal').fadeIn(200)
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

function getLoungeIconSpan(lounge) {
    var $loungeSpan = $('<span style="position:relative"></span>')
    $loungeSpan.append($('<img src="' + '/assets/images/icons/sofa.png' +  '">'))
    $loungeSpan.attr('title', lounge.name + ' at ' + lounge.airportName + ' (' + lounge.airlineName + ' level ' + lounge.level + ')')
    $loungeSpan.append('<div style="position: absolute; right: 0px; bottom: 0px; padding: 0px; vertical-align: middle; color: rgb(69, 69, 68); background-color: rgb(140, 185, 217); font-size: 8px; font-weight: bold;">' + lounge.level + '</div>')
    return $loungeSpan
}

function updateRivalTables(result) {
    var appealTable = $("#rivalAppealTable")
    var networkCapacityTable = $("#networkCapacity")
    appealTable.children(".table-row").remove()
    networkCapacityTable.children(".table-row").remove()
    var fromAirportText = getAirportText(result.fromCity, result.fromAirportCode)
    var toAirportText = getAirportText(result.toCity, result.toAirportCode)
    $("#linkRivalDetailsModal .fromAirportText").text(fromAirportText)
    $("#linkRivalDetailsModal .toAirportText").text(toAirportText)


	var airlineNameById = {}
	var fromAirportLoyalty = {}
	var toAirportLoyalty = {}
	var fromAirportCapacity = {}
    var toAirportCapacity = {}
    var fromAirportLounge = {}
    var toAirportLounge = {}

    $.each(result.fromAirport, function(index, entry) {
	     var airlineId = entry.airline.id
	     airlineNameById[airlineId] = entry.airline.name
	     fromAirportLoyalty[airlineId] = entry.loyalty
	     fromAirportCapacity[airlineId] = { "economy" : entry.network.economy, "business" : entry.network.business, "first" : entry.network.first}
	     if (entry.lounge) {
            fromAirportLounge[airlineId] = entry.lounge
	     }
     })

    $.each(result.toAirport, function(index, entry) {
         var airlineId = entry.airline.id
         toAirportLoyalty[airlineId] = entry.loyalty
         toAirportCapacity[airlineId] = { "economy" : entry.network.economy, "business" : entry.network.business, "first" : entry.network.first}
         if (entry.lounge) {
             toAirportLounge[airlineId] = entry.lounge
         }
    })


    var fullHeartSource = "/assets/images/icons/heart.png"
    var halfHeartSource = "/assets/images/icons/heart-half.png"
    var emptyHeartSource = "/assets/images/icons/heart-empty.png"
    var greenManSource = "/assets/images/icons/man-green.png"
    var blueManSource = "/assets/images/icons/man-blue.png"
    var yellowManSource = "/assets/images/icons/man-yellow.png"
    $.each(airlineNameById, function(airlineId, airlineName) {
     	var row = $("<div class='table-row'></div>")
     	var $airlineSpan = $(getAirlineSpan(airlineId, airlineName))
     	if (fromAirportLounge[airlineId]) {
     	    $airlineSpan.append(getLoungeIconSpan(fromAirportLounge[airlineId]))
     	}
     	if (toAirportLounge[airlineId]) {
            $airlineSpan.append(getLoungeIconSpan(toAirportLounge[airlineId]))
        }
        var $airlineCell = $("<div class='cell' align='left'></div>").append($airlineSpan)
		row.append($airlineCell)
		getPaddedHalfStepImageBarByValue(fullHeartSource, halfHeartSource, emptyHeartSource, 10, fromAirportLoyalty[airlineId].toFixed(2)).appendTo($("<div class='cell' align='right'></div>").appendTo(row))
		getPaddedHalfStepImageBarByValue(fullHeartSource, halfHeartSource, emptyHeartSource, 10, toAirportLoyalty[airlineId].toFixed(2)).appendTo($("<div class='cell' align='right'></div>").appendTo(row))
		appealTable.append(row)

		row = $("<div class='table-row'></div>")

		row.append("<div class='cell' align='left'>" + getAirlineSpan(airlineId, airlineName) + "</div>")
        getCapacityImageBar(greenManSource, fromAirportCapacity[airlineId].economy, "economy").appendTo($("<div class='cell' align='right'></div>").appendTo(row))
        getCapacityImageBar(blueManSource, fromAirportCapacity[airlineId].business, "business").appendTo($("<div class='cell' align='right'></div>").appendTo(row))
        getCapacityImageBar(yellowManSource, fromAirportCapacity[airlineId].first, "first").appendTo($("<div class='cell' align='right'></div>").appendTo(row))
        getCapacityImageBar(greenManSource, toAirportCapacity[airlineId].economy, "economy").appendTo($("<div class='cell' align='right'></div>").appendTo(row))
        getCapacityImageBar(blueManSource, toAirportCapacity[airlineId].business, "business").appendTo($("<div class='cell' align='right'></div>").appendTo(row))
        getCapacityImageBar(yellowManSource, toAirportCapacity[airlineId].first, "first").appendTo($("<div class='cell' align='right'></div>").appendTo(row))

        networkCapacityTable.append(row)
	});

}

function getPaddedHalfStepImageBarByValue(fullStepImageSrc, halfStepImageSrc, emptyStepImageSrc, halfStepAmount, value) {
    var containerDiv = $("<div>")
	containerDiv.prop("title", value)

    var halfSteps = Math.floor(value / halfStepAmount)
    var fullSteps = Math.floor(halfSteps / 2)
    var hasRemainder = halfSteps % 2;
    for (i = 0 ; i < fullSteps ; i ++) {
		var image = $("<img width='16' height='16' src='" + fullStepImageSrc + "'>")
		containerDiv.append(image)
    }
    if (hasRemainder && halfStepImageSrc) {
        var image = $("<img width='16' height='16' src='" + halfStepImageSrc + "'>")
    	containerDiv.append(image)
    }
    if (emptyStepImageSrc && halfSteps == 0 && fullSteps == 0) {
        var image = $("<img width='16' height='16' src='" + emptyStepImageSrc + "'>")
        containerDiv.append(image)
    }

    return containerDiv
}

function getHalfStepImageBarByValue(fullStepImageSrc, halfStepImageSrc, halfStepAmount, value) {
    return getPaddedHalfStepImageBarByValue(fullStepImageSrc, halfStepImageSrc, null, halfStepAmount, value)
}

function getCapacityImageBar(imageSrc, value, linkClass) {
    var containerDiv = $("<div>")
	containerDiv.prop("title", value)

    if (linkClass == "business") {
        value *= 5
    } else if (linkClass == "first") {
        value *= 20
    }
    var count;
    if (value >= 200000) {
        count = 10
    } else if (value >= 100000) {
        count = 9
    } else if (value >= 50000) {
        count = 8
    } else if (value >= 30000) {
        count = 7
    } else if (value >= 20000) {
        count = 6
    } else if (value >= 10000) {
        count = 5
    } else if (value >= 8000) {
        count = 4
    } else if (value >= 5000) {
        count = 3
    } else if (value >= 2000) {
        count = 2
    } else if (value > 0) {
        count = 1
    } else {
        count = 0
    }

    for (i = 0 ; i < count ; i ++) {
		var image = $("<img src='" + imageSrc + "'>")
		containerDiv.append(image)
    }

    return containerDiv
}

function updateSatisfaction(result) {
    var linkClassSatisfaction = result.linkClassSatisfaction
    var paxTypeSatisfaction = result.paxTypeSatisfaction
    var preferenceSatisfaction = result.preferenceSatisfaction
    $('#linkCompositionModal .paxTypeSatisfaction .table-row').remove()
    $('#linkCompositionModal .linkClassSatisfaction .table-row').remove()
    $('#linkCompositionModal .preferenceSatisfaction .table-row').remove()
    $('#linkCompositionModal .positiveComments .table-row').remove()
    $('#linkCompositionModal .negativeComments .table-row').remove()
    var topPositiveCommentsByClass = result.topPositiveCommentsByClass
    var topNegativeCommentsByClass = result.topNegativeCommentsByClass
    var topPositiveCommentsByPreference = result.topPositiveCommentsByPreference
    var topNegativeCommentsByPreference = result.topNegativeCommentsByPreference
    var topPositiveCommentsByType = result.topPositiveCommentsByType
    var topNegativeCommentsByType = result.topNegativeCommentsByType

    $.each(paxTypeSatisfaction, function(index, entry) {
        $row = $("<div class='table-row data-row'><div class='cell' style='width: 50%; vertical-align: middle;'>" + entry.title + "</div></div>")
        var $icon = getSatisfactionIcon(entry.satisfaction)
        $icon.on('mouseover.breakdown', function() {
            showSatisfactionBreakdown($(this), topPositiveCommentsByType[entry.id], topNegativeCommentsByType[entry.id], entry.satisfaction)
        })

        $row.append("<div class='cell' style='width: 15%;'>" + entry.passengerCount)

        $icon.on('mouseout.breakdown', function() {
            hideMarkupTooltip(document.getElementById('satisfactionDetailsTooltip'))
        })
        $iconCell = $("<div class='cell' style='width: 35%;'>").append($icon)
        $row.append($iconCell)

        $('#linkCompositionModal .paxTypeSatisfaction').append($row)
    })
    $.each(linkClassSatisfaction, function(index, entry) {
        $row = $("<div class='table-row data-row'><div class='cell' style='width: 50%; vertical-align: middle;'>" + entry.title + "</div></div>")
        var $icon = getSatisfactionIcon(entry.satisfaction)
        $icon.on('mouseover.breakdown', function() {
            showSatisfactionBreakdown($(this), topPositiveCommentsByClass[entry.level], topNegativeCommentsByClass[entry.level], entry.satisfaction)
        })

        $row.append("<div class='cell' style='width: 15%;'>" + entry.passengerCount)

        $icon.on('mouseout.breakdown', function() {
            hideMarkupTooltip(document.getElementById('satisfactionDetailsTooltip'))
        })
        $iconCell = $("<div class='cell' style='width: 35%;'>").append($icon)
        $row.append($iconCell)

        $('#linkCompositionModal .linkClassSatisfaction').append($row)
    })
    $.each(preferenceSatisfaction, function(index, entry) {
        $row = $("<div class='table-row data-row'><div class='cell' style='width: 50%; vertical-align: middle;'>" + entry.title + "</div></div>")
        var $icon = getSatisfactionIcon(entry.satisfaction)
        $icon.on('mouseover.breakdown', function() {
            showSatisfactionBreakdown($(this), topPositiveCommentsByPreference[entry.id], topNegativeCommentsByPreference[entry.id], entry.satisfaction)
        })

        $row.append("<div class='cell' style='width: 15%;'>" + entry.passengerCount)


        $icon.on('mouseout.breakdown', function() {
            hideMarkupTooltip(document.getElementById('satisfactionDetailsTooltip'))
        })

        $iconCell = $("<div class='cell' style='width: 35%;'>").append($icon)
        $row.append($iconCell)

        $('#linkCompositionModal .preferenceSatisfaction').append($row)
    })

    var topPositiveComments = result.topPositiveComments
    var topNegativeComments = result.topNegativeComments
    $.each(topPositiveComments, function(index, entry) {
            var percentage = Math.round(entry[1] * 100)
            if (percentage == 0) {
                percentage = "< 1"
            }
            $row = $("<div class='table-row data-row'><div class='cell'>" + entry[0].comment + "</div><div class='cell'>" + percentage + "%</div></div>")
            $('#linkCompositionModal .positiveComments').append($row)
        })
    $.each(topNegativeComments, function(index, entry) {
            var percentage = Math.round(entry[1] * 100)
            if (percentage == 0) {
                percentage = "< 1"
            }
            $row = $("<div class='table-row data-row'><div class='cell'>" + entry[0].comment + "</div><div class='cell'>" + percentage + "%</div></div>")
            $('#linkCompositionModal .negativeComments').append($row)
        })
}

function getSatisfactionIcon(satisfaction) {
    $icon = $('<img>')
    var source
    if (satisfaction < 0.25) {
        source = "symbols-on-mouth"
    } else if (satisfaction < 0.3) {
        source = "steam"
    } else if (satisfaction < 0.4) {
        source = "confused"
    } else if (satisfaction < 0.5) {
        source = "expressionless"
    } else if (satisfaction < 0.6) {
        source = "slightly-smiling"
    } else if (satisfaction < 0.7) {
        source = "grinning"
    } else if (satisfaction < 0.8) {
        source = "smiling"
    } else if (satisfaction < 0.9) {
        source = "blowing-a-kiss"
    } else {
        source = "heart-eyes"
    }
    source = '/assets/images/smiley/' + source + '.png'
    $icon.attr('src', source)
    //$icon.attr('title', Math.round(satisfaction * 100) + "%")
    $icon.width('22px')
    $icon.css({ display: "block", margin: "auto"})
    return $icon
}

function showSatisfactionBreakdown($icon, positiveComments, negativeComments, satisfactionValue) {
    $('#satisfactionDetailsTooltip .satisfactionValue').text(Math.round(satisfactionValue * 100) + '%')
    $('#satisfactionDetailsTooltip .table .table-row').remove()
    $.each(positiveComments, function(index, entry) {
        var percentage = Math.round(entry[1] * 100)
        if (percentage == 0) {
            percentage = "< 1"
        }
        var $row = $('<div class="table-row" style="font-size: 15px; text-shadow: 0px 0px 3px rgba(0,0,0,0.5);"><div class="cell">' + entry[0].comment + '</div><div class="cell">' + percentage + '%</div></div>')
        $row.css('color', '#9ACD32')
        $('#satisfactionDetailsTooltip .table').append($row)
    })
    $.each(negativeComments, function(index, entry) {
        var percentage = Math.round(entry[1] * 100)
        if (percentage == 0) {
            percentage = "< 1"
        }
        var $row = $('<div class="table-row"  style="font-size: 15px; text-shadow: 0px 0px 3px rgba(0,0,0,0.5);"><div class="cell">' + entry[0].comment + '</div><div class="cell">' + percentage + '%</div></div>')
        $row.css('color', '#F08080')
        $('#satisfactionDetailsTooltip .table').append($row)
    })
    showMarkupTooltip($icon[0], document.getElementById('satisfactionDetailsTooltip'))
}


function updateTopCountryComposition(countryComposition, selector) {
	countryComposition = countryComposition.sort(function (a, b) {
	    return b.passengerCount - a.passengerCount
	});

	var max = 5;
	var index = 0;
	$(selector + ' .table-row').remove()
	$.each(countryComposition, function(key, entry) {
		$(selector).append("<div class='table-row data-row'><div class='cell' style='width: 70%;'>" + getCountryFlagImg(entry.countryCode) + entry.countryName
	 			   + "</div><div class='cell' style='width: 30%; text-align: right;'>" + commaSeparateNumber(entry.passengerCount) + "</div></div>")
		index ++;
		if (index >= max) {
			return false;
		}
	});
}

function updateTopAirportComposition($container, airportComposition) {
    var halfLength = Math.ceil(airportComposition.length / 2);
    $container.empty();
    $container.css({
        'display': 'flex',
        'max-width': '100%'
    });
    var $leftTable = $('<div class="table rounded-none data" style="flex: 1; min-width: 200px;"></div>').appendTo($container);
    var $rightTable = $('<div class="table rounded-none data" style="flex: 1; min-width: 200px;"></div>').appendTo($container);

    $.each(airportComposition, function(index, entry) {
        var $targetTable = index < halfLength ? $leftTable : $rightTable;
        $targetTable.append("<div class='table-row data-row' style='max-width: 320px;'><div class='cell' style='width: 80%;'>" + getCountryFlagImg(entry.countryCode) + entry.airport
                + "</div><div class='cell' style='width: 20%; text-align: right; padding-right: 0.5rem;'>" + commaSeparateNumber(entry.passengerCount) + "</div></div>");
    });
}

function updatePassengerTypeComposition(typeComposition) {
	typeComposition = typeComposition.sort(function (a, b) {
	    return b.passengerCount - a.passengerCount
	});

	$('#linkCompositionModal .passengerTypeTable .table-row').remove()
	$.each(typeComposition, function(key, entry) {
		$('#linkCompositionModal .passengerTypeTable').append("<div class='table-row data-row'><div class='cell' style='width: 70%;'>" + entry.title
	 			   + "</div><div class='cell' style='width: 30%; text-align: right;'>" + commaSeparateNumber(entry.passengerCount) + "</div></div>")
	});
}

function updatePreferredClassComposition(preferenceComposition) {
	preferenceComposition = preferenceComposition.sort(function (a, b) {
	    return b.passengerCount - a.passengerCount
	});

	$('#linkCompositionModal .preferredClassTable .table-row').remove()
	$.each(preferenceComposition, function(key, entry) {
		$('#linkCompositionModal .preferredClassTable').append("<div class='table-row data-row'><div class='cell' style='width: 70%;'>" + entry.title
	 			   + "</div><div class='cell' style='width: 30%; text-align: right;'>" + commaSeparateNumber(entry.passengerCount) + "</div></div>")
	});
}

function updatePreferenceTypeComposition(preferenceComposition) {
	preferenceComposition = preferenceComposition.sort(function (a, b) {
	    return b.passengerCount - a.passengerCount
	});

	$('#linkCompositionModal .preferenceTypeTable .table-row').remove()
	$.each(preferenceComposition, function(key, entry) {
		$('#linkCompositionModal .preferenceTypeTable').append("<div class='table-row data-row'><div class='cell' style='width: 70%;'>" + entry.title
	 			   + "</div><div class='cell' style='width: 30%; text-align: right;'>" + commaSeparateNumber(entry.passengerCount) + "</div></div>")
	});
}

function updateAirlineBaseList(airlineId, table) {
	table.children('.table-row').remove()

	$.ajax({
		type: 'GET',
		url: "/airlines/" + airlineId + "/bases",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(bases) {
	    	var hasHeadquarters = false
	    	var hasBases = false
	    	$(bases).each(function(index, base) {
	    		var row = $("<div class='table-row'></div>")
	    		hasBases = true
	    		if (base.headquarter) {
	    			row.append("<div class='cell'><img src='/assets/images/icons/building-hedge.png' style='vertical-align:middle;'><span>(" + base.scale + ")</span></div><div class='cell'>" + getCountryFlagImg(base.countryCode) + getAirportText(base.city, base.airportCode) + "</div>")
	    			table.prepend(row)
	    		} else {
	    			row.append("<div class='cell'><img src='/assets/images/icons/building-low.png' style='vertical-align:middle;'><span>(" + base.scale + ")</span></div><div class='cell'>" + getCountryFlagImg(base.countryCode) + getAirportText(base.city, base.airportCode) + "</div>")
	    			table.append(row)
	    		}
	    	})
	    	var emptyRow = $("<div class='table-row'></div>")
			emptyRow.append("<div class='cell'>-</div>")

			if (!hasBases) {
    			table.append(emptyRow)
    		}

	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

var assignedActionPoints = 0
var availableActionPoints = 0
var negotiationOddsLookup
var difficultyLookup

function linkConfirmation() {
	$('#linkConfirmationModal div.existing').empty()
	$('#linkConfirmationModal div.updating').empty()
	$('#linkConfirmationModal div.controlButtons').hide()
	$('#linkConfirmationModal .negotiationIcons').empty()
	$('#linkConfirmationModal .negotiationBar').empty()
	//$('#linkConfirmationModal .modal-content').css("height", 600)
	$('#linkConfirmationModal div.negotiationInfo').hide()

	var fromAirportId = parseInt($("#planLinkFromAirportId").val())
    var toAirportId = parseInt($("#planLinkToAirportId").val())
    loadAirportImages(fromAirportId, toAirportId)

	if (existingLink) {
		//existing link section
		$('#linkConfirmationModal .modalHeader').text('Update Route')
		$('#linkConfirmationModal div.existing.model').text(existingLink.modelName)
		$('#linkConfirmationModal div.existing.duration').text(getDurationText(existingLink.duration))

		refreshAssignedAirplanesBar($('#linkConfirmationModal div.existingLink .airplanes'), existingLink.assignedAirplanes)

        var existingFrequency = existingLink.future ? existingLink.future.frequency : existingLink.frequency
		for (i = 0 ; i < existingFrequency ; i ++) {
			var image = $("<img>")
			image.attr("src", $(".frequencyBar").data("fillIcon"))
			$('#linkConfirmationModal div.existing.frequency').append(image)
			if ((i + 1) % 10 == 0) {
				$('#linkConfirmationModal div.existing.frequency').append("<br/>")
			}
		}

		var existingCapacity = $('<span>' + toLinkClassValueString(existingLink.capacity) + '</span>')
		$("#linkConfirmationModal div.existing.capacity").append(existingCapacity)
		if (existingLink.future) {
		    var futureCapacity = $('<div class="future">' + toLinkClassValueString(existingLink.future.capacity) + '</div>')
		    $("#linkConfirmationModal div.existing.capacity").append(futureCapacity)
		}

		$('#linkConfirmationModal div.existing.price').text(toLinkClassValueString(existingLink.price, '$'))
	} else {
	    $('#linkConfirmationModal .modalHeader').text('Create New Route')
		$('#linkConfirmationModal div.existing').text('-')
	}

	//update link section
	var updateLinkModel = planLinkInfoByModel[$("#planLinkModelSelect").val()]
	$('#linkConfirmationModal div.updating.model').text(updateLinkModel.modelName)
	$('#linkConfirmationModal div.updating.duration').text(getDurationText(updateLinkModel.duration))

	var assignedAirplaneFrequencies = [] //[(airplane, frequency)]
    $('#planLinkDetails .frequencyDetail').find('.airplaneRow').each(function(index, airplaneRow) {
        var airplane = $(airplaneRow).data("airplane")
        var frequency = parseInt($(airplaneRow).find('.frequency').val())
        assignedAirplaneFrequencies.push({"airplane" : airplane, "frequency" : frequency})
    })

	refreshAssignedAirplanesBar($('#linkConfirmationModal div.updating.airplanes'), assignedAirplaneFrequencies)

    var planInfo = getPlanLinkCapacity()
    var planFrequency = planInfo.future ? planInfo.future.frequency : planInfo.current.frequency

	for (i = 0 ; i < planFrequency ; i ++) {
		var image = $("<img>")
		image.attr("src", $(".frequencyBar").data("fillIcon"))
		$('#linkConfirmationModal div.updating.frequency').append(image)
		if ((i + 1) % 10 == 0) {
			$('#linkConfirmationModal div.updating.frequency').append("<br/>")
		}
	}

	var planCapacitySpan = $('<span>' + toLinkClassValueString(planInfo.current.capacity) + '</span>')
    $("#linkConfirmationModal div.updating.capacity").append(planCapacitySpan)
    if (planInfo.future) {
        var futureCapacitySpan = $('<div class="future">(' + toLinkClassValueString(planInfo.future.capacity) + ')</div>')
        $("#linkConfirmationModal div.updating.capacity").append(futureCapacitySpan)
    }

	$('#linkConfirmationModal div.updating.price').text('$' + $('#planLinkEconomyPrice').val() + " / $" + $('#planLinkBusinessPrice').val() + " / $" + $('#planLinkFirstPrice').val())
	$('#linkConfirmationModal').fadeIn(200)

    getLinkNegotiation()
}

function changeAssignedActionPoints(delta) {
    const newValue = assignedActionPoints + delta
    if (!isNaN(negotiationOddsLookup[newValue]) && newValue >= 0 && newValue <= availableActionPoints) {
       updateAssignedActionPoints(newValue)
    }
}

function addMinimumRequiredActionPoints() {
	var minimumRequiredActionPoints = Object.keys(negotiationOddsLookup).length - 1
	for (let i = minimumRequiredActionPoints; i > 0; i--) {
		if (negotiationOddsLookup[i] > 0) {
			minimumRequiredActionPoints = i
		}
	}
	if (negotiationOddsLookup[minimumRequiredActionPoints] > 0) {
		updateAssignedActionPoints(minimumRequiredActionPoints)
	} else {
		updateAssignedActionPoints(0)
	}
}

function addMaximumActionPoints() {
	var maximumRequiredActionPoints = 0
	for (let i = 0; i <= Object.keys(negotiationOddsLookup).length; i++) {
		if (negotiationOddsLookup[i] <= 1) {
			maximumRequiredActionPoints = i
		}
	}
	if (negotiationOddsLookup[maximumRequiredActionPoints] > 0) {
		updateAssignedActionPoints(maximumRequiredActionPoints)
	}
}

function updateAssignedActionPoints(delegateCount) {
    assignedActionPoints = delegateCount
    $('#linkConfirmationModal .assignedDelegatesIcons').html(
        assignedActionPoints > 0 ? '⚡'.repeat(assignedActionPoints) : '<span>None</span>'
    )
    //look up the odds
    var odds = negotiationOddsLookup[assignedActionPoints]
    $('#linkConfirmationModal .successRate').text(Math.floor(odds * 100) + '%')

    if (odds <= 0 && difficultyLookup > 0) { //then need to add action points
        disableButton($('#linkConfirmationModal .negotiateButton'), "Odds at 0%. Assign more action points")
    } else {
        enableButton($('#linkConfirmationModal .negotiateButton'))
    }

}



function getLinkNegotiation(callback) {
    assignedActionPoints = 0
    availableActionPoints = 0
    negotiationOddsLookup = {}
    var airlineId = activeAirline.id
    var url = "/airlines/" + activeAirline.id + "/get-link-negotiation"

	var linkData = {
    			"fromAirportId" : parseInt($("#planLinkFromAirportId").val()),
    			"toAirportId" : parseInt($("#planLinkToAirportId").val()),
    			//"airplanes" : $("#planLinkAirplaneSelect").val().map(Number),
    			airplanes : getAssignedAirplaneFrequencies(),
    			"airlineId" : airlineId,
    			//"configuration" : { "economy" : configuration.economy, "business" : configuration.business, "first" : configuration.first},
    			"price" : { "economy" : parseInt($("#planLinkEconomyPrice").val()), "business" : parseInt($("#planLinkBusinessPrice").val()), "first" : parseInt($("#planLinkFirstPrice").val())},
    			//"frequency" : parseInt($("#planLinkFrequency").val()),
    			"model" : parseInt($("#planLinkModelSelect").val()),
    			"rawQuality" : parseInt($("#planLinkServiceLevel").val()) * 20}

    $.ajax({
		type: 'POST',
		url: url,
		data: JSON.stringify(linkData),
		contentType: 'application/json; charset=utf-8',
		dataType: 'json',
	    success: function(result) {
	        if (callback) {
	            callback(result)
	        } else {
                var fromAirport = result.fromAirport
                var toAirport = result.toAirport
                $('#negotiationDifficultyModal span.fromAirport').html(getAirportSpan(fromAirport))
                $('#negotiationDifficultyModal span.toAirport').html(getAirportSpan(toAirport))
                $('#linkConfirmationModal .fromAirportText').html(getAirportSpan(fromAirport))
                $('#linkConfirmationModal .toAirportText').html(getAirportSpan(toAirport))

                var negotiationInfo = result.negotiationInfo
                negotiationOddsLookup = negotiationInfo.odds

                if (negotiationInfo.finalRequirementValue > 0) {
                    checkTutorial("negotiation")
                    $('#negotiationDifficultyModal div.negotiationInfo .requirement').remove()
                    $('#negotiationDifficultyModal div.negotiationInfo .discount').remove()

                    var currentRow = $('#negotiationDifficultyModal div.negotiationRequirements.fromAirport .table-header')
                    var fromAirportRequirementValue = 0
                    $.each(negotiationInfo.fromAirportRequirements, function(index, requirement) {
                        var sign = requirement.value >= 0 ? '+' : ''
                        currentRow = $('<div class="table-row requirement"><div class="cell">' + requirement.description + '</div><div class="cell">' + sign + requirement.value.toFixed(2) + '</div></div>').insertAfter(currentRow)
                        fromAirportRequirementValue += requirement.value
                    })
                    if (negotiationInfo.fromAirportRequirements.length == 0) {
                        $('<div class="table-row requirement"><div class="cell">-</div><div class="cell">-</div></div>').insertAfter(currentRow)
                    }

                    $('#negotiationDifficultyModal .negotiationRequirementsTotal.fromAirport .total').text(fromAirportRequirementValue.toFixed(2))

                    currentRow = $('#negotiationDifficultyModal div.negotiationRequirements.toAirport .table-header')
                    var toAirportRequirementValue = 0
                    $.each(negotiationInfo.toAirportRequirements, function(index, requirement) {
                        var sign = requirement.value >= 0 ? '+' : ''
                        currentRow = $('<div class="table-row requirement"><div class="cell">' + requirement.description + '</div><div class="cell">' + sign + requirement.value.toFixed(2) + '</div></div>').insertAfter(currentRow)
                        toAirportRequirementValue += requirement.value
                    })
                    if (negotiationInfo.toAirportRequirements.length == 0) {
                        $('<div class="table-row requirement"><div class="cell">-</div><div class="cell">-</div></div>').insertAfter(currentRow)
                    }


                    $('#negotiationDifficultyModal .negotiationRequirementsTotal.toAirport .total').text(toAirportRequirementValue.toFixed(2))

                    //from airport discounts
                    currentRow = $('#negotiationDifficultyModal div.negotiationFromDiscounts .table-header')
                    $.each(negotiationInfo.fromAirportDiscounts, function(index, discount) {
                        var displayDiscountValue = Math.round(discount.value >= 0 ? discount.value * 100 : discount.value * -100)
                        currentRow = $('<div class="table-row discount"><div class="cell">' + discount.description + '</div><div class="cell discountValue">' + displayDiscountValue + '%</div></div>').insertAfter(currentRow)
                        if (discount.value < 0) {
                            currentRow.find('.discountValue').addClass('warning')
                        }
                    })
                    if (negotiationInfo.fromAirportDiscounts.length == 0) {
                        $('<div class="table-row discount"><div class="cell">-</div><div class="cell">-</div></div>').insertAfter(currentRow)
                    }

                    //to airport discounts
                    currentRow = $('#negotiationDifficultyModal div.negotiationToDiscounts .table-header')
                    $.each(negotiationInfo.toAirportDiscounts, function(index, discount) {
                        var displayDiscountValue = Math.round(discount.value >= 0 ? discount.value * 100 : discount.value * -100)
                        currentRow = $('<div class="table-row discount"><div class="cell">' + discount.description + '</div><div class="cell discountValue">' + displayDiscountValue + '%</div></div>').insertAfter(currentRow)
                        if (discount.value < 0) {
                            currentRow.find('.discountValue').addClass('warning')
                        }
                    })
                    if (negotiationInfo.toAirportDiscounts.length == 0) {
                        $('<div class="table-row discount"><div class="cell">-</div><div class="cell">-</div></div>').insertAfter(currentRow)
                    }

                    var fromDiscount = negotiationInfo.finalFromDiscountValue
                    var displayDiscountValue = Math.round(fromDiscount >= 0 ? fromDiscount * 100 : fromDiscount * -100)
                    $('#negotiationDifficultyModal .negotiationDiscountTotal.fromAirport .total').text(displayDiscountValue + "%")
                    if (fromDiscount < 0) {
                        $('#negotiationDifficultyModal .negotiationDiscountTotal.fromAirport .total').addClass('warning')
                    } else {
                        $('#negotiationDifficultyModal .negotiationDiscountTotal.fromAirport .total').removeClass('warning')
                    }
                    var toDiscount = negotiationInfo.finalToDiscountValue
                    displayDiscountValue = Math.round(toDiscount >= 0 ? toDiscount * 100 : toDiscount * -100)
                    $('#negotiationDifficultyModal .negotiationDiscountTotal.toAirport .total').text(displayDiscountValue + "%")
                    if (toDiscount < 0) {
                        $('#negotiationDifficultyModal .negotiationDiscountTotal.toAirport .total').addClass('warning')
                    } else {
                        $('#negotiationDifficultyModal .negotiationDiscountTotal.toAirport .total').removeClass('warning')
                    }

                    //total difficulty after discount
                    var difficultyTotalText = fromAirportRequirementValue.toFixed(2) + " * " + Math.round((1 - fromDiscount) * 100) + "% + " + toAirportRequirementValue.toFixed(2) + " * " + Math.round((1 - toDiscount) * 100) + "% = " + negotiationInfo.finalRequirementValue.toFixed(2)
                    $('#linkConfirmationModal .negotiationInfo .negotiationDifficultyTotal').text(negotiationInfo.finalRequirementValue.toFixed(2))

                    availableActionPoints = result.actionPoints
                    if (negotiationInfo.finalRequirementValue > availableActionPoints) {
                        $('#linkConfirmationModal .negotiationInfo img.info').hide();
                        difficultyTotalText += ' (Not enough action points)'
                        $('#linkConfirmationModal .negotiationInfo .error').show();
                    } else if (negotiationInfo.finalRequirementValue > 11) {
                        $('#linkConfirmationModal .negotiationInfo img.info').hide();
                        difficultyTotalText += ' (Too difficult to negotiate)'
                        $('#linkConfirmationModal .negotiationInfo .error').show();
                    } else {
                        $('#linkConfirmationModal .negotiationInfo .error').hide();
                        $('#linkConfirmationModal .negotiationInfo img.info').show();
                    }

                    $('#negotiationDifficultyModal .negotiationInfo .negotiationDifficultyTotal').text(difficultyTotalText)

                    //finish updating the negotiationDifficultyModal

                    $('#linkConfirmationModal div.delegateStatus').html(
                        '<span>Action Points available: <b>' + availableActionPoints.toFixed(1) + '</b></span>'
                    )

                    if (availableActionPoints > 0) {
                        addMinimumRequiredActionPoints()
                    } else {
                        updateAssignedActionPoints(0)
                    }

                    if (result.rejection) {
                        $('#linkConfirmationModal div.negotiationInfo .rejection .reason').text(result.rejection)
                        $('#linkConfirmationModal div.negotiationInfo .rejection').css('display', 'flex')
                        $('#linkConfirmationModal .negotiateButton').hide()
                    } else {
                        $('#linkConfirmationModal div.negotiationInfo .rejection').hide()
                        $('#linkConfirmationModal .negotiateButton').show()
                    }

                    $('#linkConfirmationModal .confirmButton').hide()
                    $('#linkConfirmationModal div.negotiationInfo').show()
                } else { //then no need for negotiation
                    $('#linkConfirmationModal .negotiateButton').hide()
                    $('#linkConfirmationModal .confirmButton').show()
                }
                $('#linkConfirmationModal div.controlButtons').show()
            }
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}




function refreshAssignedAirplanesBar(container, assignedAirplanes) {
	$(container).empty()

	$.each(assignedAirplanes, function(key, entry) {
		var airplane = entry.airplane
		var icon = getAirplaneIcon(airplane)
		icon.css("padding", 0)
		icon.css("float", "left")

		$(container).append(icon)
	})
}

function refreshSavedLink(savedLink) {
    removeTempPath()

    // Store update first so refreshFlightPath gets correct profit/revenue for coloring
    if (loadedLinksById[savedLink.id]) {
        Object.assign(loadedLinksById[savedLink.id], savedLink)
        refreshFlightPath(loadedLinksById[savedLink.id], true)
    } else {
        computeLinkDerivedProperties([savedLink])
        loadedLinks.push(savedLink)
        loadedLinksById[savedLink.id] = savedLink
        AirlineMap.drawFlightPath(savedLink)
    }
    selectLinkFromMap(savedLink.id, false)

    setActiveDiv($('#linkDetails'))
    hideActiveDiv($('#extendedPanel #airplaneModelDetails'))

    // Fetch fresh balance/AP for toolbar
    $.ajax({
        type: 'GET',
        url: "/airlines/" + activeAirline.id,
        dataType: 'json',
        success: function(airline) {
            Object.keys(airline).forEach(function(key) { activeAirline[key] = airline[key] })
            refreshTopBar(activeAirline)
        }
    })

    if ($("#linkDetails").is(":visible")) {
        refreshLinkDetails(savedLink.id)
    }
    if ($('#linksCanvas').is(':visible')) {
        loadLinksTable(null, true)
    }
}


function negotiationAnimation(savedLink, callback, callbackParam) {
    var negotiationResult = savedLink.negotiationResult
    $('#negotiationAnimation .negotiationIcons').empty()
    const progressBarWidth = 400
	$('#negotiationThreshold').css("width", progressBarWidth * (1 - negotiationResult.passingScore / 100))
	animateProgressBar($('#negotiationAnimation .negotiationBar'), 0, 0, false)
	$('#negotiationAnimation .negotiationDescriptions').text('')
	$('#negotiationAnimation .negotiationBonus').text('')
	$('#negotiationAnimation .negotiationResult').hide()

	var gaugeValue = 0

	var index = 0
	$('#negotiationAnimation .successRate').text(Math.floor(negotiationResult.odds * 100))

	$(negotiationResult.sessions).each( function(index, value) {
        $('#negotiationAnimation .negotiationIcons').append("<img src='/assets/images/icons/balloon-ellipsis.png' style='padding : 5px;'>")
    });
	var animationInterval = setInterval(function() {
        var value = $(negotiationResult.sessions)[index ++] / 2 //cutting in half because halving intervals
        var icon
 		var description
        if (value > 14) {
            icon = "smiley-kiss.png"
            description = "Awesome +" + Math.round(value)
        } else if (value > 11) {
            icon = "smiley-lol.png"
            description = "Great +" + Math.round(value)
        } else if (value > 8) {
            icon = "smiley.png"
            description = "Good +" + Math.round(value)
        } else if (value > 5) {
            icon = "smiley-neutral.png"
            description = "Soso +" + Math.round(value)
        } else if (value > 0) {
            icon = "smiley-sad.png"
            description = "Bad +" + Math.round(value)
        } else {
            icon = "smiley-cry.png"
            description = "Terrible " + Math.round(value)
        }
        $('#negotiationAnimation .negotiationIcons img:nth-child(' + index + ')').attr("src", "/assets/images/icons/" + icon)
        $('#negotiationAnimation .negotiationDescriptions').text(description)

        gaugeValue += (value * 2) //modified interval number
        var percentage = gaugeValue

        var callback
        if (index == negotiationResult.sessions.length) {
            callback = function() {
                           var result
                           if (negotiationResult.isGreatSuccess) {
                            result = "Great Success"
                           } else if (negotiationResult.isSuccessful) {
                            result = "Success"
                           } else {
                            result = "Failure"
                           }
                           if (savedLink.negotiationBonus) {
                             $('#negotiationAnimation .negotiationBonus').text(savedLink.negotiationBonus.description)
                           } else if (savedLink.nextNegotiationDiscount) {
                             $('#negotiationAnimation .negotiationBonus').text(savedLink.nextNegotiationDiscount)
                           }

                           $('#negotiationAnimation .negotiationResult .result').text(result)
                           $('#negotiationAnimation .negotiationResult').show()

                            if (negotiationResult.isGreatSuccess) {
                                $('#negotiationAnimation').addClass('transparentBackground')
                                startFirework(2000, savedLink.negotiationBonus.intensity)
                            } else if (negotiationResult.isSuccessful) {
                               showConfetti($("#negotiationAnimation"))
                           }
                       };
        }
        animateProgressBar($('#negotiationAnimation .negotiationBar'), percentage, 250, callback, negotiationResult.isSuccessful)

        if (index == negotiationResult.sessions.length) {
            clearInterval(animationInterval);
        }
	}, 200)


	if (callback) {
		$('#negotiationAnimation .close, #negotiationAnimation .result').on("click.custom", function() {
		    if (negotiationResult.isGreatSuccess) {
                $('#negotiationAnimation').removeClass('transparentBackground')
                stopFirework()
		    } else if (negotiationResult.isSuccessful) {
                removeConfetti($("#negotiationAnimation"))
            }
            callback(callbackParam)
		})
    } else {
        $('#negotiationAnimation .close, #negotiationAnimation .result').off("click.custom")
    }

	$('#negotiationAnimation').show()
}

function addAirlineTooltip($target, airlineId, slogan, airlineName) {
    var $airlineTooltip = $('<div style="min-width: 150px;"></div>')
    var $liveryImg = $('<img style="max-height: 100px; max-width: 250px; display: none; margin: auto;" loading="lazy">').appendTo($airlineTooltip)
    $liveryImg.attr('src', 'airlines/' + airlineId + "/livery")
    var $sloganDiv =$("<h5></h5>").appendTo($airlineTooltip)
    if (slogan) {
        $sloganDiv.text(slogan)
    } else {
        $sloganDiv.text(airlineName)
    }
    addTooltipHtml($target, $airlineTooltip, {'top' : '100%'})
    $target.on('mouseenter', function() {
        $liveryImg.show()
    })
}

function loadAndWatchAirlineNotes(airlineId) {
    airlineId = airlineId || activeAirline.id;
    const notesOffice = document.getElementById('airlineNotes');
    const notesLink = document.getElementById('linkNotes');
    const notesAirport = document.getElementById('airportNotes');

    // Load the current note
    fetch(`/airlines/${airlineId}/notes`)
        .then(response => {
            if (!response.ok) {
                throw new Error(`Failed to fetch notes: ${response.statusText}`);
            }
            return response.json();
        })
        .then(data => {
            notes = data || {}; // Initialize notes if not present
            notesOffice.value = data?.airlineNotes[0] || ''; // Populate office textarea with the note
        })
        .catch(error => {
            console.error('Error loading notes:', error);
        });

    let debounceTimeout;
    const debounceDelay = 1000;

    notesOffice.addEventListener('input', () => {
        clearTimeout(debounceTimeout);
        debounceTimeout = setTimeout(() => {
            const sanitizedNote = notesOffice.value.replace(/</g, "&lt;").replace(/>/g, "&gt;"); // Basic sanitization

            fetch(`/airlines/${airlineId}/notes`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ note: sanitizedNote }),
            })
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`Failed to save note: ${response.statusText}`);
                    }
                    console.log('Note saved successfully');
                })
                .catch(error => {
                    console.error('Error saving note:', error);
                });
        }, debounceDelay);
    });

    notesLink.addEventListener('input', () => {
        clearTimeout(debounceTimeout);
        debounceTimeout = setTimeout(() => {
			if (!selectedLink) return;

			const sanitizedNote = linkNotes.value.replace(/</g, "&lt;").replace(/>/g, "&gt;"); // Basic sanitization
			const existsingNote = notes.linkNotes.find(note => note.id === Number(selectedLink));
			if (existsingNote) {
				existsingNote.note = sanitizedNote;
			} else {
				notes.linkNotes.push({id: Number(selectedLink), note: sanitizedNote});
			}

            fetch(`/airlines/${airlineId}/notes/link/${selectedLink}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json; charset=utf-8',
                },
                body: JSON.stringify({ note: sanitizedNote }),
            })
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`Failed to save note: ${response.statusText}`);
                    }
                    console.log('Note saved successfully');
                })
                .catch(error => {
                    console.error('Error saving note:', error);
                });
        }, debounceDelay);
    });

    notesAirport.addEventListener('input', () => {
        clearTimeout(debounceTimeout);
        debounceTimeout = setTimeout(() => {
			if (!activeAirportId) return;

			const sanitizedNote = notesAirport.value.replace(/</g, "&lt;").replace(/>/g, "&gt;"); // Basic sanitization
			const existsingNote = notes.airportNotes.find(note => note.id === Number(activeAirportId));
			if (existsingNote) {
				existsingNote.note = sanitizedNote;
			} else {
				notes.airportNotes.push({id: Number(activeAirportId), note: sanitizedNote});
			}

            fetch(`/airlines/${airlineId}/notes/airport/${activeAirportId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json; charset=utf-8',
                },
                body: JSON.stringify({ note: sanitizedNote }),
            })
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`Failed to save note: ${response.statusText}`);
                    }
                    console.log('Note saved successfully');
                })
                .catch(error => {
                    console.error('Error saving note:', error);
                });
        }, debounceDelay);
    });
}

function toggleAllLinks(source) {
    var checkboxes = $("#linksTable .link-checkbox")
    var isChecked = $(source).prop('checked')
    checkboxes.prop('checked', isChecked)

    if (isChecked) {
        // Add all visible links to set
        checkboxes.each(function () {
            var linkId = $(this).closest('.table-row').data('link-id')
            selectedLinkIds.add(linkId)
        })
    } else {
        // Remove all visible links from set (or clear all?)
        // If "Select All" is unchecked, usually means clear selection or clear visible.
        // For safety, let's clear visible ones from the set.
        checkboxes.each(function () {
            var linkId = $(this).closest('.table-row').data('link-id')
            selectedLinkIds.delete(linkId)
        })
    }
    updateLinksActionBar()
}

function toggleLinkSelection(linkId, checkbox) {
    if (checkbox.prop('checked')) {
        selectedLinkIds.add(linkId)
    } else {
        selectedLinkIds.delete(linkId)
        $("#toggleAllLinks").prop('checked', false)
    }
    updateLinksActionBar()
}

function updateLinksActionBar() {
    var count = selectedLinkIds.size
    $("#selectedLinksCount").text(count + " selected")
    if (count > 0) {
        $("#linksActionBar").fadeIn(200)
    } else {
        $("#linksActionBar").fadeOut(200)
        $("#toggleAllLinks").prop('checked', false)
    }
    var $qualityBar = $("#bulkUpdateQualityBar")
    generateImageBar($qualityBar.data("emptyIcon"), $qualityBar.data("fillIcon"), 5, $qualityBar, $("#bulkUpdateQualityLevel"), null, null, null, 20)
}

function submitBulkUpdateQuality() {
    var qualityLevel = parseInt($("#bulkUpdateQualityLevel").val())
    var qualityRaw = qualityLevel * 20
    var selectedCount = selectedLinkIds.size
    if (selectedCount === 0) return

    promptConfirm("Update quality of " + selectedCount + " selected links to " + qualityLevel + " stars?", function () {
        executeBulkUpdateQuality(qualityRaw)
    })
}

function bulkUpdateColor(color) {
    selectedLinkIds.forEach(function (linkId) {
        if (color) {
            linkColors[linkId] = color
        } else {
            delete linkColors[linkId]
        }
    })
    localStorage.setItem('linkColors', JSON.stringify(linkColors))

    // Refresh table rows to show new color
    loadLinksTable()
}

function executeBulkUpdateQuality(qualityInt) {
    if (selectedLinkIds.size === 0) return;

    $.ajax({
        type: 'PUT',
        url: "/airlines/" + activeAirline.id + "/links/bulk-update-quality",
        data: JSON.stringify({ "linkIds": Array.from(selectedLinkIds), "quality": qualityInt }),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (result) {
            loadLinksTable(null, true);
            updateLinksActionBar()
        },
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            alert("Failed to update quality: " + errorThrown);
        }
    });
}

function promptBulkDelete() {
    if (selectedLinkIds.size === 0) return;

    var count = selectedLinkIds.size;
    $.ajax({
        type: 'POST',
        url: "/airlines/" + activeAirline.id + "/links/bulk-delete-details",
        data: JSON.stringify({ "linkIds": Array.from(selectedLinkIds) }),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (result) {
            var actionPointRefund = result.actionPointRefund;
            var capacityToRemove = result.capacityToRemove;

            var message = "Are you sure you want to delete " + count + " selected links?<br><br>";
            message += "&bull; <b>Capacity to remove:</b> " + commaSeparateNumber(capacityToRemove) + " seats<br>";
            message += "&bull; <b>Delegates to refund:</b> " + actionPointRefund;

            promptConfirm(message, function () {
                executeBulkDelete();
            });
        },
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            alert("Failed to get bulk delete details: " + errorThrown);
        }
    });
}

function executeBulkDelete() {
    $.ajax({
        type: 'DELETE',
        url: "/airlines/" + activeAirline.id + "/links/bulk-delete",
        data: JSON.stringify({ "linkIds": Array.from(selectedLinkIds) }),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (result) {
            selectedLinkIds.clear();
            updateLinksActionBar();
            loadLinksTable(null, true);

            updateAirlineInfo(activeAirline.id);
            if ($('#linksCanvas').is(':visible') && typeof updateLinksInfo === 'function') {
                updateLinksInfo();
            }
        },
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            alert("Failed to delete links: " + errorThrown);
        }
    });
}