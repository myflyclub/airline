function htmlEncode(str){
  return String(str).replace(/[^\w. ]/gi, function(c){
     return '&#'+c.charCodeAt(0)+';';
  });
}

function generateSimpleImageBar(imageSrc, count) {
    var containerDiv = $("<div>")

    for (i = 0 ; i < count ; i ++) {
		var image = $("<img src='" + imageSrc + "'>")
		containerDiv.append(image)
    }

    return containerDiv
}

function generateImageBar(imageEmpty, imageFill, count, containerDiv, valueInput, indexToValueFunction, valueToIndexFunction, callback) {
    generateImageBarWithRowSize(imageEmpty, imageFill, count, containerDiv, valueInput, indexToValueFunction, valueToIndexFunction, 10, callback)
}
/**
 * used by in setting route service stars & flight frequency
 **/
function generateImageBarWithRowSize(imageEmpty, imageFill, count, containerDiv, valueInput, indexToValueFunction, valueToIndexFunction, rowSize, callback) {
	containerDiv.empty()
	var images = []

	if (!indexToValueFunction || !valueToIndexFunction) {
		indexToValueFunction = function(index) { return index + 1 }
		valueToIndexFunction = function(value) { return value - 1 }
	}

	if (valueInput.val()) { //validate the input, set the value to boundaries allowed by this bar
		if (valueToIndexFunction(valueInput.val()) < 0) { //-1 is still valid, that means none selected
			valueInput.val(indexToValueFunction(-1))
		} else if (valueToIndexFunction(valueInput.val()) >= count) {
			valueInput.val(indexToValueFunction(count - 1))
		}
	}

	for (i = 0 ; i < count ; i ++) {
		var image = $("<img width='16px' height='auto' class='img-button'>")
		image.attr("src", imageEmpty)

		image.data('index', i)
		image.click(updateImageBar)
		image.click(function () {
			var newValue = indexToValueFunction($(this).data('index'))
			var oldValue = parseInt(valueInput.val())
			valueInput.val(newValue);
			if (callback) {
				callback(oldValue, newValue)
			}
		})
		image.hover(updateImageBar, resetImageBar)

		containerDiv.append(image)
		images.push(image)

		if ((i + 1) % rowSize == 0) {
			containerDiv.append("<br/>")
		}
	}

	if (valueInput.val()) {
		updateImageBarBySelectedIndex(valueToIndexFunction(valueInput.val()))
	}

	function updateImageBar(event) {
		var index = $(this).data('index')
		updateImageBarBySelectedIndex(index)
	}
	function resetImageBar() {
		if (valueInput.val()) {
	      var index = valueToIndexFunction(valueInput.val())
		  updateImageBarBySelectedIndex(index)
		}
	}
	function updateImageBarBySelectedIndex(index) {
		for (j = 0 ; j < count; j++) {
			if (j <= index) {
				images[j].attr("src", imageFill)
			} else {
				images[j].attr("src", imageEmpty)
			}
		}
	}
}

function fadeInMarker(marker) {
	marker.opacities = [0.2, 0.4, 0.6, 0.8, 1.0]
	fadeInMarkerRecursive(marker)
}
function fadeInMarkerRecursive(marker) {
	if (marker.opacities.length > 0) {
		marker.setOpacity(marker.opacities[0])
    	marker.opacities.shift()
    	setTimeout(function() { fadeInMarkerRecursive(marker) }, 20)
	}
}

function toLinkPercentOfBasePrices(priceValues, basePrice) {
	var economyValue = priceValues.hasOwnProperty('economy') ? (priceValues.economy * 100 / basePrice.economy).toFixed(0) : '-'
	var businessValue = priceValues.hasOwnProperty('business') ? (priceValues.business * 100 / basePrice.business).toFixed(0) : '-'
	var firstValue = priceValues.hasOwnProperty('first') ? (priceValues.first * 100 / basePrice.first).toFixed(0) : '-'

    return  economyValue + "%" + " / " + businessValue + "%" + " / " + firstValue + "%"
}

function toLinkClassValueString(linkValues, prefix = "", suffix = "", displayDiscountEconomy = false) {
    const formatValue = (value) => value > 0 ? (value >= 10000 ? commaSeparateNumber(value) : value) : '-';

    const discountValue = linkValues.discountEconomy || 0;
    const economyValue = (linkValues.economy || 0) + (!displayDiscountEconomy ? discountValue : 0);

    const values = {
        discount: formatValue(discountValue),
        economy: formatValue(economyValue),
        business: formatValue(linkValues.business),
        first: formatValue(linkValues.first)
    };

    const parts = displayDiscountEconomy
        ? [values.discount, values.economy, values.business, values.first]
        : [values.economy, values.business, values.first];

    return parts.map(v => `${prefix}${v}${suffix}`).join(' / ');
}

