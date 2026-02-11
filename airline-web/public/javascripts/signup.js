function showSignupPage() {
    $('#signupPageOverlay').show();
    $('#signupPageUserName').focus();
}

function hideSignupPage() {
    $('#signupPageOverlay').hide();
}

function passwordSignupPage(e) {
    if (e.keyCode === 13) {
        signupFromPage();
    }
}

async function signupFromPage() {
    $('.signup-page-btn').addClass('loading');

    const username = $('#signupPageUserName').val();
    const email = $('#signupPageEmail').val();
    const password = $('#signupPagePassword').val();
    const passwordConfirm = $('#signupPagePasswordConfirm').val();
    const airlineName = $('#signupPageAirlineName').val();

    try {
        const response = await fetch('/signup/json', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'same-origin',
            body: JSON.stringify({
                username: username,
                email: email,
                password: password,
                passwordConfirm: passwordConfirm,
                airlineName: airlineName
            })
        });

        if (!response.ok) {
            const data = await response.json();
            if (data.errors && data.errors.length > 0) {
                showFloatMessage(data.errors.join(' | '));
            } else if (data.error) {
                showFloatMessage(data.error);
            } else {
                showFloatMessage('Error signing up, error code ' + response.status + '. Please try again.');
            }
            $('.signup-page-btn').removeClass('loading');
            return;
        }

        const user = await response.json();

        if (user) {
            localStorage.setItem('sessionActive', 'true');
            $('#signupPageUserName').val('');
            $('#signupPageEmail').val('');
            $('#signupPagePassword').val('');
            $('#signupPagePasswordConfirm').val('');
            $('#signupPageAirlineName').val('');

            showFloatMessage('Account created successfully!');
            await loadPostLoginScripts();
            await ensureFullBoot();

            await doPostLoginSetup(user);

            navigateTo('/map/');
        }

        $('.signup-page-btn').removeClass('loading');
    } catch (err) {
        showFloatMessage('Error signing up, please try again.');
        console.error(err);
        $('.signup-page-btn').removeClass('loading');
    }
}
