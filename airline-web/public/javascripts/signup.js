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

function clearSignupErrors() {
    document.querySelectorAll('#signupForm [id$="Error"]').forEach(el => {
        el.textContent = '';
        el.style.display = 'none';
    });
}

function clearSignupFieldError(fieldId) {
    const errorEl = document.getElementById(fieldId + 'Error');
    if (errorEl) {
        errorEl.textContent = '';
        errorEl.style.display = 'none';
    }
}

function displaySignupErrors(errors) {
    const fieldMap = [
        { keywords: ['username'], fieldId: 'signupPageUserName' },
        { keywords: ['email'], fieldId: 'signupPageEmail' },
        { keywords: ['match', 'passwords'], fieldId: 'signupPagePasswordConfirm' },
        { keywords: ['password'], fieldId: 'signupPagePassword' },
        { keywords: ['airline'], fieldId: 'signupPageAirlineName' },
    ];

    const unmatched = [];
    errors.forEach(error => {
        const lower = error.toLowerCase();
        const match = fieldMap.find(f => f.keywords.some(k => lower.includes(k)));
        if (match) {
            const errorEl = document.getElementById(match.fieldId + 'Error');
            if (errorEl) {
                errorEl.textContent = error;
                errorEl.style.display = 'block';
            }
        } else {
            unmatched.push(error);
        }
    });

    if (unmatched.length) {
        showFloatMessage(unmatched.join(' | '));
    }
}

async function signupFromPage() {
    $('.signup-page-btn').addClass('loading');
    clearSignupErrors();

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
            const errors = data.errors && data.errors.length > 0 ? data.errors
                : data.error ? [data.error]
                : ['Error signing up, error code ' + response.status + '. Please try again.'];
            displaySignupErrors(errors);
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