function toLinkClassDiv(linkValues, prefix, suffix) {
	if (!prefix) {
		prefix = ""
	}
	if (!suffix) {
		suffix = ""
	}
	var economyValue = linkValues.hasOwnProperty('economy') ? linkValues.economy : '-'
	var businessValue = linkValues.hasOwnProperty('business') ? linkValues.business : '-'
	var firstValue = linkValues.hasOwnProperty('first') ? linkValues.first : '-'
	return `<div class="class-values"><p class="class-value-child text-base font-mono economy">${prefix + economyValue + suffix}</p><p class="class-value-child text-base font-mono business">${prefix + businessValue + suffix}</p><p class="class-value-child text-base font-mono first">${prefix + firstValue + suffix}</p></div>`
}

function changeColoredElementValue(element, newValue) {
	var oldValue = element.data('numericValue')
	if ($.isNumeric(newValue)) {
		element.data('numericValue', parseFloat(newValue))
	}

	if (!element.is(':animated') && $.isNumeric(oldValue) && $.isNumeric(newValue)) { //only do coloring for numeric values
		var originalColor = element.css("color")
		var originalBackgroundColor = element.css("background-color")

		if (parseFloat(oldValue) < parseFloat(newValue)) {
			element.animate({"background-color" : "#A1D490", "color" : "#248F00"}, 1000, function() {
				element.animate({backgroundColor: originalBackgroundColor, color : originalColor }, 2000, function() {
                    element.css({backgroundColor : "", color : ""})
                })
			})
		} else if (parseFloat(oldValue) > parseFloat(newValue)) {
			element.animate({"background-color" : "#F7B6A1", "color" : "#FA7246"}, 1000, function() {
				element.animate({backgroundColor: originalBackgroundColor, color : originalColor }, 2000, function() {
                    element.css({backgroundColor : "", color : ""})
                })
			})
		} else {
			element.animate({"background-color" : "#FFFC9E", "color" : "#FFDD00"}, 1000, function() {
				element.animate({backgroundColor: originalBackgroundColor, color : originalColor }, 2000, function() {
                    element.css({backgroundColor : "", color : ""})
                })
			})
		}
	}
	if ($.isNumeric(newValue)) {
		element.text(commaSeparateNumber(newValue))
		if (newValue < 0) {
		    element.addClass('negative')
		} else {
		    element.removeClass('negative')
		}
	} else {
		element.text(newValue)
	}
}

function commaSeparateNumber(val, shorthand = "") {
    var isNegative = val < 0
    val = Math.abs(Math.trunc(Number(val) * 1000) / 1000)
    if (shorthand === "auto") {
        if (val >= 2500000000) {
            shorthand = "b"
        } else if (val >= 2000000) {
            shorthand = "m"
        } else if (val >= 2500) {
            shorthand = "k"
        } else {
            shorthand = ""
        }
    }
    if (shorthand === "k") val /= 1000
    if (shorthand === "m") val /= 1000000
    if (shorthand === "b") val /= 1000000000
    if (shorthand.length > 0) val = val.toFixed(1) //for shorthand, always show 1 decimal place
    while (/(\d+)(\d{3})/.test(val.toString())) {
        val = val.toString().replace(/(\d+)(\d{3})/, '$1' + ',' + '$2');
    }
    return isNegative ? ('(' + val + shorthand + ')') : val + shorthand;
}

/**
 * @value {number} 1-10
 * @width {number} in px
 * @returns {html} 5 stars, either full, half, or empty
 **/
function getGradeStarsImgs(value, width = 16) {
	const halfStar = value % 2
	const fullStars = Math.floor(value / 2)
	let html = ""
	for (let i = 0 ; i < fullStars; i ++) {
		html += `<img width='${width}' src='/assets/images/icons/star-full.svg'/>`
	}
	if (halfStar) {
		html += `<img width='${width}' src='/assets/images/icons/star-half.svg'/>`
	}
	for (let i = 0 ; i < 5 - fullStars - halfStar; i ++) {
		html += `<img width='${width}' src='/assets/images/icons/star-empty.svg'/>`
	}
	return html
}

function getCountryFlagImg(countryCode, height = "11px") {
	if (countryCode) {
		var countryFlagUrl = getCountryFlagUrl(countryCode);
		var countryName = loadedCountriesByCode[countryCode]?.name
		if (countryFlagUrl) {
            return `<img width='auto' height='${height}' class='flag' loading='lazy' src='${countryFlagUrl}' title='${countryName}' alt='${countryName} flag' style='border-radius:0;'/>`;
		} else {
			return ""
		}
	} else {
		return "";
	}
}

