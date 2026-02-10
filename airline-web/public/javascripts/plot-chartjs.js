const chartColors = {
    discounteconomy: "#78cd6b",
    economy: "#57A34B",
    business: "#4E79A7",
    first: "#D6B018",
    economyEmpty: "#346935",
    businessEmpty: "#36466D",
    firstEmpty: "#7A6925",
    cancelled: "#D66061",
    empty: "#DDDFDF",
    tourist: "#fbfe66ff",
    elite: "#7A4A9D",
    traveler: "#4ebc36",
    travelersmalltown: "#4ebc36",
    olympics: "#D8A62F",
    codeshares: "#4eafa4",
    dealseeker: "#57A34B",
    frequentflyer: "#2163aa",
    brandsensitive: "#5f3a9f",
    satisfaction: "#e3b80d",
    rask: "#1e9b2b",
    cask: "#e90e0e",
};
// helper to lookup chart color case-insensitively with fallback
function getChartColor(key, fallback) {
    if (key === null || key === undefined || key === '') {
        return fallback || colorFromString('default');
    }
    const lower = String(key).toLowerCase();
    if (Object.prototype.hasOwnProperty.call(chartColors, lower)) {
        return chartColors[lower];
    }
    return fallback || colorFromString(key);
}
// Chart utilities for clean chart management
const ChartUtils = {
    createChart(containerId, config) {
        const container = typeof containerId === 'string' ?
            document.getElementById(containerId) : containerId;

        // Destroy existing chart if any
        this.destroyChart(container);

        if (!config.options) config.options = {};
        if (!config.options.plugins) config.options.plugins = {};
        if (!config.options.plugins.legend) config.options.plugins.legend = {};

        // Set legend position to bottom by default, but allow override
        if (config.options.plugins.legend.position === undefined) {
            config.options.plugins.legend.position = 'bottom';
        }

        // Create fresh canvas
        const canvas = document.createElement('canvas');
        container.innerHTML = '';
        container.appendChild(canvas);

        // Create and store chart
        const chart = new Chart(canvas, config);
        container._chart = chart;

        this.applyTheme(chart);
        return chart;
    },

    destroyChart(container) {
        if (container && container._chart) {
            container._chart.destroy();
            container._chart = null;
        }
    },

    applyTheme(chart) {
        if (document.documentElement.getAttribute("data-theme") === "dark") {
            const darkColor = '#DDDDDD';
            if (chart.options.plugins?.legend?.labels) {
                chart.options.plugins.legend.labels.color = darkColor;
            }
            ['x', 'y', 'y1'].forEach(axis => {
                if (chart.options.scales?.[axis]) {
                    if (chart.options.scales[axis].title) {
                        chart.options.scales[axis].title.color = darkColor;
                    }
                    if (chart.options.scales[axis].ticks) {
                        chart.options.scales[axis].ticks.color = darkColor;
                    }
                }
            });
            chart.update();
        }
    }
    ,
    // Accepts either a Chart.js config object (with `data.datasets`) or a Chart instance.
    applyFinancialChartStyle(target) {
        const commonPointOptions = { pointStyle: 'triangle', pointRadius: 4, pointHoverRadius: 14, borderWidth: 1 };

        // Standard financial tooltip callbacks to use across charts
        const financialTooltip = {
            mode: 'index',
            callbacks: {
                title: function (tooltipItems) {
                    return tooltipItems[0] ? 'Week/Year: ' + tooltipItems[0].label : '';
                },
                label: function (tooltipItem) {
                    const propLabel = tooltipItem.dataset.label || tooltipItem.dataset.key || '';
                    const value = tooltipItem.raw !== undefined ? tooltipItem.raw : tooltipItem.parsed && tooltipItem.parsed.y;
                    return prettyLabel(propLabel, value, { currency: true });
                }
            }
        };

        if (!target) return;

        // If passed a Chart instance, apply directly to its datasets and options
        if (target.data && Array.isArray(target.data.datasets)) {
            target.data.datasets.forEach(ds => Object.assign(ds, commonPointOptions));
            // replace tooltip config if present
            try {
                if (target.options && target.options.plugins) {
                    target.options.plugins.tooltip = financialTooltip;
                }
                target.update && target.update();
            } catch (e) { /* ignore update errors */ }
            return;
        }

        // If passed a config object, apply to config.data.datasets and replace tooltip
        if (target.data && Array.isArray(target.data.datasets)) {
            target.data.datasets.forEach(ds => Object.assign(ds, commonPointOptions));
            if (!target.options) target.options = {};
            if (!target.options.plugins) target.options.plugins = {};
            target.options.plugins.tooltip = financialTooltip;
            return;
        }
    }
};

// Utility functions
function stringHashCode(s) {
    let h = 0;
    const l = s.length;
    let i = 0;
    if (l > 0) {
        while (i < l) {
            h = (h << 5) - h + s.charCodeAt(i++) | 0;
        }
    }
    return h;
}

const rgbMask = 0xff;
function colorFromString(s) {
    let hashCode = stringHashCode(s);
    let r = (hashCode & rgbMask);
    hashCode = hashCode >> 2;
    let g = (hashCode & rgbMask);
    hashCode = hashCode >> 2;
    let b = (hashCode & rgbMask);

    if (r < 64 && g < 64 && b < 64) {
        r *= 2;
        g *= 2;
        b *= 2;
    }

    const rr = r.toString(16).padStart(2, '0');
    const gg = g.toString(16).padStart(2, '0');
    const bb = b.toString(16).padStart(2, '0');

    return `#${rr}${gg}${bb}`;
}
/**
 * Prepare pie chart data: convert values, aggregate small slices into "Other"
 * @param {Object} dataSource - original data source
 * @param {string|null} keyName - optional key property name
 * @param {string|null} valueName - optional value property name
 * @param {number} thresholdFraction - fraction under which slices are aggregated (default 0.02)
 * @returns {{labels: string[], data: number[], backgroundColors: string[]}}
 */
