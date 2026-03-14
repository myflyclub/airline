var airportsLatestData
var airportLinkPaths = {}
var activeAirport
var activeAirportId
var activeAirportPopupInfoWindow
var targetBase
var airportBaseScale
var _toggleState_AllianceBaseMapView = false
const _etagStore = {}
const DYNAMIC_FEATURE_TYPES = new Set([
    'INTERNATIONAL_HUB', 'VACATION_HUB', 'FINANCIAL_HUB', 'ELITE_CHARM',
    'OLYMPICS_PREPARATIONS', 'OLYMPICS_IN_PROGRESS'
])
/**
 * Find an airport by id (O(1) lookup)
 */
function getAirportById(id) {
    return window.airportsById?.[id] || null;
}

/**
 * Find an airport by IATA code (O(1) lookup)
 */
function getAirportByIata(iata) {
    return window.airportsByIata?.[iata] || null;
}

/**
 * Find an airport by any attribute
 * Uses O(1) maps for id/iata, falls back to search for other attributes
 */
function getAirportByAttribute(key, attribute = 'id') {
    if (attribute === 'id') return getAirportById(key);
    if (attribute === 'iata') return getAirportByIata(key);

    // Fallback for other attributes - iterate through the map values
    if (!window.airportsById) return null;
    for (const airport of Object.values(window.airportsById)) {
        if (airport[attribute] === key) return airport;
    }
    return null;
}
/**
 * Load dynamic all airports dynamic data
 * @returns 
 */
async function loadAirportsDynamic() {
    try {
        const response = await fetch('/airports');
        const data = await response.json();
        airportsLatestData = data;

        // Patch dynamic features into the global airport store
        const dynamicFeatures = data.dynamicFeatures || {};
        for (const [airportId, features] of Object.entries(dynamicFeatures)) {
            const airport = window.airportsById?.[airportId];
            if (!airport) continue;
            // Remove stale dynamic features, keep static ones
            const staticFeatures = (airport.features || []).filter(f => !DYNAMIC_FEATURE_TYPES.has(f.type));
            airport.features = [...staticFeatures, ...features];
        }
    } catch (error) {
        console.error('Error fetching data:', error);
    }
}

/**
 * Load single airport details (static + dynamic)
 * 
 * @param {Int} airportId 
 * @returns 
 */
async function loadAirportDetails(airportId) {
    try {
        // 1. Fetch static and dynamic models concurrently
        const [staticRes, dynamicRes] = await Promise.all([
            fetch(`/airports/${airportId}/detail-static`),
            fetch(`/airports/${airportId}/detail`)
        ]);

        if (!staticRes.ok || !dynamicRes.ok) {
            throw new Error('Network request failed.');
        }

        const [staticData, dynamicData] = await Promise.all([
            staticRes.json(),
            dynamicRes.json()
        ]);

        return { ...staticData, ...dynamicData };

    } catch (error) {
        console.error("Failed to load airplane models:", error);
        return {}; // Return empty object on failure
    }
}
/**
 * Airport view
 */
async function showAirportDetails(airportId) {
    activeAirportId = airportId
    setActiveDiv($("#airportCanvas"))

    $('#main-tabs').children('.left-tab').children('span').removeClass('selected')
    checkTutorial('airport')

    if (Object.keys(loadedCountriesByCode).length === 0) {
        await loadAllCountries()
    }

    const airportDetailed = await loadAirportDetails(airportId);
    populateAirportDetails(airportDetailed)
    updateAirportDetails(airportDetailed)
    activeAirport = airportDetailed
}
/**
 * Airport view update
 */
