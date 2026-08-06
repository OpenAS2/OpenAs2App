#!/bin/sh
# Regenerate the runtime config the WebUI reads before booting.
# Runs automatically: nginx's entrypoint sources /docker-entrypoint.d/*.sh.
set -e

: "${OPENAS2_RESTAPI_URL:=http://localhost:8443/api}"

# Escape backslashes and double quotes so the URL is a safe JS string literal.
escaped=$(printf '%s' "$OPENAS2_RESTAPI_URL" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')

cat > /usr/share/nginx/html/config.js <<EOF
window.__OPENAS2_CONFIG__ = { restApiUrl: "${escaped}" };
EOF

echo "openas2-webui: REST API URL set to ${OPENAS2_RESTAPI_URL}"
