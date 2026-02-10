import { state } from './state.js';

const popupOffsets = {
    'top': [0, 0],
    'bottom': [0, -20],
    'left': [0, 0],
    'right': [0, 0],
    'center': [0, 0]
};

let airportPopup = null;
let currentPopup = null;
let alliancePopups = [];

export function createPopup(options = {}) {
    return new maplibregl.Popup({
        closeButton: options.closeButton !== false,
        closeOnClick: options.closeOnClick !== false,
        maxWidth: options.maxWidth || '300px',
        anchor: options.anchor || 'bottom',
        offset: options.offset || popupOffsets
    });
}

function createAirportPopupElement() {
    const div = document.createElement('div');
    div.id = 'airportPopupContainer';
    div.className = 'mapPopup';
    div.style.width = '100%';
    
    div.innerHTML = `
        <input type="hidden" id="airportPopupId">
        <div class="airportPopupName"><span class="iata" id="airportPopupIata"></span><span id="airportPopupName"></span></div>
        <h4 id="airportPopupCity"></h4>
        <div id="airportPopupIcons" class="airportFeatures">
            <img title="Your Headquarter" src="/assets/images/icons/building-hedge.png" id="popupHeadquarterIcon" class="baseIcon" style="display: none">
            <img title="Your Base" src="/assets/images/icons/building-low.png" id="popupBaseIcon" class="baseIcon" style="display:none">
        </div>
        <div class="flex-container-width-half pt-4 pb-2 gap-2">
            <div class="airportPopupItem tooltip-attr tooltip-attr-bottom" data-tooltip="Population coverage.">
                <img width="20px" src="/assets/images/icons/population.svg" style="margin-right: 5px;">
                <span id="airportPopupPopulation"></span>
            </div>
            <div class="airportPopupItem tooltip-attr tooltip-attr-bottom" data-tooltip="Current airport reputation.">
                <img width="20" src="/assets/images/icons/star-full.svg" style="margin-right: 5px;">
                <span id="airportPopupReputation"></span>
            </div>
            <div class="airportPopupItem tooltip-attr tooltip-attr-bottom" data-tooltip="Average income per capita.">
                <img width="20" src="/assets/images/icons/money.svg" style="margin-right: 5px;">
                <span id="airportPopupIncomeLevel"></span>
            </div>
            <div class="airportPopupItem tooltip-attr tooltip-attr-bottom" data-tooltip="Travel rate.">
                <img width="20" src="/assets/images/icons/rate.svg" style="margin-right: 5px;">
                <span id="airportPopupTravelRate"></span>
            </div>
            <div class="airportPopupItem tooltip-attr tooltip-attr-bottom" data-tooltip="Airport size | runway length.">
                <img width="20" src="/assets/images/icons/airplane-takeoff.svg" style="margin-right: 5px;">
                <span id="airportPopupSize"></span>&ensp;|&ensp;<span id="airportPopupMaxRunwayLength"></span>
            </div>
        </div>
        <div class="flex-container-width-half gap-2 pb-2">
            <a class="ml-0 button" id="planToAirportButton">Plan Flight</a>
            <a class="mr-0 button" id="viewAirportButton">View Airport</a>
            <a class="ml-0 button" id="toggleAirportLinksButton">Flight Map</a>
            <a class="mr-0 button" id="researchToAirportButton">Research To</a>
        </div>
    `;

    div.querySelector('#planToAirportButton').addEventListener('click', () => {
        const id = div.querySelector('#airportPopupId').value;
        const name = div.querySelector('#airportPopupName').textContent;
        planToAirportFromInfoWindow(id, name);
    });

    div.querySelector('#toggleAirportLinksButton').addEventListener('click', () => {
        AirlineMap.toggleAirportLinksView();
    });

    div.querySelector('#researchToAirportButton').addEventListener('click', () => {
        const id = div.querySelector('#airportPopupId').value;
        showResearchPreloaded(null, id);
    });

    return div;
}