function updateAirportDetails(airport) {
    ! isMobileDevice() && loadAndUpdateAirportImage(airport.id);
    updateAllTextNodes('.airportName', airport.name);
    updateAllTextNodes('.airportCityName', airport.city);
    updateAllTextNodes('.airportSize', airport.size);
    updateAllTextNodes('.airportAffinityZone', airport.zone);
    updateAllTextNodes('.airportIata', airport.iata);
    updateAllTextNodes('.airportIataIaco', airport.icao ? airport.iata + " / " + airport.icao : airport.iata);
    document.querySelector('#airportCanvas .airportFeatures').innerHTML = (airport.features && Array.isArray(airport.features)) ? airport.features.map(updateFeatures).join('') : "-";

    // Render loyalty for the current airline
    let hasMatch = false
    const appeals = Array.isArray(airport.appealList) ? airport.appealList : []
    const matchingAppeal = appeals.find(a => a && a.airlineId == activeAirline.id)

    if (matchingAppeal) {
        const bonus = airport.bonusList && airport.bonusList[activeAirline.id]
        if (bonus && bonus.loyalty > 0) {
            $(".airportLoyaltyBonus").text(`(+${bonus.loyalty})`).show()
            $('#airportDetailsLoyalty').data('loyaltyBreakdown', bonus.loyaltyBreakdown)
            $('.airportLoyaltyBonusTrigger').show()
        } else {
            $(".airportLoyaltyBonus").hide()
            $('.airportLoyaltyBonusTrigger').hide()
        }

        const fullHeartSource = "/assets/images/icons/heart.png"
        const halfHeartSource = "/assets/images/icons/heart-half.png"
        const emptyHeartSource = "/assets/images/icons/heart-empty.png"

        const $container = $("#airportCanvas .airportLoyalty")
        $container.empty()
        getPaddedHalfStepImageBarByValue(fullHeartSource, halfHeartSource, emptyHeartSource, 10, matchingAppeal.loyalty).appendTo($container)
        $container.append(matchingAppeal.loyalty)
        hasMatch = true
    }

    const notesAirport = document.getElementById('airportNotes');
    const airportNote = notes.airportNotes.find(note => note.id === airport.id);
    notesAirport.value = airportNote?.note || '';

    populateBaseDetailsModal(airport.isDomesticAirport ? "Domestic" : "");

    const element = document.getElementById("tooltip_lounge");
    if (element) {
        const ulElement = document.createElement('ul');
        ulElement.classList.add('list-disc');

        airport.tooltipLounge.forEach(tooltipText => {
            const liElement = document.createElement('li');
            liElement.textContent = tooltipText;
            ulElement.appendChild(liElement);
        });

        element.innerHTML = '';
        element.appendChild(ulElement);
    }

    var $runwayTable = $('#airportDetails .runwayTable')
    $runwayTable.children('.table-row').remove()
    if (airport.runways) {
        $.each(airport.runways, function (index, runway) {
            var row = $("<div class='table-row'></div>")
            row.append("<div class='cell'>" + runway.code + "</div>")
            row.append("<div class='cell'>" + runway.length + "&nbsp;m</div>")
            row.append("<div class='cell'>" + runway.type + "</div>")
            $runwayTable.append(row)
        });
    } else {
        var row = $("<div class='table-row'><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div></div>")
    }

    const populationSpan = getBoostSpan(airport.population, airport?.boosts?.population, $('#populationDetailsTooltip'))
    const populationEl = document.getElementById('airportDetailsPopulation');
    populationEl.innerHTML = '';
    populationEl.appendChild(populationSpan[0]);

    var $incomeLevelSpan = getBoostSpan(airport.income, airport?.boosts?.income, $('#incomeDetailsTooltip'), "$")
    $("#airportDetailsIncomeLevel").html($incomeLevelSpan)
    $("#airportDetailsPopMiddleIncome").html(airport.popMiddleIncome + "%")
    $("#airportDetailsPopElite").html("~" + Number(airport.popElite).toLocaleString())

    $(".airportCountryName").text(loadedCountriesByCode[airport.countryCode].name)
    $(".airportCountryFlag").empty()
    $(".airportCountryFlag").append(getCountryFlagImg(airport.countryCode))
    $("#airportDetailsOpenness").html(getOpennessSpan(loadedCountriesByCode[airport.countryCode].openness, airport.size, airport.isDomesticAirport, airport.isGateway))

updateAirportChampionDetails(airport)

$('#airportDetailsStaff').removeClass('fatal')
if (activeAirline) {
    $('#airportBaseDetails').show()
    $.ajax({
        type: 'GET',
        url: "/airlines/" + activeAirline.id + "/bases/" + airport.id,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (baseDetails) {
            targetBase = baseDetails.targetBase
            airportBaseScale = baseDetails.baseScale ?? 0

            populateBaseUpkeepModal(baseDetails.targetBase)
            const baseType = targetBase.headquarter ? "Headquarters" : "Base"
            // const upkeepByLevel = baseDetails.targetBase.upkeepByLevel

            const countryData = loadedCountriesByCode[airport.countryCode]
            const countryTitle = countryData.CountryTitle
            if (countryTitle) {
                updateAirlineTitle(countryTitle, $("#airportCanvas img.airlineTitleIcon"), $("#airportCanvas .airlineTitle"))
            }

            var $relationshipDetailsIcon = $("#airportCanvas .openCountryRelationship")
            $relationshipDetailsIcon.data("countryCode", airport.countryCode)

            if (!loadedCountriesByCode[airport.countryCode]) loadedCountriesByCode[airport.countryCode] = {}
            loadedCountriesByCode[airport.countryCode].countryRelationship = countryData.countryRelationship
            loadedCountriesByCode[airport.countryCode].CountryTitle = countryTitle || {}

            if (!baseDetails.baseScale) { //new base
                document.getElementById('airportBaseDetailsHeading').innerHTML = `Build base`
                $('#airportDetailsBaseUpkeep').text('0')
                // First HQ is free; any other new base costs 1
                if (!activeAirline.headquarterAirport) {
                    $('#airportDetailsBaseDelegatesRequired').text('None')
                } else {
                    $('#airportDetailsBaseDelegatesRequired').empty()
                    var $delegatesSpan = $('<span style="display: flex;"></span>')
                    $delegatesSpan.append($('<img src="/assets/images/icons/user-silhouette-available.png"/>'))
                    $('#airportDetailsBaseDelegatesRequired').append($delegatesSpan)
                }
                $('#airportDetailsStaff').text('-')
                $('#airportBaseDetails .baseSpecializations').text('')
                $('#airportDetailsFacilities').empty()
                $('.upgradeCostLabel').text('New base cost')
                disableButton($('#airportBaseDetails .specialization.button'), "This is not your airline base")

                $('#baseDetailsModal').removeData('scale')
            } else {
                let specializationList = "";
                if (targetBase.specializations) {
                    specializationList = document.createElement('span');

                    /**
                     * todo but need images for specializations
                     */
                    // targetBase.specializations.forEach(specialization => {
                    //     const img = document.createElement('img');
                    //     img.src = `assets/images/icons/specialization/${specialization.id}.png`;
                    //     img.title = specialization.label;
                    //     img.style.verticalAlign = 'middle';
                    //     img.classList.add("px-1");

                    //     specializationList.appendChild(img);
                    // });
                }
                const smallTextUnderLine = document.createElement("small")
                smallTextUnderLine.classList.add('text-underline', "pl-2")
                smallTextUnderLine.innerText = `Scale ${airportBaseScale}`;

                const airportBaseDetailsHeadingELM = document.getElementById('airportBaseDetailsHeading')
                airportBaseDetailsHeadingELM.innerText = `${activeAirline.name} ${baseType}`
                airportBaseDetailsHeadingELM.appendChild(smallTextUnderLine)
                //                    if (specializationList) {
                //                        const specializationListELM = document.createElement('span');
                //                        specializationListELM.innerHTML = specializationList;
                //                        airportBaseDetailsHeadingELM.appendChild(specializationList);
                //                    }

                // MANAGER_BASE delegates: 1 per level, except first HQ level is free
                const managedDelegates = targetBase.headquarter ? Math.max(0, airportBaseScale - 1) : airportBaseScale
                if (managedDelegates === 0) {
                    $('#airportDetailsBaseDelegatesRequired').text('None')
                } else {
                    $('#airportDetailsBaseDelegatesRequired').empty()
                    var $delegatesSpan = $('<span style="display: flex;"></span>')
                    for (let i = 0; i < managedDelegates; i++) {
                        $delegatesSpan.append($('<img src="/assets/images/icons/user-silhouette-available.png"/>'))
                    }
                    $('#airportDetailsBaseDelegatesRequired').append($delegatesSpan)
                }

                var capacityInfo = baseDetails.officeCapacity
                var capacityText = capacityInfo.currentStaffRequired + " / " + capacityInfo.staffCapacity
                var $capacitySpan = $('#airportDetailsStaff')

                if (capacityInfo.staffCapacity < capacityInfo.currentStaffRequired) {
                    $capacitySpan.addClass('fatal')
                }

                if (capacityInfo.currentStaffRequired != capacityInfo.futureStaffRequired) {
                    capacityText += "(future : " + capacityInfo.futureStaffRequired + ")"
                }
                $capacitySpan.text(capacityText)

                $('#airportDetailsBaseUpkeep').text('$' + commaSeparateNumber(baseDetails.targetBase.upkeepCurrentLevel))

                $('#baseDetailsModal').data('scale', airportBaseScale)
                $('#upgradeBaseButton').data('scale', airportBaseScale)
                updateFacilityIcons(airport)
                enableButton($('#airportBaseDetails .specialization.button'))
            }

            var targetBaseScale = baseDetails.targetBase.scale
            const upgradeText = targetBaseScale === 1 ? `Build ${baseType}` : `Upgrade ${baseType}`
            $('#upgradeBaseButton').text(upgradeText + " for $" + commaSeparateNumber(baseDetails.targetBase.upgradeCost))
            $('#buildBaseButton').text(upgradeText + " for $" + commaSeparateNumber(baseDetails.targetBase.upgradeCost))
            $('#airportDetailsBaseUpgradeUpkeep').text('$' + commaSeparateNumber(baseDetails.targetBase.upkeepNextLevel))

            //update buttons and reject reasons
            if (baseDetails.rejection) {
                $('#buildHeadquarterButton').hide()
                $('#buildBaseButton').hide()
                $('#upgradeBaseButton').hide()
                if (!airportBaseScale) {
                    disableButton($('#buildBaseButton'), baseDetails.rejection)
                    $('#buildBaseButton').show()
                } else {
                    disableButton($('#upgradeBaseButton'), baseDetails.rejection)
                    $('#upgradeBaseButton').show()
                }
            } else {
                if (!airportBaseScale) {
                    if (activeAirline.headquarterAirport) {
                        $('#buildHeadquarterButton').hide()
                        enableButton($('#buildBaseButton'))
                        $('#buildBaseButton').show()
                    } else {
                        enableButton($('#buildHeadquarterButton'))
                        $('#buildHeadquarterButton').show()
                        $('#buildBaseButton').hide()
                    }
                    $('#upgradeBaseButton').hide()
                } else {
                    $('#buildHeadquarterButton').hide()
                    $('#buildBaseButton').hide()
                    enableButton($('#upgradeBaseButton'))
                    $('#upgradeBaseButton').show()
                }
            }

            if (baseDetails.downgradeRejection) {
                disableButton($('#downgradeBaseButton'), baseDetails.downgradeRejection)
                $('#downgradeBaseButton').show()
            } else {
                if (airportBaseScale > 0) {
                    enableButton($('#downgradeBaseButton'))
                    $('#downgradeBaseButton').show()
                } else {
                    $('#downgradeBaseButton').hide()
                }
            }

            if (baseDetails.deleteRejection) {
                disableButton($('#deleteBaseButton'), baseDetails.deleteRejection)
                $('#deleteBaseButton').show()
            } else {
                if (!airportBaseScale) {
                    $('#deleteBaseButton').hide()
                } else {
                    enableButton($('#deleteBaseButton'))
                    $('#deleteBaseButton').show()
                }
            }
        },
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
} else {
    $('#airportBaseDetails').hide()
}
}


async function updateAirportChampionDetails(airport) {
    const url = '/airports/' + airport.id + '/champions' + (activeAirline ? '?airlineId=' + activeAirline.id : '')
    const key = 'champions-' + airport.id
    const headers = {}
    if (_etagStore[key]) headers['If-None-Match'] = _etagStore[key]

    try {
        const res = await fetch(url, { headers })
        if (res.status === 304) return
        if (!res.ok) {
            console.log('AJAX error: ' + res.status)
            return
        }
        const etag = res.headers.get('ETag')
        if (etag) _etagStore[key] = etag
        const result = await res.json()

        document.getElementById('maxRep').textContent = result.maxRep
        var champions = result.champions
        $('#airportDetailsChampionList').children('div.table-row').remove()
        $(champions).each(function (index, championDetails) {
            var row = $("<div class='table-row clickable' onclick='navigateTo(/rivals/" + championDetails.airlineId + "'></div>")
            var icon = getRankingImg(championDetails.ranking)
            row.append("<div class='cell'>" + icon + "</div>")
            row.append("<div class='cell'>" + getAirlineSpan(championDetails.airlineId, championDetails.airlineName) + "</div>")
            row.append("<div class='cell' style='text-align: right'>" + commaSeparateNumber(championDetails.loyalistCount) + "</div>")
            var $loyaltyCell = $("<div class='cell' style='text-align: right'>" + championDetails.loyalty + "</div>")
            if (!isMobileDevice()) {
                $loyaltyCell.hover(
                    function () {
                        if (airport.bonusList[championDetails.airlineId]) {
                            showAppealBreakdown($(this), airport.bonusList[championDetails.airlineId].loyaltyBreakdown)
                        }
                    },
                    function () {
                        hideAppealBreakdown()
                    }
                )
            }
            row.append($loyaltyCell)
            row.append("<div class='cell' style='text-align: right'>" + championDetails.reputationBoost + "</div>")
            $('#airportDetailsChampionList').append(row)
        })

        if (result.currentAirline) {
            var row = $(`<div class='table-row clickable' onclick='navigateTo("/rivals/${result.currentAirline.id}")'></div>`)
            row.append("<div class='cell'>" + result.currentAirline.ranking + "</div>")
            row.append("<div class='cell'>" + getAirlineSpan(result.currentAirline.airlineId, result.currentAirline.airlineName) + "</div>")
            row.append("<div class='cell' style='text-align: right'>" + commaSeparateNumber(result.currentAirline.amount) + "</div>")
            var $loyaltyCell = $("<div class='cell' style='text-align: right'>" + result.currentAirline.loyalty + "</div>")
            if (!isMobileDevice()) {
                $loyaltyCell.hover(
                    function () {
                        if (airport.bonusList[result.currentAirline.airlineId]) {
                            showAppealBreakdown($(this), airport.bonusList[result.currentAirline.airlineId].loyaltyBreakdown)
                        }
                    },
                    function () {
                        hideAppealBreakdown()
                    }
                )
            }
            row.append($loyaltyCell)
            row.append("<div class='cell' style='text-align: right'>-</div>")
            $('#airportDetailsChampionList').append(row)
        }

        if ($(champions).length == 0) {
            var row = $("<div class='table-row'></div>")
            row.append("<div class='cell'>-</div>")
            row.append("<div class='cell'>-</div>")
            row.append("<div class='cell' style='text-align: right'>-</div>")
            row.append("<div class='cell' style='text-align: right'>-</div>")
            row.append("<div class='cell' style='text-align: right'>-</div>")
            $('#airportDetailsChampionList').append(row)
        }
    } catch (e) {
        console.error('Failed to load champion details:', e)
    }
}



function checkForAirportUpdate(airport) {
    // Prefer fresh boost data embedded in the airport detail response (populationBoost /
    // incomeLevelBoost from AirportExtendedWrites) — avoids relying on the potentially
    // stale airportsLatestData bulk cache (e.g. after a specialization change).
    if (airport.populationBoost || airport.incomeLevelBoost) {
        const boostFactors = {}
        if (airport.populationBoost) {
            const popBoost = airport.populationBoost.reduce((sum, item) => sum + (Number(item.value) || 0), 0)
            const oldMiddleIncome = airport.population * airport.popMiddleIncome / 100
            airport.popMiddleIncome = ((oldMiddleIncome + popBoost) / (airport.population + popBoost) * 100).toFixed(1)
            airport.population += popBoost
            boostFactors.population = airport.populationBoost
        }
        if (airport.incomeLevelBoost) {
            boostFactors.income = airport.incomeLevelBoost
        }
        airport.boosts = boostFactors

        // Still pull travelRate / reputation / congestion from the bulk cache if available
        const stats = airportsLatestData?.champions?.[airport.id]
        if (stats) {
            airport.travelRate = stats.travelRate
            airport.reputation = stats.reputation
            airport.congestion = stats.hasOwnProperty('congestion') ? stats.congestion : 0
        }
        return airport
    }

    if (!airportsLatestData || !airportsLatestData.boosts) {
        return airport;
    }

    const boostEntry = airportsLatestData.boosts[airport.id]
    if (boostEntry) {
        const boostFactors = boostEntry.boostFactorsByType || {}
        if (boostFactors.hasOwnProperty('population')) {
            let popBoost = boostFactors.population.reduce((sum, item) => sum + (Number(item.value) || 0), 0)
            const oldMiddleIncome = airport.population * airport.popMiddleIncome / 100
            airport.popMiddleIncome = ((oldMiddleIncome + popBoost) / (airport.population + popBoost) * 100).toFixed(1)
            airport.population += popBoost
        }

        airport.boosts = Object.assign({}, boostFactors)
    }

    const stats = airportsLatestData.champions[airport.id]
    if (stats) {
        airport.travelRate = stats.travelRate
        airport.reputation = stats.reputation
        airport.congestion = stats.hasOwnProperty('congestion') ? stats.congestion : 0
    }

    return airport
}

function loadAirportImages(fromAirportId, toAirportId) {
    loadAirportImage(fromAirportId, $('#linkConfirmationModal img.fromAirport'))
    loadAirportImage(toAirportId, $('#linkConfirmationModal img.toAirport'))
}

function loadAirportImage(airportId, $imgContainer) {
    var url = "/airports/" + airportId + "/images"
    var genericImageUrl = "/assets/images/background/town.png"
    $imgContainer.attr('src', genericImageUrl)
    $imgContainer.addClass('blur')

    $.ajax({
        type: 'GET',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (result) {
            var imageUrl
            if (result.cityImageUrl) {
                imageUrl = result.cityImageUrl
            } else if (result.airportImageUrl) {
                imageUrl = result.airportImageUrl
            }

            if (imageUrl) {
                $imgContainer.attr('src', imageUrl)
            }
            $imgContainer.removeClass('blur')
        },
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        },
        beforeSend: function () {
            $('body .loadingSpinner').show()
        },
        complete: function () {
            $('body .loadingSpinner').hide()
        }
    });
}
/**
 * Used on airport view
 * @param {Int} airportId 
 */
function loadAndUpdateAirportImage(airportId) {
    var url = "/airports/" + airportId + "/images";

    fetch(url, {
        method: 'GET',
        headers: { 'Content-Type': 'application/json; charset=utf-8' }
    })
        .then(function (response) {
            if (!response.ok) throw response;
            return response.json();
        })
        .then(function (result) {
            if (result.cityImageUrl) {
                var cityImageEl = document.getElementById('airportDetailsCityImage')
                cityImageEl.style.backgroundImage = 'url("' + result.cityImageUrl + '")'
                cityImageEl.style.display = 'block'
                if (result.cityImageCaption) {
                    cityImageEl.querySelector('.caption').textContent = result.cityImageCaption
                } else {
                    cityImageEl.querySelector('.caption').textContent = ""
                }
                cityImageEl.parentNode.classList.add('pr-1/4');
            } else {
                var cityImageEl = document.getElementById('airportDetailsCityImage')
                cityImageEl.style.display = 'none'
                cityImageEl.parentNode.classList.remove('pr-1/4');
            }
            if (result.airportImageUrl) {
                var airportImageEl = document.getElementById('airportDetailsAirportImage')
                airportImageEl.style.backgroundImage = 'url("' + result.airportImageUrl + '")'
                airportImageEl.style.display = 'block'
                if (result.airportImageCaption) {
                    airportImageEl.querySelector('.caption').textContent = result.airportImageCaption
                } else {
                    airportImageEl.querySelector('.caption').textContent = ""
                }
            } else {
                var airportImageEl = document.getElementById('airportDetailsAirportImage')
                airportImageEl.style.display = 'none'
            }
        })
}

function populateAirportDetails(airport) {
    airport = checkForAirportUpdate(airport) || airport;
    loadAirportStatistics(airport)
    loadGenericTransits()
    updateAirportLoyalistDetails(airport)
    updateAirportCities(airport)
    updateAirportDestinations(airport)
    loadAirportDemand(airport.id)

    if (christmasFlag) {
        initSantaClaus()
    }
}

function loadAirportStatistics(airportStatistics) {
    var transitTypeData = [
        { "transitType": `Departures @ ${airportStatistics.name}`, "passengers": airportStatistics.departurePassengers },
        { "transitType": `Arrivals @ ${airportStatistics.name}`, "passengers": airportStatistics.destinationPassengers },
        { "transitType": "Transfer passengers", "passengers": airportStatistics.transitPassengers },
        ...airportStatistics.localPaxByAirport.map(entry => ({
            transitType: entry.airportName,
            passengers: entry.passengers
        })),
    ]
    plotPie(transitTypeData, "", "transitTypePie", "transitType", "passengers")

    assignAirlineColors(airportStatistics.airlinePax, "airlineId")
    assignAirlineColors(airportStatistics.airlinePremiumPax, "airlineId")
    assignAirlineColors(airportStatistics.airlineOrigin, "airlineId")

    plotPie(airportStatistics.airlinePax, activeAirline ? activeAirline.name : "", "airlineTotalPie", "airlineName", "passengers")
    plotPie(airportStatistics.airlineOrigin, activeAirline ? activeAirline.name : "", "airlineOriginPie", "airlineName", "passengers")
    plotPie(airportStatistics.airlinePremiumPax, activeAirline ? activeAirline.name : "", "airlinePremiumPie", "airlineName", "passengers")
    plotPie(airportStatistics.aircraftStats, "", "airportModelPie")


    document.getElementById('airportTravelRate').innerText = airportStatistics.travelRate + "%"
    updateElementColorsByValue(airportStatistics.travelRate, 100, '#airportTravelRate')

    $('#airportCongestion').text(airportStatistics.congestion + "%")
    updateElementColorsByValue(airportStatistics.congestion, 70, '#airportCongestion', true)
    $('#airportDetailsConnectedAirportCount').text(airportStatistics.connectedAirportCount)
    $('#airportDetailsConnectedCountryCount').text(airportStatistics.connectedCountryCount)
    $('#airportDetailsLF').text(airportStatistics.linksLF + "%")
    $('#airportDetailsInternational').text(airportStatistics.linksIntl + "%")
    $('#airportDetailsAirlineCount').text(airportStatistics.airlineCount)
    $('#airportDetailsLinkCount').text(airportStatistics.linkCount)
    $('#airportDetailsFlightFrequency').text(Math.floor(airportStatistics.flightFrequency / 7))
    const total = Math.floor((airportStatistics.departurePassengers + airportStatistics.destinationPassengers + airportStatistics.transitPassengers) / 7)
    $('#airportDetailsTotalTickets').text(commaSeparateNumber(total))

    //these build the base & lounge tables in the airport view
    // updateAirportRating(airportStatistics.rating)
    updateFacilityList(airportStatistics)
}

async function loadGenericTransits() {
    const key = 'transits-' + activeAirportId
    const headers = {}
    if (_etagStore[key]) headers['If-None-Match'] = _etagStore[key]

    try {
        const res = await fetch('/airports/' + activeAirportId + '/generic-transits', { headers })
        if (res.status === 304) return
        if (!res.ok) {
            console.log('AJAX error: ' + res.status)
            return
        }
        const etag = res.headers.get('ETag')
        if (etag) _etagStore[key] = etag
        const transits = await res.json()
        $('#genericTransitModal .table.genericTransits').data('transits', transits)
        $('#airportDetailsNearbyAirportCount').text(transits.length)
    } catch (e) {
        console.error('Failed to load generic transits:', e)
    }
}

function updateAirportCities(airport) {
    $('#airportDetailsCityList').children('.table-row').remove()
    var cities = airport.citiesServed
    if (!cities) {
        return
    }
    cities.sort(sortByProperty("population", false))
    document.getElementById('airportDetailsCityListCount').innerText = airport.citiesServed.length
    var count = 0
    var $rows
    $.each(cities, function (key, city) {
        if (++count > 50) { //do it for top 50 cities only
            return false
        }
        var row = $("<div class='table-row'></div>")
        row.append("<div class='cell'>" + city.name + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + parseFloat((city.cityShare * city.population).toFixed()).toLocaleString() + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + Number(city.population).toLocaleString() + "</div>")
        row.append("<div class='cell' style='text-align: right;'>$" + Number(city.income).toLocaleString() + "</div>")
        $('#airportDetailsCityList').append(row)
    })
}
function updateAirportDestinations(airport) {
    $('#airportDetailsDestinationList').children('.table-row').remove()
    var destinations = airport.destinations
    var count = 0

    $.each(destinations, function (key, destination) {
        const strength = destination.strength || 1
        var row = $("<div class='table-row'></div>")
        row.append("<div class='cell'>" + destination.name + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + destination.type + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + strength + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + destination.description + "</div>")
        $('#airportDetailsDestinationList').append(row)
    })
}

//if inverse is true then higher the rating, easier it is
function getRatingSpan(rating, inverse) {
    var $span = $('<span></span')
    var value = inverse ? 100 - rating : rating
    var description
    if (value <= 30) {
        description = "very easy"
    } else if (value <= 50) {
        description = "easy"
    } else if (value <= 70) {
        description = "quite challenging"
    } else if (value <= 90) {
        description = "challenging"
    } else {
        description = "very challenging"
    }
    $span.text(rating + " (" + description + ")")

    return $span
}

function updateFacilityList(statistics) {
    $('#airportDetailsHeadquarterList').children('.table-row').remove()
    $('#airportDetailsBaseList').children('.table-row').remove()
    $('#airportDetailsLoungeList').children('.table-row').remove()


    var hasHeadquarters = false
    var hasBases = false
    var hasLounges = false
    $.each(statistics.bases, function (index, base) {
        var row = $(`<div class='table-row clickable' onClick='navigateTo("/rivals/${base.airlineId}")'></div>`)
        let airlineTooltip = "";
        let linkCount = 0;
        let avgFreq = 0;
        let avgDistance = 0;

        for (const entry of statistics.linkCountByAirline) {
            if (entry.airlineId === base.airlineId) {
                ({ airlineType, airlineSlogan, linkCount, avgFrequency: avgFreq, avgDistance } = entry);
                airlineSlogan = airlineSlogan || "We don't have a slogan";
                airlineTooltip = `<p style="margin-bottom: 0.5rem;">${base.airlineName} <i>${airlineType} Airline</i></p><p>&ldquo;${airlineSlogan}&rdquo;</p>`
                break;
            }
        }
        var passengers = 0
        $.each(statistics.airlinePax, function (index, entry) {
            if (entry.airlineId == base.airlineId) {
                passengers += entry.passengers;
                return false; //break
            }
        });

        row.append("<div class='cell'>" + getAirlineSpan(base.airlineId, base.airlineName, airlineTooltip) + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + getCountryFlagImg(base.airlineCountryCode) + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + base.scale + "</div>")
        row.click(function () {
            Rivals.show(base.airlineId)
        })
        row.append("<div class='cell' style='text-align: right;'>" + linkCount + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + commaSeparateNumber(passengers) + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + avgFreq + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + avgDistance + " km</div>")

        if (base.headquarter) {
            $('#airportDetailsHeadquarterList').append(row)
            hasHeadquarters = true
        } else {
            $('#airportDetailsBaseList').append(row)
            hasBases = true
        }
    })

    $.each(statistics.lounges, function (index, loungeStats) {
        var lounge = loungeStats.lounge
        var row = $(`<div class='table-row clickable' onClick='navigateTo("/rivals/${lounge.airlineId}")'></div>`)
        var allianceSpan = "-"
        if (lounge.allianceId) {
            const allianceName = Alliance.loadedAlliancesById[lounge.allianceId]?.name || ""
            allianceSpan = "<div class='flex-row gap-0'>" + getAllianceLogoImg(lounge.allianceId) + htmlEncode(allianceName) + "</div>"
        }
        row.append("<div class='cell'>" + allianceSpan + "</div>")
        row.append("<div class='cell'>" + htmlEncode(lounge.airlineName) + "</div>")
        row.append("<div class='cell'>" + lounge.name + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + lounge.level + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + lounge.status + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + commaSeparateNumber(loungeStats.selfVisitors) + "</div>")
        row.append("<div class='cell' style='text-align: right;'>" + commaSeparateNumber(loungeStats.allianceVisitors) + "</div>")
        row.click(
            function () {
                Rivals.show(lounge.airlineId)
            })

        $('#airportDetailsLoungeList').append(row)
        hasLounges = true
    })

    var emptyBaseRow = $("<div class='table-row'></div>")
    emptyBaseRow.append("<div class='cell'>-</div>")
    emptyBaseRow.append("<div class='cell' style='text-align: right;'>-</div>")
    emptyBaseRow.append("<div class='cell' style='text-align: right;'>-</div>")
    emptyBaseRow.append("<div class='cell' style='text-align: right;'>-</div>")
    emptyBaseRow.append("<div class='cell' style='text-align: right;'>-</div>")
    emptyBaseRow.append("<div class='cell' style='text-align: right;'>-</div>")
    emptyBaseRow.append("<div class='cell' style='text-align: right;'>-</div>")

    if (!hasHeadquarters) {
        $('#airportDetailsHeadquarterList').append(emptyBaseRow)
    }
    if (!hasBases) {
        $('#airportDetailsBaseList').append(emptyBaseRow)
    }
    if (!hasLounges) {
        var emptyRow = $("<div class='table-row'></div>")
        emptyRow.append("<div class='cell'>-</div>")
        emptyRow.append("<div class='cell' style='text-align: right;'>-</div>")
        emptyRow.append("<div class='cell' style='text-align: right;'>-</div>")
        emptyRow.append("<div class='cell' style='text-align: right;'>-</div>")
        emptyRow.append("<div class='cell' style='text-align: right;'>-</div>")
        emptyRow.append("<div class='cell' style='text-align: right;'>-</div>")
        emptyRow.append("<div class='cell' style='text-align: right;'>-</div>")
        $('#airportDetailsLoungeList').append(emptyRow)
    }
}
/**
 * Helper function for features, map & airport views
 */
function updateFeatures(feature) {
    const image = `<img width='16' height='16' src='/assets/images/icons/airport-features/${feature.type}.png' title='${feature.title}'>`;
    const strength = feature.strength > 0 ? `<p>${feature.strength}</p>` : "";
    return `<div class='feature'>${image}${strength}</div>`;
}

function planToAirportFromInfoWindow(airportId, airportName) {
    AirlineMap.closeAirportInfoPopup();
    const id = airportId || $('#airportPopupId').val();
    const name = airportName || $('#airportPopupName').text();
    planToAirport(id, name);
}



function updateBaseInfo(airportId) {
    $("#buildHeadquarterButton").hide()
    $("#airportIcons .baseIcon").hide()

    if (!activeAirline.headquarterAirport) {
        $("#buildHeadquarterButton").show()
    } else {
        var baseAirport
        for (i = 0; i < activeAirline.baseAirports.length; i++) {
            if (activeAirline.baseAirports[i].airportId == airportId) {
                baseAirport = activeAirline.baseAirports[i]
                break
            }
        }
        if (baseAirport) { //a base
            if (baseAirport.headquarter) { //a HQ
                $("#popupHeadquarterIcon").show()
            } else {
                $("#popupBaseIcon").show()
            }
        }
    }
}

async function updateAirportLoyalistDetails(airport) {
    var url = '/airports/' + airport.id + '/loyalist-data'
    if (activeAirline) {
        url += '?airlineId=' + activeAirline.id
    }
    const key = 'loyalists-' + airport.id
    const headers = {}
    if (_etagStore[key]) headers['If-None-Match'] = _etagStore[key]

    try {
        const res = await fetch(url, { headers })
        if (res.status === 304) return
        if (!res.ok) {
            console.log('AJAX error: ' + res.status)
            return
        }
        const etag = res.headers.get('ETag')
        if (etag) _etagStore[key] = etag
        const result = await res.json()

        var $table = $('#airportCanvas .loyalistDelta')
        $table.find('.table-row').remove()
        var currentData = result.current

        $.each(result.airlineDeltas, function (index, deltaEntry) {
            var airlineName = deltaEntry.airlineName
            var airlineId = deltaEntry.airlineId
            var deltaText = (deltaEntry.passengers >= 0) ? ("+" + deltaEntry.passengers) : deltaEntry.passengers
            var $row = $(`<div class="table-row clickable" onClick="navigateTo('/rivals/${deltaEntry.airlineId}')"><div class="cell">${getAirlineSpan(airlineId, airlineName)}</div><div class="cell" style="text-align:right">${deltaText}</div></div>`)
            $row.click(function () {
                Rivals.show(deltaEntry.airlineId)
            })
            $table.append($row)
        })

        assignAirlineColors(currentData, "airlineId")
        plotPie(currentData, activeAirline ? activeAirline.name : null, "loyalistPie", "airlineName", "amount")
        plotLoyalistHistoryChart(result.history, "loyalistHistoryChart")
    } catch (e) {
        console.error('Failed to load loyalist details:', e)
    }
}

function showLoyalistHistoryModal() {
    $("#loyalistHistoryModal").fadeIn(500)
}

function toggleAirportLinks(airport) {
    AirlineMap.clearAllPaths()
    AirlineMap.closeAirportInfoPopup()
    $.ajax({
        type: 'GET',
        url: "/airports/" + airport.id + "/links",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (linksByRemoteAirport) {
            $("#topAirportLinksPanel .topDestinations .table-row").remove()
            $.each(linksByRemoteAirport, function (index, entry) {
                AirlineMap.drawAirportLinkPath(airport, entry)
                //populate top 5 destinations
                if (index < 5) {
                    var $destinationRow = $('<div class="table-row"></div>')
                    var $airportCell = $('<div class="cell"></div>')
                    $airportCell.append(getAirportSpan(entry.remoteAirport))
                    $destinationRow.append($airportCell)
                    $destinationRow.append('<div class="cell">' + toLinkClassValueString(entry.capacity) + '(' + entry.frequency + ')</div>')
                    var $operatorsCell = $('<div class="cell"></div>')
                    $.each(entry.operators, function (index, operator) {
                        var $airlineLogoSpan = $('<span></span>')
                        $airlineLogoSpan.append(getAirlineLogoImg(operator.airlineId))
                        $airlineLogoSpan.attr("title", operator.airlineName + ' ' + toLinkClassValueString(operator.capacity) + '(' + operator.frequency + ')')
                        $operatorsCell.append($airlineLogoSpan)
                    })
                    $destinationRow.append($operatorsCell)

                    $("#topAirportLinksPanel .topDestinations").append($destinationRow)
                }
            })
            if (linksByRemoteAirport.length == 0) {
                $("#topAirportLinksPanel .topDestinations").append("<div class='table-row'><div class='cell'>-</div><div class='cell'>-</div><div class='cell'>-</div></div>")
            }

            $("#topAirportLinksPanel").show();
        },
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        },
        beforeSend: function () {
            $('body .loadingSpinner').show()
        },
        complete: function () {
            $('body .loadingSpinner').hide()
        }
    });
}

