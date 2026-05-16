let showFilterRowState = 0;
function toggleLinksTableFilterRow() {
    const header = $("#linksTableFilterHeader");
    switch (showFilterRowState) {
        case 0:
            header.show();
            header.css("height", "240px");
            showFilterRowState = 1;
            break;
        default:
            header.hide();
            header.css("height", "200px");
            showFilterRowState = 0;
    }
}

function toggleAircraftTableFilterRow() {
    const header = $("#aircraftTableFilterHeader");
    switch (showFilterRowState) {
        case 0:
            header.show();
            header.css("height", "240px");
            showFilterRowState = 1;
            break;
        default:
            header.hide();
            header.css("height", "200px");
            showFilterRowState = 0;
    }
}

let tableFilterState = {
    selectedTable: '',
    getTableState: function (tableName) {
        if (!this[tableName]) {
            this[tableName] = {
                selectedColumnFilter: {},
                filterOptionValues: {},
                currentFilterOptionValues: {}
            };
        }
        return this[tableName];
    }
};

function updateColumnFilterOptions(values, tableName = 'links') {
    const state = tableFilterState.getTableState(tableName);

    if (JSON.stringify(values) === JSON.stringify(state.currentFilterOptionValues)) {
        return;
    }
    state.currentFilterOptionValues = values;

    // Clear existing filter options so we can switch tables
    if (tableName !== tableFilterState.selectedTable) {
        state.currentFilterOptionValues = {};
        tableFilterState.selectedTable = tableName;
    }

    Object.entries(values).forEach(([column, rows]) => {
        const filterDivId = "fromAirportCode" in values ? '#linksTableFilterHeader' : '#aircraftTableFilterHeader';
        const filterDiv = $(filterDivId).find(`[data-filter-property='${column}']`);

        if (isRangeColumn(column)) {
            renderRangeSlider(column, rows, state, tableName, filterDiv);
            return;
        }

        let selectElement = $('<select>', {
            multiple: "multiple",
            style: "width: 100%; height: 100%; background: transparent"
        });
        selectElement.append($('<option>', {
            value: "",
            style: "font-weight: bold",
            text: "-- Show All --",
        }));

        // Render Country-Airport
        if (column === "fromAirportCode" || column === "toAirportCode") {
            Object.entries(rows).sort((a, b) => a[0].localeCompare(b[0])).forEach(([countryCode, airportRow]) => {
                const countryGroup = $('<option>', {
                    value: countryCode,
                    style: `background: left no-repeat url(${getCountryFlagUrl(countryCode)}); padding-left: 32px; font-weight: bold`,
                    text: countryCode
                });
                selectElement.append(countryGroup);

                Object.entries(airportRow).sort((a, b) => a[0].localeCompare(b[0])).forEach(([airportCode, airportCity]) => {
                    const airport = $('<option>', { value: countryCode + '-' + airportCode, text: airportCity });
                    selectElement.append(airport);
                });
            });

            $(selectElement).on("change", function (event) {
                const countryColumn = column.replace("Airport", "Country");
                state.selectedColumnFilter[countryColumn] = [];
                state.selectedColumnFilter[column] = [];

                for (let option of this.selectedOptions) {
                    if (option.value === "") { // Show all
                        state.selectedColumnFilter[countryColumn] = [];
                        state.selectedColumnFilter[column] = [];
                        break;
                    }

                    const [countryCode, airportCode] = option.value.split("-");
                    if (countryCode === undefined && airportCode === undefined) {
                        return;
                    }

                    if (airportCode === undefined) { // Selected a country group
                        state.selectedColumnFilter[countryColumn].push(countryCode);
                    } else {
                        state.selectedColumnFilter[column].push(airportCode);
                    }
                }

                updateSelectedClasses(this, column, tableName);
                var selectedSortHeader = $(`#${tableName}TableSortHeader .cell.selected`);
                if (tableName === 'links') {
                    updateLinksTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'));
                } else if (tableName === 'aircraft') {
                    updateAirplaneModelTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'));
                }
            });
        } else { // Render other columns
            Object.entries(rows).sort((a, b) => {
                const keyA = a[0];
                const keyB = b[0];

                // Check if both keys are numeric
                const numA = Number(keyA);
                const numB = Number(keyB);

                if (!isNaN(numA) && !isNaN(numB) && keyA !== "" && keyB !== "") {
                    // Both are valid numbers - sort numerically
                    return numA - numB;
                } else {
                    // At least one is not a number - sort alphabetically
                    return keyA.localeCompare(keyB);
                }
            }).forEach(([key, value]) => {
                const option = $('<option>', { value: key, text: value });
                selectElement.append(option);
            });

            $(selectElement).on("change", function (event) {
                state.selectedColumnFilter[column] = [];

                for (let option of this.selectedOptions) {
                    if (option.value === "") { // Show all
                        state.selectedColumnFilter[column] = [];
                        break;
                    }

                    state.selectedColumnFilter[column].push(option.value);
                }

                updateSelectedClasses(this, column, tableName);
                var selectedSortHeader = $(`#${tableName}TableSortHeader .cell.selected`);
                if (tableName === 'links') {
                    updateLinksTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'));
                } else if (tableName === 'aircraft') {
                    updateAirplaneModelTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'));
                }
            });
        }

        filterDiv.empty();
        filterDiv.append(selectElement);

        // Apply initial selected state based on filterOptionValues
        applyInitialSelectedState(selectElement, column, tableName);
    });
}