function getCountryFlagUrl(countryCode) {
    return countryCode ? `/assets/images/flags/${countryCode}.svg` : ``;
}

function getAirlineLogoImg(airlineId) {
	return `<img class='logo' loading='lazy' width='36px' height='auto' alt='Airline Logo' src='/airlines/${airlineId}/logo'/>`
}

function getAllianceLogoImg(allianceId, slogan = '') {
	const tooltipAttr = slogan ? ` data-tooltip="${slogan.replace(/"/g, '&quot;')}"` : '';
	return `<img class='logo-alliance' loading='lazy' width='36px' height='18px' src='/alliances/${allianceId}/logo'${tooltipAttr}/>`
}

function getAllianceOrAirlineLogoImg(allianceId, airlineId) {
    let allianceSrc = "/alliances/" + allianceId + "/logo"
    let airlineSrc = "/airlines/" + airlineId + "/logo"

    let img = document.createElement('img');
    img.className = 'logo';
    img.loading = 'lazy';
    img.src = airlineSrc;

    // Preload alliance image and swap on success
    let preload = new Image();
    preload.onload = function() {
        try {
            img.src = allianceSrc;
            img.className = 'logo-alliance';
        } catch (e) {
            // swallow
        }
    };
    preload.onerror = function() { /* swallow network errors */ };
    preload.src = allianceSrc;

    return img;
}

function getAirlineLabelSpan(airlineId, airlineName, elementType = 'span') {
    var $airlineLabelSpan = $('<' + elementType + '>' + airlineName + '</' + elementType + '>')
	if (airlineLabelColors[airlineId]) {
	    $airlineLabelSpan.css('color', '#' + airlineLabelColors[airlineId])
	}
	return $airlineLabelSpan[0].outerHTML
}

function getAirlineSpan(airlineId, airlineName, tooltip = null) {
    var $airlineSpan = $('<span class="flex-align-center"></span>')
	$airlineSpan.append(getAirlineLogoImg(airlineId))
	$airlineSpan.append(getAirlineLabelSpan(airlineId, airlineName))
	if (tooltip) {
		$airlineSpan.append(`<div class="tooltiptext below" style="min-width: 140px; padding: 12px">${tooltip}</div>`)
        $airlineSpan.addClass('tooltip')
	}

	return $airlineSpan[0].outerHTML
}

function getAirlineLogoSpan(airlineId, airlineName) {
    var $airlineLogoSpan = $('<span></span>')
	$airlineLogoSpan.append(getAirlineLogoImg(airlineId))
	$airlineLogoSpan.attr("title", airlineName)
    return $airlineLogoSpan
}

function getUserLevelImg(level) {
	if (level <= 0) {
		return ""
	}
	var levelTitle
	var levelIcon
	if (level == 1) {
		levelIcon = "/assets/images/icons/medal-bronze-premium.png"
		levelTitle = "Patreon : Bronze"
	} else if (level == 2) {
		levelIcon = "/assets/images/icons/medal-silver-premium.png"
		levelTitle = "Patreon : Silver"
	} else if (level == 3) {
		levelIcon = "/assets/images/icons/medal-red-premium.png"
		levelTitle = "Patreon : Gold"
	}

	if (levelIcon) {
		return "<img src='" + levelIcon + "' title='" + levelTitle + "' style='vertical-align:middle;'/>"
	} else {
		return ""
	}
}

function getAdminImg(adminStatus) {
	if (!adminStatus) {
		return ""
	}

	var	levelIcon = "/assets/images/icons/star-full.svg"
    var levelTitle = "Game Admin"

	if (levelIcon) {
		return "<img src='" + levelIcon + "' title='" + levelTitle + "' style='vertical-align:middle;'/>"
	} else {
		return ""
	}
}

function getUserModifiersSpan(modifiers) {
	if (!modifiers) {
		return ""
	}

    var result = ""
    $.each(modifiers, function(index, modifier) {
        if (modifier == "WARNED") {
           result += "<span><img src='/assets/images/icons/exclamation.png' title='" + modifier + "' style='vertical-align:middle;'/></span>"
        } else if (modifier == "CHAT_BANNED") {
           result += "<span><img src='/assets/images/icons/mute.png' title='" + modifier + "' style='vertical-align:middle;'/></span>"
        } else if (modifier == "BANNED") {
           result += "<span><img src='/assets/images/icons/prohibition.png' title='" + modifier + "' style='vertical-align:middle;'/></span>"
        } else {
           result += "<span>" + modifier + "</span>"
        }
    })
    return result
}

