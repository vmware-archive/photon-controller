(function() {
    $(function() {
        handleUnauthorizedError();
        carryAccessTokenInRequest();
        changeAuthLinks();
    });
}).call(this);

function handleUnauthorizedError() {
    $(document).ajaxError(function (event, jqxhr, settings, thrownError) {
        if (jqxhr.status == 401) {
            login();
        }
    });
}

function carryAccessTokenInRequest() {
    $(document).ajaxSend(function(event, jqxhr, settings) {
        accessToken = getCookie(window.ACCESS_TOKEN_COOKIE_NAME);
        if (accessToken) {
            jqxhr.setRequestHeader(window.AUTHORIZATION_HEADER_NAME, window.ACCESS_TOKEN_PREFIX + accessToken);
        }
    });
}

function changeAuthLinks() {
    if (window.swaggerConfig.enableAuth == false) {
        // if auth is not enabled, let's disable both login and logout links.
        //
        displayAuthLinks(false, false);
    }
    else {
        // if auth is enabled and the access-token cookie is also present,
        // render the login link. Otherwise, render the logout link.
        //
        var cookieValue = window.getCookie(window.ACCESS_TOKEN_COOKIE_NAME);
        if (cookieValue) {
            displayAuthLinks(false, true);
        } else {
            displayAuthLinks(true, false);
        }
    }
}

function displayAuthLinks(loginLink, logoutLink) {
    document.getElementById("login").style.display = (loginLink == true) ? "" : "none";
    document.getElementById("logout").style.display = (logoutLink == true) ? "" : "none";
}
