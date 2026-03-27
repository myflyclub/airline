var loadedCountriesByCode = {}
const _countryEtagStore = {}
const _titleProgressionData = {}

async function loadAllCountries(airlineId) {
  if (!airlineId) {
    airlineId = activeAirline.id
  }
  try {
    // 1. Fetch static and dynamic models concurrently
    const [staticRes, dynamicRes] = await Promise.all([
      fetch('/countries'),
      fetch(`/countries/airline/${airlineId}`)
    ]);

    if (!staticRes.ok || !dynamicRes.ok) {
      throw new Error('Network request failed.');
    }

    const [staticData, dynamicData] = await Promise.all([
      staticRes.json(),
      dynamicRes.json()
    ]);

    // 2. Create a lookup map for dynamic data by ID
    const dataById = new Map(
      dynamicData.map(data => [data.countryCode, data])
    );

    // 3. Merge static models with their dynamic data
    Object.values(staticData).forEach(data => {
      const dynamic = dataById.get(data.countryCode) || {};
      const country = { ...data, ...dynamic };
      loadedCountriesByCode[country.countryCode] = country;
    });

  } catch (error) {
    console.error("Failed to load airplane models:", error);
    return {}; // Return empty object on failure
  }
}

async function showCountryCanvas(selectedCountryCode) {
	$("#countryList").empty()

    await loadAllCountries()
    document.title = selectedCountryCode ? `${loadedCountriesByCode[selectedCountryCode].name} Country` : 'Country';
   	var selectedSortHeader = $('#countryTableHeader .table-header .cell.selected') 
    updateCountryTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'), selectedCountryCode)

    var callback
    if (selectedCountryCode) {
        callback = function() {
            $("#countryCanvas #countryTable div.table-row.selected")[0].scrollIntoView()
        }
    }
    setActiveDiv($("#countryCanvas"), callback)
}

function updateCountryTable(sortProperty, sortOrder, selectedCountry) {
    if (!selectedCountry) {
        selectedCountry = $("#countryCanvas #countryTable div.table-row.selected").data('country-code')
    }
	var countryTable = $("#countryCanvas #countryTable")
	
	countryTable.children("div.table-row").remove()
	
	const countries = Object.values(loadedCountriesByCode)
	//sort the list
	countries.sort(sortByProperty(sortProperty, sortOrder == "ascending"))

	var selectedRow
	$.each(countries, function(index, country) {
		var row = $("<div class='table-row clickable' data-country-code='" + country.countryCode + "' onclick=\"selectCountry('" + country.countryCode + "', false)\"></div>")
        row.append($("<div class='cell'>").append(
            getCountryFlagImg(country.countryCode, "16px"), " " + country.name
        ));
        row.append("<div class='cell' align='right'>" + country.airportPopulation.toLocaleString() + "</div>")
		row.append("<div class='cell' align='right'>$" + country.income.toLocaleString() + "</div>")
		row.append("<div class='cell' align='right'>" + country.openness + "</div>")
		row.append("<div class='cell' align='right'>" + country.gini + "</div>")
        var countryRelationship = country.countryRelationship ? "<div class='cell' align='right'>" + country.CountryTitle.description + ", " + country.countryRelationship.total + "</div>" : "<div class='cell' align='right'>" + "-" + "</div>"
        row.append(countryRelationship)
        var managersCount = country.managersCount ? country.managersCount : "-"
        row.append("<div class='cell' align='right'>" + managersCount + "</div>")
		
		if (selectedCountry == country.countryCode) {
		    row.addClass("selected")
		    selectedRow = row
		}
		
		countryTable.append(row)
	});

	if (selectedRow) {
        loadCountryDetails(selectedCountry)
	}
}

function toggleCountryTableSortOrder(sortHeader) {
	if (sortHeader.data("sort-order") == "ascending") {
		sortHeader.data("sort-order", "descending")
	} else {
		sortHeader.data("sort-order", "ascending")
	}
	
	sortHeader.siblings().removeClass("selected")
	sortHeader.addClass("selected")
	
	updateCountryTable(sortHeader.data("sort-property"), sortHeader.data("sort-order"))
}