function preparePieData(dataSource, keyName = null, valueName = null, thresholdFraction = 0.02) {
    const labels = [];
    const data = [];
    const backgroundColors = [];

    // compute total
    let totalValue = 0;
    Object.entries(dataSource).forEach(([key, dataEntry]) => {
        let dataValue = (keyName && valueName) ? dataEntry[valueName] : dataEntry;
        dataValue = Number(dataValue) || 0;
        totalValue += dataValue;
    });

    let otherTotal = 0;
    const normalEntries = [];

    Object.entries(dataSource).forEach(([key, dataEntry]) => {
        const keyLabel = (keyName && valueName) ? dataEntry[keyName] : key;
        let dataValue = (keyName && valueName) ? Number(dataEntry[valueName]) : Number(dataEntry);
        dataValue = dataValue || 0;

        const isSmall = (totalValue > 0) ? (dataValue / totalValue) < thresholdFraction : false;
        if (isSmall) {
            otherTotal += dataValue;
            return;
        }

        // determine background color for this entry
        let bgColor;
        if (dataEntry.color) {
            bgColor = dataEntry.color;
        } else if (Object.prototype.hasOwnProperty.call(chartColors, keyLabel.replace(/\s+/g, '').toLowerCase())) {
            bgColor = chartColors[keyLabel.replace(/\s+/g, '').toLowerCase()];
        } else {
            bgColor = colorFromString(keyLabel);
        }

        normalEntries.push({ label: prettyLabel(keyLabel), value: dataValue, color: bgColor });
    });

    // Sort normal entries by value descending (largest first)
    normalEntries.sort((a, b) => b.value - a.value);

    // Fill arrays from sorted normal entries
    normalEntries.forEach(entry => {
        labels.push(entry.label);
        data.push(entry.value);
        backgroundColors.push(entry.color);
    });

    if (otherTotal > 0) {
        labels.push(prettyLabel('Others') + ', less than ' + (thresholdFraction * 100).toFixed(0) + '%');
        data.push(otherTotal);
        if (Object.prototype.hasOwnProperty.call(chartColors, 'other')) {
            backgroundColors.push(chartColors['other']);
        } else {
            backgroundColors.push(colorFromString('Other'));
        }
    }

    return { labels, data, backgroundColors };
}

/**
 * Plot configuration constants
 * 
 * maxWeek: weeks to pull data for and then chart
**/
const weeksPerYear = 48;

const plotUnitEnum = {
    MONTH: {
        value: 1,
        maxWeek: weeksPerYear
    },
    QUARTER: {
        value: 2,
        maxWeek: weeksPerYear * 2
    },
    YEAR: {
        value: 3,
        maxWeek: weeksPerYear * 3
    }
};

/**
 * Creates a single HTML legend that controls multiple Chart.js instances
 * by matching dataset labels, not indices.
 *
 * @param {Array<Chart>} charts - An array of Chart.js instances to control.
 */
function createCustomLinkLegend(charts) {
    const legendContainer = document.getElementById('linkLegend');
    if (!legendContainer) return;

    // Always rebuild the legend to tie events to the new chart instances
    legendContainer.innerHTML = '';

    if (!charts || charts.length === 0) {
        legendContainer.innerHTML = 'No charts to display.';
        return;
    }

    /**
     * Gets the base class from a dataset label.
     * e.g., "Sold economy" -> "economy", "Empty business" -> "business", "Cancelled Seats" -> "Cancelled Seats"
     * @param {string} label
     * @returns {string} The base class name.
     */
    const getBaseClass = (label) => {
        if (label.startsWith('Sold ')) return prettyLabel(label.substring(5));
        if (label.startsWith('Empty ')) return prettyLabel(label.substring(6));
        return prettyLabel(label); // Works for "economy", "business", "Cancelled Seats"
    };

    // Use a Map to find all unique base classes and get their color/initial state
    const baseClassMap = new Map();

    charts.forEach(chart => {
        chart.data.datasets.forEach((dataset, index) => {
            // Ensure dataset and meta exist before access
            if (!dataset || !chart.getDatasetMeta(index)) {
                console.warn("Chart.js dataset or meta not found at index:", index, chart);
                return;
            }
            const baseClass = getBaseClass(dataset.label);
            if (!baseClassMap.has(baseClass)) {
                baseClassMap.set(baseClass, {
                    color: dataset.borderColor || dataset.backgroundColor || '#888',
                    initialHidden: chart.getDatasetMeta(index).hidden
                });
            }
        });
    });

    // Create a legend item for each unique base class
    baseClassMap.forEach((props, baseClass) => {
        const li = document.createElement('li');
        li.style.cssText = 'list-style: none; margin-bottom: 8px; cursor: pointer; display: flex; align-items: center; width: 160px;';
        li.style.opacity = props.initialHidden ? '0.5' : '1';

        const box = document.createElement('span');
        box.style.cssText = 'display: inline-block; width: 12px; height: 12px; margin-right: 8px; border-radius: 2px;';
        box.style.backgroundColor = props.color;

        const text = document.createElement('span');
        text.textContent = baseClass;
        text.style.userSelect = 'none';

        // Click handler: Toggles all datasets in *all* charts matching this base class
        li.addEventListener('click', () => {

            // 1. Determine the *new* state.
            // Check if *any* related dataset in *any* chart is currently visible.
            let isAnyVisible = false;
            for (const chart of charts) {
                for (let i = 0; i < chart.data.datasets.length; i++) {
                    // Ensure meta exists before checking 'hidden'
                    const meta = chart.getDatasetMeta(i);
                    if (meta && getBaseClass(chart.data.datasets[i].label) === baseClass) {
                        if (!meta.hidden) {
                            isAnyVisible = true;
                            break;
                        }
                    }
                }
                if (isAnyVisible) break;
            }

            // If any are visible, the new state is to hide them all.
            const setNewStateToHidden = isAnyVisible;

            // 2. Apply this new state to all matching datasets in all charts
            charts.forEach(chartInstance => {
                let chartNeedsUpdate = false;
                chartInstance.data.datasets.forEach((dataset, index) => {
                    const meta = chartInstance.getDatasetMeta(index);
                    if (meta && getBaseClass(dataset.label) === baseClass) {
                        if (meta.hidden !== setNewStateToHidden) {
                            meta.hidden = setNewStateToHidden;
                            chartNeedsUpdate = true;
                        }
                    }
                });
                // Update chart only if something changed
                if (chartNeedsUpdate) {
                    chartInstance.update('none'); // 'none' for no animation
                }
            });

            // 3. Update the legend item's opacity
            li.style.opacity = setNewStateToHidden ? '0.5' : '1';
        });

        li.appendChild(box);
        li.appendChild(text);
        legendContainer.appendChild(li);
    });
}

