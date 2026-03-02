function changeTaskDelegateCount($delegateSection, delta, callback) {
    var assignedDelegateCount = $delegateSection.data('assignedDelegateCount')
    var availableDelegates = $delegateSection.data('availableDelegates')
    // var originalDelegates = $delegateSection.data('originalDelegates')
    var delegatesRequired = $delegateSection.data('delegatesRequired')

    var newLength = -1
    if (delta > 0) {
        if (availableDelegates >= delta) {
            newLength = assignedDelegateCount + delta
        }
    } else if (delta < 0) {
        if (assignedDelegateCount + delta >= delegatesRequired) {
            newLength = assignedDelegateCount + delta
        }
    }

    if (newLength != -1) {
        $delegateSection.data('availableDelegates', availableDelegates - delta)
        $delegateSection.data('assignedDelegateCount', newLength)
        refreshAssignedDelegates(newLength, '#4a9eed', $delegateSection.find('.assignedDelegatesIcons'))
        if (callback) {
            callback(newLength)
        }
    }
}

function refreshAssignedDelegates(number, color, containerSelector) {
    const $container = $(containerSelector)
    $container.empty()
    for (let i = 0; i < number; i++) {
        $container.append(createManagerIcon(color, null))
    }
    if (number === 0) {
        $container.append('<span style="opacity:0.5;font-size:0.8em;">None</span>')
    }
}


function createManagerIcon(color, tooltip) {
    const $wrapper = $('<span class="tooltip-attr"></span>').css({
        display: 'inline-flex',
        margin: '2px',
        cursor: 'help',
        'flex-shrink': 0,
    })
    if (tooltip) $wrapper.attr('data-tooltip', tooltip)
    $wrapper.append($('<span></span>').css({
        display: 'block',
        width: '18px',
        height: '18px',
        'background-color': color,
        '-webkit-mask': 'url(/assets/images/icons/manager.svg) center/contain no-repeat',
        mask: 'url(/assets/images/icons/manager.svg) center/contain no-repeat',
    }))
    return $wrapper
}

function refreshAirlineDelegateStatus($container, delegateInfo) {
    $container.empty()

    const FLOORS = [
        { type: 'COUNTRY',                label: 'Country | Public Affairs' },
        { type: 'CAMPAIGN',               label: 'Advertising Campaigns' },
        { type: 'MANAGER_BASE',           label: 'Flight Operations' },
        { type: 'MANAGER_AIRCRAFT_MODEL', label: 'Manufacturer Relations' },
        { type: '_AVAILABLE',             label: 'Actioning' },
    ]

    const grouped = {}
    delegateInfo.busyDelegates.forEach(d => {
        const key = d.taskType || 'OTHER'
        if (!grouped[key]) grouped[key] = []
        grouped[key].push(d)
    })

    const $building = $('<div></div>').css({
        border: '1px solid rgba(255,255,255,0.12)',
        'border-radius': '3px',
        overflow: 'hidden',
        width: '100%',
        'box-sizing': 'border-box',
    })

    FLOORS.forEach((floor, idx) => {
        const $row = $('<div></div>').css({
            display: 'flex',
            'align-items': 'center',
            'min-height': '30px',
            'border-top': idx > 0 ? '1px solid rgba(255,255,255,0.08)' : 'none',
        })

        const $label = $(`<span class="very-small">${floor.label}</span>`).css({
            width: '72px',
            'flex-shrink': 0,
            padding: '2px 6px',
            opacity: 0.6,
            'border-right': '1px solid rgba(255,255,255,0.08)',
            'align-self': 'stretch',
            display: 'flex',
            'align-items': 'center',
            'justify-content': 'flex-end',
        })

        const $icons = $('<span></span>').css({
            display: 'flex',
            'flex-wrap': 'wrap',
            'align-items': 'center',
            gap: '2px',
            padding: '3px 6px',
            flex: 1,
        })

        if (floor.type === '_AVAILABLE') {
            for (let i = 0; i < delegateInfo.availableCount; i++) {
                $icons.append(createManagerIcon('#5cb85c', 'Available delegate'))
            }
            if (delegateInfo.availableCount === 0) {
                $icons.append('<span style="opacity:0.3;font-size:0.7em;">—</span>')
            }
        } else {
            const delegates = grouped[floor.type] || []
            delegates.forEach(d => {
                if (d.completed) {
                    $icons.append(createManagerIcon('#e88d2b', d.coolDown + 'w cooldown — ' + d.taskDescription))
                } else {
                    $icons.append(createManagerIcon('#4a9eed', d.taskDescription))
                }
            })
            if (delegates.length === 0) {
                $icons.append('<span style="opacity:0.3;font-size:0.7em;">—</span>')
            }
        }

        $row.append($label).append($icons)
        $building.append($row)
    })

    $container.append($building)
}


function updateAirlineDelegateStatus($delegateStatusDiv, successFunction) {
    $delegateStatusDiv.empty()

	$.ajax({
		type: 'GET',
		url: "/delegates/airline/" + activeAirline.id,
		contentType: 'application/json; charset=utf-8',
		dataType: 'json',
	    success: function(delegateInfo) {
	        refreshAirlineDelegateStatus($delegateStatusDiv, delegateInfo)

            if (successFunction) {
                successFunction(delegateInfo)
            }
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function refreshTopBarDelegates(airline) {
    $('#topBar .delegatesShortcut').empty()
	var availableDelegates = airline.delegatesInfo.availableCount
	var busyDelegates = airline.delegatesInfo.busyDelegates.length
	var $delegateIconDiv = $('<div style="position: relative; display: inline-block;"></div>').appendTo($('#topBar .delegatesShortcut'))
    var $delegateIcon = $('<img>').appendTo($delegateIconDiv)

    if (availableDelegates <= 0) {
        $delegateIcon.attr('src', '/assets/images/icons/user-silhouette-unavailable.png')
        var minCoolDown = -1
        $.each(airline.delegatesInfo.busyDelegates, function(index, busyDelegate) {
            if (busyDelegate.completed) {
                if (minCoolDown == -1 || busyDelegate.coolDown < minCoolDown) {
                    minCoolDown = busyDelegate.coolDown
                }
            }
        })
        if (minCoolDown != -1) {
            var $coolDownDiv = $("<div style='position: absolute; left: 1px; bottom: 0; background-color: #FFC273; color: #454544; font-size: 8px; font-weight: bold;'></div>")
            $coolDownDiv.text(minCoolDown)
            $delegateIconDiv.append($coolDownDiv)
        }
        $delegateIconDiv.attr('title', "Next delegate available in " + minCoolDown + " weeks. Delegates (available/total) : 0/" + busyDelegates)
    } else {
        $delegateIcon.attr('src', '/assets/images/icons/user-silhouette-available.png')
        var $availableCountDiv = $("<div style='position: absolute; left: 1px; bottom: 0; background-color: #a4f5b0; color: #454544; font-size: 8px; font-weight: bold;'></div>")
        $availableCountDiv.text(availableDelegates)
        $delegateIconDiv.append($availableCountDiv)
        $delegateIconDiv.attr('title', "Delegates (available/total) : " + availableDelegates + "/" + (availableDelegates + busyDelegates))
    }
}

function toggleDelegateStatusModal() {
    if (!$("#delegateStatusModal").is(":visible")) {
        updateAirlineDelegateStatus($('#delegateStatusModal .delegateStatus'))
        $('#delegateStatusModal').fadeIn(500)
    } else {
        closeModal($('#delegateStatusModal'))
    }
}