function isRangeColumn(column) {
    return ['distance', 'range', 'runwayRequirement', 'capacity', 'quality'].includes(column);
}

function formatRangeLabel(column, value) {
    if (column === 'range' || column === 'distance') return Math.round(convertDistance(value));
    return Math.round(value);
}

function renderRangeSlider(column, rows, state, tableName, filterDiv) {
    const rawValues = Object.keys(rows).map(Number).filter(v => !isNaN(v));
    if (rawValues.length === 0) return;

    const overallMin = Math.min(...rawValues);
    const overallMax = Math.max(...rawValues);
    const unit = column === 'range' || column === 'distance' ? distanceLabel()
               : column === 'runwayRequirement' ? 'm' : '';

    const saved = state.filterOptionValues[column];
    const curMin = (saved && saved.min !== undefined) ? saved.min : overallMin;
    const curMax = (saved && saved.max !== undefined) ? saved.max : overallMax;

    const wrap = $('<div class="rs-wrap"></div>');
    const loLabel = $(`<div class="rs-label rs-lo-label">${formatRangeLabel(column, curMin)}${unit ? ' ' + unit : ''}</div>`);
    const hiLabel = $(`<div class="rs-label rs-hi-label">${formatRangeLabel(column, curMax)}${unit ? ' ' + unit : ''}</div>`);
    const area = $('<div class="rs-area"></div>');
    const track = $('<div class="rs-track"></div>');
    const fill = $('<div class="rs-fill"></div>');
    const inputHi = $('<input type="range" orient="vertical" class="rs-input rs-hi">').attr({ min: overallMin, max: overallMax, value: curMax, step: 1 });
    const inputLo = $('<input type="range" orient="vertical" class="rs-input rs-lo">').attr({ min: overallMin, max: overallMax, value: curMin, step: 1 });

    area.append(track, fill, inputHi, inputLo);
    wrap.append(loLabel, area, hiLabel);
    filterDiv.empty().append(wrap);

    function updateFill(lo, hi) {
        const span = overallMax - overallMin || 1;
        const loPct = (lo - overallMin) / span * 100;
        const hiPct = (hi - overallMin) / span * 100;
        // min at top (loPct=0), max at bottom (hiPct=100)
        fill.css({ top: loPct + '%', height: (hiPct - loPct) + '%' });
        loLabel.text(formatRangeLabel(column, lo) + (unit ? ' ' + unit : ''));
        hiLabel.text(formatRangeLabel(column, hi) + (unit ? ' ' + unit : ''));
        const isFiltering = lo !== overallMin || hi !== overallMax;
        loLabel.toggleClass('active', isFiltering);
        hiLabel.toggleClass('active', isFiltering);
        // When both thumbs are near the bottom (high values), lo needs to be on top so it can be dragged up
        if (hiPct > 50) { inputLo.css('z-index', 3); inputHi.css('z-index', 2); }
        else             { inputLo.css('z-index', 2); inputHi.css('z-index', 3); }
    }

    inputHi.on('input', function() {
        let hi = parseInt(this.value);
        const lo = parseInt(inputLo.val());
        if (hi < lo) { hi = lo; this.value = hi; }
        updateFill(lo, hi);
        applyRangeFilter(column, lo, hi, overallMin, overallMax, state, tableName);
    });

    inputLo.on('input', function() {
        const hi = parseInt(inputHi.val());
        let lo = parseInt(this.value);
        if (lo > hi) { lo = hi; this.value = lo; }
        updateFill(lo, hi);
        applyRangeFilter(column, lo, hi, overallMin, overallMax, state, tableName);
    });

    updateFill(curMin, curMax);

    if (saved && saved.min !== undefined && (saved.min !== overallMin || saved.max !== overallMax)) {
        state.selectedColumnFilter[column] = { min: curMin, max: curMax };
    }
}

function applyRangeFilter(column, lo, hi, overallMin, overallMax, state, tableName) {
    if (lo === overallMin && hi === overallMax) {
        delete state.selectedColumnFilter[column];
        state.filterOptionValues[column] = {};
    } else {
        state.selectedColumnFilter[column] = { min: lo, max: hi };
        state.filterOptionValues[column] = { min: lo, max: hi };
    }
    const selectedSortHeader = $(`#${tableName}TableSortHeader .cell.selected`);
    if (tableName === 'links') {
        updateLinksTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'));
    } else if (tableName === 'aircraft') {
        updateAirplaneModelTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'));
    }
}

