function initNotificationDrawer() {
    loadNotificationBadge()

    $('#notificationBell, #notificationBellMobile').off('click.notification').on('click.notification', function(e) {
        e.stopPropagation()
        if (!$(e.target).closest('#notificationDrawer').length) {
            toggleNotificationDrawer()
        }
    })

    $(document).off('click.notification').on('click.notification', function(e) {
        if ($('#notificationDrawer').is(':visible') && !$(e.target).closest('#notificationDrawer, #notificationBell, #notificationBellMobile').length) {
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
    if ($('#notificationDrawer').is(':visible')) {
        closeNotificationDrawer()
    } else {
        openNotificationDrawer()
    }
}

function openNotificationDrawer() {
    loadNotifications()
    $('#notificationDrawer').show()
}

function closeNotificationDrawer() {
    $('#notificationDrawer').hide()
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
                $('.notify-bubble').text(label).show()
            } else {
                $('.notify-bubble').hide()
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
            $.each(notifications, function(i, n) {
                if (n.expiryCycle !== undefined && currentCycle && n.expiryCycle <= currentCycle) {
                    deleteNotification(n.id, null)
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
                    ;(function(targetId) {
                        $item.on('click', function() {
                            closeNotificationDrawer()
                            if (/^\d+$/.test(targetId)) {
                                navigateTo('/flights/' + targetId)
                            } else {
                                navigateTo(targetId)
                            }
                        })
                    })(n.targetId)
                }
                $list.append($item)
            })
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
        case 'LINK_CANCELLATION': return '⚠'
        default: return '•'
    }
}

function markAllNotificationsRead() {
    if (!activeAirline) return
    $.ajax({
        type: 'POST',
        url: '/airlines/' + activeAirline.id + '/notifications/read',
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function() {
            $('.notify-bubble').hide()
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