function getAirlineModifiersSpan(modifiers) {
	if (!modifiers) {
		return ""
	}

    var result = ""
    $.each(modifiers, function(index, modifier) {
        if (modifier == "NERFED") {
           result += "<span><img src='/assets/images/icons/ghost.png' title='" + modifier + "' style='vertical-align:middle;'/></span>"
        } else if (modifier == "BANNER_LOYALTY_BOOST") {
            result += "<span><img src='/assets/images/icons/megaphone.png' title='Banner contest winner!' style='vertical-align:middle;'/></span>"
        }
//        } else { //let's no show modifiers that are not listed for now. since they could be common
//           result += "<span>" + modifier + "</span>"
    })
    return result
}

function getRankingImg(ranking, limitToTop3 = false) {
	var rankingIcon
	var rankingTitle
	if (ranking == 1) {
		rankingIcon = "/assets/images/icons/crown.png"
		rankingTitle = "1st place"
	} else if (ranking == 2) {
		rankingIcon = "/assets/images/icons/crown-silver.png"
		rankingTitle = "2nd place"
	} else if (ranking == 3) {
		rankingIcon = "/assets/images/icons/crown-bronze.png"
    	rankingTitle = "3rd place"
	} else if (ranking <= 10 && limitToTop3 !== true) {
		rankingIcon = "/assets/images/icons/trophy-" + ranking + ".png"
		rankingTitle = ranking + "th place"
	} else if (ranking <= 20 && limitToTop3 !== true) {
		rankingIcon = "/assets/images/icons/counter-" + ranking + ".png"
        rankingTitle = ranking + "th place"
	}

	if (rankingIcon) {
		return "<img src='" + rankingIcon + "' title='" + rankingTitle + "' style='vertical-align:middle; padding-right: 2px;'/>";
	} else {
		return "";
	}
}

function toHoursAndMinutes(totalMinutes) {
	const hours = Math.floor(totalMinutes / 60);
	const minutes = totalMinutes % 60;
	if(minutes < 10) {
		return { hours, minutes: "0" + minutes };
	}
	return { hours, minutes };
}

function getDurationText(duration) {
	var hour = Math.floor(duration / 60)
	var minute = duration % 60
	if (hour > 0) {
		return hour + " h " + minute + "m"
	} else {
		return minute + " m"
	}
}

function getYearMonthText(weekDuration) {
	const year = Math.floor(weekDuration / 52)
	const month = Math.floor(weekDuration / 4) % 12
	const yearTxt = year == 1 ? " year " : " years "
	const monthTxt = month == 1 ? " month " : " months "
	if (year > 0) {
		return year + yearTxt + month + monthTxt
	} else {
		return month + monthTxt
	}
}

function getOpennessIcon(openness, size=null, isDomesticAirport=false, isGateway=false) {
	var description
	var icon
	if (size && size <= 2 && ! isGateway ){
        description = "International Flights Forbidden"
        icon = "prohibition.png"
    } else if (isDomesticAirport){
	    description = "Only Small-sized International Flights"
        icon = "prohibition.png"
	} else if (openness >= 7 || size >= 7) {
		description = "All International Transfers"
		icon = "globe--plus.png"
	} else {
		description = "No International to International Transfers"
		icon = "globe--exclamation.png"
	}
	return "<img src='/assets/images/icons/" + icon + "' title='" + description + "'/>"
}

function getOpennessSpan(openness, size=null, isDomesticAirport=false, isGateway=false, iconOnly=false) {
	var description
	var icon
    if (size && size <= 2 && ! isGateway ){
        description = "International Flights Forbidden"
        icon = "prohibition.png"
    } else if (isDomesticAirport){
        description = "Only Small-sized International Flights"
        icon = "prohibition.png"
	} else if (openness >= 7 || size >= 7) {
		description = "All International Connections"
		icon = "globe--plus.png"
	} else {
		description = "No International to International Connections"
		icon = "globe--exclamation.png"
	}
	return iconOnly ? "<img src='/assets/images/icons/" + icon + "' title='" + description + "'/>" : "" + description + "&nbsp;<img src='/assets/images/icons/" + icon + "'/>"
}

function scrollToRow($matchingRow, $container) {
    var row = $matchingRow[0]
    var baseOffset = $container.find(".table-row")[0].offsetTop //somehow first row is not 0...
    var realOffset = row.offsetTop - baseOffset
    $container.stop(true, true) //stop previous animation
    $container.animate ({scrollTop: realOffset}, "fast");
}