function hideAppealBreakdown() {
    hideMarkupTooltip(document.getElementById('appealBonusDetailsTooltip'))
}

function showAppealBreakdown($icon, bonusDetails) {
    $('#appealBonusDetailsTooltip .table .table-row').remove()
    $.each(bonusDetails, function (index, entry) {
        var $row = $('<div class="table-row"><div class="cell" style="width: 70%;">' + entry.description + '</div><div class="cell" style="width: 30%; text-align: right;">+' + entry.value + '</div></div>')
        $row.css('color', 'white')
        $('#appealBonusDetailsTooltip .table').append($row)
    })
    showMarkupTooltip($icon[0], document.getElementById('appealBonusDetailsTooltip'))
}

function showSpecializationModal() {
    var $container = $('#baseSpecializationModal .container')
    $container.empty()
    $.ajax({
        type: 'GET',
        url: "/airlines/" + activeAirline.id + "/bases/" + activeAirportId + "/specialization-info",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function (info) {
            $.each(info.specializations, function (index, specializationsByScale) {
                $container.append($('<h4 class="m-0">Base Level ' + specializationsByScale.scaleRequirement + '</h4>'))
                $container.append($('<p><i>Choose any two</i></p>'))
                var $flexDiv = $('<div class="modal-grid-options"></div>').appendTo($container)
                $.each(specializationsByScale.specializations, function (index, specialization) {
                    var $specializationDiv = $('<div class="option" style="min-width: 260px; flex:1;"></div>').appendTo($flexDiv)
                    $specializationDiv.data('id', specialization.id)
                    $specializationDiv.append($('<h4 class="m-0">' + specialization.label + '</h4>'))
                    var $descriptionList = $('<ul></ul>').appendTo($specializationDiv)
                    $.each(specialization.descriptions, function (index, description) {
                        $descriptionList.append($('<li class="dot">' + description + '</li>'))
                    })

                    if (specialization.available) {
                        $specializationDiv.addClass('available')
                        if (!specialization.free) {
                            $specializationDiv.on('click', function () {
                                var $activeSpecializations = $flexDiv.find('.option.active')
                                if ($(this).hasClass('active')) {
                                    $(this).removeClass('active')
                                } else {
                                    if ($activeSpecializations.length >= 2) {
                                        $($activeSpecializations[0]).removeClass('active')
                                    }
                                    $(this).addClass('active')
                                }
                            })
                        } else {
                            $specializationDiv.attr('title', 'Free at scale ' + specializationsByScale.scaleRequirement)
                        }
                    } else {
                        $specializationDiv.addClass('unavailable')
                        $specializationDiv.attr('title', 'Do not meet hub scale requirement: ' + specializationsByScale.scaleRequirement)
                    }

                    if (specialization.active) {
                        $specializationDiv.addClass('active')
                    }
                })
            })

            if (info.cooldown > 0) {
                $('#baseSpecializationModal .warning').text("Next change can be made in " + info.cooldown + " week(s).")
                disableButton($('#baseSpecializationModal .confirm'), info.cooldown + " more week(s) before another change")
            } else {
                $('#baseSpecializationModal .warning').text("")
                enableButton($('#baseSpecializationModal .confirm'))
            }

            $('#baseSpecializationModal').data("defaultCooldown", info.defaultCooldown)

            $('#baseSpecializationModal').fadeIn(500)
        },
        error: function (jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function confirmSpecializations() {
    var defaultCooldown = $('#baseSpecializationModal').data("defaultCooldown")
    promptConfirm("Changes can only be made every " + defaultCooldown + " weeks, confirm?", function () {
        var airlineId = activeAirline.id
        var url = "/airlines/" + airlineId + "/bases/" + activeAirportId + "/specializations"
        var selectedSpecializations = []
        $('#baseSpecializationModal .option.active').each(function (index) {
            selectedSpecializations.push($(this).data('id'))
        })

        $.ajax({
            type: 'PUT',
            data: JSON.stringify({
                "selectedSpecializations": selectedSpecializations
            }),
            url: url,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function (response) {
                if (response.features && window.airportsById?.[activeAirportId]) {
                    const airport = window.airportsById[activeAirportId];
                    const staticFeatures = (airport.features || []).filter(f => !DYNAMIC_FEATURE_TYPES.has(f.type));
                    const newDynamic = response.features.filter(f => DYNAMIC_FEATURE_TYPES.has(f.type));
                    airport.features = [...staticFeatures, ...newDynamic];
                }
                closeModal($('#baseSpecializationModal'))
                showAirportDetails(activeAirportId)
            },
            error: function (jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    })
}

function showGenericTransitModal() {
    var $table = $('#genericTransitModal .table.genericTransits')
    $table.find('.table-row').remove()

    var transits = $table.data('transits')
    $.each(transits, function (index, transit) {
        $row = $('<div class="table-row" style="width: 100%"></div>')
        $row.append($('<div class="cell">' + transit.toAirportText + '</div>'))
        $row.append($('<div class="cell" align="right">' + commaSeparateNumber(transit.toAirportPopulation) + '</div>'))
        $row.append($('<div class="cell" align="right">' + commaSeparateNumber(transit.distance) + 'km</div>'))
        $row.append($('<div class="cell capacity" align="right">' + commaSeparateNumber(transit.capacity) + '</div>'))
        $row.append($('<div class="cell" align="right">' + commaSeparateNumber(transit.passenger) + '</div>'))

        $table.append($row)
    })
    if (transits.length == 0) {
        $table.append('<div class="table-row"><div class="cell">-</div><div class="cell" align="right">-</div><div class="cell" align="right">-</div><div class="cell" align="right">-</div></div>')
    }
    $('#genericTransitModal').fadeIn(200)
}


async function toggleAllianceBaseMapViewButton(state) {
    let alliancesDetails;
    let allianceBases;
    let alliancesId;
    let airlineId;

    //Allow toggle using parameter
    const toggleState = state || !_toggleState_AllianceBaseMapView
    _toggleState_AllianceBaseMapView = !_toggleState_AllianceBaseMapView


    if (activeAirline) {
        //airline is not in alliance so stop here
        if (!activeAirline.allianceId) return;
        alliancesId = activeAirline.allianceId
        airlineId = activeAirline.id
    }

    //if on turn off toggleState = false
    if (!toggleState) return AirlineMap.updateAirportMarkers(activeAirline);

    // if off turn on toggleState = ture
    try {
        const res = await fetch(`/alliances/${alliancesId}/details`)
        if (!res.ok) throw new Error('Fetch not okay');
        if (res.status !== 200) throw new Error('Fetch not 200')
        alliancesDetails = await res.json()
    }
    catch (error) {
        console.error(error)
    }

    if (alliancesDetails) {
        //FlatMap
        //Get the alliance member details and put all the members bases in to one array
        //This saves us loopping over the array twice and is a bit faster
        allianceBases = alliancesDetails.members.flatMap(member => {
            if (member.role !== 'APPLICANT') {
                return member.bases
            }
        })
    }

    if (allianceBases) {
        AirlineMap.updateAirportBaseMarkers(allianceBases, [], true)
    }
}

function populateBaseUpkeepModal(targetBase) {
    const tableContainer = document.querySelector('#baseUpkeepModal .table.data.scaleDetails');

    const existingRows = tableContainer.querySelectorAll('.table-row:not(.table-header)');
    existingRows.forEach(row => row.remove());

    targetBase.upkeepByLevel.forEach((upkeep, index) => {
        const row = document.createElement('div');
        row.className = 'table-row';
        row.setAttribute('data-scale', index);
        if (index + 1 === airportBaseScale) {
            row.classList.add('selected');
        }

        const scaleCell = document.createElement('div');
        scaleCell.className = 'cell';
        scaleCell.style.width = '30%';
        scaleCell.textContent = index + 1;
        row.appendChild(scaleCell);

        const upkeepCell = document.createElement('div');
        upkeepCell.className = 'cell';
        upkeepCell.style.width = '35%';
        upkeepCell.textContent = '$' + commaSeparateNumber(upkeep);
        row.appendChild(upkeepCell);

        const costCell = document.createElement('div');
        costCell.className = 'cell';
        costCell.style.width = '35%';
        costCell.textContent = '$' + commaSeparateNumber(targetBase.upgradeCostByLevel[index]);
        row.appendChild(costCell);

        tableContainer.appendChild(row);
    });
}

function showBaseUpkeepModal() {
    $('#baseUpkeepModal').fadeIn(500)
}

function populateBaseDetailsModal(airportType = "") {
    const tableContainer = document.querySelector('#baseDetailsModal .data');
    tableContainer.innerHTML = `<div class="table-header">
                                    <div class="cell" style="width: 15%;"><p>Scale</p></div>
                                    <div class="cell" style="width: 25%;"><p>Staff Cap<br><small>HQ / base</small></p></div>
                                    <div class="cell" data-group="INTERNATIONAL" style="width: 30%;"><p>Int'l Freq</p></div>
                                    <div class="cell" data-group="DOMESTIC" style="width: 30%;"><p>Domestic Freq</p></div>
                                </div>`;

    gameConstants.baseScaleProgression.forEach(entry => {
        const maxFrequency = entry[`maxFrequency${airportType}`];

        const row = document.createElement('div');
        row.className = 'table-row';
        row.setAttribute('data-scale', entry.scale);

        const scaleCell = document.createElement('div');
        scaleCell.className = 'cell';
        scaleCell.textContent = entry.scale;
        row.appendChild(scaleCell);

        const staffCell = document.createElement('div');
        staffCell.className = 'cell';
        staffCell.textContent = `${entry.headquartersStaffCapacity}/${entry.baseStaffCapacity}`;
        row.appendChild(staffCell);

        const internationalCell = document.createElement('div');
        internationalCell.className = 'cell';
        internationalCell.textContent = maxFrequency.INTERNATIONAL;
        row.appendChild(internationalCell);

        const domesticCell = document.createElement('div');
        domesticCell.className = 'cell';
        domesticCell.textContent = maxFrequency.DOMESTIC;
        row.appendChild(domesticCell);

        tableContainer.appendChild(row);
    });
}

function showBaseDetailsModal() {
    var scale = $('#baseDetailsModal').data('scale')
    $('#baseDetailsModal .table-row').removeClass('selected')

    if (scale) {
        var $selectRow = $('#baseDetailsModal').find('.table-row[data-scale="' + scale + '"]')
        $selectRow.addClass('selected')
    }

    $('#baseDetailsModal').fadeIn(500)
}

var _demandEtag = null

async function loadAirportDemand(airportId) {
    const container = document.getElementById('airportDemandCards')
    if (!container) return
    container.innerHTML = '<div class="table-row"><div class="cell">Loading...</div></div>'

    try {
        const headers = {}
        if (_demandEtag) headers['If-None-Match'] = _demandEtag

        const response = await fetch('/airports/' + airportId + '/demand', { headers })
        if (response.status === 304) return  // cached, container unchanged from last render

        if (!response.ok) {
            container.innerHTML = '<div class="table-row"><div class="cell">-</div></div>'
            return
        }
        _demandEtag = response.headers.get('ETag')
        const demands = await response.json()
        renderDemandCards(demands)
    } catch (e) {
        console.error('loadAirportDemand failed', e)
        container.innerHTML = '<div class="table-row"><div class="cell">-</div></div>'
    }
}

function renderDemandCards(demands) {
    const container = document.getElementById('airportDemandCards')
    if (!container) return
    container.innerHTML = ''

    if (!demands || demands.length === 0) {
        container.innerHTML = '<p class="h4">No demand data yet.</p>'
        return
    } else {
        const header = document.createElement('h3')
        header.textContent = `${activeAirport.city} Passengers`
        container.appendChild(header)
        const helper = document.createElement('p')
        helper.textContent = `A random sample from last week`
        helper.classList = 'pb-4'
        container.appendChild(helper)
    }

    demands.forEach(function(d) {
        const card = document.createElement('div')
        card.className = 'card'

        const typeColor = getChartColor(d.passengerType, '#888888')
        const typeBadge = '<span class="ml-auto" style="background:' + typeColor + ';color:#fff;border-radius:2px;padding:1px 4px;font-size:0.8em;margin-right:4px;">' + d.passengerType + '</span>'
        const classBadge = '<span style="border:1px solid rgba(255,255,255,0.3);border-radius:3px;padding:1px 4px;font-size:0.8em;">' + d.preferredLinkClass + '</span>'

        const header = document.createElement('div')
        header.style.cssText = 'display:flex;justify-content:space-between;align-items:center;margin-bottom:3px;'
        header.innerHTML = `<strong class="iata">${d.toAirportIata}</strong>${d.toAirportName}${typeBadge}${classBadge}`

        const statsRow = document.createElement('div')
        statsRow.style.cssText = 'display:flex;justify-content:space-between;align-items:center;'
        statsRow.innerHTML = '<span>&#9654; ' + commaSeparateNumber(d.passengerCount) + ' pax</span><span>Budget: $' + commaSeparateNumber(d.standardPrice) + '</span>'

        const footerRow = document.createElement('div')
        footerRow.classList = 'mt-2'

        if (d.isMissed) {
            footerRow.innerHTML = '<span style="color:#f90;font-size:0.8em;">&#9888; Unserved</span>'
        } else if (d.airlineIds && d.airlineIds.length > 0) {
            const logos = d.airlineIds.slice(0, 5).map(function(id) {
                return getAirlineLogoImg(id)
            }).join('')
            footerRow.innerHTML = logos
        }

        card.appendChild(header)
        card.appendChild(statsRow)
        card.appendChild(footerRow)
        container.appendChild(card)
    })
}
