var loadedModelsById = {}
var loadedModelsOwnerInfo = []
var loadedUsedAirplanes = []
var selectedModelId
var selectedModel
var closedAirplaneModals = []
var currentAirplaneLoadCall
var isAirplaneSelectionMode = false; // Toggles between selection and details view
var storeSelectedAirplaneIds = [];
/**
 * Find a model object by any attribute
 */
function getAirplaneModelByAttribute(key, attribute = 'id') {
    if (typeof loadedModelsById === 'undefined' || loadedModelsById == null) return null;
    for (const model of Object.values(loadedModelsById)) {
        if (!model) continue;
        if (model[attribute] == key) return model;
    }
    return null;
}

async function loadAirplaneModels(airlineId) {
  if (!airlineId) {
    if (!activeAirline) return;
    airlineId = activeAirline.id
  }
  try {
    // 1. Fetch static and dynamic models concurrently
    const [staticRes, dynamicRes] = await Promise.all([
      fetch('/airplane-models'),
      fetch(`/airlines/${airlineId}/airplane-models`)
    ]);

    if (!staticRes.ok || !dynamicRes.ok) {
      throw new Error('Network request failed.');
    }

    const [staticModels, discountModels] = await Promise.all([
      staticRes.json(),
      dynamicRes.json()
    ]);

    // 2. Create a lookup map for dynamic data by model ID
    const discountsById = new Map(
      discountModels.map(model => [model.id, model])
    );

    // 3. Merge static models with their dynamic data
    loadedModelsById = staticModels.reduce((acc, model) => {
      const { id, ...dynamicData } = discountsById.get(model.id) || {};
      acc[model.id] = { ...model, ...dynamicData };
      return acc;
    }, {});

  } catch (error) {
    console.error("Failed to load airplane models:", error);
    return {}; // Return empty object on failure
  }
}

