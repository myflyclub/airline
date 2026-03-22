function computeApTooltip(currentAP, managersInfo) {
    if (!managersInfo) return "Action Points"
    const available = Math.max(0, managersInfo.availableCount)
    var rate = 0.0
    if (available > 0) {
        if (currentAP > 24.0 * 2 * available) rate = 0.0
        else if (currentAP > 8.0 * 2 * available) rate = 0.08
        else rate = 0.1
    }
    if (rate === 0) {
        if (available === 0) return "Action Points — no ⚡ generation (no available managers)"
        return "Action Points — no ⚡ generation (max cap reached)"
    }
    const perCycle = rate * available
    const per48 = perCycle * 48
    return "Action Points! Generating " + perCycle.toFixed(1) + " ⚡ per week · " + per48.toFixed(0) + " ⚡ in " + formatCycleCountTime(48)
}

function changeTaskManagerCount($managerSection, delta, callback) {
    var assignedManagerCount = $managerSection.data('assignedManagerCount')
    var availableManagers = $managerSection.data('availableManagers')
    var managersRequired = $managerSection.data('managersRequired')

    var newLength = -1
    if (delta > 0) {
        if (availableManagers >= delta) {
            newLength = assignedManagerCount + delta
        }
    } else if (delta < 0) {
        if (assignedManagerCount + delta >= managersRequired) {
            newLength = assignedManagerCount + delta
        }
    }

    if (newLength != -1) {
        $managerSection.data('availableManagers', availableManagers - delta)
        $managerSection.data('assignedManagerCount', newLength)
        refreshAssignedManagers(newLength, '#4a9eed', $managerSection.find('.assignedActionPointsIcons'))
        if (callback) {
            callback(newLength)
        }
    }
}

