const Alliance = (() => {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    let alliancesData = null;       // from /alliances-data (historical stats for chart)
    let loadedAlliancesById = {};   // from /alliances (full member/stats data)
    let period = 'WEEKLY';
    let metric = 'totalAirportRep';
    let hiddenAlliances = {};
    let selectedAllianceId = null;
    let _logoBust = '';             // cache-bust suffix appended after logo uploads


    // =========================================================================
    // SECTION: Entry Point
    // =========================================================================

    function show(allianceId) {
        checkPendingActions();
        setActiveDiv($('#allianceCanvas'));

        _bindDelegation();
        _bindSidebarButtons();

        loadData(allianceId);
    }

    function _bindDelegation() {
        const ticker = document.getElementById('allianceTicker');
        if (!ticker || ticker.dataset.delegationSet) return;
        ticker.addEventListener('click', function(e) {
            const row = e.target.closest('.table-row[data-alliance-id]');
            if (!row) return;
            const id = parseInt(row.dataset.allianceId, 10);
            if (e.target.closest('.js-toggle-alliance')) {
                e.stopPropagation();
                toggleAlliance(id);
            } else {
                showSidebar(id);
            }
        });
        ticker.dataset.delegationSet = 'true';
    }

    function _bindSidebarButtons() {
        const sidebar = document.getElementById('allianceSidebar');
        if (!sidebar || sidebar.dataset.buttonsBound) return;

        document.getElementById('resetAllianceLabelColorBtn').addEventListener('click', () => {
            if (selectedAllianceId) checkResetAllianceLabelColor(selectedAllianceId);
        });
        document.getElementById('uploadAllianceLogoBtn').addEventListener('click', () => _showUploadLogoAlliance());
        document.getElementById('editAllianceLogoBtn').addEventListener('click', () => _editAllianceLogo());
        document.getElementById('allianceFlightMapBtn').addEventListener('click', () => showAllianceMap());
        document.getElementById('applyForAllianceButton').addEventListener('click', () => _applyForAlliance());
        document.getElementById('toggleFormAllianceButton').addEventListener('click', () => toggleFormAlliance());
        document.getElementById('formAllianceSubmitButton').addEventListener('click', () => {
            formAlliance(document.getElementById('formAllianceName').value);
        });

        document.getElementById('allianceChampionAirportList').addEventListener('click', e => {
            const row = e.target.closest('.table-row[data-airport-id]');
            if (row) showAirportDetails(row.dataset.airportId);
        });
        document.getElementById('allianceChampionCountryList').addEventListener('click', e => {
            const row = e.target.closest('.table-row[data-country-code]');
            if (row) navigateTo(`/country/${row.dataset.countryCode}`);
        });

        document.getElementById('allianceSloganInput').addEventListener('input', function() {
            _updateSloganCharCount(this);
        });
        document.getElementById('saveSloganBtn').addEventListener('click', () => {
            if (selectedAllianceId) _saveAllianceSlogan(selectedAllianceId);
        });

        sidebar.dataset.buttonsBound = 'true';
    }


    // =========================================================================
    // SECTION: Data Loading
    // =========================================================================

    function prefetch() {
        if (Object.keys(loadedAlliancesById).length) return;
        fetch('/alliances').then(r => r.json()).then(alliances => {
            loadedAlliancesById = {};
            alliances.forEach(alliance => {
                loadedAlliancesById[alliance.id] = alliance;
                alliance.memberCount = alliance.members.filter(m => m.allianceRole !== 'Applicant').length;
            });
            resetVisibilityToTop10();
        }).catch(err => console.error('Alliance prefetch failed:', err));

        fetch('/alliances-data').then(r => r.json()).then(data => {
            alliancesData = data;
        }).catch(err => console.error('Alliance chart data prefetch failed:', err));
    }

    function loadData(allianceIdToSelect) {
        $('body .loadingSpinner').show();

        const _render = () => {
            resetVisibilityToTop10();
            renderTicker();

            const defaultId = allianceIdToSelect ||
                (activeAirline && activeAirline.allianceId ? activeAirline.allianceId : null);
            showSidebar(defaultId);

            $('body .loadingSpinner').hide();

            // /alliances-data: lazy — chart historical data, non-blocking
            if (alliancesData) {
                renderChart();
            } else {
                fetch('/alliances-data').then(r => r.json()).then(data => {
                    alliancesData = data;
                    renderChart();
                }).catch(err => console.error('Failed to load alliance chart data:', err));
            }
        };

        if (Object.keys(loadedAlliancesById).length) {
            _render();
            return;
        }

        // /alliances: fast — renders ticker + sidebar immediately
        fetch('/alliances').then(r => r.json()).then(alliances => {
            loadedAlliancesById = {};
            alliances.forEach(alliance => {
                loadedAlliancesById[alliance.id] = alliance;
                alliance.memberCount = alliance.members.filter(m => m.allianceRole !== 'Applicant').length;
            });
            _render();
        }).catch(err => {
            console.error('Failed to load alliances:', err);
            $('body .loadingSpinner').hide();
        });
    }


    // =========================================================================
    // SECTION: Sidebar
    // =========================================================================

    function showSidebar(allianceId) {
        selectedAllianceId = allianceId;
        window.selectedAlliance = loadedAlliancesById[allianceId];

        const sidebar = document.getElementById('allianceSidebar');
        sidebar.style.display = '';

        if (!allianceId || !loadedAlliancesById[allianceId]) {
            document.getElementById('allianceSidebarContent').style.display = 'none';
            document.getElementById('allianceSidebarEmpty').style.display = '';

            const formBtn = document.getElementById('toggleFormAllianceButton');
            formBtn.style.display =
                (activeAirline && !activeAirline.allianceId && activeAirline.headquarterAirport) ? '' : 'none';
            return;
        }

        document.getElementById('allianceSidebarEmpty').style.display = 'none';
        document.getElementById('allianceSidebarContent').style.display = '';

        _updateSidebarBasics(allianceId);
        _updateAllianceBonus(allianceId);
        _updateAllianceChampions(allianceId);
        _updateAllianceHistory(allianceId);
        _updateAllianceTagColor(allianceId);
        _showCurrentAirlineHistory(allianceId);
    }

    function _statusDot(statusId) {
        // LoginStatus enum: 0=ONLINE, 1=ACTIVE_7_DAYS, 2=ACTIVE_30_DAYS, 3=INACTIVE
        const configs = [
            { color: '#4caf50', title: 'Online' },
            { color: '#2196f3', title: 'Active this week' },
            { color: '#ff9800', title: 'Active this month' },
            { color: '#9e9e9e', title: 'Inactive' },
        ];
        const cfg = configs[statusId] !== undefined ? configs[statusId] : configs[3];
        return `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${cfg.color};vertical-align:middle;flex-shrink:0;" title="${cfg.title}"></span>`;
    }

    function _updateSidebarBasics(allianceId) {
        const alliance = loadedAlliancesById[allianceId];
        if (!alliance) return;

        const sidebar = document.getElementById('allianceSidebar');
        sidebar.querySelector('.allianceName').textContent = alliance.name;
        sidebar.querySelector('.allianceStatus').textContent =
            alliance.status === 'Forming' ? 'Forming — need 3 approved members' : alliance.status;

        const rankEl = sidebar.querySelector('.allianceRanking');
        rankEl.innerHTML = alliance.ranking ? getRankingImg(alliance.ranking) : '';

        // Role row (only if current user is a member)
        const roleRow = document.getElementById('sidebarRoleRow');
        if (activeAirline) {
            const myMember = (alliance.members || []).find(m => m.airlineId === activeAirline.id);
            if (myMember) {
                sidebar.querySelector('.allianceRole').textContent = myMember.allianceRole;
                roleRow.style.display = '';
            } else {
                roleRow.style.display = 'none';
            }
        } else {
            roleRow.style.display = 'none';
        }

        // Tag / logo section (logged-in only)
        const tagSection = document.getElementById('sidebarTagSection');
        tagSection.style.display = activeAirline ? '' : 'none';

        // Logo admin buttons (admin of THIS alliance only)
        const isAdmin = !!(activeAirline &&
            activeAirline.allianceId === allianceId &&
            activeAirline.isAllianceAdmin);
        document.getElementById('uploadAllianceLogoBtn').style.display = isAdmin ? '' : 'none';
        document.getElementById('editAllianceLogoBtn').style.display = isAdmin ? '' : 'none';

        // Logo image
        sidebar.querySelector('.allianceLogo').src = `/alliances/${allianceId}/logo${_logoBust}`;

        // Slogan section
        const sloganSection = document.getElementById('sidebarSloganSection');
        sloganSection.style.display = isAdmin ? '' : 'none';
        if (isAdmin) {
            const sloganInput = document.getElementById('allianceSloganInput');
            sloganInput.value = alliance.slogan || '';
            _updateSloganCharCount(sloganInput);
        }

        // Stats table
        const statsTable = document.getElementById('allianceStatsTable');
        statsTable.innerHTML = '';
        if (alliance.stats) {
            Object.entries(alliance.stats).forEach(([name, value]) => {
                statsTable.insertAdjacentHTML('beforeend',
                    `<div class="table-row">
                        <div class="cell label">${prettyLabel(name)}:</div>
                        <div class="cell value">${commaSeparateNumber(value)}</div>
                    </div>`);
            });
        }

        // Member list
        const memberList = document.getElementById('allianceMemberList');
        memberList.querySelectorAll('.table-row').forEach(r => r.remove());

        fetch(`/alliances/${allianceId}/member-login-status`)
            .then(r => r.json())
            .then(loginStatusByAirlineId => {
                alliance.members.forEach(member => {
                    const statusId = loginStatusByAirlineId[member.airlineId];
                    const dot = statusId !== undefined ? _statusDot(statusId) : '';
                    const roleClass = member.allianceRole === 'Applicant' ? 'warning' : '';

                    const row = document.createElement('div');
                    row.className = 'table-row clickable';
                    row.style.height = '20px';
                    row.dataset.airlineId = member.airlineId;
                    row.innerHTML = `
                        <div class="cell" style="vertical-align:middle; width:6%; padding-right:2px;">${dot}</div>
                        <div class="cell" style="vertical-align:middle;">${getAirlineSpan(member.airlineId, member.airlineName)}</div>
                        <div class="cell" style="vertical-align:middle; width:20%;">${member.airlineType}</div>
                        <div class="cell ${roleClass}" style="vertical-align:middle; width:20%;">${member.allianceRole}</div>
                        ${activeAirline ? '<div class="cell action" style="vertical-align:middle; width:14%;"></div>' : ''}
                    `;
                    row._memberData = member;
                    row.addEventListener('click', () =>  page(`/rivals/${member.airlineId}`));
                    memberList.appendChild(row);
                });

                // Apply / member action buttons
                if (activeAirline && selectedAllianceId) {
                    fetch(`/airlines/${activeAirline.id}/evaluate-alliance/${selectedAllianceId}`)
                        .then(r => r.json())
                        .then(result => {
                            const applyBtn        = document.getElementById('applyForAllianceButton');
                            const rejectionSpan   = document.getElementById('applyForAllianceRejectionSpan');
                            const rejectionTextEl = document.getElementById('applyForAllianceRejection');

                            if (!result.isMember && !result.rejection) {
                                applyBtn.style.display = '';
                                rejectionSpan.style.display = 'none';
                            } else {
                                applyBtn.style.display = 'none';
                                if (result.rejection) {
                                    rejectionTextEl.textContent = result.rejection;
                                    rejectionSpan.style.display = '';
                                } else {
                                    rejectionSpan.style.display = 'none';
                                }
                            }

                            (result.memberActions || []).forEach(entry => {
                                const actionCell = memberList.querySelector(
                                    `.table-row[data-airline-id="${entry.airlineId}"] .action`
                                );
                                if (!actionCell) return;

                                if (entry.acceptRejection) {
                                    actionCell.insertAdjacentHTML('beforeend',
                                        `<img src="/assets/images/icons/exclamation-circle.png" class="img-button disabled" title="Cannot accept: ${entry.acceptRejection}">`);
                                } else if (entry.acceptPrompt) {
                                    const icon = document.createElement('img');
                                    icon.src = '/assets/images/icons/tick.svg';
                                    icon.className = 'img-button';
                                    icon.title = 'Accept Member';
                                    icon.addEventListener('click', e => {
                                        e.stopPropagation();
                                        promptConfirm(entry.acceptPrompt, _acceptAllianceMember, entry.airlineId);
                                    });
                                    actionCell.appendChild(icon);
                                }
                                if (!entry.promoteRejection && entry.promotePrompt) {
                                    const icon = document.createElement('img');
                                    icon.src = '/assets/images/icons/user-promote.png';
                                    icon.className = 'img-button';
                                    icon.title = 'Promote Member';
                                    icon.addEventListener('click', e => {
                                        e.stopPropagation();
                                        promptConfirm(entry.promotePrompt, _promoteAllianceMember, entry.airlineId);
                                    });
                                    actionCell.appendChild(icon);
                                }
                                if (!entry.demoteRejection && entry.demotePrompt) {
                                    const icon = document.createElement('img');
                                    icon.src = '/assets/images/icons/user-demote.png';
                                    icon.className = 'img-button';
                                    icon.title = 'Demote Member';
                                    icon.addEventListener('click', e => {
                                        e.stopPropagation();
                                        promptConfirm(entry.demotePrompt, _demoteAllianceMember, entry.airlineId);
                                    });
                                    actionCell.appendChild(icon);
                                }
                                if (!entry.removeRejection && entry.removePrompt) {
                                    const icon = document.createElement('img');
                                    icon.src = '/assets/images/icons/cross.svg';
                                    icon.className = 'svg img-button';
                                    icon.title = 'Remove Member';
                                    icon.addEventListener('click', e => {
                                        e.stopPropagation();
                                        promptConfirm(entry.removePrompt, _removeAllianceMember, entry.airlineId);
                                    });
                                    actionCell.appendChild(icon);
                                }
                            });
                        })
                        .catch(err => console.error('Failed to evaluate alliance:', err));
                } else {
                    document.getElementById('applyForAllianceButton').style.display = 'none';
                    document.getElementById('applyForAllianceRejectionSpan').style.display = 'none';
                }
            })
            .catch(err => console.error('Failed to load member login status:', err));
    }

    function _showCurrentAirlineHistory(allianceId) {
        const section = document.getElementById('currentAirlineAllianceHistorySection');
        const isOwnAlliance = !!(activeAirline && activeAirline.allianceId === allianceId);
        section.style.display = isOwnAlliance ? '' : 'none';
        if (!isOwnAlliance) return;

        const historyEl = document.getElementById('currentAirlineAllianceHistory');
        historyEl.innerHTML = '';

        fetch(`/airlines/${activeAirline.id}/alliance-details`)
            .then(r => r.json())
            .then(details => {
                const entries = details.history || [];
                if (entries.length) {
                    entries.forEach(entry => {
                        historyEl.insertAdjacentHTML('beforeend',
                            `<div class="table-row">
                                <div class="cell value" style="width:30%;">Week ${entry.cycle}</div>
                                <div class="cell value" style="width:70%;">${entry.description}</div>
                            </div>`);
                    });
                } else {
                    historyEl.insertAdjacentHTML('beforeend',
                        `<div class="table-row"><div class="cell">No history yet.</div></div>`);
                }
            })
            .catch(err => console.error('Failed to load airline alliance history:', err));
    }


    // =========================================================================
    // SECTION: Visibility
    // =========================================================================

    function resetVisibilityToTop10() {
        const alliances = Object.values(loadedAlliancesById);
        if (!alliances.length) return;

        const sorted = [...alliances].sort((a, b) => {
            const av = (a.stats && a.stats[metric]) || 0;
            const bv = (b.stats && b.stats[metric]) || 0;
            return bv - av;
        });

        const top10 = new Set(sorted.slice(0, 10).map(a => a.id));
        if (activeAirline && activeAirline.allianceId) {
            top10.add(activeAirline.allianceId);
        }

        hiddenAlliances = {};
        alliances.forEach(a => { if (!top10.has(a.id)) hiddenAlliances[a.id] = true; });
    }


    // =========================================================================
    // SECTION: Dropdowns
    // =========================================================================

    function toggleDropdown(toggleBtn) {
        const $dropdown = $(toggleBtn).closest('.smDropdown');
        const wasOpen = $dropdown.hasClass('open');
        $('.smDropdown').removeClass('open');
        if (!wasOpen) $dropdown.addClass('open');
    }

    $(document).on('click.allianceDropdown', function(e) {
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
        resetVisibilityToTop10();
        renderChart();
        renderTicker();
    }


    // =========================================================================
    // SECTION: Chart
    // =========================================================================

    function _formatMetricValue(value) {
        if (metric === 'combinedMarketCap') return '$' + commaSeparateNumber(Math.round(value));
        if (metric === 'totalAirportRep')   return Number(value).toFixed(1);
        return commaSeparateNumber(Math.round(value));
    }

    function renderChart() {
        if (!alliancesData) return;

        const history = alliancesData.history;
        const datasets = [];

        Object.values(loadedAlliancesById).forEach(alliance => {
            if (hiddenAlliances[alliance.id]) return;

            const raw = (history[alliance.id] || []).filter(h => h.period === period);
            raw.sort((a, b) => a.cycle - b.cycle);
            if (raw.length === 0) return;

            const color = colorFromString(alliance.name);
            datasets.push({
                label: alliance.name,
                data: raw.map(h => ({ x: h.cycle, y: h[metric] })),
                borderColor: color,
                backgroundColor: color + '20',
                borderWidth: 1.5,
                tension: 0.3,
                pointRadius: 0,
                pointHoverRadius: 4,
                fill: false,
            });
        });

        const allCycles = {};
        datasets.forEach(ds => ds.data.forEach(pt => { allCycles[pt.x] = true; }));
        const labels = Object.keys(allCycles).map(Number).sort((a, b) => a - b);
        const isMonetary = metric === 'combinedMarketCap';

        const config = {
            type: 'line',
            data: {
                labels: labels.map(c => getGameDate(c)),
                datasets: datasets.map(ds => {
                    const cycleMap = {};
                    ds.data.forEach(pt => { cycleMap[pt.x] = pt.y; });
                    ds.data = labels.map(c => cycleMap[c] !== undefined ? cycleMap[c] : null);
                    return ds;
                }),
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                spanGaps: true,
                interaction: { mode: 'nearest', intersect: false },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: 'rgba(30,30,30,0.95)',
                        titleColor: '#fff',
                        bodyColor: '#ddd',
                        borderColor: 'rgba(100,100,100,0.5)',
                        borderWidth: 1,
                        callbacks: {
                            title: items => items.length ? items[0].label : '',
                            label: item => `${item.dataset.label}: ${_formatMetricValue(item.raw)}`,
                        },
                    },
                },
                scales: {
                    x: { grid: { color: 'rgba(54,54,54,0.3)' }, ticks: { maxTicksLimit: 12 } },
                    y: {
                        grid: { color: 'rgba(54,54,54,0.3)' },
                        beginAtZero: true,
                        ticks: {
                            callback: value => isMonetary ? '$' + commaSeparateNumber(value) : value,
                        },
                    },
                },
            },
        };

        ChartUtils.createChart('allianceChartContainer', config);
    }


    // =========================================================================
    // SECTION: Ticker Table
    // =========================================================================

    function toggleTickerSortOrder(sortHeader) {
        const $h = $(sortHeader);
        $h.data('sort-order', $h.data('sort-order') === 'ascending' ? 'descending' : 'ascending');
        $h.siblings().removeClass('selected');
        $h.addClass('selected');
        renderTicker($h.data('sort-property'), $h.data('sort-order'));
    }

    function renderTicker(sortProperty, sortOrder) {
        if (!Object.keys(loadedAlliancesById).length) return;

        const rows = Object.values(loadedAlliancesById).map(a => ({
            id:                a.id,
            name:              a.name,
            slogan:            a.slogan || '',
            championPoints:    a.championPoints || 0,
            totalAirportRep:   (a.stats && a.stats.totalAirportRep)   || 0,
            combinedMarketCap: (a.stats && a.stats.combinedMarketCap) || 0,
            elitePax:          (a.stats && a.stats.elitePax)          || 0,
            touristPax:        (a.stats && a.stats.touristPax)        || 0,
        }));

        if (!sortProperty) {
            const $sel = $('#allianceTicker .table-header .cell.selected');
            sortProperty = $sel.data('sort-property') || 'totalAirportRep';
            sortOrder    = $sel.data('sort-order')    || 'descending';
        }
        rows.sort(sortByProperty(sortProperty, sortOrder === 'ascending'));

        const $table = $('#allianceTicker');
        $table.find('.table-row').remove();

        const html = rows.map(entry => {
            const isHidden = !!hiddenAlliances[entry.id];
            const opacity  = isHidden ? '0.4' : '1';
            const color    = colorFromString(entry.name);
            const rep      = typeof entry.totalAirportRep === 'number'
                ? entry.totalAirportRep.toFixed(1) : entry.totalAirportRep;
            const sloganHtml = entry.slogan
                ? `<div class="p-0 mt-1 text-xxs">${entry.slogan}</div>`
                : '';
            return `<div class="table-row clickable" data-alliance-id="${entry.id}" style="opacity:${opacity};">
                <div class="cell js-toggle-alliance" style="width:3%; text-align:center;">
                    <span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:${color};vertical-align:middle;"></span>
                </div>
                <div class="cell js-toggle-alliance" style="width:3%;">
                    <input type="checkbox" ${isHidden ? '' : 'checked'} class="img-button" style="vertical-align:middle;">
                </div>
                <div class="cell" style="width:32%;">
                    <img src="/alliances/${entry.id}/logo${_logoBust}" style="height:16px;width:auto;max-width:32px;vertical-align:middle;margin-right:4px;" onerror="this.style.display='none'"${entry.slogan ? ` data-tooltip="${entry.slogan.replace(/"/g, '&quot;')}"` : ''}>
                    ${entry.name}
                    ${sloganHtml}
                </div>
                <div class="cell" style="width:9%;" align="right">${rep}</div>
                <div class="cell" style="width:9%;" align="right">${commaSeparateNumber(entry.championPoints)}</div>
                <div class="cell" style="width:20%;" align="right">$${commaSeparateNumber(entry.combinedMarketCap)}</div>
                <div class="cell" style="width:11%;" align="right">${commaSeparateNumber(entry.elitePax)}</div>
                <div class="cell" style="width:13%;" align="right">${commaSeparateNumber(entry.touristPax)}</div>
            </div>`;
        });
        $table.append(html.join(''));
    }

    function toggleAlliance(allianceId) {
        hiddenAlliances[allianceId] = !hiddenAlliances[allianceId];
        renderChart();
        renderTicker();
    }

    function toggleAll(checked) {
        Object.values(loadedAlliancesById).forEach(a => { hiddenAlliances[a.id] = !checked; });
        renderChart();
        renderTicker();
    }


    // =========================================================================
    // SECTION: Alliance Detail Updates
    // =========================================================================

    function _updateAllianceBonus(allianceId) {
        const alliance = loadedAlliancesById[allianceId];
        if (!alliance) return;

        if (alliance.status === 'Forming') {
            document.getElementById('allianceCodeShareBonus').style.display    = 'none';
            document.getElementById('allianceMaxFrequencyBonus').style.display = 'none';
            document.getElementById('allianceReputationBonus').style.display   = 'none';
            document.getElementById('allianceNoneBonus').style.display         = '';
        } else {
            document.getElementById('allianceNoneBonus').style.display         = 'none';
            document.getElementById('allianceCodeShareBonus').style.display    = '';
            if (alliance.reputationBonus) {
                document.getElementById('allianceReputationBonusValue').textContent = alliance.reputationBonus;
                document.getElementById('allianceReputationBonus').style.display    = '';
            } else {
                document.getElementById('allianceReputationBonus').style.display    = 'none';
            }
            document.getElementById('allianceMaxFrequencyBonus').style.display = 'none';
        }
    }

    function _updateAllianceChampions(allianceId) {
        _updateAllianceAirportChampions(allianceId);
        _updateAllianceCountryChampions(allianceId);
    }

    function _updateAllianceAirportChampions(allianceId) {
        const list = document.getElementById('allianceChampionAirportList');
        list.querySelectorAll('.table-row').forEach(r => r.remove());

        fetch(`/alliances/${allianceId}/championed-airports`)
            .then(r => r.json())
            .then(result => {
                const sidebar = document.getElementById('allianceSidebar');

                const renderChampion = (cd, isApplicant) => {
                    const repCell = isApplicant
                        ? `<div class="cell warning info svg" align="right">
                               <img src="/assets/images/icons/information.svg" title="Points not counted — airline is not an approved member yet">
                               ${cd.reputationBoost}
                           </div>`
                        : `<div class="cell" align="right">${cd.reputationBoost}</div>`;

                    list.insertAdjacentHTML('beforeend',
                        `<div class="table-row clickable" data-link="airport" data-airport-id="${cd.airportId}">
                            <div class="cell">${getRankingImg(cd.ranking)}</div>
                            <div class="cell">${getCountryFlagImg(cd.countryCode)}${cd.airportText}</div>
                            <div class="cell">${getAirlineLogoImg(cd.airlineId)}${cd.airlineName}</div>
                            <div class="cell" align="right">${commaSeparateNumber(cd.loyalistCount)}</div>
                            ${repCell}
                        </div>`);
                };

                (result.members || []).forEach(cd => renderChampion(cd, false));
                (result.applicants || []).forEach(cd => renderChampion(cd, true));

                if (!result.members.length && !result.applicants.length) {
                    list.insertAdjacentHTML('beforeend',
                        `<div class="table-row">
                            <div class="cell">-</div><div class="cell">-</div>
                            <div class="cell">-</div><div class="cell" align="right">-</div>
                            <div class="cell" align="right">-</div>
                        </div>`);
                }

                sidebar.querySelector('.totalReputation').textContent = result.totalReputation;
                sidebar.querySelector('.reputationTruncatedEntries').textContent = result.truncatedEntries;
            })
            .catch(err => console.error('Failed to load championed airports:', err));
    }

    function _updateAllianceCountryChampions(allianceId) {
        const list = document.getElementById('allianceChampionCountryList');
        list.querySelectorAll('.table-row').forEach(r => r.remove());

        fetch(`/alliances/${allianceId}/championed-countries`)
            .then(r => r.json())
            .then(countries => {
                if (countries.length === 0) {
                    list.insertAdjacentHTML('beforeend',
                        `<div class="table-row">
                            <div class="cell">-</div><div class="cell">-</div><div class="cell">-</div>
                        </div>`);
                    return;
                }
                countries.forEach(cd => {
                    list.insertAdjacentHTML('beforeend',
                        `<div class="table-row clickable" data-country-code="${cd.countryCode}">
                            <div class="cell">${getRankingImg(cd.ranking)}</div>
                            <div class="cell">${getCountryFlagImg(cd.countryCode)}${cd.name}</div>
                            <div class="cell">${getAirlineLogoImg(cd.airlineId)}${cd.airlineName}</div>
                        </div>`);
                });
            })
            .catch(err => console.error('Failed to load championed countries:', err));
    }

    function _updateAllianceHistory(allianceId) {
        const alliance = loadedAlliancesById[allianceId];
        if (!alliance) return;

        const historyEl = document.getElementById('allianceHistory');
        historyEl.querySelectorAll('.table-row').forEach(r => r.remove());

        (alliance.history || []).forEach(entry => {
            historyEl.insertAdjacentHTML('beforeend',
                `<div class="table-row">
                    <div class="cell value" style="width:30%;">Week ${entry.cycle}</div>
                    <div class="cell value" style="width:70%;">${entry.description}</div>
                </div>`);
        });
    }

    function _updateAllianceTagColor(allianceId) {
        if (!activeAirline) return;

        const sidebar = document.getElementById('allianceSidebar');
        const picker = sidebar.querySelector('.picker.tagColor');
        // Replace node to drop previous change listener
        const newPicker = picker.cloneNode(true);
        picker.parentNode.replaceChild(newPicker, picker);
        newPicker.addEventListener('change', function() {
            const newColor = this.value;
            checkAllianceLabelColorAction(allianceId, airlineOverride => {
                setAllianceLabelColor(allianceId, newColor, () => {
                    renderTicker();
                    _updateSidebarBasics(allianceId);
                }, airlineOverride);
            });
        });

        fetch(`/airlines/${activeAirline.id}/alliance-label-color?allianceId=${allianceId}`)
            .then(r => r.json())
            .then(result => {
                newPicker.value = result.color ? `#${result.color}` : '';
            })
            .catch(err => console.error('Failed to load alliance label color:', err));
    }


    // =========================================================================
    // SECTION: Alliance Actions
    // =========================================================================

    function _updateSloganCharCount(input) {
        const MAX = 90;
        const remaining = MAX - input.value.length;
        const countEl = document.getElementById('sloganCharCount');
        if (remaining < 15) {
            countEl.textContent = `${remaining} characters remaining`;
            countEl.style.display = '';
        } else {
            countEl.style.display = 'none';
        }
    }

    function _saveAllianceSlogan(allianceId) {
        const input = document.getElementById('allianceSloganInput');
        const slogan = input.value;
        if (slogan.length > 90) return;

        fetch(`/airlines/${activeAirline.id}/set-alliance-slogan/${allianceId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json; charset=utf-8' },
            body: JSON.stringify({ slogan }),
        })
            .then(r => r.json())
            .then(result => {
                if (result.error) {
                    console.error('Failed to save slogan:', result.error);
                    return;
                }
                if (loadedAlliancesById[allianceId]) {
                    loadedAlliancesById[allianceId].slogan = slogan;
                }
                renderTicker();
            })
            .catch(err => console.error('Failed to save slogan:', err));
    }

    function toggleFormAlliance() {
        document.getElementById('toggleFormAllianceButton').style.display = 'none';
        document.getElementById('formAllianceWarning').style.display = 'none';
        document.getElementById('formAllianceSpan').style.display = '';
    }

    function formAlliance(allianceName) {
        const body = new URLSearchParams({ allianceName });
        fetch(`/airlines/${activeAirline.id}/form-alliance`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString(),
        })
            .then(r => r.json())
            .then(newAlliance => {
                if (!newAlliance.rejection) {
                    show();
                } else {
                    const warningEl = document.getElementById('formAllianceWarning');
                    warningEl.textContent = newAlliance.rejection;
                    warningEl.style.display = '';
                    activeAirline.allianceId = newAlliance.id;
                    activeAirline.allianceName = newAlliance.name;
                    updateChatTabs();
                }
            })
            .catch(err => console.error('Failed to form alliance:', err));
    }

    function _removeAllianceMember(removeAirlineId) {
        fetch(`/airlines/${activeAirline.id}/remove-alliance-member/${removeAirlineId}`, {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json; charset=utf-8' },
        })
            .then(r => r.json())
            .then(() => {
                if (activeAirline.id === removeAirlineId) {
                    activeAirline.allianceId = undefined;
                    activeAirline.allianceName = undefined;
                    updateChatTabs();
                }
                show();
            })
            .catch(err => console.error('Failed to remove alliance member:', err));
    }

    function _acceptAllianceMember(acceptAirlineId) {
        fetch(`/airlines/${activeAirline.id}/accept-alliance-member/${acceptAirlineId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json; charset=utf-8' },
        })
            .then(() => show())
            .catch(err => console.error('Failed to accept alliance member:', err));
    }

    function _promoteAllianceMember(promoteAirlineId) {
        fetch(`/airlines/${activeAirline.id}/promote-alliance-member/${promoteAirlineId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json; charset=utf-8' },
        })
            .then(() => show())
            .catch(err => console.error('Failed to promote alliance member:', err));
    }

    function _demoteAllianceMember(demoteAirlineId) {
        fetch(`/airlines/${activeAirline.id}/demote-alliance-member/${demoteAirlineId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json; charset=utf-8' },
        })
            .then(() => show())
            .catch(err => console.error('Failed to demote alliance member:', err));
    }

    function _applyForAlliance() {
        if (!selectedAllianceId) return;
        fetch(`/airlines/${activeAirline.id}/apply-for-alliance/${selectedAllianceId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json; charset=utf-8' },
        })
            .then(r => r.json())
            .then(result => {
                activeAirline.allianceId = result.allianceId;
                show();
            })
            .catch(err => console.error('Failed to apply for alliance:', err));
    }

    function showAllianceMap() {
        const alliance = selectedAllianceId ? loadedAlliancesById[selectedAllianceId] : null;
        if (!alliance || !alliance.id) return;

        AirlineMap.clearAllPaths();
        AirlineMap.deselectLink();

        $('body .loadingSpinner').show();

        fetch(`/alliances/${alliance.id}/details`, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json; charset=utf-8' },
        })
            .then(r => r.json())
            .then(result => {
                (result.links || []).forEach(link => AirlineMap.drawAllianceLink(link));

                const allianceBases = [];
                (result.members || []).forEach(airline => {
                    if (airline.role !== 'APPLICANT' && Array.isArray(airline.bases)) {
                        allianceBases.push(...airline.bases);
                    }
                });

                AirlineMap.updateAirportBaseMarkers(allianceBases);

                setActiveDiv($('#worldMapCanvas'));

                window.setTimeout(() => {
                    AirlineMap.addExitButton('Exit Alliance Flight Map', () => AirlineMap.hideAllianceMap());
                }, 500);
            })
            .catch(err => {
                console.error('Failed to load alliance map details:', err);
            })
            .finally(() => {
                $('body .loadingSpinner').hide();
            });
    }


    // =========================================================================
    // SECTION: Logo helpers
    // =========================================================================

    function updateAllianceLogo() {
        _logoBust = `?v=${Date.now()}`;
        document.querySelectorAll('.allianceLogo').forEach(img => {
            img.src = `/alliances/${activeAirline.allianceId}/logo${_logoBust}`;
        });
        renderTicker();
    }

    function _showUploadLogoAlliance() {
        if (activeAirline.isAllianceAdmin && activeAirline.reputation >= 60) {
            _updateLogoUploadAlliance()
            $('#uploadLogoModalAlliance .uploadForbidden').hide()
            $('#uploadLogoModalAlliance .uploadPanel').show()
        } else {
            $('#uploadLogoModalAlliance .uploadForbidden .warning').text('You may only upload alliance logo at Reputation 40 or above')
            $('#uploadLogoModalAlliance .uploadForbidden').show()
            $('#uploadLogoModalAlliance .uploadPanel').hide()
        }

        $('#uploadLogoModalAlliance').fadeIn(200)
    }

    function _updateLogoUploadAlliance() {
        const $panel = $('#uploadLogoModalAlliance .uploadPanel');
        const uploadUrl = "/airlines/" + activeAirline.id + "/set-alliance-logo/" + activeAirline.allianceId;

        initLogoUpload($panel, uploadUrl, "logoFile", function(data) {
            closeModal($('#uploadLogoModalAlliance'));
            updateAllianceLogo();
        });
    }

    function _editAllianceLogo() {
        logoModalConfirm = _setAllianceLogoFromTemplate
        $('#logoTemplateIndex').val(0)
        generateLogoPreview()
        $('#logoModal').fadeIn(200)
    }

    function _setAllianceLogoFromTemplate() {
        var logoTemplate = $('#logoTemplateIndex').val()
        var color1 = $('#logoModal .picker.color1').val()
        var color2 = $('#logoModal .picker.color2').val()

        var url = "/airlines/" + activeAirline.id + "/set-alliance-logo-template/" + activeAirline.allianceId
            + "?templateIndex=" + logoTemplate + "&color1=" + encodeURIComponent(color1)
            + "&color2=" + encodeURIComponent(color2)
        $.ajax({
            type: 'POST',
            url: url,
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function() {
                updateAllianceLogo()
            },
            error: function(jqXHR, textStatus, errorThrown) {
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
        });
    }


    // =========================================================================
    // SECTION: Tag-color helpers
    // =========================================================================

    function checkResetAllianceLabelColor(targetAllianceId) {
        checkAllianceLabelColorAction(targetAllianceId, airlineOverride => {
            resetAllianceLabelColor(targetAllianceId, () => {
                renderTicker();
                _updateSidebarBasics(targetAllianceId);
                const sidebar = document.getElementById('allianceSidebar');
                const picker = sidebar.querySelector('.picker.tagColor');
                if (picker) picker.value = '';
            }, airlineOverride);
        });
    }

    function checkAllianceLabelColorAction(targetAllianceId, colorAction) {
        if (activeAirline && activeAirline.isAllianceAdmin) {
            promptSelection(
                'Do you want to apply this to all your alliance members or just your airline?',
                ['Alliance', 'Airline'],
                changeType => colorAction(changeType === 'Airline')
            );
        } else {
            colorAction(true);
        }
    }


    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns a Set of airlineIds that are approved members of the active airline's alliance.
     * Used by routes.js to classify passengers on history paths.
     */
    function getMyAllianceMemberIds() {
        if (!activeAirline || !activeAirline.allianceId) return new Set();
        const alliance = loadedAlliancesById[activeAirline.allianceId];
        if (!alliance) return new Set();
        return new Set(
            alliance.members
                .filter(m => m.allianceRole !== 'Applicant')
                .map(m => m.airlineId)
        );
    }

    return {
        // Entry
        show,

        // Data
        prefetch,
        loadData,

        // Chart + Ticker
        renderChart,
        renderTicker,
        toggleTickerSortOrder,
        toggleAlliance,
        toggleAll,

        // Dropdowns
        toggleDropdown,
        selectPeriod,
        selectMetric,

        // Details
        showSidebar,
        showDetailsModal: showSidebar, // backward compat alias

        // Actions
        toggleFormAlliance,
        formAlliance,
        showAllianceMap,
        updateAllianceLogo,
        checkResetAllianceLabelColor,
        checkAllianceLabelColorAction,

        // State accessors (for external callers)
        get selectedAllianceId() { return selectedAllianceId; },
        get loadedAlliancesById() { return loadedAlliancesById; },

        // Utilities for other modules
        getMyAllianceMemberIds,
    };
})();

window.Alliance = Alliance;

// ─── Backward compatibility ───────────────────────────────────────────────────
function showAllianceCanvas(allianceId) {
    Alliance.show(allianceId);
}