/*
Get a span with value and a tool tip of breakdown
*/
function getBoostSpan(finalValue, boosts, $tooltip, prepend = "") {
    var $valueSpan = $('<span>' + prepend + commaSeparateNumber(finalValue) + '</span>')
    // Only treat boosts as present when it's a non-empty collection (array or object)
    var hasBoosts = boosts && (Array.isArray(boosts) ? boosts.length > 0 : Object.keys(boosts).length > 0)
    if (hasBoosts) {
        $valueSpan.css('color', '#41A14D')
        $tooltip.find('.table .table-row').remove()
        var $table = $tooltip.find('.table')
        $.each(boosts, function(index, entry) {
            // support both {description, boost} and {source, value} shapes
            var description = entry.description || entry.source || ''
            var boostVal = (entry.boost !== undefined && entry.boost !== null) ? entry.boost : entry.value
            // fallback to 0 for safe formatting
            if (boostVal === undefined || boostVal === null) {
                boostVal = 0
            }
            var $row = $('<div class="table-row"><div class="cell" style="width: 70%;">' + description + '</div><div class="cell" style="width: 30%; text-align:right;">+' + commaSeparateNumber(boostVal) + '</div></div>')
            $row.css('color', 'white')
            $table.append($row)
        })

        $valueSpan.hover( function() {
             var yPos = $(this).offset().top - $(window).scrollTop() + $(this).height()
             var xPos = $(this).offset().left - $(window).scrollLeft() + $(this).width() - $tooltip.width() / 2

             $tooltip.css('top', yPos + 'px')
             $tooltip.css('left', xPos + 'px')
             $tooltip.show()
        }, function() { $tooltip.hide() }
        )
    }
    return $valueSpan
}

//clone from template if not exists, assign with new id; otherwise return the previously created object
function createIfNotExist($template, id) {
    var $target = $('#' + id)
    if ($target.length) {
        return $target
    }
    $target = $template.clone()
    $target.prop('id', id)
    $template.parent().append($target) //has to attach it somewhere

    return $target
}

function sortPreserveOrder(array, property, ascending) {
	if (ascending == undefined) {
		ascending = true
	}
    var sortOrder = 1;

    if(!ascending) {
        sortOrder = -1;
    }

	var sortArray = array.map(function(data, idx){
	    return {idx:idx, data:data}
	})

	sortArray.sort(function(a, b) {
		var aVal = a.data[property]
    	var bVal = b.data[property]
    	if (Array.isArray(aVal) && Array.isArray(bVal)) {
    		aVal = aVal.length
    		bVal = bVal.length
    	}
    	var result = (aVal < bVal) ? -1 : (aVal > bVal) ? 1 : 0;
    	if (result == 0) {
    		return a.idx - b.idx
    	} else {
    		return result * sortOrder;
    	}
	});

	var result = sortArray.map(function(val){
	    return val.data
	});

	return result;
}

function sortByProperty(property, ascending) {
	if (ascending == undefined) {
		ascending = true
	}
    var sortOrder = 1;

    if(!ascending) {
        sortOrder = -1;
    }

    var keys = property.split('.')

    return function (a,b) {
    	var aVal = keys.reduce(function(obj, key) { return obj == null ? undefined : obj[key] }, a)
    	var bVal = keys.reduce(function(obj, key) { return obj == null ? undefined : obj[key] }, b)
    	if (Array.isArray(aVal) && Array.isArray(bVal)) {
    		aVal = aVal.length
    		bVal = bVal.length
    	}
    	if (aVal == null && bVal == null) return 0;
    	if (aVal == null) return 1;
    	if (bVal == null) return -1;
    	var result = (aVal < bVal) ? -1 : (aVal > bVal) ? 1 : 0;
        return result * sortOrder;
    }
}

function padAfter(str, padChar, max) {
    str = str.toString();
	return str.length < max ? padAfter(str + padChar, padChar, max) : str;
}
function padBefore(str, padChar, max) {
	str = str.toString();
	return str.length < max ? padBefore(padChar + str, padChar, max) : str;
}

function getAirportText(city, airportCode) {
	if (city) {
		return city + " (" + airportCode + ")"
	} else {
		return airportCode
	}
}

function getAirportSpan(airport) {
    return "<span style='align-items: center;'>" + getCountryFlagImg(airport.countryCode) + getAirportText(airport.city, airport.iata) + "</span>"
}

function setActiveDiv(activeDiv, callback) {
	var existingActiveDiv = activeDiv.siblings(":visible").filter(function (index) {
		return $(this).css("clear") != "both" && $(this).css("position") != "fixed"
	})
	if (!callback && activeDiv.data("initCallback")) {
        callback = activeDiv.data("initCallback")
        activeDiv.removeData("initCallback")
	}

	if (existingActiveDiv.length > 0){
	    existingActiveDiv.removeClass('active')
	    activeDiv.addClass('active')
        existingActiveDiv.hide()
        activeDiv.fadeIn(400, callback)
	} else {
		if (activeDiv.is(":visible")) { //do nothing. selecting the same div as before
		    if (callback) {
		        callback()
		    }
			return false;
		} else {
			activeDiv.siblings().filter(function() { return $(this).css("position") != "fixed"; }).hide();
			activeDiv.addClass('active')
    	    activeDiv.fadeIn(200, callback);
		}
	}


	activeDiv.parent().show()
	return true;
}

