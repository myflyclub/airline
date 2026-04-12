function initNotificationDrawer() {
    loadNotificationBadge()

    $('#notificationBell, #notificationBellMobile').off('click.notification').on('click.notification', function(e) {
        e.stopPropagation()
        if (!$(e.target).closest('#notificationDrawer').length) {
            toggleNotificationDrawer()
        }
    })

    $(document).off('click.notification').on('click.notification', function(e) {
        if (document.getElementById('notificationDrawer').matches(':popover-open') && !$(e.target).closest('#notificationDrawer, #notificationBell, #notificationBellMobile').length) {
            closeNotificationDrawer()
        }
    })

    $('#notificationDrawer .mark-read-link').off('click.notification').on('click.notification', function(e) {
        e.stopPropagation()
        markAllNotificationsRead()
    })

    $('#notificationDrawer .delete-read-link').off('click.notification').on('click.notification', function(e) {
        e.stopPropagation()
        deleteAllReadNotifications()
    })
}

function toggleNotificationDrawer() {
    var drawer = document.getElementById('notificationDrawer')
    if (drawer.matches(':popover-open')) {
        closeNotificationDrawer()
    } else {
        openNotificationDrawer()
    }
}

function openNotificationDrawer() {
    loadNotifications()
    var bell = document.getElementById('notificationBell')
    var drawer = document.getElementById('notificationDrawer')
    var rect = bell.getBoundingClientRect()
    drawer.style.top = (rect.bottom + 8) + 'px'
    drawer.style.left = (rect.left - 80) + 'px'
    drawer.showPopover()
}

function closeNotificationDrawer() {
    document.getElementById('notificationDrawer').hidePopover()
}

function loadNotificationBadge() {
    if (!activeAirline) return
    $.ajax({
        type: 'GET',
        url: '/airlines/' + activeAirline.id + '/notifications/unread',
        dataType: 'json',
        success: function(data) {
            var count = data.count
            if (count > 0) {
                var label = count > 99 ? '99+' : count
                $('#notificationBell .notify-bubble').text(label).show()
            } else {
                $('#notificationBell .notify-bubble').hide()
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log('Error loading notification badge: ' + textStatus)
        }
    })
}

function loadNotifications() {
    if (!activeAirline) return
    var $list = $('#notificationList')
    $list.empty()
    $.ajax({
        type: 'GET',
        url: '/airlines/' + activeAirline.id + '/notifications',
        dataType: 'json',
        success: function(notifications) {
            if (notifications.length === 0) {
                $list.append('<div class="notification-empty">No notifications</div>')
                return
            }
            var expiredIds = []
            $.each(notifications, function(i, n) {
                if (n.expiryCycle !== undefined && currentCycle && n.expiryCycle <= currentCycle) {
                    expiredIds.push(n.id)
                    return true
                }
                var $item = $('<div class="notification-item"></div>')
                if (!n.isRead) $item.addClass('unread')
                var icon = getCategoryIcon(n.category)
                $item.append('<span class="notification-icon">' + icon + '</span>')
                $item.append('<span class="notification-message">' + htmlEncode(n.message) + '</span>')
                var $del = $('<span class="notification-delete" title="Delete">\xd7</span>')
                ;(function(notifId, $row) {
                    $del.on('click', function(e) {
                        e.stopPropagation()
                        deleteNotification(notifId, $row)
                    })
                })(n.id, $item)
                $item.append($del)
                if (n.targetId) {
                    $item.addClass('clickable')
                    ;(function(notifId, $row, targetId) {
                        $item.on('click', function() {
                            if ($row.hasClass('unread')) {
                                markNotificationRead(notifId, $row)
                            }
                            closeNotificationDrawer()
                            if (/^\d+-\d+$/.test(targetId)) {
                                var parts = targetId.split('-')
                                planLink(parseInt(parts[0]), parseInt(parts[1]))
                            } else if (/^\d+$/.test(targetId)) {
                                resetTableFilters('links')
                                navigateTo('/flights/' + targetId)
                            } else {
                                navigateTo(targetId)
                            }
                        })
                    })(n.id, $item, n.targetId)
                } else {
                    ;(function(notifId, $row) {
                        $item.on('click', function() {
                            if ($row.hasClass('unread')) {
                                markNotificationRead(notifId, $row)
                            }
                        })
                    })(n.id, $item)
                }
                $list.append($item)
            })
            if ($list.children().length === 0) {
                $list.append('<div class="notification-empty">No notifications</div>')
            }
            $.each(expiredIds, function(i, id) { deleteNotification(id, null) })
        },
        error: function(jqXHR, textStatus, errorThrown) {
            $list.append('<div class="notification-empty">Failed to load notifications</div>')
        }
    })
}

function getCategoryIcon(category) {
    switch(category) {
        case 'LEVEL_UP': return '★'
        case 'LOYALIST': return '★'
        case 'GAME_OVER': return '!'
        case 'OLYMPICS_PRIZE': return '🏅'
        case 'TUTORIAL': return '→'
        case 'NEGOTIATION_LOSS':
        case 'LINK_CANCELLATION': return '⚠'
        default: return '•'
    }
}

function markNotificationRead(notifId, $item) {
    if (!activeAirline) return
    $.ajax({
        type: 'POST',
        url: '/airlines/' + activeAirline.id + '/notifications/' + notifId + '/read',
        success: function() {
            $item.removeClass('unread')
            if ($('#notificationList .notification-item.unread').length === 0) {
                $('#notificationBell .notify-bubble').hide()
            }
        }
    })
}

function markAllNotificationsRead() {
    if (!activeAirline) return
    $.ajax({
        type: 'POST',
        url: '/airlines/' + activeAirline.id + '/notifications/read',
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function() {
            $('#notificationBell .notify-bubble').hide()
            $('#notificationList .notification-item').removeClass('unread')
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log('Error marking notifications read: ' + textStatus)
        }
    })
}

function deleteAllReadNotifications() {
    if (!activeAirline) return
    $.ajax({
        type: 'DELETE',
        url: '/airlines/' + activeAirline.id + '/notifications',
        success: function() {
            $('#notificationList .notification-item:not(.unread)').remove()
            var remaining = $('#notificationList .notification-item').length
            if (remaining === 0) {
                $('#notificationList').append('<div class="notification-empty">No notifications</div>')
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log('Error deleting read notifications: ' + textStatus)
        }
    })
}

function deleteNotification(notifId, $item) {
    $.ajax({
        type: 'DELETE',
        url: '/airlines/' + activeAirline.id + '/notifications/' + notifId,
        success: function() {
            if ($item) $item.remove()
            loadNotificationBadge()
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.log('Error deleting notification: ' + textStatus)
        }
    })
}
