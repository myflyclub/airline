let showFilterRowState = 0;
function toggleLinksTableFilterRow() {
    const header = $("#linksTableFilterHeader");
    switch (showFilterRowState) {
        case 0:
            header.show();
            header.css("height", "200px");
            showFilterRowState = 1;
            break;
        case 1:
            header.show();
            header.css("height", "600px");
            showFilterRowState = 2;
            break;
        default:
            header.hide();
            header.css("height", "200px");
            showFilterRowState = 0;
    }
}

let linksColumnFilter = {}
let currentFilterOptionValues = {}
function updateLinksColumnFilterOptions(values) {
    if (JSON.stringify(values) === JSON.stringify(currentFilterOptionValues)) {
        return;
    }
    currentFilterOptionValues = values;

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
                    style: `background: left no-repeat url(${getCountryFlagUrl(countryCode)}); padding-left: 30px; font-weight: bold`,
                    text: countryCode
                });
                selectElement.append(countryGroup);

                Object.entries(airportRow).sort((a, b) => a[0].localeCompare(b[0])).forEach(([airportCode, airportCity]) => {
                    const airport = $('<option>', { value: countryCode + '-' + airportCode, text: airportCity });
                    selectElement.append(airport);
                });
            });

            $(selectElement).on("change", function(event) {
                const countryColumn = column.replace("Airport", "Country");
                linksColumnFilter[countryColumn] = [];
                linksColumnFilter[column] = [];

                for (let option of this.selectedOptions) {
                    if (option.value === "") {
                        // Show all
                        linksColumnFilter[countryColumn] = [];
                        linksColumnFilter[column] = [];
                        break;
                    }

                    [countryCode, airportCode] = option.value.split("-");
                    if (countryCode === undefined && airportCode === undefined) {
                        return;
                    }

                    if (airportCode === undefined) {
                        // Selected a country group
                        linksColumnFilter[countryColumn].push(countryCode);
                    } else {
                        linksColumnFilter[column].push(airportCode);
                    }
                }

                var selectedSortHeader = $('#linksTableSortHeader .cell.selected')
                updateLinksTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'))
            });
        } else {
            Object.entries(rows).sort((a, b) => a[0].localeCompare(b[0])).forEach(([key, value]) => {
                const airport = $('<option>', { value: key, text: value });
                selectElement.append(airport);
            });

            $(selectElement).on("change", function(event) {
                linksColumnFilter[column] = [];

                for (let option of this.selectedOptions) {
                    if (option.value === "") {
                        // Show all
                        linksColumnFilter[column] = [];
                        break;
                    }

                    linksColumnFilter[column].push(option.value);
                }

                var selectedSortHeader = $('#linksTableSortHeader .cell.selected')
                updateLinksTable(selectedSortHeader.data('sort-property'), selectedSortHeader.data('sort-order'))
            });
        }

        const filterDiv = $('#linksTableFilterHeader').find(`[data-filter-property='${column}']`);
        filterDiv.empty();
        filterDiv.append(selectElement);
    });
}