function updateSelectedClasses(selectElement, column, tableName) {
    const state = tableFilterState[tableName];
    if (!state) return;

    // Update filterOptionValues to reflect the current selections.
    // This state will be used by applyInitialSelectedState when the dropdowns are rebuilt.
    state.filterOptionValues[column] = {};

    // If no options selected (or "Show All" selected), clear the filterOptionValues for this column
    if (selectElement.selectedOptions.length === 0 ||
        (selectElement.selectedOptions.length === 1 && selectElement.selectedOptions[0].value === "")) {
        state.filterOptionValues[column] = {};
    } else {
        for (let option of selectElement.selectedOptions) {
            state.filterOptionValues[column][option.value] = true;
        }
    }

    // Apply selected class to currently selected options
    $(selectElement).find('option').each(function () {
        if (this.selected) {
            $(this).addClass('selected');
        } else {
            $(this).removeClass('selected');
        }
    });
}

function applyInitialSelectedState(selectElement, column, tableName) {
    const columnSelections = tableFilterState[tableName]?.filterOptionValues[column];

    // Apply selected state based on filterOptionValues
    if (columnSelections && Object.keys(columnSelections).length > 0) {
        $(selectElement).find('option').each(function () {
            const optionValue = $(this).val();
            if (columnSelections[optionValue]) {
                $(this).prop('selected', true).addClass('selected');
            } else {
                $(this).removeClass('selected');
            }
        });
    } else {
        // If no selections, explicitly select "Show All" and add selected class
        $(selectElement).find('option[value=""]').prop('selected', true).addClass('selected');
        // Remove selected class from other options
        $(selectElement).find('option:not([value=""])').removeClass('selected');
    }
}

/**
 * Resets all active filters for a table, clearing state and resetting dropdowns to "Show All".
 * Call this before navigating to a specific row so it isn't hidden by an active filter.
 * @param {string} tableName - 'links' or 'aircraft'
 */
function resetTableFilters(tableName) {
    var state = tableFilterState.getTableState(tableName)
    state.selectedColumnFilter = {}
    state.filterOptionValues = {}
    state.currentFilterOptionValues = {}
    var headerId = tableName === 'links' ? '#linksTableFilterHeader' : '#aircraftTableFilterHeader'
    $(headerId).find('select').each(function() {
        $(this).find('option').prop('selected', false).removeClass('selected')
        $(this).find('option[value=""]').prop('selected', true).addClass('selected')
    })
    $(headerId).find('.rs-lo').each(function() { this.value = this.min; })
    $(headerId).find('.rs-hi').each(function() { this.value = this.max; })
    $(headerId).find('.rs-fill').css({ top: '0%', height: '100%' })
}

function toggleSimpleSortOrder(sortHeader, renderFn, ...extraArgs) {
    sortHeader.data('sort-order') === 'ascending'
        ? sortHeader.data('sort-order', 'descending')
        : sortHeader.data('sort-order', 'ascending')
    sortHeader.siblings().removeClass('selected')
    sortHeader.addClass('selected')
    renderFn(...extraArgs, sortHeader.data('sort-property'), sortHeader.data('sort-order'))
}

/**
 * Adds a summary row to a table displaying aggregate statistics for numeric columns
 * @param {string} tableSelector - jQuery selector for the table element
 * @param {Array} dataArray - Array of data objects to calculate from
 * @param {Array} columnConfigs - Array of column config objects: [{getValue: function, format: function, label: string}]
 * @param {string} aggregationMethod - 'average' or 'median' (default: 'average')
 */
function addTableSummaryRow(tableSelector, dataArray, columnConfigs, aggregationMethod = 'average') {
    if (!dataArray || dataArray.length === 0) {
        return; // No data to summarize
    }

    const summaryRow = $("<div class='table-row summary-row'></div>");
    summaryRow.append("<div class='cell'> </div>");

    columnConfigs.forEach((config) => {
        const keyAttr = config.key ? ` data-col-key="${config.key}"` : '';
        if (!config.getValue) {
            // Non-numeric column, just add empty cell or label
            summaryRow.append(`<div class='cell'${keyAttr}>` + (config.label || '') + "</div>");
            return;
        }

        // Extract numeric values for this column
        const values = dataArray
            .map(item => config.getValue(item))
            .filter(val => val !== null && val !== undefined && val !== '-' && val !== '')
            .map(val => {
                // Convert to number if it's a string
                const num = typeof val === 'string' ? parseFloat(val.toString().replace(/[$,%]/g, '')) : val;
                return isNaN(num) ? null : num;
            })
            .filter(val => val !== null);

        let aggregateValue = '-';
        if (values.length > 0) {
            if (aggregationMethod === 'median') {
                const sorted = values.slice().sort((a, b) => a - b);
                const mid = Math.floor(sorted.length / 2);
                aggregateValue = sorted.length % 2 === 0
                    ? (sorted[mid - 1] + sorted[mid]) / 2
                    : sorted[mid];
            } else { // average
                aggregateValue = values.reduce((a, b) => a + b, 0) / values.length;
            }

            if (config.format) {
                aggregateValue = config.format(aggregateValue);
            }
        }

        summaryRow.append(`<div class='cell'${keyAttr} align='right'>` + aggregateValue + "</div>");
    });

    $(tableSelector).append(summaryRow);
}