function selectCountry(countryCode) {
    $("#countryCanvas #countryTable div.selected").removeClass("selected")
	//highlight the selected country
	$("#countryCanvas #countryTable div[data-country-code='" + countryCode +"']").addClass("selected")
    // Update the URL to reflect the selected model without updating history
    history.replaceState(null, null, `/country/${countryCode}`);
    window.title = `${loadedCountriesByCode[countryCode].name} Details`;
	loadCountryDetails(countryCode)
}

function updateAirlineTitle(title, $icon, $description) {
    var imgSrc
    if (title.title === "NATIONAL_AIRLINE") {
        imgSrc = '/assets/images/icons/star-full.svg'
    } else if (title.title === "PARTNERED_AIRLINE") {
        imgSrc = '/assets/images/icons/hand-shake.png'
    } else if (title.title === "PRIVILEGED_AIRLINE") {
        imgSrc = '/assets/images/icons/medal-silver-premium.png'
    } else if (title.title === "ESTABLISHED_AIRLINE") {
        imgSrc = '/assets/images/icons/leaf-plant.png'
    } else if (title.title === "APPROVED_AIRLINE") {
        imgSrc = '/assets/images/icons/tick.svg'
    }
    if (imgSrc) {
        $icon.attr('src', imgSrc)
        $icon.show()
    } else {
        $icon.hide()
    }
    $description.text(title.description)
}

