(function() {
    $(function() {
        processLogout();
    });
}).call(this);

function processLogout() {
    setCookie(window.ACCESS_TOKEN_COOKIE_NAME, "", -1);
    setCookie(window.ID_TOKEN_COOKIE_NAME, "", -1);
    window.location = '/api/';
}
