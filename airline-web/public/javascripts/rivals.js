const Rivals = (() => {

    // -------------------------------------------------------------------------
    // State - Chart / Market
    // -------------------------------------------------------------------------
    let rivalsData = null;
    let _rivalsEtag = null;
    let period = 'WEEKLY';
    let metric = 'price';
    let hiddenAirlines = {};
    let selectedTypes = {};

    // -------------------------------------------------------------------------
    // State - Rivals List / Details
    // -------------------------------------------------------------------------
    let loadedById = {};
    let loadedLinks = [];
    let foundedCycleById = {};

    // Exposed externally via window property (heatmap.js compatibility)
    let mapAirlineId;


    // =========================================================================
    // SECTION: Canvas / Entry Point
    // =========================================================================

    function show(selectedAirline) {
        setActiveDiv($('#rivalsCanvas'), () => loadData());
        $('#rivalDetailsModal').hide();

        const ticker = $('#rivalsTicker');
        if (!ticker.data('delegation-set')) {
            ticker.on('click', '.table-row', function() {
                showDetailsModal($(this).data('airline-id'));
            });
            ticker.on('click', '.js-toggle-airline', function(e) {
                e.stopPropagation();
                toggleAirline($(this).closest('.table-row').data('airline-id'));
            });
            ticker.on('click', '.js-show-details', function(e) {
                e.stopPropagation();
                showDetailsModal($(this).closest('.table-row').data('airline-id'));
            });
            ticker.data('delegation-set', true);
        }

        if (selectedAirline) {
            showDetailsModal(selectedAirline);
        }
    }


    // =========================================================================
    // SECTION: Rival Details
    // =========================================================================

    function showDetails(row, airlineId) {
        if (row !== null && row !== undefined) {
            row.siblings().removeClass("selected");
            row.addClass("selected");
        }

        updateRivalBasicsDetails(airlineId);
        updateRivalFormerNames(airlineId);
        updateRivalFleet(airlineId);
        updateRivalCountriesAirlineTitles(airlineId);
        updateRivalChampionedAirportsDetails(airlineId);
        updateHeadquartersMap($('#rivalDetailsModal .headquartersMap'), airlineId);
        loadLinks(airlineId);
        updateRivalBaseList(airlineId);

        if (isAdmin()) {
            showAdminActions(loadedById[airlineId]);
        }

        $('#rivalDetailsModal').data("airlineId", airlineId);
        window.history.pushState({ airlineId }, '', '/rivals/' + airlineId);
        $('#rivalDetailsModal').show();
    }

    function showDetailsModal(airlineId) {
        if (loadedById[airlineId]) {
            showDetails(null, airlineId);
        } else {
            $.ajax({
                type: 'GET',
                url: '/airlines?loginStatus=true&hideInactive=false',
                contentType: 'application/json; charset=utf-8',
                dataType: 'json',
                success: function(airlines) {
                    $.each(airlines, function(index, airline) {
                        loadedById[airline.id] = airline;
                    });
                    showDetails(null, airlineId);
                },
                error: function(jqXHR, textStatus, errorThrown) {
                    console.error('Failed to load rival data:', textStatus, errorThrown);
                }
            });
        }
    }

    function updateRivalBasicsDetails(airlineId) {
        const rival = loadedById[airlineId];
        $("#rivalDetailsModal .airlineName").text(rival.name);
        $("#rivalDetailsModal .airlineType").text(rival.type + " Airline");
        $("#rivalDetailsModal .airlineCode").text(rival.airlineCode);
        $("#rivalDetailsModal .airlineSlogan").text(rival.slogan);

        const foundedCycle = foundedCycleById[airlineId];
        if (foundedCycle != null) {
            $("#rivalDetailsModal .foundedDate").text(getGameDate(foundedCycle));
            $("#rivalDetailsModal .foundedRow").show();
        } else {
            $("#rivalDetailsModal .foundedRow").hide();
        }
        const color = airlineColors[airlineId];
        if (!color) {
            $("#rivalDetailsModal .airlineColorDot").hide();
        } else {
            $("#rivalDetailsModal .airlineColorDot").css('background-color', color);
            $("#rivalDetailsModal .airlineColorDot").show();
        }

        $("#rivalDetailsModal .airlineGrade").html(getGradeStarsImgs(rival.gradeValue - 3));
        $("#rivalDetailsModal .airlineGrade").attr('title', rival.gradeDescription);
        $("#rivalDetailsModal .alliance").data("link", "alliance");

        if (rival.allianceName) {
            $("#rivalDetailsModal .alliance").text(rival.allianceName);
            $("#rivalDetailsModal .alliance").addClass("clickable");
            $("#rivalDetailsModal .alliance").on("click.showAlliance", function() {
                showAllianceCanvas(rival.allianceId);
            });
        } else {
            $("#rivalDetailsModal .alliance").text('-');
            $("#rivalDetailsModal .alliance").removeClass("clickable");
            $("#rivalDetailsModal .alliance").off("click.showAlliance");
        }
    }

    function updateRivalFormerNames(airlineId) {
        $('#rivalDetailsModal .formerNames').children('div.table-row').remove();

        $.ajax({
            type: 'GET',
            url: "/airlines/" + airlineId + "/former-names",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                $(result).each(function(index, formerName) {
                    const row = $("<div class='table-row'></div>");
                    row.append("<div class='cell'>" + formerName + "</div>");
                    $('#rivalDetailsModal .formerNames').append(row);
                });
                if (result.length == 0) {
                    $('#rivalDetailsModal .formerNameRow').hide();
                } else {
                    $('#rivalDetailsModal .formerNameRow').show();
                }
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }

    function updateRivalFleet(airlineId) {
        $('#rivalDetailsModal .fleetList').children('div.table-row').remove();

        $.ajax({
            type: 'GET',
            url: "/airlines/" + airlineId + "/fleet",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                $(result).each(function(index, modelDetails) {
                    const row = $("<div class='table-row'></div>");
                    row.append("<div class='cell'>" + modelDetails.name + "</div>");
                    row.append("<div class='cell'>" + modelDetails.quantity + "</div>");
                    $('#rivalDetailsModal .fleetList').append(row);
                });
                if (result.length == 0) {
                    $('#rivalDetailsModal .fleetList').append('<div class="cell"></div><div class="cell"></div>');
                }
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }

    function updateRivalChampionedAirportsDetails(airlineId) {
        $('#rivalChampionedAirportsList').children('div.table-row').remove();

        $.ajax({
            type: 'GET',
            url: "/airlines/" + airlineId + "/championed-airports",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(championedInfo) {
                $(championedInfo).each(function(index, championDetails) {
                    const row = $("<div class='table-row clickable' data-link='airport' onclick=\"showAirportDetails('" + championDetails.airportId + "');\"></div>");
                    row.append("<div class='cell'>" + getRankingImg(championDetails.ranking) + "</div>");
                    row.append("<div class='cell'>" + getCountryFlagImg(championDetails.countryCode) + championDetails.airportText + "</div>");
                    row.append("<div class='cell'>" + championDetails.reputationBoost + "</div>");
                    $('#rivalChampionedAirportsList').append(row);
                });
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }

    function updateRivalCountriesAirlineTitles(airlineId) {
        $('#rivalDetailsModal .nationalAirlineCountryList').children('div.table-row').remove();
        $('#rivalDetailsModal .partneredAirlineCountryList').children('div.table-row').remove();

        $.ajax({
            type: 'GET',
            url: "/airlines/" + airlineId + "/country-airline-titles",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(titles) {
                $(titles.nationalAirlines).each(function(index, entry) {
                    const country = loadedCountriesByCode[entry.countryCode];
                    const row = $(`<div class='table-row clickable' onclick="navigateTo('/country/${entry.countryCode}');"></div>`);
                    row.append("<div class='cell'>" + getCountryFlagImg(entry.countryCode) + country.name + "</div>");
                    row.append("<div class='cell'>" + entry.bonus + "</div>");
                    $('#rivalDetailsModal .nationalAirlineCountryList').append(row);
                });

                if (titles.nationalAirlines.length == 0) {
                    $('#rivalDetailsModal .nationalAirlineCountryList').append($("<div class='table-row'><div class='cell'>-</div><div class='cell'>-</div></div>"));
                }

                $(titles.partneredAirlines).each(function(index, entry) {
                    const country = loadedCountriesByCode[entry.countryCode];
                    const row = $("<div class='table-row clickable' onclick=\"navigateTo('/country/" + country.countryCode + "');\"></div>");
                    row.append("<div class='cell'>" + getCountryFlagImg(entry.countryCode) + country.name + "</div>");
                    row.append("<div class='cell'>" + entry.bonus + "</div>");
                    $('#rivalDetailsModal .partneredAirlineCountryList').append(row);
                });

                if (titles.partneredAirlines.length == 0) {
                    $('#rivalDetailsModal .partneredAirlineCountryList').append($("<div class='table-row'><div class='cell'>-</div><div class='cell'>-</div></div>"));
                }
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }

    function updateRivalBaseList(airlineId) {
        updateAirlineBaseList(airlineId, $('#rivalBases'));
    }


    // =========================================================================
    // SECTION: Rival Links
    // =========================================================================

    function loadLinks(airlineId) {
        if (!airlineId || airlineId == activeAirline.id) {
            return;
        }
        const airlineLinksTable = $("#rivalDetailsModal #rivalLinksTable");
        airlineLinksTable.children("div.table-row").remove();

        const getUrl = "/airlines/" + airlineId + "/links";
        loadedLinks = undefined;
        $.ajax({
            type: 'GET',
            url: getUrl,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(data) {
                if (data.type === 'FeatureCollection' && data.features) {
                    loadedLinks = data.features.map(f => f.properties);
                } else {
                    loadedLinks = data;
                }
                renderLinksTable();
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }

    function renderLinksTable(sortProperty, sortOrder) {
        const rivalLinksTable = $("#rivalDetailsModal #rivalLinksTable");
        rivalLinksTable.children("div.table-row").remove();

        loadedLinks.sort(sortByProperty(sortProperty || 'distance', sortOrder == "ascending"));

        const rowsHtml = [];
        loadedLinks.forEach(function(link) {
            rowsHtml.push(
                `<div class='table-row'>` +
                `<div class='cell'>${getCountryFlagImg(link.fromCountryCode)}${getAirportText(link.fromAirportCity, link.fromAirportCode)}</div>` +
                `<div class='cell'>${getCountryFlagImg(link.toCountryCode)}${getAirportText(link.toAirportCity, link.toAirportCode)}</div>` +
                `<div class='cell' align='right'>${link.distance}km</div>` +
                `<div class='cell' align='right'>${link.frequency}</div>` +
                `</div>`
            );
        });
        rivalLinksTable.append(rowsHtml.join(''));
    }

    function toggleLinksSortOrder(sortHeader) {
        if (sortHeader.data("sort-order") == "ascending") {
            sortHeader.data("sort-order", "descending");
        } else {
            sortHeader.data("sort-order", "ascending");
        }

        sortHeader.siblings().removeClass("selected");
        sortHeader.addClass("selected");

        renderLinksTable(sortHeader.data("sort-property"), sortHeader.data("sort-order"));
    }


    // =========================================================================
    // SECTION: Rival Map
    // =========================================================================

    function showMap() {
        const airlineId = $('#rivalDetailsModal').data("airlineId");
        $('#rivalDetailsModal').hide();
        AirlineMap.clearAllPaths();
        AirlineMap.deselectLink();
        mapAirlineId = airlineId;

        $.ajax({
            type: 'GET',
            url: "/airlines/" + airlineId + "/links",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(data) {
                if (data.type === 'FeatureCollection' && data.features) {
                    AirlineMap.setRoutesFromGeoJSON(data, '#DC83FC');
                } else {
                    $.each(data, function(index, link) {
                        AirlineMap.drawFlightPath(link, '#DC83FC');
                    });
                }
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            },
            beforeSend: function() {
                $('body .loadingSpinner').show();
            },
            complete: function() {
                $('body .loadingSpinner').hide();
            }
        });

        $.ajax({
            type: 'GET',
            url: "/airlines/" + airlineId + "/bases",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(bases) {
                AirlineMap.updateAirportBaseMarkers(bases, []);
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });

        AirlineMap.setRouteSelectable(false);
        AirlineMap.addExitButton('Exit Rival Flight Map', hideMap);
        window.history.pushState({ rivalAirlineId: airlineId }, '', '/map/rival/' + airlineId);
        setActiveDiv($('#worldMapCanvas'));
        $("#worldMapCanvas").data("initCallback", function() {
            AirlineMap.setRouteSelectable(true);
            AirlineMap.clearTopCenterControls();
            AirlineMap.clearAllPaths();
            AirlineMap.updateAirportMarkers(activeAirline);
            updateLinksInfo();
            window.history.replaceState({}, '', '/map/');
        });
    }

    function hideMap() {
        const previousAirlineId = mapAirlineId;
        AirlineMap.setRouteSelectable(true);
        AirlineMap.removeExitButton();
        AirlineMap.clearAllPaths();
        AirlineMap.updateAirportBaseMarkers([]);
        mapAirlineId = undefined;
        window.history.replaceState({}, '', '/rivals/' + previousAirlineId);
        setActiveDiv($('#rivalsCanvas'));
    }

    function showHistory() {
        const airlineId = $('#rivalDetailsModal').data("airlineId");
        $('#rivalDetailsModal').hide();
        showSearchCanvas(loadedById[airlineId]);
    }


    // =========================================================================
    // SECTION: Market Data Loading
    // =========================================================================

    async function prefetch() {
        if (rivalsData) return;
        const headers = {}
        if (_rivalsEtag) headers['If-None-Match'] = _rivalsEtag
        try {
            const res = await fetch('/rivals-data', { headers })
            if (res.status === 304) return
            if (!res.ok) {
                console.error('Rivals prefetch failed:', res.status)
                return
            }
            const etag = res.headers.get('ETag')
            if (etag) _rivalsEtag = etag
            rivalsData = await res.json()
            buildTypeFilter()
            resetVisibilityToTop20()
        } catch (e) {
            console.error('Rivals prefetch failed:', e)
        }
    }

    async function loadData() {
        if (rivalsData) {
            buildTypeFilter();
            resetVisibilityToTop20();
            renderChart();
            renderTicker();
            return;
        }
        const headers = {}
        if (_rivalsEtag) headers['If-None-Match'] = _rivalsEtag
        try {
            const res = await fetch('/rivals-data', { headers })
            if (res.status === 304) return
            if (!res.ok) {
                console.error('Failed to load rivals market data:', res.status)
                return
            }
            const etag = res.headers.get('ETag')
            if (etag) _rivalsEtag = etag
            rivalsData = await res.json()
            foundedCycleById = {};
            rivalsData.airlines.forEach(function(a) {
                if (a.foundedCycle != null) foundedCycleById[a.id] = a.foundedCycle;
            });
            buildTypeFilter()
            resetVisibilityToTop20()
            renderChart()
            renderTicker()
        } catch (e) {
            console.error('Failed to load rivals market data:', e)
        }
    }

    function resetVisibilityToTop20() {
        if (!rivalsData) return;
        const airlines = rivalsData.airlines;
        const history = rivalsData.history;

        // Helper to get current value for sorting
        const getMetricValue = (airline) => {
             if (metric === 'price') return airline.currentPrice;
             const airlineHistory = history[airline.id] || [];
             const filtered = airlineHistory.filter(h => h.period === period);
             if (filtered.length === 0) return -1;
             // Sort to ensure we get the latest
             filtered.sort((a, b) => a.cycle - b.cycle);
             return filtered[filtered.length - 1][metric];
        };

        // 1. Sort by current metric descending
        // Clone array to avoid mutating original order if that matters elsewhere
        const sortedAirlines = [...airlines].sort((a, b) => getMetricValue(b) - getMetricValue(a));
        
        // 2. Select Top 20 IDs
        const top20Ids = new Set(sortedAirlines.slice(0, 20).map(a => a.id));
        
        // 3. Ensure Active Airline is included (if it exists)
        if (typeof activeAirline !== 'undefined' && activeAirline) {
            top20Ids.add(activeAirline.id);
        }

        // 4. Update hiddenAirlines
        // If it's NOT in the top 20 set, we hide it.
        hiddenAirlines = {};
        airlines.forEach(function(airline) {
            if (!top20Ids.has(airline.id)) {
                hiddenAirlines[airline.id] = true;
            }
        });
    }


    // =========================================================================
    // SECTION: Dropdown Logic
    // =========================================================================

    function toggleDropdown(toggleBtn) {
        const $dropdown = $(toggleBtn).closest('.smDropdown');
        const wasOpen = $dropdown.hasClass('open');
        // Close all dropdowns first
        $('.smDropdown').removeClass('open');
        if (!wasOpen) {
            $dropdown.addClass('open');
        }
    }

    // Close dropdowns when clicking outside
    $(document).on('click', function(e) {
        if (!$(e.target).closest('.smDropdown').length) {
            $('.smDropdown').removeClass('open');
        }
    });

    function selectPeriod(periodValue, item) {
        period = periodValue;
        const $dropdown = $(item).closest('.smDropdown');
        $dropdown.find('.smDropdownItem').removeClass('active');
        $(item).addClass('active');
        $dropdown.find('.smDropdownLabel').text($(item).text());
        $dropdown.removeClass('open');
        renderChart();
    }

    function selectMetric(metricValue, item) {
        metric = metricValue;
        const $dropdown = $(item).closest('.smDropdown');
        $dropdown.find('.smDropdownItem').removeClass('active');
        $(item).addClass('active');
        $dropdown.find('.smDropdownLabel').text($(item).text());
        $dropdown.removeClass('open');
        resetVisibilityToTop20();
        renderChart();
    }

    // SECTION: Type Filter
    function buildTypeFilter() {
        if (!rivalsData) return;

        const types = {};
        rivalsData.airlines.forEach(function(a) { types[a.airlineType] = true; });
        const typeList = Object.keys(types).sort();

        typeList.forEach(function(t) {
            if (selectedTypes[t] === undefined) {
                selectedTypes[t] = true;
            }
        });

        const $container = $('#rivalsTypeFilter');
        $container.empty();

        typeList.forEach(function(type) {
            const checked = selectedTypes[type] ? 'checked' : '';
            const safeType = type.replace(/'/g, "\\'");
            $container.append(
                '<label class="smDropdownCheckItem">' +
                '<input type="checkbox" ' + checked + ' onchange="Rivals.toggleTypeFilter(\'' + safeType + '\', this.checked)">' +
                '<span>' + type + '</span>' +
                '</label>'
            );
        });

        updateTypeDropdownLabel();
    }

    function toggleTypeFilter(type, checked) {
        selectedTypes[type] = checked;
        updateTypeDropdownLabel();
        renderChart();
        renderTicker();
    }

    function updateTypeDropdownLabel() {
        const allTypes = Object.keys(selectedTypes);
        const selected = allTypes.filter(function(t) { return selectedTypes[t]; });
        let label;
        if (selected.length === allTypes.length) {
            label = 'All Types';
        } else if (selected.length === 0) {
            label = 'No Types';
        } else if (selected.length <= 2) {
            label = selected.join(', ');
        } else {
            label = selected.length + ' Types';
        }
        $('#rivalsTypeDropdown .smDropdownLabel').text(label);
    }

    function isAirlineTypeVisible(airline) {
        return selectedTypes[airline.airlineType] !== false;
    }

    // SECTION: Chart
    function getMetricLabel() {
        switch (metric) {
            case 'price': return 'Stock Price';
            case 'totalValue': return 'Total Value';
            case 'reputation': return 'Reputation';
            default: return metric;
        }
    }

    function formatMetricValue(value) {
        switch (metric) {
            case 'price': return '$' + value.toFixed(2);
            case 'totalValue': return '$' + commaSeparateNumber(Math.round(value));
            case 'reputation': return value.toFixed(0);
            default: return value;
        }
    }

    function renderChart() {
        if (!rivalsData) return;

        const airlines = rivalsData.airlines;
        const history = rivalsData.history;

        const datasets = [];
        airlines.forEach(function(airline) {
            if (hiddenAirlines[airline.id]) return;
            if (!isAirlineTypeVisible(airline)) return;

            const airlineHistory = history[airline.id] || [];
            const filtered = airlineHistory.filter(function(h) { return h.period === period; });
            filtered.sort(function(a, b) { return a.cycle - b.cycle; });

            if (filtered.length === 0) return;

            const color = colorFromString(airline.name);
            datasets.push({
                label: airline.name,
                data: filtered.map(function(h) { return { x: h.cycle, y: h[metric] }; }),
                borderColor: color,
                backgroundColor: color + '20',
                borderWidth: 1.5,
                tension: 0.3,
                pointRadius: 0,
                pointHoverRadius: 4,
                fill: false
            });
        });

        const allCycles = {};
        datasets.forEach(function(ds) {
            ds.data.forEach(function(pt) { allCycles[pt.x] = true; });
        });
        const labels = Object.keys(allCycles).map(Number).sort(function(a, b) { return a - b; });

        const metricLabel = getMetricLabel();
        const isMonetary = metric === 'price' || metric === 'totalValue';

        const config = {
            type: 'line',
            data: {
                labels: labels.map(function(c) { return getGameDate(c); }),
                datasets: datasets.map(function(ds) {
                    const cycleMap = {};
                    ds.data.forEach(function(pt) { cycleMap[pt.x] = pt.y; });
                    ds.data = labels.map(function(c) { return cycleMap[c] !== undefined ? cycleMap[c] : null; });
                    return ds;
                })
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                spanGaps: true,
                interaction: {
                    mode: 'nearest',
                    intersect: false
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: 'rgba(30,30,30,0.95)',
                        titleColor: '#fff',
                        bodyColor: '#ddd',
                        borderColor: 'rgba(100,100,100,0.5)',
                        borderWidth: 1,
                        callbacks: {
                            title: function(tooltipItems) {
                                if (!tooltipItems.length) return '';
                                return tooltipItems[0].label;
                            },
                            label: function(tooltipItem) {
                                return tooltipItem.dataset.label + ': ' + formatMetricValue(tooltipItem.raw);
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: 'rgba(54,54,54,0.3)' },
                        ticks: { maxTicksLimit: 12 }
                    },
                    y: {
                        grid: { color: 'rgba(54,54,54,0.3)' },
                        ticks: {
                            callback: function(value) {
                                if (isMonetary) return '$' + commaSeparateNumber(value);
                                return value;
                            }
                        },
                        beginAtZero: true
                    }
                }
            }
        };

        ChartUtils.createChart('rivalsChartContainer', config);
    }

    // SECTION: Ticker
    function toggleTickerSortOrder(sortHeader) {
        if (sortHeader.data('sort-order') == 'ascending') {
            sortHeader.data('sort-order', 'descending');
        } else {
            sortHeader.data('sort-order', 'ascending');
        }

        sortHeader.siblings().removeClass('selected');
        sortHeader.addClass('selected');

        renderTicker(sortHeader.data('sort-property'), sortHeader.data('sort-order'));
    }

    function renderTicker(sortProperty, sortOrder) {
        if (!rivalsData) return;

        const history = rivalsData.history;

        const displayRows = [];
        rivalsData.airlines.forEach(function(airline) {
            if (!isAirlineTypeVisible(airline)) return;

            const airlineHistory = (history[airline.id] || []).filter(function(h) { return h.period === period; });
            airlineHistory.sort(function(a, b) { return a.cycle - b.cycle; });
            const latestEntry = airlineHistory.length > 0 ? airlineHistory[airlineHistory.length - 1] : null;

            displayRows.push({
                id: airline.id,
                name: airline.name,
                airlineType: airline.airlineType,
                alliance: airline.alliance || '',
                currentPrice: airline.currentPrice,
                totalValue: latestEntry ? latestEntry.totalValue : 0,
                reputation: latestEntry ? latestEntry.reputation : 0
            });
        });

        if (!sortProperty) {
            const selectedSortHeader = $('#rivalsTicker .table-header .cell.selected');
            sortProperty = selectedSortHeader.data('sort-property') || 'currentPrice';
            sortOrder = selectedSortHeader.data('sort-order') || 'descending';
        }
        displayRows.sort(sortByProperty(sortProperty, sortOrder == 'ascending'));

        const $table = $('#rivalsTicker');
        $table.find('.table-row').remove();

        const rowsHtml = [];
        displayRows.forEach(function(entry) {
            const color = colorFromString(entry.name);
            const isHidden = hiddenAirlines[entry.id];
            const opacity = isHidden ? '0.4' : '1';

            rowsHtml.push(
                `<div class="table-row clickable" data-airline-id="${entry.id}" style="opacity:${opacity};">` +
                `<div class="cell js-show-details" style="width:3%;"><img src="/assets/images/icons/magnifier.png" style="width:14px;height:14px;cursor:pointer;opacity:0.7;" data-tooltip="View details"></div>` +
                `<div class="cell js-toggle-airline" style="width:3%;"><input type="checkbox" ${isHidden ? '' : 'checked'} style="cursor:pointer;"></div>` +
                `<div class="cell" style="width:21%;"><span style="display:inline-block;width:10px;height:10px;margin-right:4px;border-radius:2px;background:${color};vertical-align:middle;"></span>${entry.name}</div>` +
                `<div class="cell" style="width:12%;">${entry.airlineType}</div>` +
                `<div class="cell" style="width:14%;">${entry.alliance || '-'}</div>` +
                `<div class="cell" style="width:13%;" align="right">$${entry.currentPrice.toFixed(2)}</div>` +
                `<div class="cell" style="width:16%;" align="right">$${commaSeparateNumber(entry.totalValue)}</div>` +
                `<div class="cell js-toggle-airline" style="width:13%;" align="right">${entry.reputation}</div>` +
                `</div>`
            );
        });
        $table.append(rowsHtml.join(''));
    }

    function toggleAirline(airlineId) {
        hiddenAirlines[airlineId] = !hiddenAirlines[airlineId];
        renderChart();
        renderTicker();
    }

    function toggleAll(checked) {
        if (!rivalsData) return;
        rivalsData.airlines.forEach(function(airline) {
            hiddenAirlines[airline.id] = !checked;
        });
        renderChart();
        renderTicker();
    }

    // Public API
    return {
        // Getters/setters for external property access
        get loadedById() { return loadedById; },
        set loadedById(v) { loadedById = v; },
        get mapAirlineId() { return mapAirlineId; },
        set mapAirlineId(v) { mapAirlineId = v; },

        // Entry point
        show,

        // Market data
        get airlines() { return rivalsData ? rivalsData.airlines : []; },
        prefetch,
        loadData,
        renderChart,
        renderTicker,

        // Rival details
        showDetails,
        showDetailsModal,

        // Rival links
        loadLinks,
        renderLinksTable,
        toggleLinksSortOrder,

        // Map / History
        showMap,
        hideMap,
        showHistory,

        // Dropdowns
        toggleDropdown,
        selectPeriod,
        selectMetric,

        // Type filter
        toggleTypeFilter,

        // Ticker
        toggleTickerSortOrder,
        toggleAirline,
        toggleAll,
    };
})();


window.Rivals = Rivals;