function hideActiveDiv(activeDiv) {
	if (activeDiv.is(":visible")){
		activeDiv.fadeOut(200)
		activeDiv.parent().hide()
	}
}

/**
 * Show a map overlay panel inside #worldMapCanvas, hiding any other visible overlay panels.
 */
function showMapOverlay(overlay) {
    $('#worldMapCanvas > .mapOverlayPanel').not(overlay).hide();
    overlay.fadeIn(200);
}

/**
 * Hide all map overlay panels inside #worldMapCanvas.
 */
function hideMapOverlays() {
    $('#worldMapCanvas > .mapOverlayPanel').fadeOut(200);
}

function toggleOnOff(element) {
	if (element.is(":visible")){
		element.fadeOut(200)
	} else {
		element.fadeIn(200)
	}
}

function highlightSwitch(selectedSwitch) {
	selectedSwitch.siblings().removeClass("selected")
	selectedSwitch.addClass("selected")
}

function closeModal(modal) {
    modal.fadeOut(200)
    var callback = modal.data("closeCallback")
    if (callback) {
        callback()
        modal.removeData("closeCallback")
    }
}

function closeAllModals() {
    var closedModals = []
    $.each($(".modal"), function(index, modal) {
        if ($(modal).is(":visible")) {
            closeModal($(modal))
             closedModals.push(modal);  // Add the closed modal to the array
        }
    });
    return closedModals
}

function disableButton(button, reason) {
    $(button).addClass("disabled")

    $(button).each(function() {
      $(this).data("originalClickFunction", $(this).attr("onclick"))
    })

    if (isTouchDevice()) {
        $(button).find(".touchTitle").remove()
        if (reason) {
            $(button).css({position: 'relative'});
            var touchTitleSpan = $("<span style='display: none; text-transform: none;' class='touchTitle'>" + reason + "</span>");
            $(button).append(touchTitleSpan)
            var addedClickFunction = function() {
                 if (touchTitleSpan.is(":visible")) {
                     touchTitleSpan.hide()
                 } else {
                     touchTitleSpan.show()
                 }
             }
            $(button).removeAttr("onclick") //remove on click function
            $(button).click(addedClickFunction)
            $(button).data("addedClickFunction", addedClickFunction)
        }
    } else {
        if (reason) {
            //remove any existing tooltip
            $(button).find('.tooltiptext').remove()
            //add tooltip
            $(button).addClass("tooltip")
            var $descriptionSpan = $('<span class="tooltiptext top alignLeft" style="width: 400px;  text-transform: none;">')
            $descriptionSpan.text(reason)
            $(button).append($descriptionSpan)
        }
        $(button).removeAttr("onclick") //remove on click function
    }
}

function enableButton(button) {
    $(button).removeClass("disabled")

    $(button).each(function() {
        var addedClickFunction = $(this).data("addedClickFunction")
        if (addedClickFunction) {
            $(this).unbind("click", addedClickFunction)
        }
        var originalClickFunction = $(this).data("originalClickFunction")
        if (originalClickFunction) {
            $(this).attr("onclick", originalClickFunction) //set it back
        }
        if ($(this).is(':input')) { //then have to manually remove overlay
             $(this).next('div.overlay').remove()
        }
    })

    if (isTouchDevice()) {
        $(button).find(".touchTitle").remove()
    } else {
        $(button).removeClass("tooltip")
        $(button).find('span.tooltiptext').remove()
    }
}

function addTooltip($target, text, css) {
    $target.addClass('tooltip')
    if ($target.css('overflow') === 'hidden') {
        $target.css('overflow', 'unset')
    }
    $target.children('span.tooltiptext').remove()
    var $descriptionSpan = $('<span class="tooltiptext below alignLeft" style="text-transform: none;">')
    $descriptionSpan.text(text)
    if (css) {
        $descriptionSpan.css(css)
    }
    $target.append($descriptionSpan)
}

function addTooltipHtml($target, html, css) {
    $target.addClass('tooltip')
    if ($target.css('overflow') === 'hidden') {
        $target.css('overflow', 'unset')
    }
    $target.children('span.tooltiptext').remove()
    var $descriptionSpan = $('<span class="tooltiptext below alignLeft" style="text-transform: none;">')
    $descriptionSpan.html(html)
    if (css) {
        $descriptionSpan.css(css)
    }
    $target.append($descriptionSpan)
}

