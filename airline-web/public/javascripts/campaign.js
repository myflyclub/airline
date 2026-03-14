const MIN_CAMPAIGN_RADIUS = 50
const MAX_CAMPAIGN_RADIUS = 500 //todo: replace with game rule config






// ── Campaign Overlay (main-map panel) ────────────────────────────────────────

const campaignOverlay = {
    airportId: null,
    existingCampaign: null,
    assignedCount: 0,
    availableManagers: 0,
    costPerDelegate: 0,
}

async function showCampaignOverlay(airportId, popupPosition) {
    const airport = getAirportById(airportId)
    if (!airport) return

    Object.assign(campaignOverlay, {
        airportId,
        existingCampaign: null,
        assignedCount: 0,
        availableManagers: 0,
        costPerDelegate: 0,
    })

    const $panel = $('#campaignOverlayPanel')
    $panel.removeData('campaignId').data('radius', MIN_CAMPAIGN_RADIUS)
    $('.campaignOverlayAirportName').text(getAirportText(airport.city, airport.iata))
    if (popupPosition) {
        $panel.css({
            position: 'fixed',
            left: `${popupPosition.left - 240}px`,
            top: `${popupPosition.top}px`,
            zIndex: 1000
        });
    }
    $panel.show()

    try {
        const [delegateInfo, campaigns] = await Promise.all([
            $.ajax({ type: 'GET', url: `/managers/airline/${activeAirline.id}`, dataType: 'json' }),
            $.ajax({ type: 'GET', url: `/airlines/${activeAirline.id}/campaigns?fullLoad=true`, dataType: 'json' }),
        ])
        campaignOverlay.availableManagers = delegateInfo.availableCount || 0
        const existing = campaigns.find(c => c.principalAirport?.id === airportId)
        if (existing) {
            campaignOverlay.existingCampaign = existing
            campaignOverlay.assignedCount = existing.delegates?.length || 0
            campaignOverlay.availableManagers += campaignOverlay.assignedCount
            $panel.data('radius', existing.radius).data('campaignId', existing.id)
        }
    } catch (_) { /* use defaults */ }

    refreshCampaignOverlay()
}

function closeCampaignOverlay() {
    removeCampaignOverlayCircle()
    $('#campaignOverlayPanel').hide()
    campaignOverlay.airportId = null
    campaignOverlay.existingCampaign = null
}

function refreshCampaignOverlay() {
    const { airportId } = campaignOverlay
    const radius = $('#campaignOverlayPanel').data('radius') || MIN_CAMPAIGN_RADIUS
    $('#campaignOverlayPanel .campaignOverlayIncrease').css('opacity', radius >= MAX_CAMPAIGN_RADIUS ? '0.35' : '')
    $('#campaignOverlayPanel .campaignOverlayDecrease').css('opacity', radius <= MIN_CAMPAIGN_RADIUS ? '0.35' : '')

    $.ajax({
        type: 'GET',
        url: `/airlines/${activeAirline.id}/campaign-airports/${airportId}?radius=${radius}`,
        dataType: 'json',
        success(result) {
            const airport = getAirportById(airportId) || result.principalAirport
            $('.campaignOverlayAirportName').text(getAirportText(airport.city, airport.iata))
            $('.campaignOverlayRadius').text(radius)
            $('.campaignOverlayPopulation').text(commaSeparateNumber(result.population))
            $('.campaignOverlayLoyalty').text(result.bonus?.loyalty ?? '-')
            campaignOverlay.costPerDelegate = result.costPerDelegate || 0

            const hasExisting = !!campaignOverlay.existingCampaign
            $('#campaignOverlayPanel .create').toggle(!hasExisting)
            $('#campaignOverlayPanel .update').toggle(hasExisting)
            $('#campaignOverlayPanel .delete').toggle(hasExisting)

            updateCampaignOverlayManagerUI()
            drawCampaignOverlayCircle(airport, radius)
        },
        error(jqXHR, textStatus) {
            console.log(`Campaign overlay error: ${textStatus}`)
        }
    })
}

function changeCampaignOverlayRadius(delta) {
    const current = $('#campaignOverlayPanel').data('radius') || MIN_CAMPAIGN_RADIUS
    const newRadius = Math.max(MIN_CAMPAIGN_RADIUS, Math.min(MAX_CAMPAIGN_RADIUS, current + delta))
    if (newRadius !== current) {
        $('#campaignOverlayPanel').data('radius', newRadius)
        refreshCampaignOverlay()
    }
}

function updateCampaignOverlayManagerUI() {
    const { assignedCount: assigned, availableManagers: available } = campaignOverlay

    renderManagerAssignment({
        container: '#campaignOverlayManagerDisplay',
        count: assigned,
        color: '#e879a0',
        availableCount: available,
        headerText: 'Managers (' + available + ' available)',
        defaultTooltip: 'Advertising campaign · Trainee · next level in 20m',
        onAdd: () => { campaignOverlay.assignedCount++; campaignOverlay.availableManagers--; updateCampaignOverlayManagerUI() },
        onRemove: () => { campaignOverlay.assignedCount--; campaignOverlay.availableManagers++; updateCampaignOverlayManagerUI() }
    })

    $('.campaignOverlayCost').text(assigned > 0 ? '$' + commaSeparateNumber(assigned * campaignOverlay.costPerDelegate) : '-')
    $('#campaignOverlayPanel .campaignOverlaySave').prop('disabled', assigned <= 0).css('opacity', assigned <= 0 ? '0.5' : '')
}

function saveCampaignOverlay() {
    const radius = $('#campaignOverlayPanel').data('radius') || MIN_CAMPAIGN_RADIUS
    const campaignId = $('#campaignOverlayPanel').data('campaignId')
    const payload = { delegateCount: campaignOverlay.assignedCount, radius }
    if (campaignId) payload.campaignId = campaignId
    else payload.airportId = campaignOverlay.airportId

    $.ajax({
        type: 'POST',
        url: `/airlines/${activeAirline.id}/campaigns`,
        contentType: 'application/json; charset=utf-8',
        data: JSON.stringify(payload),
        dataType: 'json',
        success() { closeCampaignOverlay(); updateAirlineDelegateStatus($('#officeCanvas .delegateStatus')) },
        error(jqXHR) { console.log(JSON.stringify(jqXHR)) }
    })
}

function deleteCampaignOverlay() {
    const campaignId = $('#campaignOverlayPanel').data('campaignId')
    if (!campaignId) return
    promptConfirm('Do you want to delete this campaign?', function() {
        $.ajax({
            type: 'DELETE',
            url: `/airlines/${activeAirline.id}/campaigns/${campaignId}`,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success() { closeCampaignOverlay(); updateAirlineDelegateStatus($('#officeCanvas .delegateStatus')) },
            error(jqXHR, textStatus) { console.log(`Delete campaign error: ${textStatus}`) }
        })
    })
}

function drawCampaignOverlayCircle(airport, radius) {
    if (typeof AirlineMap === 'undefined') return
    AirlineMap.drawCircle('campaign-overlay-circle', airport.longitude, airport.latitude, radius * 1000)
}

function removeCampaignOverlayCircle() {
    if (typeof AirlineMap === 'undefined') return
    AirlineMap.removeCircle('campaign-overlay-circle')
}