export function showAirportPopup(airport, lngLat) {
    const cycleData = window.airportsLatestData?.champions?.[airport.id];
    const rep = cycleData ? cycleData.reputation : '-';
    const travelRate = cycleData ? cycleData.travelRate : '-';

    const countryData = window.loadedCountriesByCode?.[airport.countryCode];
    const openness = countryData?.openness || 0;
    const opennessSpan = typeof getOpennessSpan === 'function'
        ? getOpennessSpan(openness, airport.size, airport.isDomesticAirport, airport.isGateway, true)
        : '';

    let popupElement = document.getElementById('airportPopupContainer');
    if (!popupElement) {
        popupElement = createAirportPopupElement();
        document.body.appendChild(popupElement);
    }

    // Update content
    popupElement.querySelector('#airportPopupId').value = airport.id;
    popupElement.querySelector('#airportPopupName').textContent = airport.name;
    popupElement.querySelector('#airportPopupIata').textContent = airport.iata;
    popupElement.querySelector('#airportPopupPopulation').textContent = typeof commaSeparateNumber === 'function' ? commaSeparateNumber(airport.population) : airport.population;
    popupElement.querySelector('#airportPopupIncomeLevel').textContent = '$' + (typeof commaSeparateNumber === 'function' ? commaSeparateNumber(airport.income) : airport.income);
    popupElement.querySelector('#airportPopupSize').textContent = airport.size;
    popupElement.querySelector('#airportPopupTravelRate').textContent = travelRate + '%';
    popupElement.querySelector('#airportPopupReputation').textContent = rep;
    const flagImg = typeof getCountryFlagImg === 'function' ? getCountryFlagImg(airport.countryCode) : '';
    popupElement.querySelector('#airportPopupCity').innerHTML = `${airport.city}&nbsp;${flagImg}${opennessSpan}`;
    popupElement.querySelector('#airportPopupMaxRunwayLength').textContent = airport.runwayLength + 'm';
    popupElement.querySelector('#airportPopupIcons').innerHTML = (airport.features && Array.isArray(airport.features)) ? airport.features.map(updateFeatures).join('') : "";

    popupElement.querySelector('#viewAirportButton').href = `/airport/${airport.iata}`;
    
    const planBtn = popupElement.querySelector('#planToAirportButton');
    planBtn.style.display = (window.activeAirline && window.activeAirline.headquarterAirport) ? '' : 'none';

    closeAirportPopup();
    airportPopup = createPopup({ maxWidth: '280px' });
    airportPopup.on('close', () => {
        state.activeAirportPopupInfoWindow = null;
        window.activeAirportPopupInfoWindow = null;
    });

    airportPopup.setLngLat([lngLat.lng, lngLat.lat]).setDOMContent(popupElement).addTo(state.map);

    state.activeAirportPopupInfoWindow = airportPopup;
    window.activeAirportPopupInfoWindow = airportPopup;
}

export function closeAirportPopup() {
    if (airportPopup) {
        airportPopup.remove();
        airportPopup = null;
    }
    state.activeAirportPopupInfoWindow = null;
    window.activeAirportPopupInfoWindow = null;
}

export function closeAirportInfoPopup() {
    closeAirportPopup();
}

export function showLinkPopup(link, lngLat) {
    if (!state.map) return;

    const popupEl = document.getElementById('linkPopup');
    if (popupEl) {
        popupEl.style.display = 'block';
        const fromFlag = typeof getCountryFlagImg === 'function' ? getCountryFlagImg(link.fromCountryCode) : '';
        const toFlag = typeof getCountryFlagImg === 'function' ? getCountryFlagImg(link.toCountryCode) : '';
        const fromAirport = typeof getAirportText === 'function' ? getAirportText(link.fromAirportCity, link.fromAirportCode) : link.fromAirportCode;
        const toAirport = typeof getAirportText === 'function' ? getAirportText(link.toAirportCity, link.toAirportCode) : link.toAirportCode;

        popupEl.querySelector('#linkPopupFrom').innerHTML = `${fromFlag}&nbsp;${fromAirport}`;
        popupEl.querySelector('#linkPopupTo').innerHTML = `${toFlag}&nbsp;${toAirport}`;
        popupEl.querySelector('#linkPopupCapacity').textContent = link.capacity?.total || '';
        
        const logo = typeof getAirlineLogoImg === 'function' ? getAirlineLogoImg(link.airlineId) : '';
        popupEl.querySelector('#linkPopupAirline').innerHTML = `${logo}&nbsp;${link.airlineName || ''}`;

        closePopup();
        currentPopup = createPopup({ maxWidth: '400px' });
        currentPopup.setLngLat([lngLat.lng, lngLat.lat])
            .setDOMContent(popupEl)
            .addTo(state.map);
    }
}

