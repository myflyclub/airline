const AircraftConfig = (() => {
    // -------------------------------------------------------------------------
    // Private state
    // -------------------------------------------------------------------------
    let _configStore;   // first config used as template for addNew
    let _currentModel;  // current model object for addNew

    // -------------------------------------------------------------------------
    // SVG icons (inlined from AircraftConfig.svelte)
    // -------------------------------------------------------------------------
    const SVG_STAR = `<svg fill="#debe1b" height="200px" width="200px" version="1.1" xmlns="http://www.w3.org/2000/svg" viewBox="-0.96 -0.96 25.92 25.92" enable-background="new 0 0 24 24" xml:space="preserve" stroke="#debe1b"><g stroke-width="0"></g><g stroke-linecap="round" stroke-linejoin="round"></g><g><g><g><polygon points="12,0 16,8 24,8 18,14.32 20.42,24 12,18.9 3.58,24 6,14.32 0,8 8,8 "></polygon></g></g></g></svg>`;
    const SVG_CHECK = `<svg viewBox="0 -1.5 12 12" fill="none" xmlns="http://www.w3.org/2000/svg"><g stroke-width="0"></g><g stroke-linecap="round" stroke-linejoin="round"></g><g><path fill-rule="evenodd" clip-rule="evenodd" d="M1.70711 4.2929C1.31658 3.9024 0.68342 3.9024 0.29289 4.2929C-0.09763 4.6834 -0.09763 5.3166 0.29289 5.7071L3.29289 8.7071C3.68342 9.0976 4.3166 9.0976 4.7071 8.7071L11.7071 1.70711C12.0976 1.31658 12.0976 0.68342 11.7071 0.29289C11.3166 -0.09763 10.6834 -0.09763 10.2929 0.29289L4 6.5858L1.70711 4.2929z" fill="#24cc38"></path></g></svg>`;
    const SVG_RESET = `<svg viewBox="0 0 24.00 24.00" fill="none" xmlns="http://www.w3.org/2000/svg"><g stroke-width="0"></g><g stroke-linecap="round" stroke-linejoin="round"></g><g><path d="M3 12C3 16.9706 7.02944 21 12 21C14.3051 21 16.4077 20.1334 18 18.7083L21 16M21 12C21 7.02944 16.9706 3 12 3C9.69494 3 7.59227 3.86656 6 5.29168L3 8M21 21V16M21 16H16M3 3V8M3 8H8" stroke="#558fce" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"></path></g></svg>`;
    const SVG_DELETE = `<svg fill="#d20404" height="200px" width="200px" version="1.1" xmlns="http://www.w3.org/2000/svg" viewBox="-4.32 -4.32 56.61 56.61" xml:space="preserve" stroke="#d20404"><g stroke-width="0"></g><g stroke-linecap="round" stroke-linejoin="round"></g><g><g><path d="M28.228,23.986L47.092,5.122c1.172-1.171,1.172-3.071,0-4.242c-1.172-1.172-3.07-1.172-4.242,0L23.986,19.744L5.121,0.88 c-1.172-1.172-3.07-1.172-4.242,0c-1.172,1.171-1.172,3.071,0,4.242l18.865,18.864L0.879,42.85c-1.172,1.171-1.172,3.071,0,4.242 C1.465,47.677,2.233,47.97,3,47.97s1.535-0.293,2.121-0.879l18.865-18.864L42.85,47.091c0.586,0.586,1.354,0.879,2.121,0.879 s1.535-0.293,2.121-0.879c1.172-1.171,1.172-3.071,0-4.242L28.228,23.986z"></path></g></g></svg>`;

    // -------------------------------------------------------------------------
    // Layout / diagram helpers (ported from AircraftConfig.svelte)
    // -------------------------------------------------------------------------

    function _maxSeatsPerRow(maxCapacity) {
        if (maxCapacity <= 60)  return 4;
        if (maxCapacity <= 90)  return 5;
        if (maxCapacity >= 660) return 10;
        if (maxCapacity >= 410) return 9;
        if (maxCapacity >= 260) return 7;
        return 6;
    }

    function _calcDimensions(maxCapacity) {
        const maxSeatsPerRow = _maxSeatsPerRow(maxCapacity);
        const width = (maxCapacity / maxSeatsPerRow) * (3 + 4) + 30;
        const isWideBody = maxCapacity >= 245;
        const sections = isWideBody ? 3 : 2;
        const seatHeight = 8;
        const aisleHeights = (sections - 1) * 7;
        const height = maxSeatsPerRow * seatHeight + aisleHeights;
        const sectionHeight = Math.round(maxSeatsPerRow / sections) * seatHeight;
        const sectionMiddleHeight = isWideBody
            ? (maxSeatsPerRow - 2 * Math.round(maxSeatsPerRow / sections)) * seatHeight
            : 0;
        return { width, height, isWideBody, sections, sectionHeight, sectionMiddleHeight, maxSeatsPerRow };
    }

    function _distributeSeats(config, dim) {
        const { sections, isWideBody, sectionHeight, sectionMiddleHeight } = dim;
        const seatClasses = ['first', 'business', 'economy'];
        const view = Array.from({ length: sections }, () => ({ first: 0, business: 0, economy: 0 }));

        let middleBonusPercentage = 0;
        if (sectionMiddleHeight > 0) {
            middleBonusPercentage = 1 - sectionHeight / sectionMiddleHeight;
        }

        let currentSection = 0;
        seatClasses.forEach(seatClass => {
            const seatsToDistribute = config[seatClass] || 0;
            const isFirstAssignment = seatsToDistribute >= 4 ? 4 : 0;
            const middleSeats = isWideBody
                ? Math.floor(seatsToDistribute / sections * middleBonusPercentage) + isFirstAssignment
                : 0;
            const basePerSection = Math.floor((seatsToDistribute - middleSeats) / sections);

            for (let i = 0; i < sections; i++) {
                view[i][seatClass] = basePerSection;
                if (isWideBody && i === 2) {
                    view[i][seatClass] += middleSeats;
                }
            }

            let remainingSeats = (seatsToDistribute - middleSeats) % sections;
            while (remainingSeats > 0) {
                view[currentSection][seatClass]++;
                remainingSeats--;
                currentSection = (currentSection + 2) % sections;
            }
        });

        return view;
    }

    function _getValidation(config) {
        if (config.maxCapacity == null) throw new Error(`AircraftConfig: config.maxCapacity missing on config ${config.id}`);
        const { economy, business, first, maxCapacity, maxSeats } = config;
        const totalSeats    = economy + business + first;
        const totalCapacity = economy + business * 2.5 + first * 6;
        const minSeats = gameConstants.aircraft.minSeatsPerClass;
        const minViolations = ['economy', 'business', 'first'].filter(cls => {
            const count = config[cls];
            return count > 0 && count < minSeats;
        });
        const minSeatsError = minViolations.length > 0
            ? `${minViolations.map(c => c.charAt(0).toUpperCase() + c.slice(1)).join(', ')} must have at least ${minSeats} seats`
            : '';
        const isValid = totalCapacity <= maxCapacity && totalSeats <= maxSeats && minViolations.length === 0;
        const seatsColor    = totalSeats    > maxSeats    ? 'crimson' : 'inherit';
        const capacityColor = totalCapacity > maxCapacity ? 'crimson' : 'inherit';
        const borderColor = !isValid
            ? 'crimson'
            : (totalCapacity === maxCapacity || totalSeats === maxSeats)
                ? 'forestgreen'
                : 'inherit';
        return { isValid, totalCapacity, totalSeats, capacityColor, seatsColor, borderColor, minSeatsError };
    }

    // -------------------------------------------------------------------------
    // DOM rendering helpers
    // -------------------------------------------------------------------------

    function _renderSeatsHtml(count, cls) {
        return `<div class="seat ${cls}"></div>`.repeat(count);
    }

    function _renderSeatGroupHtml(sectionData, dim, position) {
        const height = position === 'center' ? dim.sectionMiddleHeight : dim.sectionHeight;
        let businessHtml = '';
        if (sectionData.business > 0) {
            const rowsInSection = height > 0 ? Math.floor(height / 11) : 1;
            const bWidth = Math.ceil(sectionData.business / Math.max(rowsInSection, 1)) * 10;
            businessHtml = `<div class="group-business" style="width:${bWidth}px">${_renderSeatsHtml(sectionData.business, 'business')}</div>`;
        }
        return `<div class="seat-group ${position}" style="height:${height}px">`
            + _renderSeatsHtml(sectionData.first, 'first')
            + businessHtml
            + _renderSeatsHtml(sectionData.economy, 'economy')
            + `</div>`;
    }

    function _renderDiagramHtml(config, validation, dim, view) {
        const borderStyle = `border-color:${validation.borderColor};border-width:${validation.isValid ? '1px' : '2px'};`;
        let inner;
        if (dim.isWideBody) {
            inner = _renderSeatGroupHtml(view[0], dim, 'top')
                + '<div class="aisle"></div>'
                + _renderSeatGroupHtml(view[2], dim, 'center')
                + '<div class="aisle"></div>'
                + _renderSeatGroupHtml(view[1], dim, 'bottom');
        } else {
            inner = _renderSeatGroupHtml(view[0], dim, 'top')
                + '<div class="aisle"></div>'
                + _renderSeatGroupHtml(view[1], dim, 'bottom');
        }
        return `<div class="planeDiagram" style="width:${dim.width}px;height:${dim.height}px;${borderStyle}">${inner}</div>`;
    }

    function _buildPanelHtml(config) {
        const validation = _getValidation(config);
        const dim  = _calcDimensions(config.maxCapacity);
        const view = _distributeSeats(config, dim);

        const starDisabled   = (config.isDefault || !validation.isValid) ? 'disabled' : '';
        const saveDisabled   = !validation.isValid ? 'disabled' : '';
        const deleteDisabled = config.isDefault ? 'disabled' : '';

        const buttonsHtml = `<div>
            <button class="svg-btn ac-btn-star" ${starDisabled}
                style="filter:${config.isDefault ? '' : 'grayscale(100%)'};cursor:${config.isDefault ? 'auto' : 'pointer'}"
            >${SVG_STAR}</button>
            <button class="svg-btn ac-btn-save" ${saveDisabled}
                style="filter:${validation.isValid ? '' : 'grayscale(100%)'};cursor:${validation.isValid ? 'pointer' : 'auto'}"
            >${SVG_CHECK}</button>
            <button class="svg-btn ac-btn-reset">${SVG_RESET}</button>
            <button class="svg-btn ac-btn-delete" ${deleteDisabled}
                style="filter:${config.isDefault ? 'grayscale(100%)' : ''};cursor:${config.isDefault ? 'auto' : 'pointer'}"
            >${SVG_DELETE}</button>
        </div>`;

        const inputsHtml = `<div class="inputs">
            <div><label>First:</label><input type="number" class="ac-input" data-class="first" min="0" value="${config.first}"><a class="config-btn-max img-button" data-class="first" aria-label="Increase first seats to max">➞</a></div>
            <div><label>Business:</label><input type="number" class="ac-input" data-class="business" min="0" value="${config.business}"><a class="config-btn-max img-button" data-class="business" aria-label="Increase business seats to max">➞</a></div>
            <div><label>Economy:</label><input type="number" class="ac-input" data-class="economy" min="0" value="${config.economy}"><a class="config-btn-max img-button" data-class="economy" aria-label="Increase economy seats to max">➞</a></div>
        </div>`;

        const statsHtml = `<p class="ac-stats" style="margin:2px 0;text-align:center;">using <span class="ac-capacity-stat" style="color:${validation.capacityColor}">${validation.totalCapacity} of ${config.maxCapacity} capacity</span></p>`
            + `<p class="ac-min-seats-error" style="margin:2px 0;text-align:center;color:crimson;${validation.minSeatsError ? '' : 'display:none;'}">${validation.minSeatsError}</p>`;

        const diagramHtml = _renderDiagramHtml(config, validation, dim, view);

        return `<div class="aircraft-config-view" style="display:flex;align-items:center;">
            <div style="margin-right:16px;">${buttonsHtml}${inputsHtml}</div>
            <div style="margin:auto;">${statsHtml}<div class="ac-diagram">${diagramHtml}</div></div>
        </div>`;
    }

    function _updatePanel($panel, config) {
        const { isValid, totalCapacity, totalSeats, capacityColor, seatsColor, borderColor, minSeatsError } = _getValidation(config);

        $panel.find('.ac-capacity-stat')
            .css('color', capacityColor)
            .text(totalCapacity + ' of ' + config.maxCapacity + ' capacity');
        $panel.find('.ac-seat-stat')
            .css('color', seatsColor)
            .text(totalSeats + ' of ' + config.maxSeats + ' seats');

        if (minSeatsError) {
            $panel.find('.ac-min-seats-error').text(minSeatsError).show();
        } else {
            $panel.find('.ac-min-seats-error').hide();
        }

        if (isValid) {
            const dim  = _calcDimensions(config.maxCapacity);
            const view = _distributeSeats(config, dim);
            const validation = { isValid, totalCapacity, totalSeats, capacityColor, seatsColor, borderColor };
            $panel.find('.ac-diagram').html(_renderDiagramHtml(config, validation, dim, view));
        } else {
            $panel.find('.planeDiagram').css({ borderColor: borderColor, borderWidth: '2px' });
        }

        const starDisabled = config.isDefault || !isValid;
        $panel.find('.ac-btn-star')
            .prop('disabled', starDisabled)
            .css({ filter: config.isDefault ? '' : 'grayscale(100%)', cursor: config.isDefault ? 'auto' : 'pointer' });
        $panel.find('.ac-btn-save')
            .prop('disabled', !isValid)
            .css({ filter: isValid ? '' : 'grayscale(100%)', cursor: isValid ? 'pointer' : 'auto' });
    }

    function _bindEvents($configDiv, config) {
        const $panel = $configDiv.find('.aircraft-config-view');

        // Mutable local state — starts with the server-returned values
        const state = { economy: config.economy, business: config.business, first: config.first };

        function getCurrentConfig() {
            return Object.assign({}, config, state);
        }

        // Live-update diagram as user types
        $panel.find('.ac-input').on('input', function() {
            const val = parseInt($(this).val());
            state[$(this).data('class')] = isNaN(val) ? 0 : val;
            _updatePanel($panel, getCurrentConfig());
        });

        // Reset to original saved values
        $panel.find('.ac-btn-reset').on('click', function() {
            state.economy  = config.original.economy;
            state.business = config.original.business;
            state.first    = config.original.first;
            $panel.find('.ac-input[data-class="economy"]').val(state.economy);
            $panel.find('.ac-input[data-class="business"]').val(state.business);
            $panel.find('.ac-input[data-class="first"]').val(state.first);
            _updatePanel($panel, getCurrentConfig());
        });

        // Save (check mark)
        $panel.find('.ac-btn-save').on('click', function() {
            if ($(this).prop('disabled')) return;
            promptConfirm(
                `Do you want to change this configuration? This will affect ${config.airplaneCount} airplane(s)`,
                save,
                getCurrentConfig()
            );
        });

        // Set as default (star)
        $panel.find('.ac-btn-star').on('click', function() {
            if ($(this).prop('disabled')) return;
            promptConfirm(
                `Do you want to save and set this configuration as default?`,
                setDefault,
                getCurrentConfig()
            );
        });

        // Fill remaining capacity into one class
        $panel.find('.config-btn-max').on('click', function() {
            const cls = $(this).data('class');
            const cur = getCurrentConfig();
            const usedCapacity = cur.economy * 1 + cur.business * 2.5 + cur.first * 6;
            const remainingCapacity = cur.maxCapacity - usedCapacity;
            const remainingSeats = cur.maxSeats - (cur.economy + cur.business + cur.first);
            const lcEntry = gameConstants.linkClassValues.find(v => v.name === cls);
            const seatSize = lcEntry ? lcEntry.spaceMultiplier : 1;
            const additional = Math.min(Math.floor(remainingCapacity / seatSize), remainingSeats);
            if (additional > 0) {
                state[cls] += additional;
                $panel.find(`.ac-input[data-class="${cls}"]`).val(state[cls]);
                _updatePanel($panel, getCurrentConfig());
            }
        });

        // Delete
        $panel.find('.ac-btn-delete').on('click', function() {
            if ($(this).prop('disabled')) return;
            promptConfirm(
                `Do you want to delete this configuration? ${config.airplaneCount} airplane(s) with this configuration will be switched to default configuration`,
                deleteConfig,
                config
            );
        });
    }

    function _addAirplaneInventory($configDiv, modelId) {
        var $airplanesDiv = $("<div style='max-height: 60px; min-height: 20px; overflow: auto; box-shadow: inset 0 0 2px #ffffff82, 0 0 3px rgba(0, 0, 0, 0.2); padding: 3px 6px; border-radius: 12px; justify-content: center; display: flex; flex-wrap: wrap;'></div>");
        var configurationId = $configDiv.data("configuration").id;
        var info = loadedModelsById[modelId];
        if (!info.isFullLoad) {
            loadAirplaneModelOwnerInfoByModelId(modelId);
        }

        var allAirplanes = $.merge($.merge($.merge([], info.assignedAirplanes), info.availableAirplanes), info.constructingAirplanes);
        $.each(allAirplanes, function(key, airplane) {
            if (airplane.configurationId == configurationId) {
                var airplaneId = airplane.id;
                var li = $("<div class='clickable' onclick='loadOwnedAirplaneDetails(" + airplaneId + ", $(this), AircraftConfig.refresh)'></div>").appendTo($airplanesDiv);
                var airplaneIcon = getAirplaneIcon(airplane, gameConstants.aircraft.conditionBad);
                enableAirplaneIconDrag(airplaneIcon, airplaneId);
                enableAirplaneIconDrop(airplaneIcon, airplaneId, "AircraftConfig.refresh");
                li.append(airplaneIcon);
            }
        });

        $configDiv.append($airplanesDiv);
    }

    function _renderModal(info) {
        var model = info.model;
        _configStore  = info.configurations[0];
        _currentModel = model;

        $("#modelConfigurationModal .configContainer").empty();
        $("#modelConfigurationModal .modelName").text(model.name);
        $("#modelConfigurationModal .modelCapacity").text(model.capacity);

        info.configurations.forEach(function(configuration) {
            var $configDiv = $(`<div style='min-height:130px;' class='config' id='config-${configuration.id}'></div>`);
            $configDiv.data("configuration", configuration);

            _addAirplaneInventory($configDiv, model.id);
            $configDiv.attr("ondragover", "allowAirplaneIconDragOver(event)");
            $configDiv.attr("ondrop", "AircraftConfig.handleDrop(event, " + configuration.id + ")");

            // Merge configuration + model properties; keep config id and add explicit model ref.
            // The API's Model JSON has "capacity" (space units) but no "maxCapacity"/"maxSeats" fields,
            // so we map them explicitly here.
            var mergedConfig = Object.assign({}, configuration, model);
            mergedConfig.id          = configuration.id;
            mergedConfig.model       = model;
            mergedConfig.maxCapacity = model.capacity;   // total space units (economy=1, business=2.5, first=6)
            mergedConfig.maxSeats    = model.capacity;   // max seats if all-economy (1 space/seat)
            mergedConfig.original    = { economy: configuration.economy, business: configuration.business, first: configuration.first };
            mergedConfig.airplaneCount = getAssignedAirplanesCount("configurationId", configuration.id, model.id);

            $configDiv.append(_buildPanelHtml(mergedConfig));
            _bindEvents($configDiv, mergedConfig);

            $("#modelConfigurationModal .configContainer").append($configDiv);
        });

        // Pad remaining slots with "Add New" buttons
        var emptyCount = info.maxConfigurationCount - info.configurations.length;
        for (var i = 0; i < emptyCount; i++) {
            var isDefault = info.configurations.length === 0;
            var $emptyDiv = $("<div style='width:98%;min-height:130px;position:relative;' class='config'></div>");
            var $promptDiv = $(`<div style='position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);'>
                <button class='button' onclick='AircraftConfig.addNew(${isDefault})'>
                    <img class='svg svg-monochrome svg-hover-green' src='/assets/images/icons/plus.svg' title='Add new configuration'>
                    <div style='float:right'><h3 class='pl-2'>Add New Configuration</h3></div>
                </button>
            </div>`);
            $emptyDiv.append($promptDiv);
            $("#modelConfigurationModal .configContainer").append($emptyDiv);
        }

        toggleUtilizationRate($("#modelConfigurationModal"), $("#modelConfigurationModal .toggleUtilizationRateBox"));
        toggleCondition($("#modelConfigurationModal"), $("#modelConfigurationModal .toggleConditionBox"));

        $('#modelConfigurationModal').fadeIn(200);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    function show(modelId) {
        var airlineId = activeAirline.id;
        $.ajax({
            type: 'GET',
            url: "/airlines/" + airlineId + "/configurations?modelId=" + modelId,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                _renderModal(result);
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }

    function showFromPlanLink(modelId) {
        show(modelId);
        $('#modelConfigurationModal').data('closeCallback', function() {
            planLink($("#planLinkFromAirportId").val(), $("#planLinkToAirportId").val(), true);
        });
    }

    function refresh() {
        loadAirplaneModelOwnerInfoByModelId(selectedModelId);
        show(selectedModelId);
    }

    function save(config) {
        var airlineId = activeAirline.id;
        $.ajax({
            type: 'PUT',
            url: "/airlines/" + airlineId + "/configurations?modelId=" + config.model.id
                + "&configurationId=" + config.id
                + "&economy="         + config.economy
                + "&business="        + config.business
                + "&first="           + config.first
                + "&isDefault="       + config.isDefault,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function() {
                show(config.model.id);
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }

    function setDefault(config) {
        config.isDefault = true;
        save(config);
    }

    function deleteConfig(config) {
        var airlineId = activeAirline.id;
        $.ajax({
            type: 'DELETE',
            url: "/airlines/" + airlineId + "/configurations/" + config.id,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function() {
                refresh();
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }

    function addNew(isDefault) {
        if (!_configStore || !_currentModel) return;
        var configuration = {
            id:       0,
            model:    _currentModel,
            economy:  _configStore.economy,
            business: _configStore.business,
            first:    _configStore.first,
            isDefault: isDefault
        };
        save(configuration);
    }

    function handleDrop(event, configurationId) {
        event.preventDefault();
        var airplaneId = event.dataTransfer.getData("airplane-id");
        if (airplaneId) {
            $.ajax({
                type: 'PUT',
                url: "/airlines/" + activeAirline.id + "/airplanes/" + airplaneId + "/configuration/" + configurationId,
                contentType: 'application/json; charset=utf-8',
                dataType: 'json',
                async: false,
                success: function() {
                    refresh();
                },
                error: function(jqXHR, textStatus, errorThrown) {
                    console.log(JSON.stringify(jqXHR));
                    console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
                }
            });
        }
    }

    return {
        show,
        showFromPlanLink,
        refresh,
        save,
        setDefault,
        delete:    deleteConfig,
        addNew,
        handleDrop,
    };
})();

window.AircraftConfig = AircraftConfig;
