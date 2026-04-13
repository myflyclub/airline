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
        } else if (shouldUseBuckets(column, rows)) {
            const buckets = createDynamicBuckets(column, rows);

            if (buckets.length === 0) {
                // Fallback to regular dropdown if no valid buckets
                Object.entries(rows)
                    .sort((a, b) => {
                        const numA = Number(a[0]);
                        const numB = Number(b[0]);
                        return !isNaN(numA) && !isNaN(numB) ? numA - numB : a[0].localeCompare(b[0]);
                    })
                    .forEach(([key, value]) => {
                        const option = $('<option>', { value: key, text: value });
                        selectElement.append(option);
                    });
            } else {
                // Create options for each bucket that has data
                buckets.forEach((bucket, index) => {
                    // Check if any values fall within this bucket
                    const hasDataInBucket = Object.keys(rows).some(key => {
                        const value = parseInt(key);
                        return !isNaN(value) && value >= bucket.min && value <= bucket.max;
                    });

                    if (hasDataInBucket) {
                        const bucketOption = $('<option>', {
                            value: `bucket-${index}`,
                            text: bucket.label
                        });
                        selectElement.append(bucketOption);
                    }
                });
            }

            $(selectElement).on("change", function (event) {
                state.selectedColumnFilter[column] = [];

                for (let option of this.selectedOptions) {
                    if (option.value === "") { // Show all
                        state.selectedColumnFilter[column] = [];
                        break;
                    }

                    if (option.value.startsWith("bucket-")) {
                        const bucketIndex = parseInt(option.value.replace("bucket-", ""));
                        const bucket = buckets[bucketIndex];

                        if (bucket) {
                            // Add all values that fall within this bucket
                            Object.keys(rows).forEach(key => {
                                const value = parseInt(key);
                                if (!isNaN(value) && value >= bucket.min && value <= bucket.max) {
                                    state.selectedColumnFilter[column].push(key);
                                }
                            });
                        }
                    } else {
                        // Handle non-bucket selections (fallback case)
                        state.selectedColumnFilter[column].push(option.value);
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

        // look for hard-coded values to set the filterDivId
        const filterDivId = "fromAirportCode" in values ? '#linksTableFilterHeader' : '#aircraftTableFilterHeader';
        const filterDiv = $(filterDivId).find(`[data-filter-property='${column}']`);
        filterDiv.empty();
        filterDiv.append(selectElement);

        // Apply initial selected state based on filterOptionValues
        applyInitialSelectedState(selectElement, column, tableName);
    });
}

function shouldUseBuckets(column, rows) {
    const bucketColumns = ['distance', 'range', 'runwayRequirement', 'capacity'];

    if (!bucketColumns.includes(column)) {
        return false;
    }

    // Check if the data is primarily numeric
    const keys = Object.keys(rows);
    const numericKeys = keys.filter(key => !isNaN(parseInt(key))).length;

    // Use bucketing if more than 75% of values are numeric and we have enough data points
    return numericKeys > keys.length * 0.75 && keys.length > 6;
}

function getUnitLabel(column) {
    const unitMap = {
        'distance': distanceLabel(),
        'range': distanceLabel(),
        'runwayRequirement': 'm',
        'capacity': 'pax'
    };
    return unitMap[column] || '';
}

function roundToNearest(value, nearest) {
    if (nearest <= 0) return Math.round(value); // Default to Math.round if nearest is invalid
    return Math.round(value / nearest) * nearest;
}

function createDynamicBuckets(column, rows, bucketCount = 9) {
    // Get all numeric values from the keys of the 'rows' object
    const values = Object.keys(rows).map(key => parseInt(key)).filter(val => !isNaN(val));

    if (values.length === 0) {
        return [];
    }

    const minValue = Math.min(...values);
    const maxValue = Math.max(...values);
    const unit = getUnitLabel(column);

    // Handle cases where all values are the same
    if (minValue === maxValue) {
        return [{
            min: minValue,
            max: maxValue,
            label: `${minValue} ${unit}`
        }];
    }

    const range = maxValue - minValue;
    let roundingPrecision;

    // Determine rounding precision based on the range
    if (range < 10) {         // Very small range
        roundingPrecision = 1;  // Round to nearest integer
    } else if (range > 1000) { // Large range
        roundingPrecision = 100;
    } else {
        roundingPrecision = 10; // Defaulting to 10 for this intermediate range
    }

    // Calculate base segment size
    const segmentSize = range / bucketCount;

    const buckets = [];
    let lastProcessedMax = -Infinity; // Tracks the max of the previously created bucket

    for (let i = 0; i < bucketCount; i++) {
        let bucketMin, bucketMax;

        // --- Calculate bucketMin ---
        if (i === 0) {
            // First bucket's min is the rounded overall minValue
            bucketMin = roundToNearest(minValue, roundingPrecision);
        } else {
            // Subsequent bucket's min starts after the previous bucket's max
            let potentialMin = lastProcessedMax + 1; // Conceptual start (e.g., if lastMax was 19, this is 20)
            bucketMin = roundToNearest(potentialMin, roundingPrecision);

            // Adjust if rounding didn't advance enough
            // e.g. lastProcessedMax=20 (rp=10). potentialMin=21. roundToNearest(21,10)=20.
            // So, bucketMin (20) <= lastProcessedMax (20). Needs to jump to 30.
            if (bucketMin <= lastProcessedMax) {
                bucketMin = lastProcessedMax + 1;
            }
        }

        // --- Calculate bucketMax ---
        if (i === bucketCount - 1) {
            // Last bucket's max is the rounded overall maxValue
            bucketMax = roundToNearest(maxValue, roundingPrecision);
        } else {
            // Theoretical end (exclusive) for this bucket
            let rawBucketEndExclusive = minValue + ((i + 1) * segmentSize);
            // Inclusive max is (theoretical end - 1), then rounded
            bucketMax = roundToNearest(rawBucketEndExclusive - 1, roundingPrecision);
        }

        // --- Ensure min <= max ---
        // This can happen if segmentSize is very small relative to roundingPrecision,
        // or if bucketMin was pushed up aggressively.
        if (bucketMin > bucketMax) {
            bucketMax = bucketMin; // Make it a "point" bucket
        }

        // combine the last two buckets as our biggest bucket is often too small
        if (i === bucketCount - 2) {
            buckets.push({
                min: bucketMin,
                max: maxValue
            });
            break;
        }

        buckets.push({
            min: bucketMin,
            max: bucketMax,
        });

        lastProcessedMax = bucketMax;
    }

    buckets.forEach((bucket, index) => {
        if (bucket.min === bucket.max) {
            bucket.label = `${bucket.min} ${unit}`;
        } else if (index === buckets.length - 1) { // Last bucket
            // Check if this last bucket effectively is the end or extends beyond known max
            // The "+" implies it catches all higher values.
            bucket.label = `${bucket.min}+ ${unit}`;
        } else {
            bucket.label = `${bucket.min}-${bucket.max} ${unit}`;
        }
    });

    // Filter out any potentially problematic buckets if min somehow ended up > max
    return buckets.filter(b => b.min <= b.max);
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
        if (!config.getValue) {
            // Non-numeric column, just add empty cell or label
            summaryRow.append("<div class='cell'>" + (config.label || '') + "</div>");
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

        summaryRow.append("<div class='cell' align='right'>" + aggregateValue + "</div>");
    });

    $(tableSelector).append(summaryRow);
}