// Chart plotting functions
function plotSeatConfigurationBar(id, configuration, maxSeats, spaceMultipliers, hideValues, height) {
    const businessPosition = configuration.economy / maxSeats * 100;
    const firstPosition = configuration.economy / maxSeats * 100 + configuration.business * spaceMultipliers.business / maxSeats * 100;
    const emptyPosition = configuration.economy / maxSeats * 100 + configuration.business * spaceMultipliers.business / maxSeats * 100 + configuration.first * spaceMultipliers.first / maxSeats * 100;

    const datasets = [
        {
            label: hideValues ? 'Economy' : `Y : ${configuration.economy}`,
            data: [businessPosition],
            backgroundColor: getChartColor('economy', '#2eb918'),
        },
        {
            label: hideValues ? 'Business' : `J : ${configuration.business}`,
            data: [firstPosition - businessPosition],
            backgroundColor: getChartColor('business', '#0077CC'),
        },
        {
            label: hideValues ? 'First' : `F : ${configuration.first}`,
            data: [emptyPosition - firstPosition],
            backgroundColor: getChartColor('first', '#FFE62B'),
        },
        {
            label: 'Empty',
            data: [100 - emptyPosition],
            backgroundColor: getChartColor('empty', '#cccccc'),
        }
    ];

    const config = {
        type: 'bar',
        data: {
            labels: ['Configuration'],
            datasets: datasets
        },
        options: {
            indexAxis: 'y',
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    enabled: !hideValues,
                    mode: 'index',
                    intersect: false
                }
            },
            scales: {
                x: {
                    display: false,
                    stacked: true,
                    min: 0,
                    max: 100
                },
                y: {
                    display: false,
                    stacked: true
                }
            }
        }
    };

    return ChartUtils.createChart(id, config);
}

function plotLinkProfit(linkConsumptions, id, plotUnit = plotUnitEnum.MONTH) {
    const data = [];
    const category = [];
    const profitByMark = {};
    const markOrder = [];

    const maxMark = plotUnit.maxWeek;

    linkConsumptions.length > 0 && linkConsumptions.forEach((linkConsumption) => {
        const mark = getGameDate(linkConsumption.cycle);
        if (profitByMark[mark] === undefined) {
            profitByMark[mark] = linkConsumption.profit;
            markOrder.push(mark);
        } else {
            profitByMark[mark] += linkConsumption.profit;
        }
    });

    markOrder.slice(0, maxMark).reverse().forEach(mark => {
        data.push(profitByMark[mark]);
        category.push(mark.toString());
    });

    const config = {
        type: 'bar',
        data: {
            labels: category,
            datasets: [
                {
                    type: 'line',
                    label: 'Profit',
                    data: data,
                    backgroundColor: 'rgba(153, 234, 243, 1)',
                    borderColor: 'rgba(246, 255, 147, 1)',
                    borderWidth: 1,
                    fill: true,
                    pointStyle: false
                }
            ]
        },
        options: {
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    mode: 'index',
                    callbacks: {
                        title: function (tooltipItems) {
                            return tooltipItems[0] ? 'Week/Year: ' + tooltipItems[0].label : '';
                        },
                        label: function (tooltipItem) {
                            const propLabel = tooltipItem.dataset.label || '';
                            const value = tooltipItem.raw !== undefined ? tooltipItem.raw : tooltipItem.parsed && tooltipItem.parsed.y;
                            return prettyLabel(propLabel, value, { currency: true });
                        }
                    }
                }
            },
            interaction: {
                mode: 'nearest',
                axis: 'x',
                intersect: false
            },
            scales: {
                x: {
                    title: {
                        display: false,
                        text: 'Week/Year'
                    }
                },
                y: {
                    title: {
                        display: true,
                        text: "Profit"
                    },
                    ticks: {
                        callback: function (value) {
                            return '$' + commaSeparateNumber(value, 'auto');
                        }
                    }
                }
            }
        }
    };

    return ChartUtils.createChart(id, config);
}

