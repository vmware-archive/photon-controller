(function() {
    $(function() {
        acceptAccessToken();
    });
}).call(this);

function acceptAccessToken() {
    var params = {};
    var regex = /([^&=]+)=([^&]*)/g;
    var m;
    while (m = regex.exec(location.hash.substring(1))) {
        params[decodeURIComponent(m[1])] = m[2];
    }

    if (params[window.LOTUS_URI_ACCESS_TOKEN_NAME]) {
        setCookie(window.ACCESS_TOKEN_COOKIE_NAME, params[window.LOTUS_URI_ACCESS_TOKEN_NAME], 10);
    }

    if (params[window.LOTUS_URI_ID_TOKEN_NAME]) {
        setCookie(window.ID_TOKEN_COOKIE_NAME, params[window.LOTUS_URI_ID_TOKEN_NAME], 10);
    }

    window.location = '/api/';
}
