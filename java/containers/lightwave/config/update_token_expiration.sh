#!/usr/bin/env bash

admin_password=$1
domain_name=$2
expiration_limit=30000000

# Update tenant token expiration time to expiration limit

token=$(curl -X POST --insecure -sS \
 -d "username=Administrator@${domain_name}&password=${admin_password}&&grant_type=password&scope=openid offline_access id_groups at_groups rs_admin_server" \
https://lightwave.${domain_name}/openidconnect/token | jq -r .access_token)

output_tmp=result.tmp

http_code=$(curl -X GET -w "%{http_code}" --insecure -sS -o ${output_tmp} \
 -H "Authorization: Bearer ${token}" \
 -H "Content-Type: application/json" \
 https://lightwave.${domain_name}/idm/tenant/${domain_name}/config)

if [[ ${http_code} != 2* ]]; then
    cat ${output_tmp}
fi

# Replace expiration limit in the tenant configuration json
rb="\
require 'json';\
_hash=JSON.parse(File.open(\"${output_tmp}\", \"rb\").read);\
_hash[\"tokenPolicy\"][\"maxBearerTokenLifeTimeMillis\"]=${expiration_limit};\
_hash[\"providerPolicy\"][\"defaultProviderAlias\"]=\"localos\";\
puts _hash.to_json"

updated_config=$(ruby -e "${rb}")

http_code=$(curl -X PUT -w "%{http_code}" -o ${output_tmp} --insecure -sS -H "Authorization: Bearer ${token}" \
 -H "Content-Type: application/json" \
 -d "${updated_config}" \
 https://lightwave.${domain_name}/idm/tenant/${domain_name}/config)

 if [[ ${http_code} != 2* ]]; then
    cat ${output_tmp}
 else
    rm -rf $output_tmp
 fi

echo "Updated tenant token expiration time updated to ${expiration_limit}"