function plotLinkConsumption(linkConsumptions, ridershipId, revenueId, priceId, plotUnit = plotUnitEnum.MONTH) {
    const cancelledSeatsData = {};
    const soldSeatsData = {};
    const revenueByClass = {};
    const priceByClass = {};
    const emptySeatsData = {};
    const category = [];

    const maxWeek = plotUnit.maxWeek;
    const xLabel = "Week / Year";

    if (linkConsumptions && Object.keys(linkConsumptions).length > 0) {
        const consumptionsArray = Array.from(linkConsumptions).slice(0, maxWeek);

        // Detect which classes are actually present
        const availableClasses = [
            ...new Set(
                consumptionsArray.flatMap((entry) =>
                    Object.keys(entry.capacity).filter(
                        (classType) => entry.capacity[classType] > 0 && classType !== "total"
                    )
                )
            ),
        ];

        availableClasses.forEach((cls) => {
            cancelledSeatsData[cls] = [];
            soldSeatsData[cls] = [];
            revenueByClass[cls] = [];
            priceByClass[cls] = [];
            emptySeatsData[cls] = [];
        });

        consumptionsArray.reverse().forEach((linkConsumption) => {
            const mark = getGameDate(linkConsumption.cycle);
            category.push(mark.toString());

            // Per-class data
            availableClasses.forEach((cls) => {
                const sold = linkConsumption.soldSeats?.[cls] || 0;
                const cancelled = linkConsumption.cancelledSeats?.[cls] || 0;
                const price = linkConsumption.price?.[cls] || 0;
                const capacity = linkConsumption.capacity?.[cls] || 0;
                const empty = capacity - sold - cancelled;

                cancelledSeatsData[cls].push(cancelled);
                soldSeatsData[cls].push(sold);
                revenueByClass[cls].push(price * sold);
                priceByClass[cls].push(price);
                emptySeatsData[cls].push(empty);
            });
        });

        // Ridership Chart
        const ridershipDatasets = [];

        availableClasses.forEach((cls) => {
            // Sold
            ridershipDatasets.push({
                label: `Sold ${cls}`,
                data: soldSeatsData[cls],
                backgroundColor: chartColors[cls] || "#888",
                fill: true,
                pointStyle: false,
            });

            // Empty
            ridershipDatasets.push({
                label: `Empty ${cls}`,
                data: emptySeatsData[cls],
                backgroundColor: (chartColors[cls] ? chartColors[cls + "Empty"] : getChartColor('empty', "#bbbbbb33")),
                fill: true,
                pointStyle: false,
            });

            // Cancelled
            ridershipDatasets.push({
                label: `Cancelled ${cls}`,
                data: cancelledSeatsData[cls],
                backgroundColor: getChartColor('cancelled', "#D46A6A"),
                fill: true,
                pointStyle: false,
            });
        });

        const ridershipConfig = {
            type: "line",
            data: {
                labels: category,
                datasets: ridershipDatasets,
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false,
                        onClick: function () { return false; }
                    },
                    title: {
                        display: true,
                        text: "Ridership (Tickets sold)"
                    },
                    tooltip: {
                        mode: "index",
                        callbacks: {
                            title: function (tooltipItems) {
                                return tooltipItems[0] ? "Week/Year: " + tooltipItems[0].label : "";
                            },
                        },
                    },
                },
                interaction: {
                    mode: "nearest",
                    axis: "x",
                    intersect: false,
                },
                scales: {
                    y: {
                        stacked: true,
                        min: 0,
                        startAtZero: true,
                        title: { display: true, text: "Seats Consumption" },
                    },
                    x: {
                        title: { display: true, text: xLabel },
                    },
                },
            },
        };

        // Revenue Chart
        const revenueConfig = {
            type: "line",
            data: {
                labels: category,
                datasets: availableClasses.map((cls) => ({
                    label: `${cls}`,
                    data: revenueByClass[cls],
                    borderColor: chartColors[cls] || "#888",
                    backgroundColor: (chartColors[cls] ? (chartColors[cls]) : "#8888"),
                    fill: true,
                    pointStyle: false
                })),
            },
            options: {
                maintainAspectRatio: false,
                interaction: {
                    mode: "nearest",
                    axis: "x",
                    intersect: false,
                },
                title: {
                    display: true,
                    text: "Revenue"
                },
                plugins: {
                    legend: {
                        display: false,
                        onClick: function () { return false; }
                    },
                    tooltip: {
                        mode: "index",
                        callbacks: {
                            title: function (tooltipItems) {
                                return tooltipItems[0] ? "Week/Year: " + tooltipItems[0].label : "";
                            },
                            label: function (tooltipItem) {
                                const propLabel = tooltipItem.dataset.label || tooltipItem.dataset.key || '';
                                const value = tooltipItem.raw !== undefined ? tooltipItem.raw : tooltipItem.parsed && tooltipItem.parsed.y;
                                return prettyLabel(propLabel, value, { currency: true });
                            }
                        },
                    },
                },
                scales: {
                    y: {
                        stacked: true,
                        min: 0,
                        startAtZero: true,
                        title: { display: true, text: "Revenue" },
                        ticks: {
                            callback: function (value) {
                                return '$' + commaSeparateNumber(value, 'auto');
                            },
                            maxTicksLimit: 6
                        },
                        grid: {
                            color: 'rgba(54, 54, 54, 0.7)',
                            lineWidth: 1
                        }
                    },
                    x: {
                        title: { display: true, text: xLabel },
                    },
                },
            },
        };

        // Price Chart
        const priceConfig = {
            type: "line",
            data: {
                labels: category,
                datasets: availableClasses.map((cls) => ({
                    label: `Price (${cls})`,
                    data: priceByClass[cls],
                    borderColor: chartColors[cls] || "#888",
                    backgroundColor: chartColors[cls] || "#888",
                    pointStyle: false,
                })),
            },
            options: {
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                },
                scales: {
                    y: {
                        border: {
                            display: false
                        },
                        type: 'logarithmic',
                        title: { display: true, text: "Ticket Price" },
                        ticks: {
                            callback: function (value) {
                                return "$" + value;
                            },
                            maxTicksLimit: 7
                        },
                        grid: {
                            color: 'rgba(255,255,255,0.04)',
                            lineWidth: 1,
                            borderDash: [5, 5]
                        },
                    },
                    x: {
                        title: { display: true, text: xLabel },
                    },
                },
            },
        };

        const ridershipChart = ChartUtils.createChart(ridershipId, ridershipConfig);
        const revenueChart = ChartUtils.createChart(revenueId, revenueConfig);
        createCustomLinkLegend([ridershipChart, revenueChart]);

        ChartUtils.createChart(priceId, priceConfig);
    }
}