async function loadCountryDetails(countryCode) {
	$("#countryDetailsSharesChart").hide()
	var url = "/countries/" + countryCode
	const key = 'country-' + countryCode
	const headers = {}
	if (_countryEtagStore[key]) headers['If-None-Match'] = _countryEtagStore[key]
	try {
		const res = await fetch(url, { headers })
		if (res.status === 304) {
			$("#countryCanvas .sidePanel").fadeIn(200)
			return
		}
		if (!res.ok) { console.log('AJAX error: ' + res.status); return }
		const etag = res.headers.get('ETag')
		if (etag) _countryEtagStore[key] = etag
		const country = await res.json()
		$("#countryDetailsName").text(country.name)
	    	$("#countryDetailsIncomeLevel").text("$" + commaSeparateNumber(country.income))
	    	$("#countryDetailsOpenness").html(getOpennessSpan(country.openness))

	    	$("#countryDetailsLargeAirportCount").text(loadedCountriesByCode[countryCode].largeAirportCount)
	    	$("#countryDetailsMediumAirportCount").text(loadedCountriesByCode[countryCode].mediumAirportCount)
	    	$("#countryDetailsSmallAirportCount").text(loadedCountriesByCode[countryCode].smallAirportCount)

            var countryRelationship = loadedCountriesByCode[countryCode].countryRelationship
            $("#countryDetails div.relationship span.total").empty()
            $("#countryDetails div.relationship span.total").append(countryRelationship.total)

            var title = loadedCountriesByCode[countryCode].CountryTitle
            var $relationshipDetailsIcon = $("#countryDetails div.relationship .detailsIcon")
            $relationshipDetailsIcon.data("countryCode", countryCode)
            $relationshipDetailsIcon.show()

            $("#countryDetails img.airlineTitleIcon").hide()
            $("#countryDetails span.airlineTitle").empty()
            updateAirlineTitle(title, $("#countryDetails img.airlineTitleIcon"), $("#countryDetails span.airlineTitle"))

    		$("#countryDetailsAirlineHeadquarters").text(country.headquartersCount)
    		$("#countryDetailsAirlineBases").text(country.basesCount)

            function renderTitleAirlineTable(selector, airlines, iconHtml, detailsFn, bonusSelector, bonusFn) {
                $("#countryCanvas " + selector).empty()
                if (airlines && airlines.length > 0) {
                    if (bonusSelector) $(bonusSelector).text(bonusFn(airlines[0]))
                    $.each(airlines, function(index, airline) {
                        var row = $("<div class='table-row clickable' data-link='rival'><div class='cell'>" + iconHtml + getAirlineLogoImg(airline.airlineId) + "<span style='font-weight: bold;'>" + getAirlineLabelSpan(airline.airlineId, airline.airlineName) + "</span> " + detailsFn(airline) + "</div></div>")
                        row.click(function() { Rivals.show(airline.airlineId) })
                        $("#countryCanvas " + selector).append(row)
                    })
                } else {
                    $("#countryCanvas " + selector).append($("<div class='table-row'><div class='cell'>-</div></div>"))
                }
            }

            renderTitleAirlineTable(".nationalAirlines", country.nationalAirlines,
                "<img class='px-1 svg' src='/assets/images/icons/star-full.svg'>",
                function(a) { return a.relationship + " relationship" },
                ".national-title-bonus", function(a) { return `receive a ${a.loyaltyBonus} loyalty bonus` })

            renderTitleAirlineTable(".partneredAirlines", country.partneredAirlines,
                "<img class='px-1' src='/assets/images/icons/hand-shake.png' style='vertical-align:middle;'>",
                function(a) { return a.relationship + " relationship" },
                ".partnered-title-bonus", function(a) { return `receive a ${a.loyaltyBonus} loyalty bonus` })

            renderTitleAirlineTable(".favoredAirlines", country.favoredAirlines,
                "<img class='px-1' src='/assets/images/icons/medal-silver-premium.png' style='vertical-align:middle;'>",
                function(a) { return "score: " + a.score + " | " + a.relationship + " relationship" },
                null, null)

            $("#countryCanvas .countryDetailsChampion").empty()
	    	if (country.champions) {
	    		$.each(country.champions, function(index, champion) {
	    		    var championRow = $("<div class='table-row clickable' data-link='rival'><div class='cell'>" + getRankingImg(champion.ranking) + getAirlineLogoImg(champion.airlineId) + "<span style='font-weight: bold;'>" + getAirlineLabelSpan(champion.airlineId, champion.airlineName) + "</span> " + champion.passengerCount + " passengers</div></div>")
	    		    championRow.click(function() {
                        Rivals.show(champion.airlineId)
                    })
    				$("#countryCanvas .countryDetailsChampion").append(championRow)
    			})
	    	} else {
	    		$("#countryCanvas .countryDetailsChampion").append($("<div class='table-row'><div class='cell'>-</div></div>"))
	    	}

	    	$("#countryDetailsSharesChart").show()
	    	assignAirlineColors(country.marketShares, "airlineId")
	    	plotPie(country.marketShares, activeAirline ? activeAirline.name : null , "countryDetailsSharesChart", "airlineName", "passengerCount")

	    	$("#countryCanvas .sidePanel").fadeIn(200);
	} catch (e) {
		console.error('Failed to load country details:', e)
	}
}

function getCountryRelationshipDescription(value) {
	var description;
	if (value >= 5) {
		description = "Home Market"
    } else if (value == 4) {
		description = "Alliance"
	} else if (value == 3) {
		description = "Close"
	} else if (value == 2) {
		description = "Friendly"
	} else if (value == 1) { 
		description = "Warm"
	} else if (value == 0) {
		description = "Neutral"
	} else if (value == -1) {
		description = "Cold"
	} else if (value == -2) {
		description = "Hostile"
	} else if (value == -3) {
		description = "In Conflict"
	} else if (value <= -4) {
		description = "War"
	}
	
	return description + ' (' + value + ')'
}

function getAirlineRelationshipDescriptionSpan(value) {
    var color
    if (value < 0) {
        color = "#FF9973"
    } else if (value < 10) {
        color = "#FFC273"
    } else if (value < 40) {
        color = "#41A14D"
    } else {
        color = "#646cdc"
    }

    return $('<span><span style="color: ' + color + '">' + getAirlineRelationshipDescription(value) + '</span>(' + value + ')</span>');
}
function getAirlineRelationshipDescription(value) {
    var description;
	if (value >= 80) {
		description = "Trusted"
    } else if (value >= 60) {
		description = "Enthusiastic"
	} else if (value >= 40) {
		description = "Welcoming"
	} else if (value >= 20) {
		description = "Friendly"
	} else if (value >= 10) {
		description = "Warm"
	} else if (value >= 0) {
		description = "Cautious"
	} else if (value >= -10) {
		description = "Cold"
	} else {
		description = "Hostile"
	}

	return description
}

