:: This script must be run as administrator

:: This adds your opencola ssl cert to the root store, so that https connections are trusted in browsers
certutil -addstore -f "ROOT" opencola-ssl.pem

:: To remove the cert
:: certutil -delstore "ROOT" serial-number-hex