function plotRivalHistory(rivalHistory, container, cycleHoverFunc, chartOutFunc) {
    const dataByAirlineId = {};
    const airlineNameByAirlineId = {};
    const category = [];

    if (rivalHistory && Object.keys(rivalHistory).length > 0) {
        Object.entries(rivalHistory).forEach(([cycle, linkConsumptionByAirline]) => {
            Object.entries(linkConsumptionByAirline).forEach(([airlineId, entry]) => {
                airlineNameByAirlineId[airlineId] = entry.airlineName;
            });
            category.push(cycle);
        });

        Object.keys(airlineNameByAirlineId).forEach(airlineId => {
            dataByAirlineId[airlineId] = [];
        });

        Object.entries(rivalHistory).forEach(([cycle, linkConsumptionByAirline]) => {
            Object.keys(airlineNameByAirlineId).forEach(airlineId => {
                const passenger = linkConsumptionByAirline[airlineId] ? linkConsumptionByAirline[airlineId].passenger : 0;
                dataByAirlineId[airlineId].push(passenger);
            });
        });
    }

    const datasets = [];
    Object.entries(dataByAirlineId).forEach(([airlineId, data]) => {
        let color;
        if (window.airlineColors && window.airlineColors[airlineId]) {
            color = window.airlineColors[airlineId];
        } else {
            color = colorFromString(airlineNameByAirlineId[airlineId]);
        }

        const dataset = {
            label: airlineNameByAirlineId[airlineId],
            data: data,
            borderColor: color,
            backgroundColor: color
        };

        if (window.activeAirline && airlineId == window.activeAirline.id) {
            datasets.unshift(dataset);
        } else {
            datasets.push(dataset);
        }
    });

    const config = {
        type: 'bar',
        data: {
            labels: category,
            datasets: datasets
        },
        options: {
            scales: {
                x: {
                    title: { display: false, text: 'Week' }
                },
                y: {
                    title: { display: true, text: 'Tickets Sold' }
                }
            }
        }
    };

    return ChartUtils.createChart(container, config);
}

function plotLinkEvent(linkConsumptions, linkEventContainer, cycleHoverFunc, chartOutFunc) {
    const soldSeatsData = { economy: [], business: [], first: [] };
    const cancelledSeatsData = [];
    const emptySeatsData = [];
    const category = [];

    if (linkConsumptions && Object.keys(linkConsumptions).length > 0) {
        Array.from(linkConsumptions).reverse().forEach(linkConsumption => {
            const capacity = linkConsumption.capacity.economy + linkConsumption.capacity.business + linkConsumption.capacity.first;
            const soldSeats = linkConsumption.soldSeats.economy + linkConsumption.soldSeats.business + linkConsumption.soldSeats.first;
            const cancelledSeats = linkConsumption.cancelledSeats.economy + linkConsumption.cancelledSeats.business + linkConsumption.cancelledSeats.first;

            emptySeatsData.push(capacity - soldSeats - cancelledSeats);
            cancelledSeatsData.push(cancelledSeats);

            soldSeatsData.economy.push(linkConsumption.soldSeats.economy);
            soldSeatsData.business.push(linkConsumption.soldSeats.business);
            soldSeatsData.first.push(linkConsumption.soldSeats.first);

            category.push(linkConsumption.cycle.toString());
        });
    }

    const config = {
        type: 'bar',
        data: {
            labels: category,
            datasets: [
                { label: 'Sold Economy', data: soldSeatsData.economy, backgroundColor: getChartColor('economy'), stack: 'sold' },
                { label: 'Sold Business', data: soldSeatsData.business, backgroundColor: getChartColor('business'), stack: 'sold' },
                { label: 'Sold First', data: soldSeatsData.first, backgroundColor: getChartColor('first'), stack: 'sold' },
                { label: 'Cancelled', data: cancelledSeatsData, backgroundColor: getChartColor('cancelled'), stack: 'cancelled' },
                { label: 'Empty Seats', data: emptySeatsData, backgroundColor: getChartColor('empty'), stack: 'empty' }
            ]
        },
        options: {
            maintainAspectRatio: false,
            scales: {
                x: {
                    stacked: true,
                    title: { display: true, text: 'Week' }
                },
                y: {
                    stacked: true,
                    title: { display: true, text: 'Seats Consumption' }
                }
            },
            onHover: function (event, chartElement) {
                if (cycleHoverFunc) {
                    if (chartElement.length > 0) {
                        cycleHoverFunc(category[chartElement[0].index]);
                    } else if (chartOutFunc) {
                        chartOutFunc();
                    }
                }
            }
        }
    };

    return ChartUtils.createChart(linkEventContainer, config);
}

