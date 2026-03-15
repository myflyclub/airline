function updateProfiles(profiles) {
    $('#profiles').find('.option').remove()
    $.each(profiles, function(index, profile) {
        $('#profiles').append(createProfileDiv(profile, index))
    })
    $('#profilesModal').fadeIn(500)
}

function createProfileDiv(profile, profileId) {
	var $profileDiv = $('<div class="option available" onclick="selectProfile(' + profileId + ', this)"></div>')
    $profileDiv.append('<h4>' + profile.name + '</h4>')
    if (profile.typeLabel) {
        var $typeSpan = $('<span data-tooltip-trigger>' + profile.typeLabel + '</span>')
        $typeSpan.css('text-decoration', 'underline dashed')
        $typeSpan.css('cursor', 'help')
        $typeSpan.on('mouseover', function() {
            showAirlineTypeTooltip($(this), profile.typeLabel, profile.rule)
        }).on('mouseout', function() {
            hideTooltip()
        })
        $typeSpan.on('click touchend', function(e) {
            e.preventDefault()
            e.stopPropagation()
            if ($('#popperTooltip').is(':visible')) {
                hideTooltip()
            } else {
                showAirlineTypeTooltip($(this), profile.typeLabel, profile.rule)
            }
        })
        var $typeP = $('<p class="pb-2"></p>')
        $typeP.append($typeSpan)
        $profileDiv.append($typeP)
    }
    $profileDiv.append('<p class="pb-2">' + profile.description + '</p>')
    var $list = $('<ul></ul>').appendTo($profileDiv)
    $list.append('<li class="dot">$' + commaSeparateNumber(profile.cash) + '&nbsp;cash</li>')
    if (profile.airplanes.length > 0) {
        var $airplaneLi = $('<li class="dot"></li>')
        $airplaneLi.appendTo($list).append('<span>' + profile.airplanes.length + ' X&nbsp;</span>')
        var $airplaneSpan = $('<span data-tooltip-trigger>' + profile.airplanes[0].name + '</span>')
        $airplaneSpan.css('text-decoration', 'underline dashed')
        $airplaneSpan.on('mouseover', function() {
            showAirplaneQuickSummary($(this), profile.airplanes[0])
        }).on('mouseout', function() {
            hideTooltip()
        })
        $airplaneSpan.on('click touchend', function(e) {
            e.preventDefault()
            e.stopPropagation()
            if ($('#popperTooltip').is(':visible')) {
                hideTooltip()
            } else {
                showAirplaneQuickSummary($(this), profile.airplanes[0])
            }
        })
        $airplaneLi.append($airplaneSpan)
    }
    $('<li class="dot"></li>').appendTo($list).text(profile.reputation + " reputation")
    $('<li class="dot"></li>').appendTo($list).text(profile.quality + " employee quality")
    if (profile.loan) {
        $('<li class="dot"></li>').appendTo($list).text("Outstanding loan of $" + commaSeparateNumber(profile.loan.remainingAmount) + " weekly payment of $" + commaSeparateNumber(profile.loan.weeklyPayment))
    }
    $profileDiv.append($list)

	if ($('#profileId').val() == profileId) {
		selectProfile(profileId, $profileDiv)
	}

	return $profileDiv
}

function selectProfile(profileId, profileDiv) {
	$('#profileId').val(profileId)
	$(profileDiv).siblings().removeClass("active")
	$(profileDiv).addClass("active")
}

function showAirplaneQuickSummary($trigger, airplane) {
    var $content = $('<div class="table" style="min-width:150px;"></div>')
    ;[
        ['Capacity',  airplane.capacity],
        ['Range',     airplane.range + ' km'],
        ['Lifespan',  (airplane.lifespan / 52) + ' years'],
        ['Condition', airplane.condition + '%'],
        ['Value',     '$' + commaSeparateNumber(airplane.value)]
    ].forEach(function(row) {
        $content.append(
            $('<div class="table-row"></div>')
                .append($('<div class="cell"></div>').text(row[0] + ':'))
                .append($('<div class="cell"></div>').text(row[1]))
        )
    })
    showTooltip($trigger[0], $content, { rich: true, placement: 'bottom' })
}

function showAirlineTypeTooltip($trigger, typeLabel, rules) {
    var $content = $('<div></div>')
    $content.append($('<h4></h4>').text(typeLabel))
    if (rules && rules.length > 0 && rules[0].length > 0) {
        var $ul = $('<ul style="list-style:disc; padding-left:16px;"></ul>')
        rules.forEach(function(ruleText) {
            $ul.append($('<li></li>').text(ruleText))
        })
        $content.append($ul)
    }
    showTooltip($trigger[0], $content, { rich: true, placement: 'bottom' })
}

$(document).on('touchend click', function(e) {
    if (!$(e.target).closest('#popperTooltip, [data-tooltip-trigger]').length) {
        hideTooltip()
    }
})

function buildHqWithProfile() {
    $.ajax({
            type: 'PUT',
            url: "/airlines/" + activeAirline.id + "/profiles/" + $('#profileId').val() + "?airportId=" + activeAirportId,
            data: { } ,
    		contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                closeModal($('#profilesModal'))
                updateAirlineInfo(activeAirline.id).then(function() {
                    if (activeAirline.headquarterAirport) {
                        $('#planLinkFromAirportId').val(activeAirline.headquarterAirport.airportId)
                    }
                    loadAllCountries() //has a home country now, reload country info
                    showWorldMap()
                })
            },
            error: function(jqXHR, textStatus, errorThrown) {
                    console.log(JSON.stringify(jqXHR));
                    console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
}