export function showAirportLinkPopup(pathData, lngLat) {
    if (!state.map) return;

    const localAirport = pathData.localAirport || pathData.fromAirport;
    const remoteAirport = pathData.remoteAirport || pathData.toAirport;
    const details = pathData.details;

    const popupEl = document.getElementById('airportLinkPopup');
    if (popupEl) {
        popupEl.style.display = 'block';
        const fromFlag = typeof getCountryFlagImg === 'function' ? getCountryFlagImg(localAirport.countryCode) : '';
        const toFlag = typeof getCountryFlagImg === 'function' ? getCountryFlagImg(remoteAirport.countryCode) : '';
        const fromAirport = typeof getAirportText === 'function' ? getAirportText(localAirport.city, localAirport.iata) : localAirport.iata;
        const toAirport = typeof getAirportText === 'function' ? getAirportText(remoteAirport.city, remoteAirport.iata) : remoteAirport.iata;

        popupEl.querySelector('#airportLinkPopupFrom').innerHTML = `${fromFlag}${fromAirport}`;
        popupEl.querySelector('#airportLinkPopupTo').innerHTML = `${toFlag}${toAirport}`;
        
        const capacityStr = typeof toLinkClassValueString === 'function' ? toLinkClassValueString(details.capacity) : details.capacity.total;
        popupEl.querySelector('#airportLinkPopupCapacity').textContent = `${capacityStr}(${details.frequency})`;

        const operatorsEl = popupEl.querySelector('#airportLinkOperators');
        if (operatorsEl && details.operators) {
            operatorsEl.innerHTML = '';
            details.operators.forEach(operator => {
                const div = document.createElement('div');
                const logo = typeof getAirlineLogoSpan === 'function' ? getAirlineLogoSpan(operator.airlineId, operator.airlineName) : operator.airlineName;
                const capStr = typeof toLinkClassValueString === 'function' ? toLinkClassValueString(operator.capacity) : operator.capacity;
                div.innerHTML = `${logo}<span>${operator.frequency}&nbsp;flight(s) weekly&nbsp;${capStr}</span>`;
                operatorsEl.appendChild(div);
            });
        }

        closePopup();
        currentPopup = createPopup({ maxWidth: '400px' });
        currentPopup.setLngLat([lngLat.lng, lngLat.lat])
            .setDOMContent(popupEl)
            .addTo(state.map);
    }
}

export function showAllianceBasePopup(baseInfo, lngLat) {
    if (!state.map) return;

    const popupEl = document.getElementById('allianceBasePopup');
    if (popupEl) {
        popupEl.style.display = 'block';
        const flag = typeof getCountryFlagImg === 'function' ? getCountryFlagImg(baseInfo.countryCode) : '';
        popupEl.querySelector('.city').innerHTML = `${flag}&nbsp;${baseInfo.city}`;
        popupEl.querySelector('.airportName').textContent = baseInfo.airportName;
        popupEl.querySelector('.iata').textContent = baseInfo.airportCode;
        
        const logo = typeof getAirlineLogoImg === 'function' ? getAirlineLogoImg(baseInfo.airlineId) : '';
        popupEl.querySelector('.airlineName').innerHTML = `${logo}&nbsp;${baseInfo.airlineName}`;
        popupEl.querySelector('.baseScale').textContent = baseInfo.scale;

        const popup = createPopup({ maxWidth: '400px' });
        popup.setLngLat([lngLat.lng, lngLat.lat])
            .setDOMContent(popupEl)
            .addTo(state.map);

        alliancePopups.push(popup);
    }
}

export function showLinkHistoryPopup(historyData, lngLat) {
    if (!state.map) return;

    const link = historyData.link;
    const popupEl = document.getElementById('linkHistoryPopup');
    if (popupEl) {
        popupEl.style.display = 'block';
        const fromFlag = typeof getCountryFlagImg === 'function' ? getCountryFlagImg(link.fromCountryCode) : '';
        const toFlag = typeof getCountryFlagImg === 'function' ? getCountryFlagImg(link.toCountryCode) : '';
        const fromAirport = typeof getAirportText === 'function' ? getAirportText(link.fromAirportCity, link.fromAirportCode) : link.fromAirportCode;
        const toAirport = typeof getAirportText === 'function' ? getAirportText(link.toAirportCity, link.toAirportCode) : link.toAirportCode;

        popupEl.querySelector('#linkHistoryPopupFrom').innerHTML = `${fromFlag}${fromAirport}`;
        popupEl.querySelector('#linkHistoryPopupTo').innerHTML = `${toFlag}${toAirport}`;
        popupEl.querySelector('#linkHistoryThisAirlinePassengers').textContent = historyData.thisAirlinePassengers;
        popupEl.querySelector('#linkHistoryThisAlliancePassengers').textContent = historyData.thisAlliancePassengers;
        popupEl.querySelector('#linkHistoryOtherAirlinePassengers').textContent = historyData.otherAirlinePassengers;

        closePopup();
        currentPopup = createPopup({ maxWidth: '400px' });
        currentPopup.setLngLat([lngLat.lng, lngLat.lat])
            .setDOMContent(popupEl)
            .addTo(state.map);
    }
}

export function closePopup() {
    if (currentPopup) {
        currentPopup.remove();
        currentPopup = null;
    }
}

export function closeAlliancePopups() {
    alliancePopups.forEach(popup => popup.remove());
    alliancePopups = [];
}

export function isPopupOpen() {
    return !!currentPopup || !!airportPopup || alliancePopups.length > 0;
}

export function closeAllianceLinkPopup() {
    if (state.map && state.map.allianceLinkPopup) {
        state.map.allianceLinkPopup.remove();
        state.map.allianceLinkPopup = null;
    }
}