function toggleLinkEventBar(chart, cycle, on) {
    if (!chart || chart.lastToggledCycle === cycle) {
        return;
    }

    chart.lastToggledCycle = cycle;

    chart.data.datasets.forEach(dataset => {
        if (!dataset.originalBackgroundColor) {
            dataset.originalBackgroundColor = [...dataset.backgroundColor];
        }

        if (on) {
            dataset.backgroundColor = dataset.backgroundColor.map((color, index) => {
                return chart.data.labels[index] !== cycle ? color + '40' : color;
            });
        } else {
            dataset.backgroundColor = dataset.originalBackgroundColor;
        }
    });

    chart.update();
}
/**
 * Plot a pie chart
 * @param {Object} dataSource - The data source object
 * @deprecated {string|null} currentKey
 * @param {string|HTMLElement} container - The container element or its ID
 * @param {string|null} keyName - The property name for keys in data entries
 * @param {string|null} valueName - The property name for values in data entries
 * @param {boolean} hasLegend - Whether to display the legend
 */
function plotPie(dataSource, currentKey = null, container, keyName = null, valueName = null, hasLegend = false) {
    const { labels, data, backgroundColors } = preparePieData(dataSource, keyName, valueName, 0.01);

    const total = data.reduce((sum, value) => sum + value, 0);

    const config = {
        type: 'pie',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: backgroundColors
            }]
        },
        options: {
            maintainAspectRatio: false,
            layout: {
                padding: {
                    top: 20,
                    bottom: 20
                }
            },
            borderWidth: 1,
            hoverOffset: 12,
            plugins: {
                legend: { display: hasLegend },
                tooltip: {
                    callbacks: {
                        label: function (tooltipItem) {
                            const value = tooltipItem.parsed;
                            const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : 0;
                            return ' ' + value.toLocaleString() + ' (' + percentage + '%)';
                        }
                    }
                }
            }
        }
    };

    return ChartUtils.createChart(container, config);
}

function plotIncomeChart(airlineIncomes, period, container) {
    const labels = [];
    const totalData = [];
    const linksData = [];
    const transactionsData = [];
    const othersData = [];
    const stockPriceData = [];

    Object.entries(airlineIncomes).forEach(([key, airlineIncome]) => {
        labels.push(getGameDate(airlineIncome.cycle));
        totalData.push(airlineIncome.totalProfit);
        linksData.push(airlineIncome.linksProfit);
        transactionsData.push(airlineIncome.transactionsProfit);
        othersData.push(airlineIncome.othersProfit);
        stockPriceData.push(airlineIncome.stockPrice.toFixed(2));
    });

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: 'Total Income', data: totalData, borderColor: getChartColor('total'), backgroundColor: getChartColor('total'), hidden: true },
                { label: 'Flight Income', data: linksData, borderColor: getChartColor('flight'), backgroundColor: getChartColor('flight') },
                { label: 'Transaction Income', data: transactionsData, borderColor: getChartColor('transaction'), backgroundColor: getChartColor('transaction'), hidden: true },
                { label: 'Other Income', data: othersData, borderColor: getChartColor('other'), backgroundColor: getChartColor('other'), hidden: true },
                { label: 'Stock Price', data: stockPriceData, borderColor: getChartColor('stock'), backgroundColor: getChartColor('stock'), yAxisID: 'y1' }
            ]
        },
        options: {
            maintainAspectRatio: false,
            scales: {
                y: {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    title: { display: true, text: 'Income' },
                    ticks: { callback: function (value) { return '$' + commaSeparateNumber(value, 'auto'); } }
                },
                y1: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: { display: true, text: 'Stock Price' },
                    grid: { drawOnChartArea: false },
                    ticks: { callback: function (value) { return '$' + value; } }
                }
            },
        }
    };

    ChartUtils.applyFinancialChartStyle(config);

    return ChartUtils.createChart(container, config);
}

function plotCashFlowChart(airlineCashFlows, period, container) {
    const labels = [];
    const cashFlowData = [];

    Object.entries(airlineCashFlows).forEach(([key, airlineCashFlow]) => {
        labels.push(getGameDate(airlineCashFlow.cycle));
        cashFlowData.push(airlineCashFlow.totalCashFlow);
    });

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: 'Total CashFlow', data: cashFlowData, borderColor: getChartColor('cashflow', '#36A2EB') }
            ]
        },
        options: {
            maintainAspectRatio: false,
            scales: {
                y: {
                    title: { display: true, text: 'Cash Flow' },
                    ticks: { callback: function (value) { return '$' + commaSeparateNumber(value, 'auto'); } }
                }
            }
        }
    };

    ChartUtils.applyFinancialChartStyle(config);

    return ChartUtils.createChart(container, config);
}

//assets airline value chart
function plotTotalValueChart(airlineValue, period, container) {
    const labels = [];
    const totalValueData = [];

    Object.entries(airlineValue).forEach(([key, airlineValue]) => {
        labels.push(getGameDate(airlineValue.cycle));
        totalValueData.push(airlineValue.totalValue);
    });

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: 'Total Value', data: totalValueData, borderColor: getChartColor('totalvalue', '#36A2EB') }
            ]
        },
        options: {
            maintainAspectRatio: false,
            scales: {
                y: {
                    title: { display: true, text: 'Total Value' },
                    ticks: { callback: function (value) { return '$' + commaSeparateNumber(value, 'auto'); } }
                }
            }
        }
    };

    ChartUtils.applyFinancialChartStyle(config);

    return ChartUtils.createChart(container, config);
}