function refreshTitleDescription() {
    var selectedTitle = $('#airlineCountryRelationshipModal .titleProgression .title.selected').data('title')
    $("#airlineCountryRelationshipModal .titleDescriptions .titleDescription").hide()
    $("#airlineCountryRelationshipModal .titleDescriptions .titleDescription[data-title='" + selectedTitle + "']").show()

}

function renderTitleProgressionItems($progression, titleInfoList, currentAirlineTitle) {
    $progression.empty()
    $.each(titleInfoList, function(index, titleInfo) {
        if (index > 0) {
            $progression.append('<img src="/assets/images/icons/arrow.png">')
        }
        var $titleSpan = $('<span class="title tooltip progressionItem">')
        $titleSpan.text(titleInfo.description)
        $titleSpan.data('title', titleInfo.title)
        if (titleInfo.title == currentAirlineTitle.title) {
          $titleSpan.addClass("selected")
        }
        var $descriptionSpan = $('<span class="tooltiptext below" style="width: 400px;">')
        var $requirementsDiv = $('<div><h3>Requirements</h3></div>').appendTo($descriptionSpan)
        var $requirementsList = $('<ul></ul>').appendTo($requirementsDiv)
        $.each(titleInfo.requirements, function(index, requirement){
            $requirementsList.append('<li style="text-align: left;">' + requirement +' </li>')
        })

        var $benefitsDiv = $('<div style="margin-top: 10px;"><h3>Benefits</h3></div>').appendTo($descriptionSpan)
        var $benefitsList = $('<ul></ul>').appendTo($benefitsDiv)
        $.each(titleInfo.bonus, function(index, entry){
            $benefitsList.append('<li style="text-align: left;">' + entry +' </li>')
        })

        $titleSpan.append($descriptionSpan)
        $progression.append($titleSpan)
    })
}

async function updateTitleProgressionInfo(currentAirlineTitle, countryCode) {
    var $progression = $("#airlineCountryRelationshipModal .titleProgression")
    var url = "/countries/" + countryCode + "/title-progression"
    const key = 'titleProg-' + countryCode
    const headers = {}
    if (_countryEtagStore[key]) headers['If-None-Match'] = _countryEtagStore[key]
    try {
        const res = await fetch(url, { headers })
        if (res.status === 304 && _titleProgressionData[countryCode]) {
            renderTitleProgressionItems($progression, _titleProgressionData[countryCode], currentAirlineTitle)
            return
        }
        if (!res.ok) { console.log('AJAX error: ' + res.status); return }
        const etag = res.headers.get('ETag')
        if (etag) _countryEtagStore[key] = etag
        const result = await res.json()
        _titleProgressionData[countryCode] = result
        renderTitleProgressionItems($progression, result, currentAirlineTitle)
    } catch (e) {
        console.error('Failed to load title progression:', e)
    }
}

function showRelationshipDetailsModal(countryCode, closeCallback) {
    var country = loadedCountriesByCode[countryCode]
    var relationship = country.countryRelationship
    var title = country.CountryTitle || {}

    $('#airlineCountryRelationshipModal .country').empty()
    $('#airlineCountryRelationshipModal .country').append(getCountryFlagImg(countryCode, "16px") + country.name)

    updateAirlineTitle(title, $('#airlineCountryRelationshipModal .currentTitle .titleIcon'), $('#airlineCountryRelationshipModal .currentTitle .titleDescription'))
    updateTitleProgressionInfo(title, countryCode)

    var $table = $('#airlineCountryRelationshipModal .factors')
    $table.children("div.table-row").remove()

    $.each(relationship.factors, function(index, factor) {
        $table.append('<div class="table-row"><div class="cell">' + factor.description + '</div><div class="cell">' + factor.value + '</div></div>')
    })

    $table.append('<div class="table-row"><div class="cell">Total</div><div class="cell"><b>' + relationship.total + '</b></div></div>')

    getCountryManagersSummary(countryCode)

    if (closeCallback) {
        $('#airlineCountryRelationshipModal').data('closeCallback', closeCallback)
    } else {
        $('#airlineCountryRelationshipModal').removeData('closeCallback')
    }

    $('#airlineCountryRelationshipModal').fadeIn(500)
}