function isTouchDevice() {
  try {
    document.createEvent("TouchEvent");
    return true;
  } catch (e) {
    return false;
  }
}

function bindEnter(bindToElement, actionFunction) {
   $(bindToElement).keyup(function(ev) {
       // 13 is ENTER
       if (ev.which === 13) {
          actionFunction()
       }
   });
}

function isPremium() {
    return activeUser && activeUser.level > 0
}

function capitalizeFirstLetter(string) {
    return String(string).charAt(0).toUpperCase() + String(string).slice(1);
}

//from millisec
function toReadableDuration(duration) {
  var hours = Math.floor((duration / (1000 * 60 * 60)) % 24),
  days = Math.floor((duration / (1000 * 60 * 60 * 24)));
  var result = ""
  if (days > 0) {
    result += (days == 1 ? "1 day" : days + " days")
  }
  if (hours > 0) {
    result += " " + (hours == 1 ? "1 hour" : hours + " hours")
  }
  return result.trim()
}

function buildAffinityText(text) {
  const firstPipeIndex = text.indexOf("|");
  text = (firstPipeIndex !== -1) ? text.slice(0, firstPipeIndex) + "Diaspora communities: " + text.slice(firstPipeIndex) : text;
  text = text.replace("CC","Caribbean Community")
  const parts = text.split(",");
  const editedParts = parts.map((part) => {
    if (part.trim().endsWith("|")) {
      const updatedPart = part.replace(/\|(.*?)\|(.*?)\|/g,"$1");
      return updatedPart;
    } else {
      return part;
    }
  });
  return editedParts.join(",");
}

/**
 * from BAC
 * https://gist.github.com/aphix/fdeeefbc4bef1ec580d72639bbc05f2d
 * Aphix/Torus (original cost per PAX by Alrianne), mdons
 **/
function getLoadFactorsFor(consumption) {
    var factor = {};
    for (let key in consumption.capacity) {
        factor[key] = getFactorPercent(consumption, key) || "-";
    }
    return factor;
}
function getFactorPercent(consumption, subType) {
    return (consumption.capacity[subType] > 0)
        ? parseInt(consumption.soldSeats[subType] / consumption.capacity[subType] * 100)
        : null;
}
function _seekSubVal(val, ...subKeys) {
    if (subKeys.length === 0) {
        return val;
    }
    return _seekSubVal(val[subKeys[0]], ...subKeys.slice(1));
}

function averageFromSubKey(array, ...subKeys) {
    return array.map((obj) => _seekSubVal(obj, ...subKeys)).reduce((sum, val) => (sum += val || 0), 0) / array.length;
}

function formatNumberInput(_obj) {
    var num = parseNumber(_obj.val());
    _obj.val(commaSeparateNumber(num));
}

function parseNumber(_str) {
    if (typeof(_str) !== 'number') {
        var arr = _str.split('');
        var out = new Array();
        for (var cnt = 0; cnt < arr.length; cnt++) {
            if (isNaN(arr[cnt]) == false) {
                out.push(arr[cnt]);
            }
        }
        return Number(out.join(''));
    } else {
        return _str
    }
}

function getGameDate(cycle, period = "WEEKLY", hasUnits = false) {
    const periods = Math.floor(cycle / 48)
    const remainder = cycle % 48
    const remainderUnit = hasUnits ? " Week " : ""
    const periodUnit = hasUnits ? " Year " : ""
    const seperator = hasUnits ? "," : "/"

    if (period == "WEEKLY") {
        return `${remainderUnit}${remainder}${seperator}${periodUnit}${periods}`;
    } else if (period == "QUARTER") {
        return `${remainderUnit}${remainder - 3}${seperator}${periodUnit}${periods} - ${remainderUnit}${remainder}${seperator}${periodUnit}${periods}`;
    } else if (period == "YEAR") {
        return `${remainderUnit}${remainder}${seperator}${periodUnit}${periods - 1} - ${remainderUnit}${remainder}${seperator}${periodUnit}${periods}`;
    }
}
/**
 * Updates all text nodes matching the selector
 * @param {string} selector 
 * @param {string} updateText 
 */
function updateAllTextNodes(selector, updateText) {
    document.querySelectorAll(selector).forEach(el => {
        el.textContent = updateText;
    });
}
/**
 * Creates a DOM element from an HTML string.
 * @param {string} html - The HTML string to parse.
 * @returns {HTMLElement | null} The first element created from the string.
 */
function htmlToElement(html) {
    const template = document.createElement('template');
    template.innerHTML = html.trim();
    return template.content.firstElementChild;
}