function plotAirlineReputationChart(stats, period, container) {
    const labels = [];
    const totalData = [];
    const leaderboardData = [];

    stats.forEach(stat => {
        labels.push(getGameDate(stat.cycle));
        totalData.push(stat.rep_total);
        leaderboardData.push(stat.rep_leaderboards);
    });

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: "Total", data: totalData, borderColor: getChartColor('total'), backgroundColor: getChartColor('total') },
                { label: "Leaderboards", data: leaderboardData, borderColor: getChartColor('leaderboards'), backgroundColor: getChartColor('leaderboards') },
            ]
        },
        options: {
            maintainAspectRatio: false,
            scales: {
                y: {
                    title: { display: true, text: 'Reputation' }
                }
            }
        }
    };

    ChartUtils.applyFinancialChartStyle(config);

    return ChartUtils.createChart(container, config);
}

function plotOpsChart(stats, period, container) {
    const labels = [];
    const raskData = [];
    const caskData = [];
    const satisfactionData = [];
    const loadFactorData = [];
    const onTimeData = [];
    const linkCountData = [];
    const epsData = [];
    const monthsCashOnHand = [];

    stats.forEach(stat => {
        labels.push(getGameDate(stat.cycle));
        epsData.push(stat.eps);
        raskData.push(stat.RASK * 100);
        caskData.push(stat.CASK * 100);
        satisfactionData.push(stat.satisfaction * 100);
        loadFactorData.push(stat.load_factor * 100);
        onTimeData.push(stat.on_time * 100);
        linkCountData.push(stat.link_count);
        monthsCashOnHand.push(stat.months_cash_on_hand);
    });

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                { label: "EPS", data: epsData, borderColor: getChartColor('eps'), backgroundColor: getChartColor('eps'), yAxisID: 'y' },
                { label: "RASK", data: raskData, borderColor: getChartColor('rask'), backgroundColor: getChartColor('rask'), yAxisID: 'y' },
                { label: "CASK", data: caskData, borderColor: getChartColor('cask'), backgroundColor: getChartColor('cask'), yAxisID: 'y' },
                { label: "Satisfaction", data: satisfactionData, borderColor: getChartColor('satisfaction'), backgroundColor: getChartColor('satisfaction'), yAxisID: 'y1' },
                { label: "Load Factor", data: loadFactorData, borderColor: getChartColor('loadfactor'), backgroundColor: getChartColor('loadfactor'), yAxisID: 'y1' },
                { label: "On Time", data: onTimeData, borderColor: getChartColor('ontime'), backgroundColor: getChartColor('ontime'), yAxisID: 'y1' },
                { label: "Link Count", data: linkCountData, borderColor: getChartColor('linkCountData'), backgroundColor: getChartColor('linkCountData'), yAxisID: 'y2' },
                { label: "Months of Cash", data: linkCountData, borderColor: getChartColor('monthsCashOnHand'), backgroundColor: getChartColor('monthsCashOnHand'), yAxisID: 'y3' },
            ]
        },
        options: {
            maintainAspectRatio: false,
            scales: {
                y: {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    title: { display: true, text: '$/Seat-km' }
                },
                y1: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: { display: false },
                    ticks: { callback: function (value) { return value.toFixed(0) + '%'; } },
                    grid: { drawOnChartArea: false }
                },
                y2: {
                    type: 'linear',
                    display: false,
                    position: 'right',
                    title: { display: false },
                    grid: { drawOnChartArea: false },
                    beginAtZero: true
                },
                y3: {
                    type: 'linear',
                    display: false,
                    position: 'right',
                    title: { display: false },
                    grid: { drawOnChartArea: false },
                    beginAtZero: true
                }
            }
        }
    };

    ChartUtils.applyFinancialChartStyle(config);

    return ChartUtils.createChart(container, config);
}

function plotAirlineStats(stats, period, container) {
    if (!stats || stats.length === 0) {
        const emptyConfig = { type: 'line', data: { labels: [], datasets: [] }, options: { scales: { y: { title: { display: true, text: 'Passengers' } } } } };
        return ChartUtils.createChart(container, emptyConfig);
    }
    // Build labels once and keep only requested props as numeric arrays
    const labels = stats.map(s => getGameDate(s.cycle));

    const keepProps = ['business', 'codeshares', 'elite', 'tourist', 'traveler', 'total'];

    const datasets = keepProps.map(prop => {
        const data = stats.map(s => Number(s[prop]) || 0);
        return {
            label: prettyLabel(prop),
            data: data,
            borderColor: getChartColor(prop, '#888888'),
            backgroundColor: getChartColor(prop, '#888888')
        };
    });

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: datasets
        },
        options: {
            scales: {
                y: {
                    title: { display: true, text: 'Passengers' }
                }
            }
        }
    };

    ChartUtils.applyFinancialChartStyle(config);

    return ChartUtils.createChart(container, config);
}

function plotOilPriceChart(oilPrices, container) {
    const labels = [];
    const data = [];
    let total = 0;

    Object.entries(oilPrices).forEach(([key, oilPrice]) => {
        labels.push(getGameDate(oilPrice.cycle));
        data.push(oilPrice.price);
        total += oilPrice.price;
    });

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Price',
                data: data,
                borderColor: '#36A2EB',
                pointStyle: false
            }]
        },
        options: {
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    title: { display: true, text: 'Oil Price Per Barrel' },
                    ticks: { callback: function (value) { return '$' + value; } }
                }
            }
        }
    };

    return ChartUtils.createChart(container, config);
}