function updateCountryManagers() {
    var $managerSection = $('#airlineCountryRelationshipModal .managerSection')
    var countryCode = $managerSection.data("countryCode")

    var assignedManagerCount = $managerSection.data('assignedManagerCount')
    $.ajax({
        type: 'POST',
        url: "/managers/airline/" + activeAirline.id + "/country/" + countryCode,
        contentType: 'application/json; charset=utf-8',
        data:  JSON.stringify({ 'managerCount' : assignedManagerCount }) ,
        dataType: 'json',
        success: function(result) {
            closeModal($('#airlineCountryRelationshipModal'))
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log(JSON.stringify(jqXHR));
            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}


function renderCountryManagers(assignedCount) {
    var $managerSection = $('#airlineCountryRelationshipModal .managerSection')
    var availableCount = $managerSection.data('availableManagers') || 0
    var maxManagers = $managerSection.data('maxManagers')
    var original = $managerSection.data('originalManagers') || []

    // Use rich objects for already-fetched managers; synthesise a level-0 entry for each new addition
    var managers = original.slice(0, assignedCount)
    while (managers.length < assignedCount) {
        managers.push({ taskType: 'COUNTRY', levelDescription: 'Trainee', taskDescription: 'New assignment · pending confirmation', completed: false, nextLevelCycleCount: 4 })
    }

    renderManagerAssignment({
        container: '#countryManagersDisplay',
        managers: managers,
        availableCount: availableCount,
        maxManagers: maxManagers,
        headerText: 'Managers (' + availableCount + ' available)',
        onAdd: function() {
            var current = $managerSection.data('assignedManagerCount')
            var avail = $managerSection.data('availableManagers')
            $managerSection.data('assignedManagerCount', current + 1)
            $managerSection.data('availableManagers', avail - 1)
            renderCountryManagers(current + 1)
        },
        onRemove: function() {
            var current = $managerSection.data('assignedManagerCount')
            var avail = $managerSection.data('availableManagers')
            $managerSection.data('assignedManagerCount', current - 1)
            $managerSection.data('availableManagers', avail + 1)
            renderCountryManagers(current - 1)
        }
    })
}

function getCountryManagersSummary(countryCode) {
    var $managerSection = $('#airlineCountryRelationshipModal .managerSection')
    $managerSection.removeData('assignedManagerCount')
    $managerSection.removeData('originalManagers')
    $managerSection.data("countryCode", countryCode)

	$.ajax({
        type: 'GET',
        url: "/managers/airline/" + activeAirline.id + "/country/" + countryCode,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $('#airlineCountryRelationshipModal span.managerMultiplier').text(result.multiplier)

            var countryManagers = result.managers
            countryManagers.sort(function(a, b) { return a.startCycle - b.startCycle })
            $managerSection.data('originalManagers', countryManagers)
            $managerSection.data('assignedManagerCount', countryManagers.length)
            $managerSection.data('availableManagers', result.availableCount)
            $managerSection.data('maxManagers', result.maxManagers)

            renderCountryManagers(countryManagers.length)
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

// Delegated click handlers for showRelationshipDetailsModal
$(document).on('click', '#countryDetails div.relationship .detailsIcon', function() {
    var countryCode = $(this).data('countryCode')
    showRelationshipDetailsModal(countryCode, function() { page.show('/country/' + countryCode) })
})

$(document).on('click', '#airportCanvas .openCountryRelationship', function() {
    var countryCode = $(this).data('countryCode')
    showRelationshipDetailsModal(countryCode, function() { page('/airport/' + activeAirport.iata) })
})

$(document).on('click', '#planLinkToCountryRelationship .detailsIcon', function() {
    var countryCode = $(this).data('countryCode')
    showRelationshipDetailsModal(countryCode)
})