function refreshAssignedManagers(number, color, containerSelector) {
    renderManagerAssignment({ container: containerSelector, count: number, color })
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

// Colors by task type; completed always orange
function _managerColor(m) {
    if (m.completed) return '#e88d2b'
    const MAP = { COUNTRY: '#7ec8a0', CAMPAIGN: '#e879a0', MANAGER_AIRCRAFT_MODEL: '#4a9eed', MANAGER_BASE: '#a08060' }
    return MAP[m.taskType] || '#4a9eed'
}

// Tooltip: "Novice · Public Affairs - France · next level in 4h 0m"
function _managerTooltip(m) {
    var parts = []
    if (m.completed) {
        parts.push(m.coolDown + 'w cooldown — ' + (m.taskDescription || 'Task complete'))
    } else {
        parts.push(m.levelDescription ? m.levelDescription + ' · ' + (m.taskDescription || '') : (m.taskDescription || ''))
    }
    if (m.nextLevelCycleCount != null) parts.push('next level in ' + formatCycleCountTime(m.nextLevelCycleCount))
    else if (m.levelDescription != null && !m.completed) parts.push('max level')
    return parts.join(' · ') || 'Week 0 Onboarding'
}

/**
 * @param {Object} options
 *   container      {string|Element|jQuery}  - emptied and rendered into
 *   managers       {Array}   - rich manager API objects (takes precedence over count/color)
 *   count          {number}  - icon count when no rich array available
 *   color          {string}  - icon color for count mode (default '#4a9eed')
 *   availableCount {number}  - available pool (gates + button)
 *   maxManagers    {number}  - hard cap (gates + button)
 *   onAdd          {Function}- if present, renders + button
 *   onRemove       {Function}- if present, renders - button
 *   extraInfo      {string|jQuery} - appended below icon row
 *   emptyText      {string}  - placeholder when no icons (default 'None')
 */
function renderManagerAssignment(options) {
    const { container, managers, count, color = '#4a9eed', availableCount = 0,
            maxManagers, onAdd, onRemove, extraInfo, emptyText = 'None', headerText,
            defaultTooltip = null } = options
    const $container = $(container)
    $container.empty()

    if (headerText) {
        $container.append($('<div>').addClass('text-xs opacity-70 pb-1').text(headerText))
    }

    const assignedLen = Array.isArray(managers) ? managers.length : (count || 0)
    const $row = $('<div>').css({ display:'flex', alignItems:'center', gap:'4px', flexWrap:'wrap' })
    const $icons = $('<div>').css({ display:'flex', alignItems:'center', flexWrap:'wrap', gap:'2px' })

    if (Array.isArray(managers) && managers.length > 0) {
        managers.forEach(m => $icons.append(createManagerIcon(_managerColor(m), _managerTooltip(m))))
    } else if (assignedLen > 0) {
        for (let i = 0; i < assignedLen; i++) $icons.append(createManagerIcon(color, defaultTooltip))
    } else {
        $icons.append($('<span>').css({ opacity:'0.5', fontSize:'0.8em' }).text(emptyText))
    }

    $row.append($icons)

    if (onAdd) {
        const atMax = maxManagers != null && assignedLen >= maxManagers
        const noAvail = availableCount <= 0
        const $btn = $('<img class="img-button svg svg-hover-green svg-monochrome" src="/assets/images/icons/plus.svg">')
            .css({ width:'14px', height:'14px' })
            .attr('title', atMax ? 'Maximum managers reached' : noAvail ? 'No managers available' : 'Assign manager')
        if (atMax || noAvail) $btn.css('opacity', '0.35')
        else $btn.css('cursor','pointer').on('click', onAdd)
        $row.append($btn)
    }

    if (onRemove) {
        const $btn = $('<img class="img-button svg svg-hover-green svg-monochrome" src="/assets/images/icons/minus.svg">')
            .css({ width:'14px', height:'14px' }).attr('title', 'Remove manager')
        if (assignedLen <= 0) $btn.css('opacity', '0.35')
        else $btn.css('cursor','pointer').on('click', onRemove)
        $row.append($btn)
    }

    $container.append($row)
    if (extraInfo != null) $container.append($(extraInfo))
}

function refreshAirlineManagerStatus($container, managersInfo) {
    $container.empty()

    const FLOORS = [
        { type: 'COUNTRY',                label: 'Public Affairs' },
        { type: 'CAMPAIGN',               label: 'Advertising Campaigns' },
        { type: 'MANAGER_BASE',           label: 'Base Operations' },
        { type: 'MANAGER_AIRCRAFT_MODEL', label: 'Manufacturer Relations' },
        { type: '_AVAILABLE',             label: 'Actioning' },
    ]

    const grouped = {}
    managersInfo.busyManagers.forEach(d => {
        const key = d.taskType || 'OTHER'
        if (!grouped[key]) grouped[key] = []
        grouped[key].push(d)
    })

    const $building = $('<div></div>').addClass('border rounded w-full')

    FLOORS.forEach((floor, idx) => {
        const $row = $('<div></div>')
            .addClass('flex-row items-center px-2 py-1')
            .css({
                gap: '0',
            })

        if (idx > 0) {
            $row.addClass('border-top')
        }

        const $label = $('<div></div>')
            .addClass('text-xxs opacity-70 pr-2 w-56')
            .css({
                'flex-shrink': 0,
                'line-height': '1.2',
            })
            .text(floor.label)

        const $value = $('<div></div>').css({
                flex: '1 1 auto',
                'min-width': '0',
            })

        const $managerRow = $('<div></div>').addClass('flex-align-center flex-scroll-x-hidden')

        if (floor.type === '_AVAILABLE') {
            renderManagerAssignment({
                container: $managerRow,
                count: managersInfo.availableCount,
                color: '#5cb85c',
                emptyText: '—',
            })
        } else {
            renderManagerAssignment({
                container: $managerRow,
                managers: grouped[floor.type] || [],
                emptyText: '—',
            })
        }

        $managerRow.children().first().css({
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            flexWrap: 'nowrap',
            width: 'max-content',
            minWidth: '100%',
        })

        $managerRow.find('.tooltip-attr').css({
            margin: '1px',
        })

        $value.append($managerRow)
        $row.append($label).append($value)
        $building.append($row)
    })

    $container.append($building)
}

function showManagerModal() {
    const $table = $('#managerModalTable')
    $table.find('.table-row').remove()
    $('#managerModalEmpty').hide()

    $.ajax({
        type: 'GET',
        url: `/managers/airline/${activeAirline.id}/leveling`,
        dataType: 'json',
        success(managers) {
            if (managers.length === 0) {
                $('#managerModalEmpty').show()
            } else {
                managers.forEach(m => {
                    const $row = $('<div class="table-row"></div>')

                    let taskLabel
                    if (m.taskType === 'COUNTRY')
                        taskLabel = `Public Affairs · ${m.countryName}`
                    else if (m.taskType === 'CAMPAIGN')
                        taskLabel = `Campaign · ${m.airportName} ${m.airportIata}`
                    else if (m.taskType === 'MANAGER_AIRCRAFT_MODEL')
                        taskLabel = `Manufacturer · ${m.modelName}`
                    else
                        taskLabel = m.taskDescription
                    
                    const $iconCell = $(`<div class="cell" style="width:25%">${taskLabel}</div>`)
                    $iconCell.prepend(createManagerIcon(_managerColor(m)))

                    let promoText
                    if (m.completed)
                        promoText = `Cooldown: ${m.coolDown}w`
                    else if (m.nextLevelCycleCount != null)
                        promoText = `${m.nextLevelCycleCount}w`
                    else
                        promoText = 'Max level'

                    let desc = ''
                    if (m.taskType === 'COUNTRY') {
                        desc = `+${m.contribution} pts now · ×${m.bonusMultiplier}/level`
                    } else if (m.taskType === 'CAMPAIGN') {
                        desc = `+${m.loyaltyBonus} loyalty/wk · ${commaSeparateNumber(m.population)} pop`
                    } else if (m.taskType === 'MANAGER_AIRCRAFT_MODEL') {
                        desc = `Combined: ${m.currentPriceDiscountPct}% price · ${m.currentTimeDiscountPct}% delivery`
                    }

                    const costText = (m.taskType === 'CAMPAIGN' && !m.completed)
                        ? `$${commaSeparateNumber(m.costPerManager)}`
                        : '—'

                    const $removeCell = $('<div class="cell" style="width:5%"></div>')
                    if (!m.completed) {
                        const $btn = $('<img class="img-button svg svg-monochrome svg-hover-red" src="/assets/images/icons/minus.svg">')
                            .css({ width: '16px', height: '16px', cursor: 'pointer' })
                            .attr('title', 'Remove manager')
                            .on('click', () => removeManagerFromModal(m.id))
                        $removeCell.append($btn)
                    }

                    $row
                        .append($iconCell)
                        .append($('<div class="cell" style="width:10%"></div>').text(m.levelDescription || '—'))
                        .append($('<div class="cell" style="width:10%"></div>').text(promoText))
                        .append($('<div class="cell" style="width:40%"></div>').text(desc))
                        .append($('<div class="cell" style="width:10%"></div>').text(costText))
                        .append($removeCell)

                    $table.append($row)
                })
            }
            $('#managerModal').show()
        },
        error(jqXHR, textStatus) {
            console.log(`Manager modal error: ${textStatus}`)
        }
    })
}

function removeManagerFromModal(managerId) {
    promptConfirm('Would you like to offboard this manager to action other synergies that better align with your core objectives? We will send the manager a nice video.', function() {
        $.ajax({
            type: 'DELETE',
            url: `/managers/airline/${activeAirline.id}/manager/${managerId}`,
            dataType: 'json',
            success() { showManagerModal() },
            error(jqXHR) { console.log(JSON.stringify(jqXHR)) }
        })
    })
}

function updateAirlineManagerStatus($managerStatusDiv, successFunction) {
    $managerStatusDiv.empty()

	$.ajax({
		type: 'GET',
		url: "/managers/airline/" + activeAirline.id,
		contentType: 'application/json; charset=utf-8',
		dataType: 'json',
	    success: function(managersInfo) {
	        refreshAirlineManagerStatus($managerStatusDiv, managersInfo)

            if (successFunction) {
                successFunction(managersInfo)
            }
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}