function plotLoanInterestRatesChart(rates, container) {
    const labels = [];
    const data = [];
    let total = 0;

    Object.entries(rates).forEach(([key, rate]) => {
        const annualRate = rate.rate * 100;
        labels.push(getGameDate(rate.cycle));
        data.push(annualRate.toFixed(1));
        total += annualRate;
    });

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Rate',
                data: data,
                borderColor: '#36A2EB',
                pointStyle: false
            }]
        },
        options: {
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    title: { display: true, text: 'Base Annual Rate' },
                    ticks: { callback: function (value) { return value + '%'; } }
                }
            }
        }
    };

    return ChartUtils.createChart(container, config);
}

function plotRivalHistoryChart(allRivalLinkConsumptions, priceContainer, linkClass, field, numberPrefix, currentAirlineId) {
    const datasets = [];
    const labels = [];

    if (allRivalLinkConsumptions && Object.keys(allRivalLinkConsumptions).length > 0) {
        let weekCount = 0;
        Object.entries(allRivalLinkConsumptions).forEach(([key, linkConsumptions]) => {
            if (linkConsumptions.length > weekCount) {
                weekCount = linkConsumptions.length;
            }
        });

        let sourceCycles = null;
        Object.entries(allRivalLinkConsumptions).forEach(([key, linkConsumptions]) => {
            if (!linkConsumptions || linkConsumptions.length === 0) return;
            if (!sourceCycles || linkConsumptions.length > sourceCycles.length) {
                sourceCycles = linkConsumptions;
            }
        });

        if (sourceCycles) {
            // use reversed order to match the rest of the logic
            const reversed = Array.from(sourceCycles).reverse();
            reversed.forEach(item => {
                // prefer formatted date if getGameDate exists
                try {
                    labels.push(typeof getGameDate === 'function' ? getGameDate(item.cycle).toString() : item.cycle.toString());
                } catch (e) {
                    labels.push(item.cycle.toString());
                }
            });
        } else {
            for (let i = 0; i < weekCount; i++) {
                labels.push((i + 1));
            }
        }

        Object.entries(allRivalLinkConsumptions).forEach(([key, linkConsumptions]) => {
            if (linkConsumptions.length === 0) {
                return;
            }
            const airlineName = linkConsumptions[0].airlineName;
            const priceHistory = [];
            const lineColor = linkConsumptions[0].airlineId == currentAirlineId ? "#d84f4f" : "#f6bf1b";

            const reversedConsumptions = Array.from(linkConsumptions).reverse();
            for (let i = 0; i < weekCount; i++) {
                if (i < reversedConsumptions.length) {
                    priceHistory.push(reversedConsumptions[i][field][linkClass]);
                } else {
                    priceHistory.push(null);
                }
            }

            datasets.push({ label: airlineName, data: priceHistory, borderColor: lineColor, pointStyle: false });
        });
    }

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: datasets
        },
        options: {
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    grid: {
                        color: 'rgba(54, 54, 54, 0.7)',
                        lineWidth: 1
                    },
                    type: 'logarithmic',
                    title: { display: true },
                    ticks: { maxTicksLimit: 6, callback: function (value) { return numberPrefix + value; } }
                },
                x: {
                    grid: {
                        color: 'rgba(54, 54, 54, 0.7)',
                        lineWidth: 1
                    },
                }
            }
        }
    };

    return ChartUtils.createChart(priceContainer, config);
}

function plotLoyalistHistoryChart(loyalistHistory, container) {
    if (!loyalistHistory || Object.keys(loyalistHistory).length === 0) {
        return;
    }

    const labels = [];
    const dataByAirlineId = {};
    const airlineNameByAirlineId = {};

    Object.entries(loyalistHistory).forEach(([index, keyValue]) => {
        const cycle = keyValue[0];
        const cycleEntries = keyValue[1];
        labels.push(cycle.toString());
        cycleEntries.forEach(entry => {
            const airlineId = entry.airlineId;
            if (!dataByAirlineId[airlineId]) {
                dataByAirlineId[airlineId] = [];
            }
            dataByAirlineId[airlineId].push(entry.amount);
            airlineNameByAirlineId[airlineId] = entry.airlineName;
        });
    });

    const datasets = [];
    Object.entries(airlineNameByAirlineId).forEach(([airlineId, airlineName]) => {
        datasets.push({ label: airlineName, data: dataByAirlineId[airlineId], fill: false });
    });

    const config = {
        type: 'line',
        data: {
            labels: labels,
            datasets: datasets
        },
        options: {
            maintainAspectRatio: false,
            scales: {
                y: {
                    title: { display: true, text: 'Loyalist Amount' }
                }
            }
        }
    };

    return ChartUtils.createChart(container, config);
}



// Helper function for displaying every nth label
function showEveryNthLabel(rawData, displayInterval) {
    return rawData.map((item, index) => {
        const dataPoint = { value: item.value };
        if ((index + 1) % displayInterval === 0) {
            dataPoint.showValue = "1";
        }
        return dataPoint;
    });
}

// Utility function to destroy charts when hiding views
function destroyChartsInContainer(containerId) {
    const container = document.getElementById(containerId);
    if (container) {
        const chartContainers = container.querySelectorAll('[id*="chart"], .chart-container');
        chartContainers.forEach(chartContainer => {
            ChartUtils.destroyChart(chartContainer);
        });
    }
}

// Export for external access
window.ChartUtils = ChartUtils;
window.destroyChartsInContainer = destroyChartsInContainer;