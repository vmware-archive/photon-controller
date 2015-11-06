window.ACCESS_TOKEN_COOKIE_NAME = "access_token";
window.ACCESS_TOKEN_PREFIX = "Bearer ";
window.AUTHORIZATION_HEADER_NAME = "Authorization";
window.ID_TOKEN_COOKIE_NAME = "id_token";
window.LOTUS_URI_ACCESS_TOKEN_NAME = "access_token";
window.LOTUS_URI_ID_TOKEN_NAME = "id_token";
window.LOGOUT_URL_ID_TOKEN_PLACEHOLDER = "[ID_TOKEN_PLACEHOLDER]";

function login () {
    // redirect to the Login-URL
    window.location = window.swaggerConfig.swaggerLoginUrl;
}

function logout () {
    // expire the access-token cookie and redirect to logout URL
    idToken = getCookie(window.ID_TOKEN_COOKIE_NAME);
    if (idToken) {
        window.location = window.swaggerConfig.swaggerLogoutUrl.replace(
            window.LOGOUT_URL_ID_TOKEN_PLACEHOLDER, idToken);
    }
}

function setCookie(cName, cValue, days) {
    var d = new Date();
    d.setDate(d.getDate() + days);
    var expires = "expires=" + d.toUTCString();

    document.cookie = cName + "=" + cValue + "; " + expires;
}

function getCookie(cName) {
    var cookies = document.cookie.split(";");
    for (var i=0; i<cookies.length; i++) {
        var cookieComponents = cookies[i].split("=");
        var cookieName = cookieComponents[0].trim();
        var cookieValue = "";
        if (cookieComponents[1]) {
            cookieValue = cookieComponents[1].trim();
        }
        if (cookieName === cName) {
            return cookieValue;
        }
    }
    return "";
}