//load model info on airplanes that matches the model ID
function loadAirplaneModelOwnerInfoByModelId(modelId) {
    var airlineId = activeAirline.id
	$.ajax({
		type: 'GET',
		url: "/airlines/"+ airlineId + "/airplanes/model/" + modelId,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    async: false,
	    success: function(result) {
            existingInfo = loadedModelsById[modelId] //find the existing info
            //update the existing info with the newly loaded result
            var newInfo = result[modelId]
            if (newInfo) { //then has owned airplanes with this model
                existingInfo.assignedAirplanes = newInfo.assignedAirplanes
	            existingInfo.assignedAirplanes.sort(sortByProperty('condition'))
                existingInfo.availableAirplanes = newInfo.availableAirplanes
                existingInfo.availableAirplanes.sort(sortByProperty('condition'))
                existingInfo.constructingAirplanes = newInfo.constructingAirplanes

                existingInfo.totalOwned = existingInfo.assignedAirplanes.length + existingInfo.availableAirplanes.length + existingInfo.constructingAirplanes.length
	        } else { //no longer owned this model
	            existingInfo.assignedAirplanes = []
                existingInfo.availableAirplanes = []
                existingInfo.constructingAirplanes = []
                existingInfo.totalOwned = 0
	        }
	        existingInfo.isFullLoad = true
        },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

//load model info on all airplanes
function loadAirplaneModelOwnerInfo() {
	var airlineId = activeAirline.id
	loadedModelsOwnerInfo = []
	$.ajax({
		type: 'GET',
		url: "/airlines/"+ airlineId + "/airplanes?simpleResult=true&groupedResult=true",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    async: false,
	    success: function(ownedModels) { //a list of model with airplanes
	    	//add airplane ownership info to existing model entries
	    	$.each(loadedModelsById, function(modelId, model) {
	    	    var ownedAirplanesInfo = ownedModels[modelId]
	    	    if (ownedAirplanesInfo) { //then own some airplanes of this model
	    	        model.assignedAirplanes = ownedAirplanesInfo.assignedAirplanes
	    	        model.availableAirplanes = ownedAirplanesInfo.availableAirplanes
	    	        model.constructingAirplanes = ownedAirplanesInfo.constructingAirplanes
	    	        model.assignedAirplanes.sort(sortByProperty('condition'))
                    model.availableAirplanes.sort(sortByProperty('condition'))
	    	    } else {
	    	        model.assignedAirplanes = []
                    model.availableAirplanes = []
                    model.constructingAirplanes = []
	    	    }
                model.totalOwned = model.assignedAirplanes.length + model.availableAirplanes.length + model.constructingAirplanes.length
       			loadedModelsOwnerInfo.push(model)
	    	})
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

let selectedAircraftTab; //todo: refactor calculator to be independent and remove this global variable

async function showAirplaneCanvas(selectedAircraftTab = 'hangar', airplaneModel = null) {
	setActiveDiv($("#airplaneCanvas"))

    await loadAirplaneModels()
    loadAirplaneModelOwnerInfo()

    const airplaneModelTable = $('#airplaneModelTable');
    if (!airplaneModelTable.data('delegation-set')) {
        airplaneModelTable.on('click', '.table-row', function() {
            selectAirplaneModel(loadedModelsById[$(this).data('model-id')]);
        });
        airplaneModelTable.data('delegation-set', true);
    }

    const aircraftAvailableOptions = {
        "name": {}, "family": {}, "airplaneType": {}, "capacity": {},
        "quality": {}, "range": {}, "runwayRequirement": {}, "speed": {},
    };
    $.each(loadedModelsOwnerInfo, function(index, m) {
        aircraftAvailableOptions.name[m.name] = m.name;
        aircraftAvailableOptions.family[m.family] = m.family;
        aircraftAvailableOptions.airplaneType[m.airplaneType] = m.airplaneType;
        aircraftAvailableOptions.capacity[m.capacity] = Number(m.capacity);
        aircraftAvailableOptions.quality[m.quality] = Number(m.quality);
        aircraftAvailableOptions.speed[m.speed] = Number(m.speed);
        aircraftAvailableOptions.range[m.range] = Number(m.range);
        aircraftAvailableOptions.runwayRequirement[m.runwayRequirement] = m.runwayRequirement;
    });
    updateColumnFilterOptions(aircraftAvailableOptions, 'aircraft');

    if (selectedAircraftTab === 'discounts') {
        document.querySelector('.details.market').style.display = 'none';
        document.querySelector('.details.hangar').style.display = 'none';
        document.querySelector('.details.discounts').style.display = 'block';

    } else if (selectedAircraftTab === 'market') {
        document.querySelector('.details.market').style.display = 'block';
        document.querySelector('.details.hangar').style.display = 'none';
        document.querySelector('.details.discounts').style.display = 'none';

        const $hideForbidden = $('#toggleHideForbiddenPlanes');
        if (!$hideForbidden.data('listener-attached')) {
            $hideForbidden.on('change', function() {
                updateAirplaneModelTable();
            });
            $hideForbidden.data('listener-attached', true);
        }

        updateAirplaneModelTable();
        showAirplaneModelTableFilters()
    } else {
        document.querySelector('.details.hangar').style.display = 'block';
        document.querySelector('.details.market').style.display = 'none';
        document.querySelector('.details.discounts').style.display = 'none';
        populateMaintenanceFactor()
        populatePreferredSuppliers()
        populateHangerModels()
        hideAirplaneModelTableFilters()
    }

    if (airplaneModel) {
        selectAirplaneModel(airplaneModel)
    }

    $("#ownedAirplaneDetailModal").data("reloadFunction", function() {
        $("#ownedAirplaneDetailModal").data("hasChange", true) //so closing this modal always force update. it's hard to detect whether there was any change before the reload...
        $.ajax(currentAirplaneLoadCall)
    })
}

function updateAirplaneModelTable(sortProperty, sortOrder) {
    if (!sortProperty && !sortOrder) {
        var selectedSortHeader = $('#airplaneModelSortHeader .cell.selected')
        sortProperty = selectedSortHeader.data('sort-property')
        sortOrder = selectedSortHeader.data('sort-order')
    }
	//sort the list
	loadedModelsOwnerInfo.sort(sortByProperty(sortProperty, sortOrder == "ascending"))

	var airplaneModelTable = $("#airplaneModelTable")
	airplaneModelTable.children("div.table-row").remove()

    const state = tableFilterState.getTableState('aircraft');

     //used for pricing calculation
    const {rangeRequirement, airportFromSizeRequirement, airportToSizeRequirement} = airplaneModelCalculator()

    const showForbidden = $('#toggleHideForbiddenPlanes').is(':checked');
    const rowsHtml = [];
    let selectedModel = null;

	$.each(loadedModelsOwnerInfo, function(index, modelOwnerInfo) {
        if (!showForbidden && modelOwnerInfo.rejection && modelOwnerInfo.rejection.length > 0) {
            return;
        }

        modelOwnerInfo.costPerPax = calcCostPerPax(modelOwnerInfo, rangeRequirement, airportFromSizeRequirement, airportToSizeRequirement)
        modelOwnerInfo.trips = calcFreq(modelOwnerInfo, rangeRequirement)

        let isFiltered = false;
		Object.entries(state.selectedColumnFilter).forEach(([property, filterValues]) => {
			if (!Array.isArray(filterValues) || filterValues.length < 1) {
				return;
			}
			if (!filterValues.includes(String(modelOwnerInfo[property]))) {
				isFiltered = true;
			}
		});
		if (isFiltered) {
			return;
		}

        const selectedClass = selectedModelId == modelOwnerInfo.id ? ' selected' : '';
        if (selectedModelId == modelOwnerInfo.id) {
            selectedModel = modelOwnerInfo;
        }

        const nameCell = `<div class='cell'>${modelOwnerInfo.name}</div>`;

        rowsHtml.push(
            `<div class='table-row clickable${selectedClass}' data-model-id='${modelOwnerInfo.id}'>` +
            nameCell +
            `<div class='cell'>${modelOwnerInfo.family}</div>` +
            `<div class='cell'>${modelOwnerInfo.airplaneType}</div>` +
            `<div class='cell' align='right'>$${commaSeparateNumber(modelOwnerInfo.price)}</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.capacity}</div>` +
            `<div class='cell' align='right'>${getGradeStarsImgs(modelOwnerInfo.quality)}</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.range} km</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.trips}</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.ascentBurn}</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.cruiseBurn}</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.lifespan / 52} yrs</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.speed} km/h</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.runwayRequirement} m</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.assignedAirplanes.length}/${modelOwnerInfo.availableAirplanes.length}/${modelOwnerInfo.constructingAirplanes.length}</div>` +
            `<div class='cell' align='right'>${modelOwnerInfo.total}</div>` +
            `<div class='cell' align='right'>$${(modelOwnerInfo.costPerPax).toFixed()}</div>` +
            `</div>`
        );
	});

    airplaneModelTable.append(rowsHtml.join(''));
    if (selectedModel) {
        selectAirplaneModel(selectedModel);
    }
}

function updateAirplaneModelCalculator() {
    if (selectedAircraftTab = 'market') {
        updateAirplaneModelTable() //refresh the table which triggers the calculator update
    } else {
        airplaneModelCalculator();
    }
}

function airplaneModelCalculator() {
    const rangeRequirement = document.getElementById("AMC_distance").value
    const airportFromSizeRequirement = document.getElementById("AMC_airportFromSize").value || 0
    const airportToSizeRequirement = document.getElementById("AMC_airportToSize").value || 0
    // amc_age = document.getElementById("AMC_age").value || 0

    document.getElementById("AMC_airportFromSizeLabel").innerText = airportFromSizeRequirement
    document.getElementById("AMC_airportToSizeLabel").innerText = airportToSizeRequirement

    if (selectedModel) {
        const costPerPax = calcCostPerPax(selectedModel, rangeRequirement, airportFromSizeRequirement, airportToSizeRequirement)
        document.getElementById("AMC_pricePax").innerText = `Estimated cost per pax: $${costPerPax.toFixed(2)}`
    } else {
        document.getElementById("AMC_pricePax").innerText = `-`
    }
    return {rangeRequirement, airportFromSizeRequirement, airportToSizeRequirement};
}

function calcFreq(airplane, distance) {
    if (airplane.range < distance) {
        return "-"
    }
    const flightTime = calcFlightTime(airplane, distance);
    return Math.floor(gameConstants.aircraft.maxFlightMin / ((flightTime + airplane.turnaroundTime) * 2));
}

function calcCostPerPax(airplane, distance, airportSizeFrom, airportSizeTo) {
    const MAX_FLIGHT_MIN = gameConstants.aircraft.maxFlightMin;
    const FUEL_UNIT_COST = gameConstants.linkCosts.fuelCost

    const flightTime = calcFlightTime(airplane, distance);
    const frequency = Math.floor(MAX_FLIGHT_MIN / ((flightTime + airplane.turnaroundTime) * 2));
    const aircraftFlightTime = frequency * 2 * (flightTime + airplane.turnaroundTime);
    const availableFlightMinutes = MAX_FLIGHT_MIN - aircraftFlightTime;
    const utilisation = aircraftFlightTime / (MAX_FLIGHT_MIN - availableFlightMinutes);
    const planeUtilisation = (MAX_FLIGHT_MIN - availableFlightMinutes) / MAX_FLIGHT_MIN;

    const decayRate = 100 / (airplane.lifespan * 3) * (1 + 2 * planeUtilisation);
    const depreciationRate = Math.floor(airplane.price * (decayRate / 100) * utilisation);

    const fuelCost = frequency * calcFuelBurn(airplane, distance) * FUEL_UNIT_COST;
    const fuelTax = fuelCost * (activeAirline.fuelTaxRate / 100);

    const airportFees = calcAirportFees(airplane, airportSizeFrom, airportSizeTo) * frequency;

    const cost = (fuelCost + fuelTax + depreciationRate + airportFees) / (airplane.capacity * frequency);
    return cost;
}

function calcFlightTime(airplaneModel, distance) {
  let timeToCruise = gameConstants.aircraft.timeToCruise[airplaneModel.airplaneType] ?? Math.sqrt(airplaneModel.capacity) + 10;
  return parseInt(timeToCruise + distance * 60 / airplaneModel.speed);
}

function calcFuelBurn(airplane, distance){
    const loadFactor = 1 //assuming 100%
    const flightTime = calcFlightTime(airplane, distance);
    const distanceFactor = 1 + 0.1 * Math.pow(flightTime / 60, gameConstants.linkCosts.fuelDistanceExponent);
    const fuelBurn = airplane.capacity * distanceFactor * (airplane.ascentBurn * loadFactor + airplane.cruiseBurn * distance / 800);
    return fuelBurn;
}

function calcAirportFees(airplane, airportSizeFrom, airportSizeTo){
    const baseSlotFee = (airportSize) => (airplane.airplaneType === 'helicopter') ? 2 : gameConstants.airportFees.airportSize[airportSize - 1]; //index airportSize to array index
    const perSeatFee = (airportSize) => airportSize - 1;
    const multiplier = gameConstants.airportFees.airplaneType[airplane.airplaneType]
    return baseSlotFee(airportSizeFrom) * multiplier + baseSlotFee(airportSizeTo) * multiplier + perSeatFee(airportSizeFrom) * airplane.capacity + perSeatFee(airportSizeTo) * airplane.capacity;
}

function updateUsedAirplaneTable(sortProperty, sortOrder) {
	var usedAirplaneTable = $("#airplaneCanvas #usedAirplaneTable")
	usedAirplaneTable.children("div.table-row").remove()
	
	//sort the list
	loadedUsedAirplanes.sort(sortByProperty(sortProperty, sortOrder == "ascending"))
	
	$.each(loadedUsedAirplanes, function(index, usedAirplane) {
		var row = $("<div class='table-row'></div>")
		row.data("airplane", usedAirplane)
		row.append("<div class='cell'>" + usedAirplane.id + "</div>")

		row.append("<div class='cell'>" +  getAirlineSpan(usedAirplane.ownerId, usedAirplane.ownerName) + "</div>")

		var priceColor
		var dealerRatio = usedAirplane.purchasePrice > 0 ? usedAirplane.dealerValue / usedAirplane.purchasePrice : 1
		if (dealerRatio >= 1.1) { //expensive
		    priceColor = "#D46A6A"
		} else if (dealerRatio <= 0.9) { //cheap
		    priceColor = "#68A357"
		}
		var priceDiv = $("<div class='cell' align='right'>$" + commaSeparateNumber(usedAirplane.dealerValue) + "</div>")
		if (priceColor) {
		    priceDiv.css("color", priceColor)
	    }
		row.append(priceDiv)
		row.append("<div class='cell' align='right'>" + usedAirplane.condition.toFixed(2) + "%</div>")
		if (!usedAirplane.rejection) {
			row.append("<div class='cell' align='right'><img class='clickable' src='/assets/images/icons/airplane-plus.png' title='Purchase this airplane' onclick='promptBuyUsedAirplane($(this).closest(\".table-row\").data(\"airplane\"))'></div>")
		} else {
			row.append("<div class='cell' align='right'><img src='/assets/images/icons/prohibition.png' title='" + usedAirplane.rejection + "'/></div>")
		}
		usedAirplaneTable.append(row)
	});
	
	if (loadedUsedAirplanes.length == 0 ) {
		var row = $("<div class='table-row'></div>")
		row.append("<div class='cell'>-</div>")
		row.append("<div class='cell' align='right'>-</div>")
		row.append("<div class='cell' align='right'>-</div>")
		row.append("<div class='cell' align='right'>-</div>")
		row.append("<div class='cell' align='right'></div>")
		usedAirplaneTable.append(row)
	}
	
}

function toggleAirplaneModelTableSortOrder(sortHeader) {
	if (sortHeader.data("sort-order") == "ascending") {
		sortHeader.data("sort-order", "descending")
	} else {
		sortHeader.data("sort-order", "ascending")
	}
	
	sortHeader.siblings().removeClass("selected")
	sortHeader.addClass("selected")
	
	updateAirplaneModelTable(sortHeader.data("sort-property"), sortHeader.data("sort-order"))
}

function toggleUsedAirplaneTableSortOrder(sortHeader) {
	if (sortHeader.data("sort-order") == "ascending") {
		sortHeader.data("sort-order", "descending")
	} else {
		sortHeader.data("sort-order", "ascending")
	}
	
	sortHeader.siblings().removeClass("selected")
	sortHeader.addClass("selected")
	
	updateUsedAirplaneTable(sortHeader.data("sort-property"), sortHeader.data("sort-order"))
}

function promptBuyUsedAirplane(airplane) {
    var buyAirplaneFunction = function(dummyQuantity, homeAirportId, selectedConfigurationId) {
        buyUsedAirplane(airplane.id, homeAirportId, selectedConfigurationId)
    }
    promptBuyAirplane(airplane.modelId, airplane.condition.toFixed(2), airplane.dealerValue, 0, null, false, buyAirplaneFunction)
}

function promptBuyNewAirplane(modelId, fromPlanLink, explicitHomeAirportId) {
    var buyAirplaneFunction = function(quantity, homeAirportId, selectedConfigurationId) {
        var callback
        if (fromPlanLink) {
            callback = function() {
                planLink($("#planLinkFromAirportId").val(), $("#planLinkToAirportId").val(), true)
                $("#planLinkModelSelect").data('explicitId', modelId) //force the plan link to use this value after buying a plane
            }
        }
        buyAirplane(modelId, quantity, homeAirportId, selectedConfigurationId, callback)
    }

    promptBuyAirplane(modelId, 100, loadedModelsById[modelId].price, loadedModelsById[modelId].constructionTime, explicitHomeAirportId, true, buyAirplaneFunction)
}

function updateAirplaneTotalPrice(totalPrice) {
    $('#buyAirplaneModal .totalPrice .value').text("$" + commaSeparateNumber(totalPrice))
    if (totalPrice == 0) {
        disableButton($('#buyAirplaneModal .add'), "Amount should not be 0")
    } else if (totalPrice > activeAirline.balance) {
        $('#buyAirplaneModal .add')
        disableButton($('#buyAirplaneModal .add'), "Not enough cash")
    } else {
        enableButton($('#buyAirplaneModal .add'))
    }
}

function validateAirplaneQuantity() {
    if ($('#buyAirplaneModal .quantity .input').val() === "") {
        return 0
    }
    //validate
    var quantity = $('#buyAirplaneModal .quantity .input').val()
    if (!($.isNumeric(quantity))) {
        quantity = 1
    }
    quantity = Math.floor(quantity)

    if (quantity < 1) {
        quantity = 1
    }

    $('#buyAirplaneModal .quantity .input').val(quantity)
    return quantity
}

function promptBuyAirplane(modelId, condition, price, deliveryTime, explicitHomeAirportId, multipleAble, buyAirplaneFunction) {
    var model = loadedModelsById[modelId]
    if (model.imageUrl) {
        const imageLocation = '/assets/images/airplanes/' + model.name.replace(/\s+/g, '-').toLowerCase() + '.webp'
        const fallbackLocation = '/assets/images/airplanes/' + model.name.replace(/\s+/g, '-').toLowerCase() + '.png'
        $('#buyAirplaneModal .modelIllustration source').attr('srcset', imageLocation)
        $('#buyAirplaneModal .modelIllustration img').attr('src', fallbackLocation)
        $('#buyAirplaneModal .modelIllustration a').attr('href', model.imageUrl)
        $('#buyAirplaneModal .modelIllustration').show()
    } else {
        $('#buyAirplaneModal .modelIllustration').hide()
    }

    $('#buyAirplaneModal .modelName').text(model.name)
    if (deliveryTime == 0) {
		$('#buyAirplaneModal .delivery').text("immediate")
		$('#buyAirplaneModal .delivery').removeClass('warning')
		$('#buyAirplaneModal .add').text('Purchase')
	} else {
		$('#buyAirplaneModal .delivery').text(deliveryTime + " weeks")
        $('#buyAirplaneModal .delivery').addClass('warning')
        $('#buyAirplaneModal .add').text('Place Order')
	}

	$('#buyAirplaneModal .price').text("$" + commaSeparateNumber(price))
	$('#buyAirplaneModal .condition').text(condition + "%")

	if (multipleAble) {
	    $('#buyAirplaneModal .quantity .input').val(1)
	    $('#buyAirplaneModal .quantity .input').on('input', function(e){
	        var quantity = validateAirplaneQuantity()
            updateAirplaneTotalPrice(quantity * price)
        });
	    $('#buyAirplaneModal .quantity').show()

	    updateAirplaneTotalPrice(1 * price)
	    $('#buyAirplaneModal .totalPrice').show()
	} else {
	    $('#buyAirplaneModal .quantity').hide()
	    $('#buyAirplaneModal .totalPrice').hide()
	    enableButton($('#buyAirplaneModal .add'))
	}

    var homeOptionsSelect = $("#buyAirplaneModal .homeOptions").empty()
    var hasValidBase = false
    $.each(activeAirline.baseAirports, function(index, baseAirport) {
        if (baseAirport.airportRunwayLength >= model.runwayRequirement) {
            var option = $("<option value='" + baseAirport.airportId + "'>" + getAirportText(baseAirport.city, baseAirport.airportCode) + "</option>")
            if (baseAirport.headquarter) {
                homeOptionsSelect.prepend(option)
            } else {
                homeOptionsSelect.append(option)
            }
            if (explicitHomeAirportId) { //if an explicit home is provided
                if (explicitHomeAirportId == baseAirport.airportId) {
                    option.attr("selected", "selected")
                }
            } else { //otherwise look for HQ
                if (baseAirport.airportId == activeAirline.headquarterAirport.airportId) {
                    option.attr("selected", "selected")
                }
            }
            hasValidBase = true
        }

        if (hasValidBase) {
            enableButton($('#buyAirplaneModal .add'))
        } else {
            disableButton($('#buyAirplaneModal .add'), "No base with runway >= " + model.runwayRequirement + "m")
        }
    })
    var airlineId = activeAirline.id

    $.ajax({
        type: 'GET',
        url: "/airlines/" + airlineId + "/configurations?modelId=" + modelId,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $("#buyAirplaneModal .configuration-options").empty()
            if (result.configurations.length == 0) {
                $('#buyAirplaneModal .table-row.seatConfiguration').hide()
                $("#buyAirplaneModal .configuration-options").removeData("selectedIndex")
            } else {
                $("#buyAirplaneModal .configuration-options").empty()
                $("#buyAirplaneModal .configuration-options").data("selectedIndex", 0)
                $("#buyAirplaneModal .configuration-options").data("optionCount", result.configurations.length)

                var defaultIndex = 0
                $.each(result.configurations, function(index, option) {
                    if (option.isDefault) {
                        defaultIndex = index
                    }
                })

                for (i = 0 ; i < result.configurations.length; i ++) {
                    //start from the default
                    var index = (i + defaultIndex) % result.configurations.length
                    var option = result.configurations[index]
                    var barDiv = $(`<div id='configuration-${option.id}' class='configuration-option' style="max-height:32px;"></div>`)
                    
                    $("#buyAirplaneModal .configuration-options").append(barDiv)
                    barDiv.data("configurationId", option.id)
                    if (i != 0) { //if not the matching one, hide by default
                        barDiv.hide()
                    }
                    plotSeatConfigurationBar(`configuration-${option.id}`, option, model.capacity, result.spaceMultipliers)
                }
                if (result.configurations.length == 1) { //then hide the arrows buttons
                    $("#buyAirplaneModal .seatConfiguration .button").hide()
                } else {
                    $("#buyAirplaneModal .seatConfiguration .button").show()
                }
                $('#buyAirplaneModal .table-row.seatConfiguration').show()
            }
            $('#buyAirplaneModal .add').unbind("click").bind("click", function() {
                var selectedIndex = $("#buyAirplaneModal .configuration-options").data("selectedIndex")
                var selectedConfigurationId
                if (selectedIndex === undefined) {
                    selectedConfigurationId = -1
                } else {
                    selectedConfigurationId = $($("#buyAirplaneModal .configuration-options").children()[selectedIndex]).data("configurationId")
                }

                buyAirplaneFunction($('#buyAirplaneModal .quantity .input').val(), $("#buyAirplaneModal .homeOptions").find(":selected").val(), selectedConfigurationId)
            })
            $('#buyAirplaneModal').fadeIn(200)
        },
         error: function(jqXHR, textStatus, errorThrown) {
                        console.log(JSON.stringify(jqXHR));
                        console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}


function buyAirplane(modelId, quantity, homeAirportId, configurationId, callback) {
	var airlineId = activeAirline.id
	var url = "/airlines/" + airlineId + "/airplanes?modelId=" + modelId + "&quantity=" + quantity + "&airlineId=" + airlineId + "&homeAirportId=" + homeAirportId + "&configurationId=" + configurationId
	$.ajax({
		type: 'PUT',
		data: JSON.stringify({}),
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(response) {
	    	refreshPanels(airlineId)
	    	if (callback) {
	    		callback()
	    	} else {
	    		showAirplaneCanvas()
	    	}
	    	closeModal($('#buyAirplaneModal'))
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

function sellAirplane(airplaneId) {
	$.ajax({
		type: 'DELETE',
		url: "/airlines/" + activeAirline.id + "/airplanes/" + airplaneId,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(response) {
	        refreshPanels(activeAirline.id)
	    	$("#ownedAirplaneDetailModal").data("hasChange", true)
	    	showAirplaneCanvas()
	    	closeModal($('#ownedAirplaneDetailModal'))
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

function sellUnassignedPlanes() {
	$.ajax({
		type: 'DELETE',
		url: "/airlines/" + activeAirline.id + "/airplanes/sell-unassigned",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(response) {
	        refreshPanels(activeAirline.id)
	    	showAirplaneCanvas()
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

function replaceAirplane(airplaneId) {
	var airlineId = activeAirline.id
	var url = "/airlines/" + airlineId + "/airplanes/" + airplaneId
	$.ajax({
		type: 'PUT',
		data: JSON.stringify({}),
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(response) {
	        $("#ownedAirplaneDetailModal").data("hasChange", true)
	        closeModal($('#ownedAirplaneDetailModal'))
	        refreshPanels(airlineId)
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

function buyUsedAirplane(airplaneId, homeAirportId, configurationId) {
	var airlineId = activeAirline.id
	var url = "/airlines/" + airlineId + "/used-airplanes/airplanes/" + airplaneId + "?homeAirportId=" + homeAirportId + "&configurationId=" + configurationId
	$.ajax({
		type: 'PUT',
		data: JSON.stringify({}),
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(response) {
	    	refreshPanels(airlineId)
	    	showAirplaneCanvas()
	    	closeModal($('#buyAirplaneModal'))
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


function updateModelInfo(modelId) {
	// loadAirplaneModels()
	model = loadedModelsById[modelId]
	var $stars = $(getGradeStarsImgs(model.quality))
	$('#airplaneModelDetails .selectedModel').val(modelId)
	$('#airplaneModelDetails #modelName').text(model.name)
	$('#airplaneModelDetails .modelFamily').text(model.family)
	$('#airplaneModelDetails #capacity').text(model.capacity)
	$('#airplaneModelDetails #maxCapacity').text(model.maxCapacity)
	$('#airplaneModelDetails #airplaneTypeQuality').empty()
	$('#airplaneModelDetails #airplaneTypeQuality').append($stars)
	$('#airplaneModelDetails #airplaneType').text(model.airplaneType)
	$('#airplaneModelDetails .turnaroundTime').text(model.turnaroundTime)
	$('#airplaneModelDetails .runwayRequirement').text(model.runwayRequirement)
	$('#airplaneModelDetails #ascentBurn').text(model.ascentBurn)
	$('#airplaneModelDetails #cruiseBurn').text(model.cruiseBurn)
	$('#airplaneModelDetails #range').text(model.range + "km")
	$('#airplaneModelDetails #speed').text(model.speed + "km/h")
	$('#airplaneModelDetails #lifespan').text(model.lifespan / 52 + " years")

	var $manufacturerSpan = $('<span>' + model.manufacturer + '&nbsp;</span>')
	$manufacturerSpan.append(getCountryFlagImg(model.countryCode))
	$('#airplaneModelDetails .manufacturer').empty()
	$('#airplaneModelDetails .manufacturer').append($manufacturerSpan)
	$('#airplaneModelDetails .price').text("$" + commaSeparateNumber(model.price))
	
	if (model.constructionTime == 0) {
		$('#airplaneModelDetails .delivery').text("immediate")
		$('#airplaneModelDetails .delivery').removeClass('warning')
		$('#airplaneModelDetails .add').text('Purchase')
	} else {
		$('#airplaneModelDetails .delivery').text(model.constructionTime + " weeks")
		$('#airplaneModelDetails .delivery').addClass('warning')
		$('#airplaneModelDetails .add').text('Place Order')
	}
	
	if (model.rejection) {
		disableButton($('#airplaneModelDetails .add'), model.rejection)
		$('#airplaneModelDetails .canPurchase').text(model.rejection)
	} else {
		enableButton($('#airplaneModelDetails .add'))
		$('#airplaneModelDetails .canPurchase').text('✓')
	}
}

function selectAirplaneModel(model) {
    if (typeof model === 'string') {
        model = getAirplaneModelByAttribute(model, 'name')
    }
	selectedModel = model
	selectedModelId = model.id
    // Update the URL to reflect the selected model without updating history
    history.replaceState(null, null, `/aircraft/${stringToPath(model.name)}`)
	$("#airplaneCanvas #airplaneModelTable div.selected").removeClass("selected")
	//highlight the selected model
	$("#airplaneCanvas #airplaneModelTable div[data-model-id='" + model.id +"']").addClass("selected")
	
	loadUsedAirplanes(model)

	//show basic airplane model details
	//model = loadedModels[modelId]
	if (model.imageUrl) {
		const imageLocation = '/assets/images/airplanes/' + model.name.replace(/\s+/g, '-').toLowerCase() + '.webp'
        const fallbackLocation = '/assets/images/airplanes/' + model.name.replace(/\s+/g, '-').toLowerCase() + '.png'
        $('#airplaneCanvas .modelIllustration source').attr('srcset', imageLocation)
        $('#airplaneCanvas .modelIllustration img').attr('src', fallbackLocation)
		$('#airplaneCanvas .modelIllustration a').attr('href', model.imageUrl)
		$('#airplaneCanvas .modelIllustration').show()
	} else {
		$('#airplaneCanvas .modelIllustration').hide()
	}
	var $stars = $(getGradeStarsImgs(model.quality))
	
	$('#airplaneCanvas .selectedModel').val(model.id)
	$('#airplaneCanvas .modelName').text(model.name)
	$('#airplaneCanvas .modelFamily').text(model.family)
	$('#airplaneCanvas #capacity').text(model.capacity)
	$('#airplaneCanvas #airplaneTypeQuality').empty()
    $('#airplaneCanvas #airplaneTypeQuality').append($stars)
	$('#airplaneCanvas #airplaneType').text(model.airplaneType)
	$('#airplaneCanvas .turnaroundTime').text(model.turnaroundTime)
	$('#airplaneCanvas .runwayRequirement').text(model.runwayRequirement)
	$('#airplaneCanvas #ascentBurn').text(model.ascentBurn)
	$('#airplaneCanvas #cruiseBurn').text(model.cruiseBurn)
	$('#airplaneCanvas #range').text(model.range + " km")
	$('#airplaneCanvas #speed').text(model.speed + " km/h")
	$('#airplaneCanvas #lifespan').text(model.lifespan / 52 + " years")

    $('#airplaneCanvas .manufacturer').empty()
    var $manufacturerSpan = $('<span>' + model.manufacturer + '&nbsp;</span>')
    $manufacturerSpan.append(getCountryFlagImg(model.countryCode))
    $('#airplaneCanvas .manufacturer').append($manufacturerSpan)

	if (model.originalPrice) {
	    var priceSpan = $("<span><span style='text-decoration: line-through'>$" + commaSeparateNumber(model.originalPrice) + "</span>&nbsp;$" + commaSeparateNumber(model.price) + "</span>")
	    priceSpan.append(getDiscountsTooltip(model.discounts.price))
	    $('#airplaneCanvas .price').html(priceSpan)
	} else {
	    $('#airplaneCanvas .price').text("$" + commaSeparateNumber(model.price))
    }
	
	if (model.constructionTime == 0) {
		$('#airplaneCanvas .delivery').text("immediate")
		$('#airplaneCanvas .delivery').removeClass('warning')
		$('#airplaneCanvas .add').text('Purchase')
	} else {
	    if (model.originalConstructionTime) {
            var deliverySpan = $("<span><span style='text-decoration: line-through'>" + model.originalConstructionTime + " weeks</span>&nbsp;" + model.constructionTime + " weeks</span>")
            deliverySpan.append(getDiscountsTooltip(model.discounts.construction_time))
            $('#airplaneCanvas .delivery').html(deliverySpan)
        } else {
            $('#airplaneCanvas .delivery').text(model.constructionTime + " weeks")
        }
		$('#airplaneCanvas .delivery').addClass('warning')
		$('#airplaneCanvas .add').text('Place Order')
	}

	if (model.quality === 10) {
        $('.aircraftNote').show()
        $('.aircraftNote .note').text('Five star aircraft can only have premium seats.')
    } else {
        $('.aircraftNote').hide()
    }

	if (model.rejection) {
		disableButton($('#airplaneCanvas .add'), model.rejection)
		$('#airplaneCanvas .canPurchase').text(model.rejection)
	} else {
		enableButton($('#airplaneCanvas .add'))
		$('#airplaneCanvas .canPurchase').text('✓')
	}
	loadAirplaneModelStats(model)
    loadAircraftModelManagers(model)

	$('#airplaneCanvas #airplaneModelDetail').fadeIn(200)

    airplaneModelCalculator()
}

function getDiscountsTooltip(discounts) {
    var tooltipDiv = $('<div class="tooltip"></div>')
    tooltipDiv.append("<img class='info svg' src='/assets/images/icons/information.svg'>")
    var tooltipTextSpan = $('<span class="tooltiptext" style="width: 200px;"></span>')
    tooltipDiv.append(tooltipTextSpan)
    var tooltipList = $('<ul></ul>')
    tooltipTextSpan.append(tooltipList)

    $.each(discounts, function(index, discount) {
        tooltipList.append("<li>" + discount.discountDescription + "</li>")
    })
    return tooltipDiv
}

function loadAirplaneModelStats(modelInfo) {
    var model = loadedModelsById[modelInfo.id]

	$.ajax({
		type: 'GET',
		url: "/airlines/" + activeAirline.id + "/airplanes/model/" + model.id + "/stats",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(stats) {
	    	updateTopOperatorsTable(stats)
	    	$('#airplaneCanvas .total').text(stats.total)
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

var loadedAircraftModelManagers = []

function loadAircraftModelManagers(model) {
    $.ajax({
        type: 'GET',
        url: "/delegates/airline/" + activeAirline.id + "/aircraft-model/" + model.id,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            loadedAircraftModelManagers = result.delegates
            renderAircraftModelManagers(model.id, result.delegates, result.availableCount, result.maxManagers)
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log("AJAX error loading aircraft managers: " + textStatus + ' : ' + errorThrown)
        }
    })
}

function renderAircraftModelManagers(modelId, managers, availableCount, maxManagers) {
    var $display = $('#aircraftManagersDisplay')
    $display.empty()

    var $row = $('<div style="display:flex; align-items:center; gap:4px; flex-wrap:wrap;"></div>')
    var $icons = $('<div style="display:flex; align-items:center; flex-wrap:wrap; gap:2px;"></div>')

    var $addBtn = $('<img class="img-button svg svg-hover-red svg-monochrome" src="/assets/images/icons/plus.svg" style="width:14px; height:14px;" title="Assign manager">')
    if (managers.length >= maxManagers) {
        $addBtn.css('opacity', '0.35').attr('title', 'Maximum managers reached')
    } else if (availableCount <= 0) {
        $addBtn.css('opacity', '0.35').attr('title', 'No delegates available')
    } else {
        $addBtn.css('cursor', 'pointer').on('click', function() { addAircraftModelManager(modelId) })
    }

    var $removeBtn = $('<img class="img-button svg svg-hover-green svg-monochrome" src="/assets/images/icons/minus.svg" style="width:14px; height:14px;" title="Remove manager">')
    if (managers.length <= 0) {
        $removeBtn.css('opacity', '0.35')
    } else {
        $removeBtn.css('cursor', 'pointer').on('click', function() { removeAircraftModelManager(modelId) })
    }

    $row.append($icons).append($addBtn).append($removeBtn)
    $display.append($row)
    refreshAssignedDelegates(managers.length, '#4a9eed', $icons)

    // Discount projection info
    var model = loadedModelsById[modelId]
    var maxPct = (model && model.maxManagerPriceDiscountPct) || 0
    if (maxPct > 0) {
        var perLevelPct = model.discountPerManagerLevelPct || 0
        var totalLevel = managers.reduce(function(sum, m) { return sum + m.level }, 0)
        var currentPct = Math.round(maxPct * Math.min(1.0, totalLevel * 0.125))
        var $info = $('<div class="text-xxs opacity-70 pt-1"></div>')
        $info.text('Discount: ' + currentPct + '% (max ' + maxPct + '%, +' + perLevelPct.toFixed(1) + '%/level)')
        $display.append($info)
    }
}

function addAircraftModelManager(modelId) {
    $.ajax({
        type: 'POST',
        url: "/delegates/airline/" + activeAirline.id + "/aircraft-model/" + modelId,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        data: JSON.stringify({ delegateCount: loadedAircraftModelManagers.length + 1 }),
        success: function() {
            loadAircraftModelManagers(loadedModelsById[modelId])
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log("AJAX error adding aircraft manager: " + textStatus + ' : ' + errorThrown)
        }
    })
}

function removeAircraftModelManager(modelId) {
    $.ajax({
        type: 'POST',
        url: "/delegates/airline/" + activeAirline.id + "/aircraft-model/" + modelId,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        data: JSON.stringify({ delegateCount: loadedAircraftModelManagers.length - 1 }),
        success: function() {
            loadAircraftModelManagers(loadedModelsById[modelId])
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log("AJAX error removing aircraft manager: " + textStatus + ' : ' + errorThrown)
        }
    })
}

function updateTopOperatorsTable(stats) {
    var statsTable = $("#airplaneModelDetail .topOperators")
	statsTable.children("div.table-row").remove()


	$.each(stats.topAirlines, function(index, entry) {
		var row = $("<div class='table-row'></div>")
		var airline = entry.airline
		row.append("<div class='cell'>" + getAirlineSpan(airline.id, airline.name) + "</div>")
		row.append("<div class='cell' align='right'>" + entry.airplaneCount + "</div>")

		var percentage = (entry.airplaneCount * 100.0 / stats.total).toFixed(2)
		row.append("<div class='cell' align='right'>" + percentage + "%</div>")
		statsTable.append(row)
	});
	//append total row
    var row = $("<div style='display:table-row'></div>")
    row.append("<div class='cell' style='border-top: 1px solid #6093e7;'>All</div>")
    row.append("<div class='cell' align='right' style='border-top: 1px solid #6093e7;'>" + stats.total + "</div>")
    row.append("<div class='cell' align='right' style='border-top: 1px solid #6093e7;'>-</div>")

}

function loadUsedAirplanes(modelInfo) {
	$.ajax({
		type: 'GET',
		url: "/airlines/" + activeAirline.id + "/used-airplanes/models/" + modelInfo.id,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(usedAirplanes) { 
	    	loadedUsedAirplanes = usedAirplanes
	    	var selectedSortHeader = $('#usedAirplaneSortHeader .cell.selected')
	    	updateUsedAirplaneTable(selectedSortHeader.data("sort-property") || 'dealerValue', selectedSortHeader.data("sort-order") || 'ascending')
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function toggleUtilizationRate(container, checkbox) {
    if (checkbox.is(':checked')) {
        container.find('.utilization').show()
    } else {
        container.find('.utilization').hide()
    }
}

function toggleCondition(container, checkbox) {
    if (checkbox.is(':checked')) {
        container.find('.condition').show()
    } else {
        container.find('.condition').hide()
    }
}

function showAirplaneBaseFromPlanLink(modelId) {
    showAirplaneBase(modelId)
    $('#airplaneBaseModal').data('closeCallback', function() {
        planLink($("#planLinkFromAirportId").val(), $("#planLinkToAirportId").val(), true)
    })
    //console.log("Added " + $('#airplaneBaseModal').data('closeCallback'))
}

function showAirplaneBase(modelId) {
    if (!loadedModelsOwnerInfo) {
        loadAirplaneModelOwnerInfo()
    }

    $("#airplaneBaseModal .inventoryContainer").empty()
    var model = loadedModelsById[modelId]

    $("#airplaneBaseModal .modelName").text(model.name)

    $.each(activeAirline.baseAirports, function(index, base) {
        var inventoryDiv = $("<div style='width : 95%; min-height : 85px;' class='section config'></div>")
        if (base.headquarter) {
            inventoryDiv.append("<div style='display : inline-block;'><img src='/assets/images/icons/building-hedge.png' style='vertical-align:middle;'></div>&nbsp;<div style='display : inline-block;'><h4>" + getCountryFlagImg(base.countryCode) + getAirportText(base.city, base.airportCode) + "</h4></div>")
        } else {
            inventoryDiv.append("<div style='display : inline-block;'><img src='/assets/images/icons/building-low.png' style='vertical-align:middle;'></div>&nbsp;<div style='display : inline-block;'><h4>" + getCountryFlagImg(base.countryCode) + getAirportText(base.city, base.airportCode) + "</h4></div>")
        }

        var assignedAirplanesCount = getAssignedAirplanesCount("homeAirportId", base.airportId, model.id)
        addAirplaneInventoryDivByBase(inventoryDiv, model.id, "homeAirportId", base.airportId)

        if (base.headquarter) {
            $("#airplaneBaseModal .inventoryContainer").prepend(inventoryDiv)
        } else {
            $("#airplaneBaseModal .inventoryContainer").append(inventoryDiv)
        }
        inventoryDiv.attr("ondragover", "allowUnassignedAirplaneIconDragOver(event)")
        inventoryDiv.attr("ondrop", "onAirplaneIconBaseDrop(event, " + base.airportId + ")");
    })
    toggleUtilizationRate($("#airplaneBaseModal .inventoryContainer"), $("#airplaneBaseModal .toggleUtilizationRateBox"))
    toggleCondition($("#airplaneBaseModal .inventoryContainer"), $("#airplaneBaseModal .toggleConditionBox"))
    $('#airplaneBaseModal').fadeIn(200)
}

//inventory for ALL airplanes
function showAllAirplaneInventory(modelId) {
    selectedModelId = modelId
    if (!loadedModelsOwnerInfo) {
        loadAirplaneModelOwnerInfo()
    }

    $("#allAirplaneInventoryModal .inventoryContainer").empty()
    var info = loadedModelsById[modelId]
    if (!info.isFullLoad) {
        loadAirplaneModelOwnerInfoByModelId(modelId) //refresh to get the utility rate
    }

    $("#allAirplaneInventoryModal .modelName").text(info.name)
    var inventoryDiv = $("<div style='width : 95%; min-height : 85px;' class='section'></div>")
    var airplanesDiv = $("<div style= 'width : 100%; overflow: auto;'></div>")
    var empty = true

    var allAirplanes = $.merge($.merge($.merge([], info.assignedAirplanes), info.availableAirplanes), info.constructingAirplanes)
    $.each(allAirplanes, function( key, airplane ) {
        var airplaneId = airplane.id
        var li = $("<div style='float: left;' class='clickable' onclick='loadOwnedAirplaneDetails(" + airplaneId + ", $(this), refreshAllAirplaneInventoryAfterAirplaneUpdate)'></div>").appendTo(airplanesDiv)
        var airplaneIcon = getAirplaneIcon(airplane, gameConstants.aircraft.conditionBad)
        enableAirplaneIconDrag(airplaneIcon, airplaneId)
        enableAirplaneIconDrop(airplaneIcon, airplaneId, "refreshAllAirplaneInventoryAfterAirplaneUpdate")
        li.append(airplaneIcon)
        empty = false
    });

    if (empty) {
        airplanesDiv.append("<h4>-</h4>")
    }

    inventoryDiv.append(airplanesDiv)
    $("#allAirplaneInventoryModal .inventoryContainer").append(inventoryDiv)

    toggleUtilizationRate($("#allAirplaneInventoryModal .inventoryContainer"), $("#allAirplaneInventoryModal .toggleUtilizationRateBox"))
    toggleCondition($("#allAirplaneInventoryModal .inventoryContainer"), $("#allAirplaneInventoryModal .toggleConditionBox"))

    $('#allAirplaneInventoryModal').fadeIn(200)
}


function refreshBaseAfterAirplaneUpdate() {
    loadAirplaneModelOwnerInfoByModelId(selectedModelId) //refresh the loaded airplanes on the selected model
    updateAirplaneModelTable()
    showAirplaneBase(selectedModelId)

    if ($('#airplaneCanvas .hangar').is(':visible')) {
        populateHangerModels()
    }
}

function refreshAllAirplaneInventoryAfterAirplaneUpdate() {
    loadAirplaneModelOwnerInfoByModelId(selectedModelId) //refresh the loaded airplanes on the selected model
    updateAirplaneModelTable()
    showAllAirplaneInventory(selectedModelId)
}

function addAirplaneInventoryDivByBase(containerDiv, modelId, compareKey, compareValue) {
    var airplanesDiv = $("<div style= 'width : 100%; height : 50px; overflow: auto;'></div>")
    var info = loadedModelsById[modelId]
    if (!info.isFullLoad) {
        loadAirplaneModelOwnerInfoByModelId(modelId) //refresh to get the utility rate
    }

    var empty = true

    var allAirplanes = $.merge($.merge($.merge([], info.assignedAirplanes), info.availableAirplanes), info.constructingAirplanes)
    $.each(allAirplanes, function( key, airplane ) {
        if (airplane[compareKey] == compareValue) {
            var airplaneId = airplane.id
            var li = $("<div style='float: left;' class='clickable' onclick='loadOwnedAirplaneDetails(" + airplaneId + ", $(this), refreshBaseAfterAirplaneUpdate)'></div>").appendTo(airplanesDiv)
            var airplaneIcon = getAirplaneIcon(airplane, gameConstants.aircraft.conditionBad)

            enableAirplaneIconDrag(airplaneIcon, airplaneId, airplane.availableFlightMinutes != gameConstants.aircraft.maxFlightMin)
            enableAirplaneIconDrop(airplaneIcon, airplaneId, "refreshBaseAfterAirplaneUpdate")
            li.append(airplaneIcon)
            empty = false
         }
    });

    if (empty) {
        airplanesDiv.append("<h4>-</h4>")
    }

    containerDiv.append(airplanesDiv)
}

function togglePlaneSelect() {
    if (isAirplaneSelectionMode) {
        $('.isAirplane').removeClass('selected')
        storeSelectedAirplaneIds = []
    }
    isAirplaneSelectionMode = !isAirplaneSelectionMode;
}

function handleAirplaneClick(event, airplaneId, element) {
    if (isAirplaneSelectionMode) {
        planeSelect(event, element);
    } else {
        loadOwnedAirplaneDetails(airplaneId, element, refreshBaseAfterAirplaneUpdate);
    }
}

function planeSelect(event, element) {
    var airplaneId = element.data('airplaneid');

    if (element.hasClass('selected')) {
        // It's already selected, so unselect it.
        element.removeClass('selected');
        var index = storeSelectedAirplaneIds.indexOf(airplaneId);
        if (index > -1) {
            storeSelectedAirplaneIds.splice(index, 1);
        }
    } else {
        element.addClass('selected');
        if (!storeSelectedAirplaneIds.includes(airplaneId)) {
            storeSelectedAirplaneIds.push(airplaneId);
        }
    }
}


function addAirplaneHangarDivByModel($containerDiv, modelInfo) {
    var $airplanesDiv = $("<div style='width: 100%; height: 88px; overflow: auto;'></div>")

    var allAirplanes = $.merge($.merge($.merge([], modelInfo.assignedAirplanes), modelInfo.availableAirplanes), modelInfo.constructingAirplanes)
    //group by base Id
    var airplanesByAirportId = {}

    $.each(allAirplanes, function( key, airplane ) {
        var airportId = airplane.homeAirportId
        var airplanesOfThisBase = airplanesByAirportId[airportId]
        if (!airplanesByAirportId[airportId]) {
           airplanesOfThisBase = []
           airplanesByAirportId[airportId] = airplanesOfThisBase
        }
        airplanesOfThisBase.push(airplane)
    })

    $.each(airplanesByAirportId, function( airportId, airplanes ) {
        var airportIata
        $.each(activeAirline.baseAirports, function(index, baseAirport) {
            if (baseAirport.airportId == airportId) {
                airportIata = baseAirport.airportCode
            }
        })
        var $airplanesByBaseDiv = $("<div style='width : 100%;'><div style='float: left; width: 35px;'>" + airportIata + ":</div></div>")
        $.each(airplanes, function( index, airplane ) {
            var airplaneId = airplane.id
            var li = $("<div style='float: left;' data-airplaneId='" + airplaneId + "' class='clickable isAirplane' onclick='handleAirplaneClick(event, " + airplaneId + ", $(this))'></div>").appendTo($airplanesByBaseDiv)
            var airplaneIcon = getAirplaneIcon(airplane, gameConstants.aircraft.conditionBad)
            li.append(airplaneIcon)
        })
        $airplanesByBaseDiv.append("<div style='clear:both; '></div>")
        $airplanesDiv.append($airplanesByBaseDiv)

    });

    $containerDiv.append($airplanesDiv)
}



function getAirplaneIconImg(airplane, badConditionThreshold, isAssigned) {
    var img = $("<img>")
    var src
    var condition = airplane.condition

    if (!airplane.isReady) {
        if (isAssigned) {
            src = '/assets/images/icons/airplane-construct.png'
        } else {
            src = '/assets/images/icons/airplane-empty-construct.png'
        }
    } else if (condition < badConditionThreshold) {
		if (isAssigned) {
			src = '/assets/images/icons/airplane-exclamation.png'
		} else {
			src = '/assets/images/icons/airplane-empty-exclamation.png'
		}
	} else {
		if (isAssigned) {
			src = '/assets/images/icons/airplane.png'
		} else {
			src = '/assets/images/icons/airplane-empty.png'
		}
	}
	img.attr("src", src)
	return img
}

function getAirplaneIcon(airplane, badConditionThreshold, explicitIsAssigned) {
    var condition = airplane.condition
    var airplaneId = airplane.id
    var div = $("<div style='position: relative;'></div>")
    div.addClass("airplaneIcon")
    var isAssigned
    if (typeof explicitIsAssigned != 'undefined') {
        isAssigned = explicitIsAssigned
    } else {
        isAssigned = airplane.availableFlightMinutes != gameConstants.aircraft.maxFlightMin
    }

    if (typeof explicitIsAssigned == 'undefined') {
        badConditionThreshold = airplane.badConditionThreshold
    }

    var img = getAirplaneIconImg(airplane, badConditionThreshold, isAssigned)
    div.append(img)

    //utilization label
	var utilization = Math.round((gameConstants.aircraft.maxFlightMin - airplane.availableFlightMinutes) / gameConstants.aircraft.maxFlightMin * 100)
    var color
    if (utilization < 25) {
        color = "#FF9973"
    } else if (utilization < 50) {
        color = "#FFC273"
    } else if (utilization < 75) {
        color = "#59C795"
    } else {
        color = "#8CB9D9"
    }

	var utilizationDiv = $("<div class='utilization' style='position: absolute; right: 0; bottom: 0; color: #454544; background-color: " + color + "; font-size: 8px; font-weight: bold; display: none;'></div>")
	utilizationDiv.text(utilization)
	div.append(utilizationDiv)

	//condition label
	if (condition < 20) {
        color = "#FF9973"
    } else if (condition < 40) {
        color = "#FFC273"
    } else {
        color = "#8CB9D9"
    }


    var conditionDiv = $("<div class='condition' style='position: absolute; right: 0; top: 0; color:#454544; background-color: " + color + "; font-size: 8px; display: none;'></div>")
    conditionDiv.text(Math.floor(condition))

    div.attr("title", "#"+ airplaneId + " condition: " + condition.toFixed(2) + "% util: " + utilization + "%")
    div.append(conditionDiv)

    return div;
}

function enableAirplaneIconDrag(airplaneIcon, airplaneId, isAssigned) {
    //airplaneIcon.attr("ondrop", "onAirplaneSwapDrop(event, " + airplaneId + ")")
    //airplaneIcon.attr("ondragover", "event.preventDefault")
    airplaneIcon.attr("ondragstart", "onAirplaneDragStart(event, " + airplaneId + ", " + isAssigned + ")")
    airplaneIcon.attr("draggable", true)
}

function enableAirplaneIconDrop(airplaneIcon, airplaneId, refreshFunction) {
    airplaneIcon.attr("ondrop", "onAirplaneSwapDrop(event, " + airplaneId + ", " + refreshFunction + ")")
    airplaneIcon.attr("ondragover", "allowAirplaneIconDragOver(event)")
}

function isAirplaneIconEvent(event) {
    for (i = 0 ; i < event.dataTransfer.items.length; i ++) { //hacky way. since for security reason dataTransfer.getData() also returns empty
        if (event.dataTransfer.items[i].type === "airplane-id") {
            return true;
        }
    }
    return false;
}

function isAssignedAirplaneIconEvent(event) {
    for (i = 0 ; i < event.dataTransfer.items.length; i ++) { //hacky way. since for security reason dataTransfer.getData() also returns empty
        if (event.dataTransfer.items[i].type === "airplane-assigned") {
            return true;
        }
    }
    return false;
}

function allowAirplaneIconDragOver(event) {
    if (isAirplaneIconEvent(event)) { //only allow dropping if the item being dropped is an airplane icon
        event.preventDefault();
    }
}

function allowUnassignedAirplaneIconDragOver(event) {
    if (!isAssignedAirplaneIconEvent(event) && isAirplaneIconEvent(event)) { //only allow dropping if the airplane is unassigned
        event.preventDefault();
    }
}

function onAirplaneIconBaseDrop(event, airportId) {
  event.preventDefault();
  var airplaneId = event.dataTransfer.getData("airplane-id")
  if (airplaneId) {
    $.ajax({
                type: 'PUT',
                url: "/airlines/" + activeAirline.id + "/airplanes/" + airplaneId + "/home/" + airportId,
                contentType: 'application/json; charset=utf-8',
                dataType: 'json',
                async: false,
                success: function(result) {
                    refreshBaseAfterAirplaneUpdate()
                },
                error: function(jqXHR, textStatus, errorThrown) {
                        console.log(JSON.stringify(jqXHR));
                        console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
                }
            });
  }
}


function onAirplaneSwapDrop(event, currentAirplaneId, refreshFunction) {
  event.preventDefault();
  event.stopPropagation();

  var fromAirplaneId = currentAirplaneId
  var toAirplaneId = event.dataTransfer.getData("airplane-id")
  $.ajax({
          type: 'PUT',
          url: "/airlines/" + activeAirline.id + "/swap-airplanes/" + fromAirplaneId + "/" + toAirplaneId,
          contentType: 'application/json; charset=utf-8',
          dataType: 'json',
          success: function(result) {
            refreshFunction()
          },
          error: function(jqXHR, textStatus, errorThrown) {
                  console.log(JSON.stringify(jqXHR));
                  console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
          }
      });
}

function onAirplaneDragStart(event, airplaneId, isAssigned) {
  event.dataTransfer.setData("airplane-id", airplaneId);
  if (isAssigned) {
    event.dataTransfer.setData("airplane-assigned", "true"); //do not set anything if it's not assigned
  }
}

function closeAllAndStoreAirplaneModals() {
    closedAirplaneModals = closeAllModals() //in case we want to restore the modals when clicking "back" button
}

function loadOwnedAirplaneDetails(airplaneId, selectedItem, closeCallback, disableChangeHome) {
	//highlight the selected model
	if (selectedItem) {
	    selectedItem.addClass("selected")
   }
	
	var airlineId = activeAirline.id 
	$("#actionAirplaneId").val(airplaneId)
	var currentCycle
	$.ajax({
        type: 'GET',
        url: "/current-cycle",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        async: false,
        success: function(result) {
            currentCycle = result.cycle
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });

    currentAirplaneLoadCall =
    {
    		type: 'GET',
    		url: "/airlines/" + airlineId + "/airplanes/" + airplaneId,
    	    contentType: 'application/json; charset=utf-8',
    	    dataType: 'json',
    	    success: function(airplane) {
    	        var model = loadedModelsById[airplane.modelId]
                if (model.imageUrl) {
                    const imageLocation = '/assets/images/airplanes/' + model.name.replace(/\s+/g, '-').toLowerCase() + '.webp'
                    const fallbackLocation = '/assets/images/airplanes/' + model.name.replace(/\s+/g, '-').toLowerCase() + '.png'
                    $('#ownedAirplaneDetail .modelIllustration source').attr('srcset', imageLocation)
                    $('#ownedAirplaneDetail .modelIllustration img').attr('src', fallbackLocation)
                    $('#ownedAirplaneDetail .modelIllustration a').attr('href', model.imageUrl)
                    $('#ownedAirplaneDetail .modelIllustration').show()
                } else {
                    $('#ownedAirplaneDetail .modelIllustration').hide()
                }

        		var age = currentCycle - airplane.constructedCycle

        		if (age >= 0) {
        		    $("#airplaneDetailsAge").text(getYearMonthText(age) + " old | " + airplane.condition.toFixed(2) + "% condition")
                    $("#airplaneDetailsCondition").removeClass("warning fatal")
                    if (airplane.condition < gameConstants.aircraft.conditionCritical) {
                        $("#airplaneDetailsCondition").addClass("fatal")
                    } else if (airplane.condition < gameConstants.aircraft.conditionBad) {
                        $("#airplaneDetailsCondition").addClass("warning")
                    }
        			$("#airplaneDetailsAge").show()
        			$("#airplaneDetailsDelivery").hide()
        		} else {
        			$("#airplaneDetailsAge").hide()
        			$("#airplaneDetailsDelivery").text("Will be available in " + -age * -1 + "week(s)")
        			$("#airplaneDetailsDeliver").show()
        		}
    	    	$("#sellAirplaneButton").text("Sell for $" + commaSeparateNumber(airplane.sellValue))
    	    	var replaceCost = model.price - airplane.sellValue
                $("#replaceAirplaneButton").text("Replace for $" + commaSeparateNumber(replaceCost))
    	    	$("#airplaneDetailsLink").empty()
    	    	if (airplane.links.length > 0) {
    	    	    $.each(airplane.links, function(index, linkEntry) {
    	    	        var link = linkEntry.link
    	    	        var linkDescription = "<div style='display: flex; align-items: center; margin: 4px 0;'>" + getAirportText(link.fromAirportCity, link.fromAirportCode) + "<img src='/assets/images/icons/arrow.png'>" + getAirportText(link.toAirportCity, link.toAirportCode) + " " + linkEntry.frequency + " flight(s) per week</div>"
    	    	        $("#airplaneDetailsLink").append("<div><a data-link='show-link-from-airplane' href='javascript:void(0)' onclick='closeAllAndStoreAirplaneModals(); showWorldMap(); selectLinkFromMap(" + link.id + ", true)'>" + linkDescription + "</a></div>" )
    	    	    })
    	    		disableButton("#sellAirplaneButton", "Cannot sell airplanes with route assigned")
    	    	} else {
    	    		$("#airplaneDetailsLink").text("-")
    	    		if (age >= 0) {
    	    		    enableButton("#sellAirplaneButton")
    	    		} else {
    	    			disableButton("#sellAirplaneButton", "Cannot sell airplanes that are still under construction")
    	    		}

    	    	}
    	    	$("#ownedAirplaneDetail .availableFlightMinutes").text(airplane.availableFlightMinutes + " available flight minutes")
    	    	populateAirplaneHome(airplane, disableChangeHome)

                var weeksRemainingBeforeReplacement = airplane.constructionTime - (currentCycle - airplane.purchasedCycle)
    	    	if (weeksRemainingBeforeReplacement <= 0) {
    	    	    if (activeAirline.balance < replaceCost) {
                	    disableButton("#replaceAirplaneButton", "Not enough cash to replace this airplane")
                	} else {
    	    	        enableButton("#replaceAirplaneButton")
                    }
    	    	} else {
                    disableButton("#replaceAirplaneButton", "Can only replace this airplane " + weeksRemainingBeforeReplacement + " week(s) from now")
    	    	}

    	    	$("#ownedAirplaneDetail").data("airplane", airplane)

                $.ajax({
                    type: 'GET',
                    url: "/airlines/" + airlineId + "/configurations?modelId=" + airplane.modelId,
                    contentType: 'application/json; charset=utf-8',
                    dataType: 'json',
                    success: function(result) {
                        var configuration
                        var matchingIndex
                        $.each(result.configurations, function(index, option) {
                            if (option.id == airplane.configurationId) {
                                configuration = option
                                matchingIndex = index
                            }
                        })

                        //just in case, sometimes it comes to a stale state
                        if (configuration == null && result.configurations) {
                            configuration = result.configurations[0]
                            matchingIndex = 0
                        }

                        plotSeatConfigurationBar('ownedAirplaneDetailModal-configurationBar', configuration, airplane.capacity, result.spaceMultipliers)

                        if (result.configurations.length <= 1) { //then cannot change
                            $("#ownedAirplaneDetail .configuration-edit-button").hide()
                            $("#ownedAirplaneDetail #configuration-warning").show()
                        } else {
                            $("#ownedAirplaneDetail #configuration-warning").hide()
                            $("#ownedAirplaneDetail .configuration-edit-button").show()

                            $("#ownedAirplaneDetail .configuration-options").empty()
                            $("#ownedAirplaneDetail .configuration-options").data("selectedIndex", 0)
                            $("#ownedAirplaneDetail .configuration-options").data("optionCount", result.configurations.length)
                            for (i = 0 ; i < result.configurations.length; i ++) {
                                //start from the matching one
                                var index = (i + matchingIndex) % result.configurations.length
                                var option = result.configurations[index]
                                // make the configuration div id unique per option so multiple configs render correctly
                                var barDiv = $(`<div id='configuration-${airplaneId}-${option.id}' class='configuration-option' style="height:16px; width: 100%;"></div>`)
                                $("#ownedAirplaneDetail .configuration-options").append(barDiv)
                                barDiv.data("configurationId", option.id)
                                if (i != 0) { //if not the matching one, hide by default
                                    barDiv.hide()
                                }
                                // use the unique id above when plotting so ChartUtils targets the correct element
                                plotSeatConfigurationBar(`configuration-${airplaneId}-${option.id}`, option, airplane.capacity, result.spaceMultipliers)
                            }
                            $("#ownedAirplaneDetail .configuration-view .edit").show()
                        }
//                        $("#ownedAirplaneDetail #configuration-view").show()
//                        $("#ownedAirplaneDetail #configuration-edit-button").hide()

                        if (closeCallback) {
                            $("#ownedAirplaneDetailModal").data("closeCallback", function() {
                                if ($("#ownedAirplaneDetailModal").data("hasChange")) { //only trigger close callback if there are changes
                                    closeCallback()
                                    $("#ownedAirplaneDetailModal").removeData("hasChange")
                                }
                            })
                        } else {
                            $("#ownedAirplaneDetailModal").removeData("closeCallback")
                        }
                        $("#ownedAirplaneDetailModal").fadeIn(200)
                    },
                     error: function(jqXHR, textStatus, errorThrown) {
                    	            console.log(JSON.stringify(jqXHR));
                    	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
                    	    }
                });




    	    },
            error: function(jqXHR, textStatus, errorThrown) {
    	            console.log(JSON.stringify(jqXHR));
    	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
    	    }
    	}
	$.ajax(currentAirplaneLoadCall);
}

function populateAirplaneHome(airplane, disableChangeHome) {
    var homeAirportId = airplane.homeAirportId
    var hasLinks = airplane.links.length > 0
    var currentAirport
    var homeOptionsSelect = $("#ownedAirplaneDetail .homeEdit .homeOptions").empty()
    $.each(activeAirline.baseAirports, function(index, baseAirport) {
        if (baseAirport.airportId == homeAirportId) {
            currentAirport = baseAirport
        }
    })

    if (!hasLinks && !disableChangeHome) { //only allow switching home if the airplane is free
        $.each(activeAirline.baseAirports, function(index, baseAirport) {
            var option = $("<option value='" + baseAirport.airportId + "'>" + getAirportText(baseAirport.city, baseAirport.airportCode) + "</option>")
            if (baseAirport.headquarter) {
                homeOptionsSelect.prepend(option)
            } else {
                homeOptionsSelect.append(option)
            }

            if (baseAirport.airportId == homeAirportId) {
                option.attr("selected", "selected")
            }
        })
    }


    if (currentAirport) {
        $("#ownedAirplaneDetail .homeView .home").text("Home airport: " + getAirportText(currentAirport.city, currentAirport.airportCode))
    } else {
        $("#ownedAirplaneDetail .homeView .home").text("No home airport")
    }
    if (disableChangeHome) { //explicitly disabled it, do not even show any reason
        $("#ownedAirplaneDetail .homeView .editDisabled").hide()
        $("#ownedAirplaneDetail .homeView .edit").hide()
    } else if (hasLinks) {
        $("#ownedAirplaneDetail .homeView .editDisabled").show()
        $("#ownedAirplaneDetail .homeView .edit").hide()
    } else {
        $("#ownedAirplaneDetail .homeView .editDisabled").hide()
        $("#ownedAirplaneDetail .homeView .edit").show()
    }
    $("#ownedAirplaneDetail .homeView").show()
    $("#ownedAirplaneDetail .homeEdit").hide()
}

function toggleAirplaneHome() {
    $("#ownedAirplaneDetail .homeView").hide()
    $("#ownedAirplaneDetail .homeEdit").show()
}

function showAircraftInfoModal() {
    $("#aircraftInfoModal").fadeIn(500)
}

function showAirplaneModelTableFilters() {
    $("#airplaneModelTableFilters").fadeIn(100)
}

function hideAirplaneModelTableFilters() {
    $("#airplaneModelTableFilters").hide()
}



function populateHangerModels() {
    var $container = $('#airplaneCanvas .hangar .sectionContainer')
    $container.empty()
    populateHangarByModel($container)

    toggleUtilizationRate($container, $("#airplaneCanvas div.hangar .toggleUtilizationRateBox"))
    toggleCondition($container, $("#airplaneCanvas div.hangar .toggleConditionBox"))
}

function populateMaintenanceFactor() {
    $.ajax({
          type: 'GET',
          url: "/airlines/" + activeAirline.id + "/maintenance-factor",
          contentType: 'application/json; charset=utf-8',
          dataType: 'json',
          success: function(result) {
            $('div.maintenanceFactorSection .factor').text(Number(result.factor).toFixed(2))
            $('div.maintenanceFactorSection .baseFactor').text(result.baseFactor)
            $('div.maintenanceFactorSection .familyFactor').text(result.familyFactor)
            $('div.maintenanceFactorSection .modelFactor').text(result.modelFactor)

            var familyCount = result.families ? result.families.length : 0
            $('div.maintenanceFactorSection .familyCount').text(familyCount)
            var modelCount = result.models ? result.models.length : 0
            $('div.maintenanceFactorSection .modelCount').text(modelCount)

            $('div.maintenanceFactorSection .familyList').empty()
            $.each(result.families, function(index, family) {
                $('div.maintenanceFactorSection .familyList').append('<li>' + family + '</li>')
            })
            if (familyCount == 0) {
                $('div.maintenanceFactorSection .familyList').append('<li>-</li>')
            }

            $('div.maintenanceFactorSection .modelList').empty()
            $.each(result.models, function(index, model) {
                $('div.maintenanceFactorSection .modelList').append('<li>' + model + '</li>')
            })
            if (modelCount == 0) {
                $('div.maintenanceFactorSection .modelList').append('<li>-</li>')
            }

          },
          error: function(jqXHR, textStatus, errorThrown) {
                  console.log(JSON.stringify(jqXHR));
                  console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
          }
      });
}

function populatePreferredSuppliers() {
    $('#airplaneCanvas .hangar .preferredSupplier .supplierList').empty()
    $('#airplaneCanvas .hangar .preferredSupplier .discount').empty()
    $.ajax({
          type: 'GET',
          url: "/airlines/" + activeAirline.id + "/preferred-suppliers",
          contentType: 'application/json; charset=utf-8',
          dataType: 'json',
          success: function(result) {
            var $container = $('#airplaneCanvas .preferredSuppliers')
            $.each(result, function(category, info) {
                var $categorySection = $container.find('.' + category)
                if ($categorySection.length > 0) { //Super sonic has no section for now...
                    $categorySection.find('.capacityRange').text(info.minCapacity + " - " + info.maxCapacity + " capacity; " + info.minSpeed + " - " + info.maxSpeed + "km/h")
                    var $supplierList = $categorySection.find('.supplierList')
                    var $discount =  $categorySection.find('.discount')
                    $supplierList.empty()

                    $.each(info.ownership, function(manufacturer, ownedFamilies) {
                        var $manufacturerLabel = $('<span style="font-weight: bold;">' + manufacturer + '</span>')
                        var familyText = " : "
                        $.each(ownedFamilies, function(index, ownedFamily) {
                            if (index > 0) {
                                familyText += ", "
                            }
                            familyText += ownedFamily
                        })
                        var $familyList = $('<span></span>')
                        $familyList.text(familyText)

                        var $ownershipDivByManufacturer = $('<div style="margin-bottom: 10px;"></div>')
                        $ownershipDivByManufacturer.append($manufacturerLabel)
                        $ownershipDivByManufacturer.append($familyList)
                        $supplierList.append($ownershipDivByManufacturer)
                    })

                    if (info.discount) {
                        $discount.text(info.discount)
                    } else {
                        $discount.text('-')
                    }
                }
            })

          },
          error: function(jqXHR, textStatus, errorThrown) {
                  console.log(JSON.stringify(jqXHR));
                  console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
          }
      });
}

function populateHangarByModel($container) {
    const sorted = [...loadedModelsOwnerInfo].sort(
        function(a, b) {
            return a.capacity - b.capacity;
        }
    )

    // sell all planes button
    const totalAvailableAirplanes = Object.values(sorted).reduce(
      (sum, entry) => sum + entry.availableAirplanes.length,
      0
    );
    if (totalAvailableAirplanes > 0) {
        const btn = document.getElementById('sellUnassignedPlanes');
        btn.textContent = `Sell All ${totalAvailableAirplanes} Unused Planes`
        btn.disabled = false;
    } else {
    const btn = document.getElementById('sellUnassignedPlanes');
        btn.disabled = true;
        btn.textContent = `No Unused Planes to Sell`
    }

    $.each(sorted, function(index, modelOwnerInfo) {
        if (modelOwnerInfo.totalOwned > 0) {
            var modelId = modelOwnerInfo.id
            var $inventoryDiv = $("<div style='min-width: 300px; min-height: 100px; flex: 1;' class='section pb-2 clickable'></div>")
            if (!modelOwnerInfo.isFullLoad) {
                loadAirplaneModelOwnerInfoByModelId(modelId) //refresh to get the utility rate
            }
            $inventoryDiv.bind('click', function() {
                selectAirplaneModel(loadedModelsById[modelId])
                $inventoryDiv.siblings().removeClass('selected')
                $inventoryDiv.addClass('selected')
            })
            $inventoryDiv.append("<h4>" + modelOwnerInfo.name  + "</h4>")
            addAirplaneHangarDivByModel($inventoryDiv, modelOwnerInfo)
            $container.append($inventoryDiv)
        }
    })
}

function toggleAirplaneConfiguration() {
  const isOpen = $("#ownedAirplaneDetail .configuration-edit").is(":visible")
  if (isOpen) {
    $("#ownedAirplaneDetail .configuration-edit").hide()
    $("#ownedAirplaneDetail .configuration-edit-button").show()
  } else {
    $("#ownedAirplaneDetail .configuration-edit-button").hide()
    $("#ownedAirplaneDetail .configuration-edit").show()
    refreshAirplaneConfigurationOption($("#ownedAirplaneDetail"))
  }
}


function switchAirplaneConfigurationOption(containerDiv, indexDiff) {
    var currentIndex = containerDiv.find(".configuration-options").data("selectedIndex")
    var optionCount =  containerDiv.find(".configuration-options").data("optionCount")
    var index = currentIndex + indexDiff
    if (index < 0) {
      index = optionCount - 1
    } else if (index >= optionCount) {
      index = 0
    }
    containerDiv.find(".configuration-options").data("selectedIndex", index)
    refreshAirplaneConfigurationOption(containerDiv)
}
function cancelAirplaneConfigurationOption() {
    $("#ownedAirplaneDetail .configuration-options").data("selectedIndex", 0)
    $("#ownedAirplaneDetail .configuration-edit").hide()
    $("#ownedAirplaneDetail .configuration-edit-button").show()
}
function refreshAirplaneConfigurationOption(containerDiv) {
    var currentIndex = containerDiv.find(".configuration-options").data("selectedIndex")
    var optionCount =  containerDiv.find(".configuration-options").data("optionCount")

    for (i = 0 ; i < optionCount; i++) {
        if (currentIndex == i) {
            $(containerDiv.find(".configuration-options").children()[i]).show()
        } else {
            $(containerDiv.find(".configuration-options").children()[i]).hide()
        }
    }
}

function confirmAirplaneConfigurationOption() {
    var airlineId = activeAirline.id
    var airplane = $("#ownedAirplaneDetail").data("airplane")
    var currentIndex = $("#ownedAirplaneDetail .configuration-options").data("selectedIndex")
    var selectedConfigurationId = $($("#ownedAirplaneDetail .configuration-options").children()[currentIndex]).data("configurationId")
    if (selectedConfigurationId != airplane.configurationId) { //then need to update
        $.ajax({
                type: 'PUT',
                url: "/airlines/" + airlineId + "/airplanes/" + airplane.id + "/configuration/" + selectedConfigurationId,
                contentType: 'application/json; charset=utf-8',
                dataType: 'json',
                success: function(result) {
                    loadOwnedAirplaneDetails(result.id, null, $("#ownedAirplaneDetailModal").data("close-callback"))
                    $("#ownedAirplaneDetailModal").data("hasChange", true)
                },
                error: function(jqXHR, textStatus, errorThrown) {
                        console.log(JSON.stringify(jqXHR));
                        console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
                }
            });
    } else { //flip back to view mode
        $("#ownedAirplaneDetail .configuration-edit-button").show()
        $("#ownedAirplaneDetail .configuration-edit").hide()
    }
}

function confirmAirplaneHome() {
    var airlineId = activeAirline.id
    var airplane = $("#ownedAirplaneDetail").data("airplane")
    var selectedAirportId = $('#ownedAirplaneDetail .homeOptions').find(":selected").val()

    if (selectedAirportId != airplane.homeAirportId) { //then need to update
        $.ajax({
                type: 'PUT',
                url: "/airlines/" + airlineId + "/airplanes/" + airplane.id + "/home/" + selectedAirportId,
                contentType: 'application/json; charset=utf-8',
                dataType: 'json',
                async: false,
                success: function(result) {
                    loadOwnedAirplaneDetails(result.id, null, $("#ownedAirplaneDetailModal").data("close-callback"))
                    $("#ownedAirplaneDetailModal").data("hasChange", true)
                },
                error: function(jqXHR, textStatus, errorThrown) {
                        console.log(JSON.stringify(jqXHR));
                        console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
                }
            });
    } else { //flip back to view mode
        $("#ownedAirplaneDetail .homeView").show()
        $("#ownedAirplaneDetail .homeEdit").hide()
    }
}

function cancelAirplaneHome() {
    $("#ownedAirplaneDetail .homeView").show()
    $("#ownedAirplaneDetail .homeEdit").hide()
}

function getAssignedAirplanesCount(compareKey, compareValue, modelId) {
    var count = 0;
    $.each(loadedModelsById[modelId].assignedAirplanes, function( key, airplane ) {
            if (airplane[compareKey] == compareValue) {
                count ++
             }
        });

    $.each(loadedModelsById[modelId].availableAirplanes, function( key, airplane ) {
        if (airplane[compareKey] == compareValue) {
            count ++
        }
    });

    $.each(loadedModelsById[modelId].constructingAirplanes, function( key, airplane ) {
        if (airplane[compareKey] == compareValue) {
            count ++
        }
    });
    return count
}

function promptSwapModels() {
    if (storeSelectedAirplaneIds.length < 1) {
        const infoEl = document.getElementById('promptSwapModelsWarning');
        if (infoEl) infoEl.innerText = "No aircraft selected for swap";
        return;
    }

    let modelId = null;
    for (const airplaneId of storeSelectedAirplaneIds) {
        let foundModelId = null;
        for (const model of loadedModelsOwnerInfo) {
            const allAirplanes = [...model.assignedAirplanes, ...model.availableAirplanes, ...model.constructingAirplanes];
            if (allAirplanes.some(a => a.id === airplaneId)) {
                foundModelId = model.id;
                break;
            }
        }
        
        if (foundModelId === null) {
            console.error("Could not find model for airplane ID " + airplaneId);
            continue;
        }

        if (modelId === null) {
            modelId = foundModelId;
        } else if (modelId !== foundModelId) {
             const infoEl = document.getElementById('promptSwapModelsWarning');
             if (infoEl) infoEl.innerText = "Selected aircraft must all be of the same model";
             return;
        }
    }

    if (modelId === null) return;

    const storeSelectedAirplaneIdsModelId = modelId;
    const selectedAirplaneModel = loadedModelsById[storeSelectedAirplaneIdsModelId];

    $('#swapAirplaneModal .modelName').text(selectedAirplaneModel.name);
    $('#swapAirplaneModal .airplaneIds').text(storeSelectedAirplaneIds.join(', '));
    
    // Populate old model stats
    $('#swapAirplaneModal .oldModelName').text(selectedAirplaneModel.name);
    $('#swapAirplaneModal .oldPrice').text("$" + commaSeparateNumber(selectedAirplaneModel.price));
    $('#swapAirplaneModal .oldRange').text(selectedAirplaneModel.range + " km");
    $('#swapAirplaneModal .oldAscent').text(selectedAirplaneModel.ascentBurn);
    $('#swapAirplaneModal .oldCruise').text(selectedAirplaneModel.cruiseBurn);
    
    if (selectedAirplaneModel.imageUrl) {
        const imageLocation = '/assets/images/airplanes/' + selectedAirplaneModel.name.replace(/\s+/g, '-').toLowerCase() + '.webp'
        const fallbackLocation = '/assets/images/airplanes/' + selectedAirplaneModel.name.replace(/\s+/g, '-').toLowerCase() + '.png'
        $('#swapAirplaneModal .old-model-stats .modelIllustration source').attr('srcset', imageLocation)
        $('#swapAirplaneModal .old-model-stats .modelIllustration img').attr('src', fallbackLocation)
    }

    // Reset new model stats
    $('#swapAirplaneModal .new-model-stats').hide();
    $('#swapAirplaneModal .capacityChange').text('-');
    $('#swapAirplaneModal .turnaroundDiff').text('-');
    $('#swapAirplaneModal .rangeDiff').text('-');
    $('#swapAirplaneModal .speedDiff').text('-');
    $('#swapAirplaneModal .envelopeDistance').text('-');
    $('#swapAirplaneModal .envelopeRunway').text('-');
    $('#swapAirplaneModal .envelopeCustoms').hide();
    $('#swapAirplaneModal .envelope-range-row').hide();
    $('#swapAirplaneModal .envelope-runway-row').hide();
    $('#swapAirplaneModal #swapError').hide().text('');
    $('#swapAirplaneModal .cost').text('-');
    $('#swapAirplaneModal .delivery').text('-');

    $('#swapAirplaneModal').fadeIn(200)

    // Build select options for models of matching size
    var selectElement = document.getElementById('swapAirplaneModalSelect');
    selectElement.innerHTML = '';
    var defaultOption = document.createElement('option');
    defaultOption.value = '';
    defaultOption.textContent = 'Select a model...';
    selectElement.appendChild(defaultOption);

    // Iterate through loadedModelsById object
    for (var mId in loadedModelsById) {
        if (!Object.prototype.hasOwnProperty.call(loadedModelsById, mId)) continue;
        var model = loadedModelsById[mId];
        // Same or smaller size
        if (model.size <= selectedAirplaneModel.size + 0.01) {
            var option = document.createElement('option');
            option.value = model.id;
            option.textContent = model.name;
            selectElement.appendChild(option);
        }
    }
}

function processSwapModels(isEstimate = true) {
    var newModelId = $('#swapAirplaneModalSelect').val();
    
    if (!newModelId) {
        console.warn("No model selected for swap");
        $('#swapAirplaneModal .new-model-stats').hide();
        return;
    }

    const newModel = loadedModelsById[newModelId];
    // Find the old model from the modal title or store it
    const oldModelName = $('#swapAirplaneModal .oldModelName').text();
    const oldModel = getAirplaneModelByAttribute(oldModelName, 'name');

    if (isEstimate && newModel) {
        // Update new model stats and illustration
        $('#swapAirplaneModal .newModelName').text(newModel.name);
        $('#swapAirplaneModal .newPrice').text("$" + commaSeparateNumber(newModel.price));
        $('#swapAirplaneModal .newRange').text(newModel.range + " km");
        $('#swapAirplaneModal .newAscent').text(newModel.ascentBurn);
        $('#swapAirplaneModal .newCruise').text(newModel.cruiseBurn);

        if (newModel.imageUrl) {
            const imageLocation = '/assets/images/airplanes/' + newModel.name.replace(/\s+/g, '-').toLowerCase() + '.webp'
            const fallbackLocation = '/assets/images/airplanes/' + newModel.name.replace(/\s+/g, '-').toLowerCase() + '.png'
            $('#swapAirplaneModal .new-model-stats .modelIllustration source').attr('srcset', imageLocation)
            $('#swapAirplaneModal .new-model-stats .modelIllustration img').attr('src', fallbackLocation)
        }
        $('#swapAirplaneModal .new-model-stats').show();

        // Calculate differences
        if (oldModel) {
            const capDiff = newModel.capacity - oldModel.capacity;
            const turnDiff = newModel.turnaroundTime - oldModel.turnaroundTime;
            const rangeDiff = newModel.range - oldModel.range;
            const speedDiff = newModel.speed - oldModel.speed;
            
            const capDiffText = (capDiff > 0 ? "+" : "") + capDiff;
            $('#swapAirplaneModal .capacityChange').text(capDiffText).css('color', capDiff >= 0 ? (capDiff > 0 ? 'green' : '') : 'red');
            
            const turnDiffText = (turnDiff > 0 ? "+" : "") + turnDiff + " mins";
            $('#swapAirplaneModal .turnaroundDiff').text(turnDiffText).css('color', turnDiff <= 0 ? (turnDiff < 0 ? 'green' : '') : 'red');

            const rangeDiffText = (rangeDiff > 0 ? "+" : "") + rangeDiff + " km";
            $('#swapAirplaneModal .rangeDiff').text(rangeDiffText).css('color', rangeDiff >= 0 ? (rangeDiff > 0 ? 'green' : '') : 'red');

            const speedDiffText = (speedDiff > 0 ? "+" : "") + speedDiff + " km/h";
            $('#swapAirplaneModal .speedDiff').text(speedDiffText).css('color', speedDiff >= 0 ? (speedDiff > 0 ? 'green' : '') : 'red');
        }
    }

    if (!storeSelectedAirplaneIds || storeSelectedAirplaneIds.length === 0) {
        console.warn("No airplanes selected for swap");
        return;
    }
    
    var airlineId = activeAirline.id;
    var swapData = {
        airplaneIds: storeSelectedAirplaneIds,
        newModelId: parseInt(newModelId),
        isEstimate: isEstimate
    };
    
    $.ajax({
        type: 'POST',
        url: '/airlines/' + airlineId + '/swap-model',
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        data: JSON.stringify(swapData),
        success: function(result) {
            console.log("Swap models result:", result);
            $('#swapAirplaneModal #swapError').hide();
            if (isEstimate) {
                const costDiff = result.costDifference;
                $('#swapAirplaneModal .cost').text("$" + commaSeparateNumber(costDiff)).css('color', costDiff > 0 ? 'red' : (costDiff < 0 ? 'green' : ''));
                $('#swapAirplaneModal .delivery').text(result.buyCost > 0 ? "Instant (Trade-in)" : "-");
                
                if (result.envelope && result.envelope.maxDistance > 0) {
                    $('#swapAirplaneModal .envelope-range-row').show();
                    $('#swapAirplaneModal .envelope-runway-row').show();
                    $('#swapAirplaneModal .envelopeDistance').text(result.envelope.maxDistance + " km");
                    $('#swapAirplaneModal .envelopeRunway').text(result.envelope.minRunway + " m");
                    if (result.envelope.hasCustomsRestriction) {
                        $('#swapAirplaneModal .envelopeCustoms').show().attr('title', "International flight at domestic airport restriction (Max capacity: " + result.envelope.customsMaxCapacity + ")");
                    } else {
                        $('#swapAirplaneModal .envelopeCustoms').hide();
                    }

                    // Color coding for range and runway if they fail (though backend should have thrown 400, but good for visualization)
                    const newModel = loadedModelsById[parseInt($('#swapAirplaneModalSelect').val())];
                    if (newModel) {
                        $('#swapAirplaneModal .envelopeDistance').css('color', newModel.range < result.envelope.maxDistance ? 'red' : '');
                        $('#swapAirplaneModal .envelopeRunway').css('color', newModel.runwayRequirement > result.envelope.minRunway ? 'red' : '');
                        if (result.envelope.hasCustomsRestriction && newModel.capacity > result.envelope.customsMaxCapacity) {
                             $('#swapAirplaneModal .envelopeCustoms img').attr('src', '/assets/images/icons/exclamation-red.svg');
                        }
                    }
                } else {
                    $('#swapAirplaneModal .envelope-range-row').hide();
                    $('#swapAirplaneModal .envelope-runway-row').hide();
                }

                if (costDiff > activeAirline.balance) {
                     disableButton($('#swapAirplaneModal .add'), "Not enough cash");
                } else {
                     enableButton($('#swapAirplaneModal .add'));
                }
            } else {
                closeModal($('#swapAirplaneModal'));
                // Refresh the airplane canvas to show updated models
                showAirplaneCanvas();
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            const errorMsg = jqXHR.responseText || "Error processing swap";
            $('#swapAirplaneModal #swapError').text(errorMsg).show();
            const infoEl = document.getElementById('promptSwapModelsWarning');
            if (infoEl) infoEl.innerText = errorMsg;
        }
    });
}