/**
 * Changes the color of DOM elements based on current value vs target value comparison
 * Uses a gradient color system: red for below target, neutral for on target, green for above target
 * consumes localStorage theme: light or dark mode
 * 
 * @param {number} currentValue - The current numeric value
 * @param {number} targetValue - The target numeric value to compare against
 * @param {string} domQueryString - CSS selector string to find elements
 * @param {boolean} inverse - If true, inverts the color logic (lower values are better)
 */
function updateElementColorsByValue(currentValue, targetValue, domQueryString, inverse = false) {
    const elements = document.querySelectorAll(domQueryString);
    const lightMode = localStorage.getItem("theme") ?? 'dark'
    
    let ratio = currentValue / targetValue;
    
    // If inverse is true, flip the ratio logic (lower is better)
    if (inverse) {
        ratio = targetValue / currentValue;
    }
    
    let color;
    
    // Set base color based on mode
    const baseColor = lightMode === 'dark' ? 255 : 0; // black for light mode, white for dark mode
    
    if (ratio > 0.95 && ratio < 1.05) {
        // close to target - base color (black in light mode, white in dark mode)
        color = `rgb(${baseColor}, ${baseColor}, ${baseColor})`;
    } else if (ratio <= 0.95) {
        // Below target (or above target if inverse) - gradient from dark red to light red to base color
        if (ratio <= 0.5) {
            // Very low (0-50% of target) - dark red to medium red
            const intensity = Math.max(0, ratio * 2); // 0 to 1
            if (lightMode) {
                const red = 139 + Math.floor((255 - 139) * intensity); // 139 to 255
                color = `rgb(${red}, 0, 0)`;
            } else {
                const red = 139 + Math.floor((255 - 139) * intensity); // 139 to 255
                color = `rgb(${red}, 0, 0)`;
            }
        } else {
            // Moderately low (50-100% of target) - medium red to light red to base color
            const intensity = (ratio - 0.5) * 2; // 0 to 1
            if (lightMode) {
                const red = 255 - Math.floor(255 * intensity);
                const green = Math.floor(baseColor * intensity);
                const blue = Math.floor(baseColor * intensity);
                color = `rgb(${red}, ${green}, ${blue})`;
            } else {
                const red = 255 - Math.floor(255 * intensity);
                const green = Math.floor(255 * intensity);
                const blue = Math.floor(255 * intensity);
                color = `rgb(${red}, ${green}, ${blue})`;
            }
        }
    } else {
        // Above target (or below target if inverse) - gradient from base color to light green to bright green
        if (ratio <= 2) {
            // Moderately high (100-200% of target) - base color to light green to medium green
            const intensity = (ratio - 1); // 0 to 1
            if (lightMode) {
                const red = baseColor - Math.floor(baseColor * intensity);
                const green = Math.min(255, baseColor + Math.floor((255 - baseColor) * intensity));
                const blue = baseColor - Math.floor(baseColor * intensity);
                color = `rgb(${red}, ${green}, ${blue})`;
            } else {
                const red = 255 - Math.floor(255 * intensity);
                const green = 255;
                const blue = 255 - Math.floor(255 * intensity);
                color = `rgb(${red}, ${green}, ${blue})`;
            }
        } else {
            // Very high (200%+ of target) - medium green to bright green
            const intensity = Math.min(1, (ratio - 2) / 2); // 0 to 1, capped at 1
            const green = 255 - Math.floor(100 * intensity); // 255 to 155
            color = `rgb(0, ${green}, 0)`;
        }
    }
    
    elements.forEach(element => {
        element.style.color = color;
    });
}

// Pretty-print dataset labels and format values when needed
function prettyLabel(prop, value, { currency = false } = {}) {
    // Convert camelCase / snake_case / dot.notation to Title Case
    let label = String(prop)
        .replace(/([a-z0-9])([A-Z])/g, '$1 $2') // camelCase -> space
        .replace(/[_\.\-]+/g, ' ') // snake_case or dot.case -> space
        .trim()
        .split(' ')
        .map(w => w.length > 1 ? (w[0].toUpperCase() + w.slice(1).toLowerCase()) : w.toUpperCase())
        .join(' ');

    if (value === undefined || value === null) {
        // when no value supplied, return just the pretty label
        return label;
    }

    if (currency && !isNaN(Number(value))) {
        return label + ': $' + commaSeparateNumber(value, 'auto');
    }

    return label + ': ' + value;
}

const debounce = (callback, wait) => {
    let timeoutId = null;
    return (...args) => {
        window.clearTimeout(timeoutId);
        timeoutId = window.setTimeout(() => {
            callback(...args);
        }, wait);
    };
}
